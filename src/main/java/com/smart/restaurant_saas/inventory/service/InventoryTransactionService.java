package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.searchPattern;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.dto.command.InventoryTransactionCommand;
import com.smart.restaurant_saas.inventory.dto.request.CreateManualInventoryTransactionRequest;
import com.smart.restaurant_saas.inventory.dto.response.InventoryTransactionResponse;
import com.smart.restaurant_saas.inventory.entity.InventoryTransaction;
import com.smart.restaurant_saas.inventory.entity.Material;
import com.smart.restaurant_saas.inventory.entity.StockBalance;
import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.entity.Warehouse;
import com.smart.restaurant_saas.inventory.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.mapper.InventoryTransactionMapper;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryTransactionService {

    private static final int MONEY_SCALE = 6;
    private static final int QUANTITY_SCALE = 6;

    private final CurrentTenantProvider currentTenantProvider;
    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;
    private final UomRepository uomRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final UomConversionService uomConversionService;
    private final InventoryTransactionMapper transactionMapper;

    @Transactional
    public InventoryTransactionResponse createManualTransaction(CreateManualInventoryTransactionRequest request) {
        InventoryTransactionType transactionType = request.transactionType();
        InventoryTransactionDirection direction = resolveManualDirection(transactionType);
        return createTransaction(new InventoryTransactionCommand(
                request.warehouseId(),
                request.materialId(),
                transactionType,
                direction,
                request.quantity(),
                request.uomId(),
                request.unitCost(),
                null,
                null,
                request.transactionDate(),
                request.notes()
        ));
    }

    @Transactional
    public InventoryTransactionResponse createTransaction(InventoryTransactionCommand command) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        validateCommand(command);

        Material material = materialRepository.findDetailedByIdAndTenantId(command.materialId(), tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material not found: " + command.materialId()));
        if (!Boolean.TRUE.equals(material.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Material is inactive: " + material.getId());
        }

        Warehouse warehouse = warehouseRepository.findDetailedByIdAndTenantId(command.warehouseId(), tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Warehouse not found: " + command.warehouseId()));
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Warehouse is inactive: " + warehouse.getId());
        }

        Uom enteredUom = uomRepository.findById(command.enteredUomId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Entered UOM not found: " + command.enteredUomId()));
        if (!Boolean.TRUE.equals(enteredUom.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Entered UOM is inactive: " + enteredUom.getId());
        }

        Uom stockUom = material.getStockUom();
        BigDecimal enteredQuantity = normalizePositive(command.enteredQuantity(), "enteredQuantity");
        BigDecimal stockQuantity = normalizeConvertedQuantity(uomConversionService.convert(enteredQuantity, enteredUom, stockUom));
        BigDecimal unitCost = normalizeOptionalNonNegative(command.unitCost(), "unitCost");
        BigDecimal totalCost = calculateTotalCost(enteredQuantity, unitCost);
        BigDecimal stockUnitCost = calculateStockUnitCost(stockQuantity, totalCost);

        StockBalance balance = stockBalanceRepository.findForUpdate(
                        tenantId,
                        warehouse.getId(),
                        material.getId()
                )
                .orElseGet(() -> createEmptyBalance(tenantId, warehouse, material, stockUom));
        validateBalanceUom(balance, stockUom);

        BigDecimal oldQuantity = nullToZero(balance.getQuantity());
        BigDecimal oldAverageCost = nullToZero(balance.getAverageCost());
        BigDecimal newQuantity = calculateNewQuantity(oldQuantity, stockQuantity, command.direction());
        BigDecimal newAverageCost = calculateAverageCost(
                oldQuantity,
                oldAverageCost,
                stockQuantity,
                stockUnitCost,
                command.direction()
        );

        balance.setQuantity(scaleQuantity(newQuantity));
        balance.setAverageCost(scaleMoney(newAverageCost));
        balance.setUpdatedAt(LocalDateTime.now());
        stockBalanceRepository.save(balance);

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTenantId(tenantId);
        transaction.setWarehouse(warehouse);
        transaction.setMaterial(material);
        transaction.setTransactionType(command.transactionType());
        transaction.setDirection(command.direction());
        transaction.setEnteredQuantity(scaleQuantity(enteredQuantity));
        transaction.setEnteredUom(enteredUom);
        transaction.setStockQuantity(stockQuantity);
        transaction.setStockUom(stockUom);
        transaction.setUnitCost(unitCost);
        transaction.setTotalCost(totalCost);
        transaction.setReferenceType(trimToNull(command.referenceType()));
        transaction.setReferenceId(command.referenceId());
        transaction.setTransactionDate(command.transactionDate() == null ? LocalDateTime.now() : command.transactionDate());
        transaction.setNotes(trimToNull(command.notes()));
        transaction.setCreatedBy(currentTenantProvider.getActorUserId());

        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> listTransactions(
            Long warehouseId,
            Long materialId,
            Long categoryId,
            String transactionType,
            String direction,
            String dateFrom,
            String dateTo,
            String referenceType,
            String search
    ) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return transactionRepository.findByTenantIdAndFilters(
                        tenantId,
                        warehouseId,
                        materialId,
                        categoryId,
                        parseTransactionType(transactionType),
                        parseDirection(direction),
                        parseDateFrom(dateFrom),
                        parseDateTo(dateTo),
                        trimToNull(referenceType),
                        searchPattern(search)
                ).stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    private InventoryTransactionDirection resolveManualDirection(InventoryTransactionType transactionType) {
        if (transactionType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "transactionType is required");
        }
        return switch (transactionType) {
            case OPENING_BALANCE, MANUAL_IN -> InventoryTransactionDirection.IN;
            case MANUAL_OUT -> InventoryTransactionDirection.OUT;
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Manual transaction type must be one of OPENING_BALANCE, MANUAL_IN, MANUAL_OUT"
            );
        };
    }

    private void validateCommand(InventoryTransactionCommand command) {
        if (command == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction command is required");
        }
        if (command.warehouseId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "warehouseId is required");
        }
        if (command.materialId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "materialId is required");
        }
        if (command.transactionType() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "transactionType is required");
        }
        if (command.direction() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "direction is required");
        }
        if (command.enteredUomId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "uomId is required");
        }
    }

    private StockBalance createEmptyBalance(Long tenantId, Warehouse warehouse, Material material, Uom stockUom) {
        StockBalance balance = new StockBalance();
        balance.setTenantId(tenantId);
        balance.setWarehouse(warehouse);
        balance.setMaterial(material);
        balance.setUom(stockUom);
        balance.setQuantity(BigDecimal.ZERO);
        balance.setAverageCost(BigDecimal.ZERO);
        return balance;
    }

    private void validateBalanceUom(StockBalance balance, Uom stockUom) {
        if (balance.getUom() == null || !balance.getUom().getId().equals(stockUom.getId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Stock balance UOM does not match material default UOM for material: " + balance.getMaterial().getId()
            );
        }
    }

    private BigDecimal calculateNewQuantity(
            BigDecimal oldQuantity,
            BigDecimal stockQuantity,
            InventoryTransactionDirection direction
    ) {
        if (direction == InventoryTransactionDirection.IN) {
            return oldQuantity.add(stockQuantity);
        }
        if (oldQuantity.compareTo(stockQuantity) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock for OUT transaction");
        }
        return oldQuantity.subtract(stockQuantity);
    }

    private BigDecimal calculateAverageCost(
            BigDecimal oldQuantity,
            BigDecimal oldAverageCost,
            BigDecimal stockQuantity,
            BigDecimal stockUnitCost,
            InventoryTransactionDirection direction
    ) {
        if (direction != InventoryTransactionDirection.IN || stockUnitCost == null) {
            return oldAverageCost;
        }
        BigDecimal newQuantity = oldQuantity.add(stockQuantity);
        if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return oldAverageCost;
        }
        BigDecimal oldValue = oldQuantity.multiply(oldAverageCost);
        BigDecimal incomingValue = stockQuantity.multiply(stockUnitCost);
        return oldValue.add(incomingValue).divide(newQuantity, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTotalCost(BigDecimal enteredQuantity, BigDecimal unitCost) {
        if (unitCost == null) {
            return null;
        }
        return scaleMoney(enteredQuantity.multiply(unitCost));
    }

    private BigDecimal calculateStockUnitCost(BigDecimal stockQuantity, BigDecimal totalCost) {
        if (totalCost == null) {
            return null;
        }
        return totalCost.divide(stockQuantity, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private InventoryTransactionType parseTransactionType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return InventoryTransactionType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid inventory transaction type: " + value
                    + ". Allowed values: " + Arrays.toString(InventoryTransactionType.values()));
        }
    }

    private InventoryTransactionDirection parseDirection(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return InventoryTransactionDirection.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid inventory transaction direction: " + value
                    + ". Allowed values: " + Arrays.toString(InventoryTransactionDirection.values()));
        }
    }

    private LocalDateTime parseDateFrom(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return parseDateTime(normalized, false);
    }

    private LocalDateTime parseDateTo(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return parseDateTime(normalized, true);
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDayForDateOnly) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            try {
                LocalDate date = LocalDate.parse(value);
                return endOfDayForDateOnly
                        ? LocalDateTime.of(date, LocalTime.MAX)
                        : date.atStartOfDay();
            } catch (DateTimeParseException dateEx) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid date filter: " + value);
            }
        }
    }

    private BigDecimal normalizePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than 0");
        }
        return value;
    }

    private BigDecimal normalizeConvertedQuantity(BigDecimal value) {
        BigDecimal scaled = scaleQuantity(value);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Converted stock quantity must be greater than 0");
        }
        return scaled;
    }

    private BigDecimal normalizeOptionalNonNegative(BigDecimal value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than or equal to 0");
        }
        return scaleMoney(value);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scaleQuantity(BigDecimal value) {
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
