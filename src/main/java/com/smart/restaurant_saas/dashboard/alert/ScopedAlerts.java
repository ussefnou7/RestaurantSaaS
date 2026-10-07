package com.smart.restaurant_saas.dashboard.alert;

import com.smart.restaurant_saas.common.ScopedConditionAggregate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Turns the {@link ScopedConditionAggregate} rows a query returns into {@link AlertOccurrence}s. */
public final class ScopedAlerts {

    private ScopedAlerts() {
    }

    /** Maps rows for a per-warehouse condition, linking to the warehouse's own screen filter. */
    public static List<AlertOccurrence> perWarehouse(
            AlertCode code,
            List<ScopedConditionAggregate> rows,
            Function<ScopedConditionAggregate, Map<String, Object>> extraParams) {
        return rows.stream()
            .map(row -> new AlertOccurrence(
                code,
                row.getBranchId(),
                row.getWarehouseId(),
                count(row),
                row.getTotalValue(),
                row.getOldestAt(),
                params(row, extraParams),
                Map.of("warehouseId", row.getWarehouseId())))
            .toList();
    }

    /** Maps rows for a per-branch condition — shifts, orders — linking on {@code branchId}. */
    public static List<AlertOccurrence> perBranch(
            AlertCode code,
            List<ScopedConditionAggregate> rows,
            Function<ScopedConditionAggregate, Map<String, Object>> extraParams) {
        return rows.stream()
            .map(row -> new AlertOccurrence(
                code,
                row.getBranchId(),
                null,
                count(row),
                row.getTotalValue(),
                row.getOldestAt(),
                params(row, extraParams),
                // A branchless row would link to an unfiltered list rather than to nothing,
                // which is the better failure: the owner still reaches the screen.
                row.getBranchId() == null ? Map.of() : Map.of("branchId", row.getBranchId())))
            .toList();
    }

    /** No extra parameters beyond count and value. */
    public static Function<ScopedConditionAggregate, Map<String, Object>> noExtras() {
        return row -> Map.of();
    }

    /** A null count is treated as zero, which {@code AlertService} then drops. */
    private static long count(ScopedConditionAggregate row) {
        return row.getItemCount() == null ? 0L : row.getItemCount();
    }

    private static Map<String, Object> params(
            ScopedConditionAggregate row,
            Function<ScopedConditionAggregate, Map<String, Object>> extraParams) {
        // LinkedHashMap rather than Map.of: the value is legitimately absent on many codes, and
        // Map.of rejects a null rather than carrying the absence.
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("count", count(row));
        if (row.getTotalValue() != null) {
            params.put("value", row.getTotalValue());
        }
        params.putAll(extraParams.apply(row));
        return Map.copyOf(params);
    }
}
