package com.smart.restaurant_saas.dashboard.alert;

import com.smart.restaurant_saas.common.ScopedConditionAggregate;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionBatchingProperties;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionRepository;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionMaterialRepository;
import com.smart.restaurant_saas.inventory.orderconsumption.UnpostedConsumptionCostRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Dashboard checks sharing the same domain repositories. */
@Component
@RequiredArgsConstructor
public class ConsumptionAlerts {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private final OrderConsumptionRepository consumptionRepository;
    private final OrderConsumptionMaterialRepository materialRowRepository;
    private final OrderConsumptionBatchingProperties batchingProperties;

    public List<AlertOccurrence> unpostedConsumption(AlertContext context) {
        List<ScopedConditionAggregate> docs = consumptionRepository
            .aggregateUnpostedConsumption(context.tenantId(), context.branchFilter());
        if (docs.isEmpty()) {
            return List.of();
        }

        Map<Long, BigDecimal> valueByWarehouse = pricedByWarehouse(context);

        return docs.stream()
            .map(doc -> new AlertOccurrence(
                AlertCode.UNPOSTED_CONSUMPTION,
                doc.getBranchId(),
                doc.getWarehouseId(),
                doc.getItemCount(),
                valueByWarehouse.get(doc.getWarehouseId()),
                doc.getOldestAt(),
                Map.of("docCount", doc.getItemCount()),

                Map.of("warehouseId", doc.getWarehouseId(), "status", "PARTIAL,CONFLICT")))
            .toList();
    }

    private Map<Long, BigDecimal> pricedByWarehouse(AlertContext context) {
        List<UnpostedConsumptionCostRow> rows =
            materialRowRepository.findUnpostedCostRows(context.tenantId(), context.branchFilter());
        if (rows.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> byWarehouse = new HashMap<>();
        for (UnpostedConsumptionCostRow row : rows) {
            BigDecimal value = valueFor(row);
            if (value != null) {
                byWarehouse.merge(row.getWarehouseId(), value, BigDecimal::add);
            }
        }
        return byWarehouse;
    }

    private BigDecimal valueFor(UnpostedConsumptionCostRow row) {
        if (row.getAverageCost() == null || row.getRequiredQuantity() == null) return null;
        // Both are display-UOM values (D87); converting to stock-UOM would inflate this cost.
        return row.getRequiredQuantity().multiply(row.getAverageCost()).setScale(SCALE, ROUNDING);
    }

    public List<AlertOccurrence> consumptionSchedulerStalled(AlertContext context) {
        long overdueHours = batchingProperties.getMaxAge().toHours()
            + AlertThresholds.CONSUMPTION_OVERDUE_GRACE_HOURS;

        return ScopedAlerts.perWarehouse(
            AlertCode.CONSUMPTION_SCHEDULER_STALLED,
            consumptionRepository.aggregateOverduePendingConsumption(
                context.tenantId(), context.branchFilter(), context.hoursAgo(overdueHours)),
            row -> Map.of("overdueHours", overdueHours));
    }
}
