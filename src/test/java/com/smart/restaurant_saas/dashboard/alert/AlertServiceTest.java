package com.smart.restaurant_saas.dashboard.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.dashboard.DashboardPermissions;
import com.smart.restaurant_saas.dashboard.alert.dto.DashboardAlertGroup;
import com.smart.restaurant_saas.dashboard.alert.dto.DashboardAlertsResponse;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Sorting, totals, permissions and failure behavior of the assembled alert response. */
class AlertServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private SecurityService securityService;
    private CurrentUserScopeProvider scopeProvider;
    private BranchRepository branchRepository;
    private WarehouseRepository warehouseRepository;

    @BeforeEach
    void setUp() {
        securityService = mock(SecurityService.class);
        scopeProvider = mock(CurrentUserScopeProvider.class);
        branchRepository = mock(BranchRepository.class);
        warehouseRepository = mock(WarehouseRepository.class);

        when(securityService.hasPermission(any())).thenReturn(true);
        when(scopeProvider.resolveBranchFilter(any())).thenReturn(null);
        when(branchRepository.findByTenantIdAndIdIn(anyLong(), any())).thenReturn(List.of());
        when(warehouseRepository.findByTenantIdAndIdIn(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("groups are ordered by severity first, so expired food outranks bigger money")
    void severityOutranksValue() {
        DashboardAlertsResponse response = serviceWith(
            // 40,000 of unposted cost — a bookkeeping error.
            rule(AlertCode.UNPOSTED_CONSUMPTION, occurrence(
                AlertCode.UNPOSTED_CONSUMPTION, 1L, 11L, 4, "40000", null)),
            // 400 of expired chicken — can be served to a customer.
            rule(AlertCode.EXPIRED_STOCK_ON_SHELF, occurrence(
                AlertCode.EXPIRED_STOCK_ON_SHELF, 1L, 11L, 2, "400", null)),
            // An INFO note worth more money than either.
            rule(AlertCode.SUPPLIER_PRICE_JUMP, occurrence(
                AlertCode.SUPPLIER_PRICE_JUMP, null, null, 9, "90000", null)))
            .alerts(TENANT_ID, null);

        assertThat(response.getGroups())
            .extracting(DashboardAlertGroup::getCode)
            .containsExactly(
                // Both CRITICAL, so money breaks the tie between them...
                AlertCode.UNPOSTED_CONSUMPTION,
                AlertCode.EXPIRED_STOCK_ON_SHELF,
                // ...but no amount of money promotes an INFO above a CRITICAL.
                AlertCode.SUPPLIER_PRICE_JUMP);
    }

    @Test
    @DisplayName("a group's totals are folded from its rows, so the header cannot disagree")
    void groupTotalsFoldFromRows() {
        LocalDateTime older = LocalDateTime.of(2026, 3, 20, 8, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 3, 28, 8, 0);

        DashboardAlertsResponse response = serviceWith(
            rule(AlertCode.UNPOSTED_CONSUMPTION,
                occurrence(AlertCode.UNPOSTED_CONSUMPTION, 1L, 11L, 4, "1200.50", newer),
                occurrence(AlertCode.UNPOSTED_CONSUMPTION, 2L, 12L, 3, "800.25", older)))
            .alerts(TENANT_ID, null);

        DashboardAlertGroup group = response.getGroups().getFirst();
        assertThat(group.getCount()).isEqualTo(7);
        assertThat(new BigDecimal(group.getValue())).isEqualByComparingTo("2000.75");
        // The earliest, not the latest: "stuck since" is the oldest instance.
        assertThat(group.getOldestAt()).isEqualTo(older);
        assertThat(group.getRows()).hasSize(2);
        // Rows sort by money descending, so the branch worth calling about is first.
        assertThat(group.getRows().getFirst().getValue()).isEqualTo("1200.500000");
    }

    @Test
    @DisplayName("an age is dropped for a code whose source records none, even if a rule sets one")
    void ageIsSuppressedForCodesThatCannotHaveOne() {
        LocalDateTime fabricated = LocalDateTime.of(2026, 3, 28, 8, 0);

        DashboardAlertsResponse response = serviceWith(
            // BELOW_MINIMUM has hasAge = false: stock_balance never recorded when the quantity
            // crossed the line, so any timestamp here would be invented from updatedAt.
            rule(AlertCode.BELOW_MINIMUM,
                occurrence(AlertCode.BELOW_MINIMUM, 1L, 11L, 9, null, fabricated)))
            .alerts(TENANT_ID, null);

        DashboardAlertGroup group = response.getGroups().getFirst();
        assertThat(group.getRows().getFirst().getOldestAt()).isNull();
        // Suppressed on the header too. Nulling the row and keeping the header would put the
        // invented date back in the one place the owner reads first.
        assertThat(group.getOldestAt()).isNull();
        assertThat(group.getCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("an age survives for a code whose source does record one")
    void ageSurvivesForCodesThatHaveOne() {
        LocalDateTime stuckSince = LocalDateTime.of(2026, 3, 28, 8, 0);

        DashboardAlertsResponse response = serviceWith(
            // A PARTIAL document carries createdAt, so this date is read rather than inferred —
            // the counterpart to the suppression above, so the suppression cannot pass by
            // nulling every age.
            rule(AlertCode.UNPOSTED_CONSUMPTION,
                occurrence(AlertCode.UNPOSTED_CONSUMPTION, 1L, 11L, 4, "1200", stuckSince)))
            .alerts(TENANT_ID, null);

        DashboardAlertGroup group = response.getGroups().getFirst();
        assertThat(group.getOldestAt()).isEqualTo(stuckSince);
        assertThat(group.getRows().getFirst().getOldestAt()).isEqualTo(stuckSince);
    }

    @Test
    @DisplayName("a null value stays null — absent money is not zero money")
    void nullValueIsNotCoercedToZero() {
        DashboardAlertsResponse response = serviceWith(
            rule(AlertCode.STOCK_OUT, occurrence(AlertCode.STOCK_OUT, 1L, 11L, 3, null, null)))
            .alerts(TENANT_ID, null);

        DashboardAlertGroup group = response.getGroups().getFirst();
        assertThat(group.getValue()).isNull();
        assertThat(group.getRows().getFirst().getValue()).isNull();
    }

    @Test
    @DisplayName("a zero-count occurrence is dropped rather than rendered as '0 need attention'")
    void zeroCountOccurrencesAreDropped() {
        DashboardAlertsResponse response = serviceWith(
            rule(AlertCode.STOCK_OUT, occurrence(AlertCode.STOCK_OUT, 1L, 11L, 0, null, null)))
            .alerts(TENANT_ID, null);

        assertThat(response.getGroups()).isEmpty();
    }

    @Test
    @DisplayName("a code the caller cannot see is named in withheld, never silently omitted")
    void withheldCodesAreNamed() {
        // Denied the one permission CASH_VARIANCE declares — the real constant, so a rename
        // that decoupled the alert from the shift screen's gate would fail here.
        when(securityService.hasPermission(DashboardPermissions.SHIFTS_VIEW_VARIANCE))
            .thenReturn(false);

        DashboardAlertsResponse response = serviceWith(
            rule(AlertCode.CASH_VARIANCE,
                occurrence(AlertCode.CASH_VARIANCE, 1L, null, 3, "900", null)),
            rule(AlertCode.STOCK_OUT, occurrence(AlertCode.STOCK_OUT, 1L, 11L, 3, null, null)))
            .alerts(TENANT_ID, null);

        // An empty strip means "nothing is wrong" only when withheld is empty too, so the
        // withheld code has to survive to the response rather than being dropped.
        assertThat(response.getWithheld()).containsExactly(AlertCode.CASH_VARIANCE, AlertCode.CASHIER_SHORTAGE_PATTERN);
        assertThat(response.getGroups())
            .extracting(DashboardAlertGroup::getCode)
            .containsExactly(AlertCode.STOCK_OUT);
    }

    @Test
    @DisplayName("failed evaluation must not return a successful all-clear response")
    void aFailingCheckFailsTheRequest() {
        AlertRule broken = new AlertRule() {
            @Override
            public AlertCode code() {
                return AlertCode.UNPOSTED_CONSUMPTION;
            }

            @Override
            public List<AlertOccurrence> evaluate(AlertContext context) {
                throw new IllegalStateException("query failed");
            }
        };

        AlertService service = serviceWith(broken);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.alerts(TENANT_ID, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("query failed");
    }

    @Test
    @DisplayName("branch and warehouse names are resolved onto the rows")
    void scopeNamesAreResolved() {
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setName("Nasr City");
        branch.setNameAr("مدينة نصر");
        when(branchRepository.findByTenantIdAndIdIn(anyLong(), any())).thenReturn(List.of(branch));

        DashboardAlertsResponse response = serviceWith(
            rule(AlertCode.CASH_VARIANCE,
                occurrence(AlertCode.CASH_VARIANCE, 1L, null, 3, "900", null)))
            .alerts(TENANT_ID, null);

        // Rule 4: an alert that does not name its branch is not actionable.
        assertThat(response.getGroups().getFirst().getRows().getFirst().getBranchName())
            .isEqualTo("Nasr City");
        assertThat(response.getGroups().getFirst().getRows().getFirst().getBranchNameAr())
            .isEqualTo("مدينة نصر");
    }

    @Test
    void deniedChecksAreNotEvaluatedAndEveryCodeIsWithheld() {
        when(securityService.hasPermission(any())).thenReturn(false);
        AlertRule forbidden = new AlertRule() {
            public AlertCode code() { return AlertCode.UNPOSTED_CONSUMPTION; }
            public List<AlertOccurrence> evaluate(AlertContext context) {
                throw new AssertionError("A denied check must not read its data");
            }
        };
        var response = serviceWith(forbidden).alerts(TENANT_ID, null);
        assertThat(response.getGroups()).isEmpty();
        assertThat(response.getWithheld()).containsExactlyInAnyOrder(AlertCode.values());
    }

    // ---- fixtures --------------------------------------------------------------------------

    private AlertService serviceWith(AlertRule... rules) {
        TenantTimeZoneService zones = mock(TenantTimeZoneService.class);
        when(zones.zoneFor(TENANT_ID)).thenReturn(CAIRO);

        StockAlerts stockAlerts = mock(StockAlerts.class);
        ConsumptionAlerts consumptionAlerts = mock(ConsumptionAlerts.class);
        ShiftAlerts shiftAlerts = mock(ShiftAlerts.class);
        OrderAlerts orderAlerts = mock(OrderAlerts.class);
        DocumentAlerts documentAlerts = mock(DocumentAlerts.class);
        for (AlertRule rule : rules) {
            switch (rule.code()) {
                case RUNNING_OUT_SOON -> when(stockAlerts.runningOutSoon(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case UNPOSTED_CONSUMPTION -> when(consumptionAlerts.unpostedConsumption(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case CONSUMPTION_SCHEDULER_STALLED -> when(consumptionAlerts.consumptionSchedulerStalled(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case SHIFT_OPEN_TOO_LONG -> when(shiftAlerts.shiftOpenTooLong(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case CASH_VARIANCE -> when(shiftAlerts.cashVariance(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case CASHIER_SHORTAGE_PATTERN -> when(shiftAlerts.cashierShortagePattern(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case CANCELLED_AFTER_COOKING -> when(orderAlerts.cancelledAfterCooking(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case BRANCH_SILENT -> when(orderAlerts.silentBranch(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case UNPOSTED_PURCHASE_INVOICE -> when(documentAlerts.unpostedPurchaseInvoice(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case FROZEN_PHYSICAL_COUNT -> when(documentAlerts.frozenPhysicalCount(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case LARGE_COUNT_VARIANCE -> when(documentAlerts.largeCountVariance(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case SUPPLIER_PRICE_JUMP -> when(documentAlerts.supplierPriceJump(any()))
                    .thenAnswer(call -> rule.evaluate(call.getArgument(0)));
                case STOCK_OUT, BELOW_MINIMUM, EXPIRED_STOCK_ON_SHELF, EXPIRING_SOON, MISSING_EXPIRY_DATE -> { }
            }
        }
        when(stockAlerts.lowStock(any())).thenAnswer(call -> java.util.Arrays.stream(rules)
            .filter(rule -> rule.code() == AlertCode.STOCK_OUT || rule.code() == AlertCode.BELOW_MINIMUM)
            .flatMap(rule -> rule.evaluate(call.getArgument(0)).stream()).toList());
        when(stockAlerts.expiry(any())).thenAnswer(call -> java.util.Arrays.stream(rules)
            .filter(rule -> List.of(AlertCode.EXPIRED_STOCK_ON_SHELF, AlertCode.EXPIRING_SOON,
                AlertCode.MISSING_EXPIRY_DATE).contains(rule.code()))
            .flatMap(rule -> rule.evaluate(call.getArgument(0)).stream()).toList());
        return new AlertService(stockAlerts, consumptionAlerts, shiftAlerts, orderAlerts, documentAlerts,
            securityService, scopeProvider, zones, branchRepository, warehouseRepository);
    }

    private interface AlertRule {
        AlertCode code();
        List<AlertOccurrence> evaluate(AlertContext context);
    }

    private static AlertRule rule(AlertCode code, AlertOccurrence... occurrences) {
        return new AlertRule() {
            @Override
            public AlertCode code() {
                return code;
            }

            @Override
            public List<AlertOccurrence> evaluate(AlertContext context) {
                return List.of(occurrences);
            }
        };
    }

    private static AlertOccurrence occurrence(AlertCode code, Long branchId, Long warehouseId,
                                              long count, String value, LocalDateTime oldestAt) {
        return new AlertOccurrence(
            code, branchId, warehouseId, count,
            value == null ? null : new BigDecimal(value),
            oldestAt, Map.of("count", count),
            warehouseId == null ? Map.of() : Map.of("warehouseId", warehouseId));
    }
}
