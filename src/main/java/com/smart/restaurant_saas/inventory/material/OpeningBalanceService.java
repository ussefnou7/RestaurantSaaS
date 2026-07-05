package com.smart.restaurant_saas.inventory.material;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.core.InventoryLedgerException;
import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.InventoryTransaction;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.material.dto.OpeningBalanceRequest;
import com.smart.restaurant_saas.inventory.material.dto.OpeningBalanceResponse;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;

/**
 * Handles the one-time opening balance entry for a (warehouse, material) pair.
 *
 * Enforces uniqueness via idempotencyKey: "OPENING_{tenantId}_{warehouseId}_{materialId}".
 * If a user tries to re-enter an opening balance, the existing transaction is
 * returned and no new one is created — they must use Adjustment to correct it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpeningBalanceService {

    private static final String IDEMPOTENCY_PREFIX = "OPENING_";

    private final InventoryLedgerService ledgerService;
    private final InventoryTransactionRepository transactionRepository;
    private final MaterialRepository materialRepository;
    private final OpeningBalanceMapper mapper;

    @Transactional
    public OpeningBalanceResponse create(OpeningBalanceRequest req, Long tenantId, Long actingUserId) {
        String key = buildIdempotencyKey(tenantId, req.getWarehouseId(), req.getMaterialId());

        boolean idempotentHit = transactionRepository
            .findByTenantIdAndIdempotencyKey(tenantId, key)
            .isPresent();

        Long resolvedUomId = resolveUomId(req, tenantId);

        LedgerCommand cmd = LedgerCommand.builder()
            .tenantId(tenantId)
            .warehouseId(req.getWarehouseId())
            .materialId(req.getMaterialId())
            .transactionType(InventoryTransactionType.OPENING_BALANCE)
            .direction(InventoryTransactionDirection.IN)
            .enteredQuantity(req.getQuantity())
            .enteredUomId(resolvedUomId)
            .enteredUnitCost(req.getUnitCost())
            .idempotencyKey(key)
            .notes(req.getNotes())
            .createdBy(actingUserId)
            .build();

        InventoryTransaction tx = ledgerService.record(cmd);

        log.info("Opening balance {} for tenant={} warehouse={} material={} qty={}",
            idempotentHit ? "(idempotent-hit)" : "created",
            tenantId, req.getWarehouseId(), req.getMaterialId(), req.getQuantity());

        return mapper.toResponse(tx, idempotentHit);
    }

    @Transactional
    public List<OpeningBalanceResponse> createBulk(List<OpeningBalanceRequest> requests,
                                                    Long tenantId, Long actingUserId) {
        List<OpeningBalanceResponse> responses = new ArrayList<>(requests.size());
        for (OpeningBalanceRequest req : requests) {
            responses.add(create(req, tenantId, actingUserId));
        }
        return responses;
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * If the caller omitted uomId, fall back to the material's displayUom.
     * The material tenant is validated by InventoryLedgerService; loading here
     * is only for UOM defaulting.
     */
    private Long resolveUomId(OpeningBalanceRequest req, Long tenantId) {
        if (req.getUomId() != null) {
            return req.getUomId();
        }
        Material material = materialRepository.findById(req.getMaterialId())
            .orElseThrow(() -> InventoryLedgerException.notFound("Material", req.getMaterialId()));
        return material.getDisplayUom().getId();
    }

    private String buildIdempotencyKey(Long tenantId, Long warehouseId, Long materialId) {
        return IDEMPOTENCY_PREFIX + tenantId + "_" + warehouseId + "_" + materialId;
    }
}
