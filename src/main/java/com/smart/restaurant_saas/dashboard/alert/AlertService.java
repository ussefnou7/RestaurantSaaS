package com.smart.restaurant_saas.dashboard.alert;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.dashboard.alert.dto.DashboardAlertGroup;
import com.smart.restaurant_saas.dashboard.alert.dto.DashboardAlertRow;
import com.smart.restaurant_saas.dashboard.alert.dto.DashboardAlertsResponse;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates permitted checks and groups their results; failures propagate to the caller. */
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final StockAlerts stockAlerts;
    private final ConsumptionAlerts consumptionAlerts;
    private final ShiftAlerts shiftAlerts;
    private final OrderAlerts orderAlerts;
    private final DocumentAlerts documentAlerts;

    private final SecurityService securityService;
    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public DashboardAlertsResponse alerts(Long tenantId, Long requestedBranchId) {
        ZoneId zone = tenantTimeZoneService.zoneFor(tenantId);
        AlertContext context = new AlertContext(
            tenantId,
            currentUserScopeProvider.resolveBranchFilter(requestedBranchId),
            zone,
            LocalDateTime.now(zone));

        Map<String, Boolean> granted = new HashMap<>();
        List<AlertOccurrence> occurrences = new ArrayList<>();
        Set<AlertCode> withheld = new LinkedHashSet<>();

        collect(occurrences, withheld, granted, () -> stockAlerts.lowStock(context),
            AlertCode.STOCK_OUT, AlertCode.BELOW_MINIMUM);
        collect(occurrences, withheld, granted, () -> stockAlerts.runningOutSoon(context),
            AlertCode.RUNNING_OUT_SOON);
        collect(occurrences, withheld, granted, () -> stockAlerts.expiry(context),
            AlertCode.EXPIRED_STOCK_ON_SHELF, AlertCode.EXPIRING_SOON, AlertCode.MISSING_EXPIRY_DATE);
        collect(occurrences, withheld, granted, () -> consumptionAlerts.unpostedConsumption(context),
            AlertCode.UNPOSTED_CONSUMPTION);
        collect(occurrences, withheld, granted, () -> consumptionAlerts.consumptionSchedulerStalled(context),
            AlertCode.CONSUMPTION_SCHEDULER_STALLED);
        collect(occurrences, withheld, granted, () -> shiftAlerts.shiftOpenTooLong(context),
            AlertCode.SHIFT_OPEN_TOO_LONG);
        collect(occurrences, withheld, granted, () -> shiftAlerts.cashVariance(context),
            AlertCode.CASH_VARIANCE);
        collect(occurrences, withheld, granted, () -> shiftAlerts.cashierShortagePattern(context),
            AlertCode.CASHIER_SHORTAGE_PATTERN);
        collect(occurrences, withheld, granted, () -> orderAlerts.cancelledAfterCooking(context),
            AlertCode.CANCELLED_AFTER_COOKING);
        collect(occurrences, withheld, granted, () -> orderAlerts.silentBranch(context),
            AlertCode.BRANCH_SILENT);
        collect(occurrences, withheld, granted, () -> documentAlerts.unpostedPurchaseInvoice(context),
            AlertCode.UNPOSTED_PURCHASE_INVOICE);
        collect(occurrences, withheld, granted, () -> documentAlerts.frozenPhysicalCount(context),
            AlertCode.FROZEN_PHYSICAL_COUNT);
        collect(occurrences, withheld, granted, () -> documentAlerts.largeCountVariance(context),
            AlertCode.LARGE_COUNT_VARIANCE);
        collect(occurrences, withheld, granted, () -> documentAlerts.supplierPriceJump(context),
            AlertCode.SUPPLIER_PRICE_JUMP);

        return DashboardAlertsResponse.builder()
            .groups(toGroups(occurrences, tenantId))
            .withheld(List.copyOf(withheld))
            .evaluatedAt(context.now())
            .build();
    }

    // Fail the request if a check fails: an unperformed check must never look like all-clear.
    // A SQL failure also aborts the PostgreSQL transaction, so continuing it is not safe.
    private void collect(List<AlertOccurrence> occurrences, Set<AlertCode> withheld,
                         Map<String, Boolean> granted, Supplier<List<AlertOccurrence>> check,
                         AlertCode... codes) {
        Set<AlertCode> allowed = new LinkedHashSet<>();
        for (AlertCode code : codes) {
            if (granted.computeIfAbsent(code.getRequiredPermission(), securityService::hasPermission)) {
                allowed.add(code);
            } else {
                withheld.add(code);
            }
        }
        if (!allowed.isEmpty()) {
            check.get().stream()
                .filter(row -> allowed.contains(row.code()) && row.count() > 0)
                .forEach(occurrences::add);
        }
    }

    private List<DashboardAlertGroup> toGroups(List<AlertOccurrence> occurrences, Long tenantId) {
        if (occurrences.isEmpty()) {
            return List.of();
        }

        Map<Long, Branch> branches = resolveBranches(occurrences, tenantId);
        Map<Long, Warehouse> warehouses = resolveWarehouses(occurrences, tenantId);

        Map<AlertCode, List<AlertOccurrence>> byCode = occurrences.stream()
            .collect(Collectors.groupingBy(AlertOccurrence::code,
                () -> new EnumMap<>(AlertCode.class), Collectors.toList()));

        return byCode.entrySet().stream()
            .map(entry -> toGroup(entry.getKey(), entry.getValue(), branches, warehouses))
            .sorted(groupOrder())
            .toList();
    }

    private DashboardAlertGroup toGroup(AlertCode code, List<AlertOccurrence> occurrences,
                                        Map<Long, Branch> branches,
                                        Map<Long, Warehouse> warehouses) {
        BigDecimal total = occurrences.stream()
            .map(AlertOccurrence::value)
            .filter(Objects::nonNull)
            .reduce(BigDecimal::add)
            .orElse(null);

        return DashboardAlertGroup.builder()
            .code(code)
            .severity(code.getSeverity())
            .count(occurrences.stream().mapToLong(AlertOccurrence::count).sum())
            .value(money(total))
            .valueIsEstimate(code.isValueIsEstimate())

            .oldestAt(!code.isHasAge() ? null : occurrences.stream()
                .map(AlertOccurrence::oldestAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null))
            .link(code.getLink())
            .rows(occurrences.stream()
                .map(occurrence -> toRow(code, occurrence, branches, warehouses))
                .sorted(rowOrder())
                .toList())
            .build();
    }

    private DashboardAlertRow toRow(AlertCode code, AlertOccurrence occurrence,
                                    Map<Long, Branch> branches, Map<Long, Warehouse> warehouses) {
        Branch branch = occurrence.branchId() == null ? null : branches.get(occurrence.branchId());
        Warehouse warehouse =
            occurrence.warehouseId() == null ? null : warehouses.get(occurrence.warehouseId());

        return DashboardAlertRow.builder()
            .branchId(occurrence.branchId())
            .branchName(branch == null ? null : branch.getName())
            .branchNameAr(branch == null ? null : branch.getNameAr())
            .warehouseId(occurrence.warehouseId())
            .warehouseName(warehouse == null ? null : warehouse.getName())
            .warehouseNameAr(warehouse == null ? null : warehouse.getNameAr())
            .count(occurrence.count())
            .value(money(occurrence.value()))

            .oldestAt(code.isHasAge() ? occurrence.oldestAt() : null)
            .params(occurrence.params())
            .link(code.getLink())
            .linkParams(occurrence.linkParams())
            .build();
    }

    private Comparator<DashboardAlertGroup> groupOrder() {
        return Comparator
            .comparingInt((DashboardAlertGroup g) -> g.getSeverity().ordinal())
            .thenComparing(DashboardAlertGroup::getValue, DESCENDING_MONEY)
            .thenComparing(Comparator.comparingLong(DashboardAlertGroup::getCount).reversed())

            .thenComparing(g -> g.getCode().name());
    }

    private Comparator<DashboardAlertRow> rowOrder() {
        return Comparator
            .comparing(DashboardAlertRow::getValue, DESCENDING_MONEY)
            .thenComparing(Comparator.comparingLong(DashboardAlertRow::getCount).reversed())
            .thenComparing(DashboardAlertRow::getBranchName, NULLABLE_TEXT)
            .thenComparing(DashboardAlertRow::getWarehouseName, NULLABLE_TEXT);
    }

    private static final Comparator<String> DESCENDING_MONEY = Comparator.nullsLast(
        Comparator.comparing((String value) -> new BigDecimal(value)).reversed());

    private static final Comparator<String> NULLABLE_TEXT =
        Comparator.nullsLast(Comparator.<String>naturalOrder());

    private Map<Long, Branch> resolveBranches(List<AlertOccurrence> occurrences, Long tenantId) {
        List<Long> ids = occurrences.stream()
            .map(AlertOccurrence::branchId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return branchRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
            .collect(Collectors.toMap(Branch::getId, Function.identity()));
    }

    private Map<Long, Warehouse> resolveWarehouses(List<AlertOccurrence> occurrences, Long tenantId) {
        List<Long> ids = occurrences.stream()
            .map(AlertOccurrence::warehouseId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
            .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    private String money(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING).toPlainString();
    }
}
