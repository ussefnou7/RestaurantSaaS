package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.inventory.core.UomConversionService;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.reports.dto.ShrinkageRow;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Which materials came up short at physical counts, and what did that cost?"
 *
 * <p>The gap has <b>no known cause</b> by definition (D89): theft, over-portioning, short delivery,
 * and unrecorded waste all land here identically. That is precisely why it is worth reporting —
 * nothing else in the system explains it. {@code WasteAnalysisReportService} answers the other
 * half, where the cause <i>is</i> recorded. Same query shape, different {@code reference_type}.
 *
 * <p><b>Read-only (D4).</b> Nothing on this path writes to {@code inventory_transaction}.
 */
@Service
@RequiredArgsConstructor
public class ShrinkageReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final InventoryTransactionRepository transactionRepository;
    private final MaterialRepository materialRepository;
    private final UomConversionService uomConversionService;
    private final TenantTimeZoneService tenantTimeZoneService;

    /**
     * Net physical-count variance per material over {@code [dateFrom, dateTo]} (calendar days, both
     * inclusive), sorted by absolute value descending.
     *
     * <p><b>Sorted by absolute value, not by quantity.</b> 7 kg of chicken matters more than 70 kg
     * of ice, and only the value says so. Absolute so a large <i>positive</i> variance surfaces too —
     * a surplus usually reveals a wrong recipe or a rushed count, which is worth knowing.
     *
     * <p>{@code warehouseId} and {@code categoryId} are optional and AND-ed; an absent warehouse
     * means all warehouses. {@code negativesOnly} narrows to net shortages. An empty window returns
     * an empty list, not an error; an inverted one is rejected (see {@link ReportDateRange}).
     *
     * <p><b>Two queries, regardless of how many materials the window covers</b>: one aggregate in
     * stock UOM, then one batched material load to resolve the conversion pairs.
     */
    @Transactional(readOnly = true)
    public List<ShrinkageRow> shrinkage(Long tenantId, LocalDate dateFrom, LocalDate dateTo,
                                        Long warehouseId, Long categoryId, boolean negativesOnly) {
        ReportDateRange range = ReportDateRange.of(dateFrom, dateTo, tenantTimeZoneService.zoneFor(tenantId));

        List<ShrinkageAggregate> aggregates = transactionRepository.aggregateShrinkage(
            tenantId,
            PhysicalCountService.REFERENCE_TYPE,
            range.fromInclusive(),
            range.toExclusive(),
            warehouseId,
            categoryId,
            negativesOnly);

        if (aggregates.isEmpty()) {
            return List.of();
        }

        List<Long> materialIds = aggregates.stream().map(ShrinkageAggregate::materialId).toList();
        Map<Long, Material> materials = materialRepository
            .findAllWithUomsByIdIn(tenantId, materialIds).stream()
            .collect(Collectors.toMap(Material::getId, Function.identity()));

        return aggregates.stream()
            .map(aggregate -> toRow(aggregate, materials.get(aggregate.materialId()), tenantId))
            .toList();
    }

    private ShrinkageRow toRow(ShrinkageAggregate aggregate, Material material, Long tenantId) {
        ReportQuantity quantity = ReportQuantity.of(
            uomConversionService, aggregate.netStockQuantity(), material, tenantId);

        return ShrinkageRow.builder()
            .materialId(aggregate.materialId())
            .materialCode(aggregate.materialCode())
            .materialName(aggregate.materialName())
            .materialNameAr(aggregate.materialNameAr())
            .materialActive(aggregate.materialActive())
            .netQuantity(quantity.text())
            .uomId(quantity.uomId())
            .uomSymbol(quantity.uomSymbol())
            .netValue(aggregate.netValue().setScale(SCALE, ROUNDING).toPlainString())
            .movementCount(aggregate.movementCount())
            .build();
    }
}
