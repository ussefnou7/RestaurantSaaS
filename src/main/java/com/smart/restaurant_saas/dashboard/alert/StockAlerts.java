package com.smart.restaurant_saas.dashboard.alert;

import com.smart.restaurant_saas.inventory.core.UomConversionService;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionService;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository.DaysOfCoverProjection;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository.LowStockCountProjection;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.uom.Uom;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Stock checks; related conditions share a single aggregate query. */
@Component
@RequiredArgsConstructor
public class StockAlerts {
    private final StockBalanceRepository stockBalanceRepository;
    private final StockBatchRepository stockBatchRepository;
    private final UomRepository uomRepository;
    private final UomConversionService uomConversionService;

    public List<AlertOccurrence> lowStock(AlertContext context) {
        return stockBalanceRepository.aggregateLowStockCounts(context.tenantId(), context.branchFilter())
            .stream().flatMap(row -> Stream.of(
                lowStockOccurrence(AlertCode.STOCK_OUT, row, row.getOutOfStockCount()),
                lowStockOccurrence(AlertCode.BELOW_MINIMUM, row, row.getBelowMinimumCount())))
            .filter(row -> row.count() > 0).toList();
    }

    private AlertOccurrence lowStockOccurrence(AlertCode code, LowStockCountProjection row, Long count) {
        long total = count == null ? 0 : count;
        return new AlertOccurrence(code, row.getBranchId(), row.getWarehouseId(), total, null, null,
            Map.of("count", total), Map.of("warehouseId", row.getWarehouseId()));
    }

    public List<AlertOccurrence> expiry(AlertContext context) {
        return stockBatchRepository.aggregateExpiryConditions(context.tenantId(), context.branchFilter(),
                context.now().toLocalDate(),
                context.now().toLocalDate().plusDays(AlertThresholds.EXPIRING_SOON_DAYS))
            .stream().flatMap(row -> Stream.of(
                new AlertOccurrence(AlertCode.EXPIRED_STOCK_ON_SHELF, row.getBranchId(),
                    row.getWarehouseId(), row.getExpiredCount(), row.getExpiredValue(),
                    row.getOldestExpiryAt(), Map.of("count", row.getExpiredCount()),
                    Map.of("warehouseId", row.getWarehouseId())),
                new AlertOccurrence(AlertCode.EXPIRING_SOON, row.getBranchId(),
                    row.getWarehouseId(), row.getExpiringSoonCount(), row.getExpiringSoonValue(),
                    null, Map.of("count", row.getExpiringSoonCount(),
                        "withinDays", AlertThresholds.EXPIRING_SOON_DAYS),
                    Map.of("warehouseId", row.getWarehouseId())),
                new AlertOccurrence(AlertCode.MISSING_EXPIRY_DATE, row.getBranchId(),
                    row.getWarehouseId(), row.getMissingExpiryCount(), null, null,
                    Map.of("count", row.getMissingExpiryCount()),
                    Map.of("warehouseId", row.getWarehouseId()))))
            .filter(row -> row.count() > 0).toList();
    }

    public List<AlertOccurrence> runningOutSoon(AlertContext context) {
        var rows = stockBalanceRepository.findConsumptionRates(context.tenantId(), context.branchFilter(),
            OrderConsumptionService.REFERENCE_TYPE,
            context.daysAgo(AlertThresholds.DAYS_OF_COVER_WINDOW_DAYS), context.now(),
            AlertThresholds.DAYS_OF_COVER_WINDOW_DAYS);
        if (rows.isEmpty()) return List.of();

        // IDs come from tenant-scoped balances/materials. Include global UOMs too.
        Map<Long, Uom> units = uomRepository.findAllById(rows.stream()
                .flatMap(row -> Stream.of(row.getBalanceUomId(), row.getStockUomId()))
                .distinct().toList()).stream()
            .collect(Collectors.toMap(Uom::getId, Function.identity()));
        List<Cover> low = new ArrayList<>();
        for (var row : rows) {
            // Balance is display-UOM; rate is stock-UOM. Convert the on-hand quantity once.
            BigDecimal stockQuantity = uomConversionService.convert(row.getQuantity(),
                units.get(row.getBalanceUomId()), units.get(row.getStockUomId()), null, context.tenantId());
            if (stockQuantity.compareTo(row.getDailyRate().multiply(AlertThresholds.DAYS_OF_COVER_MAX)) <= 0) {
                low.add(new Cover(row, stockQuantity.divide(row.getDailyRate(), 6, RoundingMode.HALF_UP)));
            }
        }
        return low.stream().collect(Collectors.groupingBy(cover -> cover.row().getWarehouseId()))
            .values().stream().map(warehouse -> {
                Cover first = warehouse.stream().min(Comparator.comparing(Cover::days)
                    .thenComparing(cover -> cover.row().getMaterialId())).orElseThrow();
                var row = first.row();
                return new AlertOccurrence(AlertCode.RUNNING_OUT_SOON, row.getBranchId(),
                    row.getWarehouseId(), warehouse.size(), null, null,
                    Map.of("count", warehouse.size(),
                        "soonestMaterialName", row.getMaterialName() == null ? "" : row.getMaterialName(),
                        "soonestMaterialNameAr", row.getMaterialNameAr() == null ? "" : row.getMaterialNameAr(),
                        "soonestDaysOfCover", first.days().setScale(1, RoundingMode.HALF_UP).toPlainString(),
                        "rateWindowDays", AlertThresholds.DAYS_OF_COVER_WINDOW_DAYS),
                    Map.of("warehouseId", row.getWarehouseId()));
            }).toList();
    }

    private record Cover(DaysOfCoverProjection row, BigDecimal days) { }
}
