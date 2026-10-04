package com.smart.restaurant_saas.hr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.hr.dto.request.CreateLeaveRequestRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateLeaveRequestStatusRequest;
import com.smart.restaurant_saas.hr.repository.LeaveBalanceRepository;
import com.smart.restaurant_saas.hr.repository.LeaveRequestRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LeaveRequestServiceIntegrationTest {
    private static final long TENANT = 986701L;
    private static final long EMPLOYEE = 986702L;
    private static final long TYPE = 986703L;
    @Autowired private LeaveRequestService service;
    @Autowired private LeaveBalanceService balanceService;
    @Autowired private LeaveBalanceRepository balances;
    @Autowired private LeaveRequestRepository requests;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @MockitoBean private CurrentTenantProvider tenantProvider;
    @MockitoBean private CurrentUserScopeProvider scopeProvider;

    @BeforeEach
    void seed() {
        when(tenantProvider.getCurrentTenantId()).thenReturn(TENANT);
        jdbc.update("""
                INSERT INTO tenants (id, name, code, status, created_at, timezone)
                VALUES (?, 'HR leave', 'HR_LEAVE', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
                """, TENANT);
        jdbc.update("""
                INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
                VALUES (?, ?, 'HR Branch', 'HR-BR', true, CURRENT_TIMESTAMP)
                """, EMPLOYEE, TENANT);
        jdbc.update("""
                INSERT INTO jobs (id, tenant_id, name, code, is_active, created_at)
                VALUES (?, ?, 'HR Job', 'HR-JOB', true, CURRENT_TIMESTAMP)
                """, EMPLOYEE, TENANT);
        jdbc.update("""
                INSERT INTO hr_employees (id, tenant_id, branch_id, job_id, code, full_name,
                    hire_date, salary, is_active, created_at)
                VALUES (?, ?, ?, ?, 'HR-REG-1', 'Leave Employee', '2025-01-01', 5000, true, CURRENT_TIMESTAMP)
                """, EMPLOYEE, TENANT, EMPLOYEE, EMPLOYEE);
        jdbc.update("""
                INSERT INTO hr_leave_type (id, tenant_id, code, name_en, default_days, paid, active, created_at)
                VALUES (?, ?, 'ANNUAL', 'Annual', 21, true, true, CURRENT_TIMESTAMP)
                """, TYPE, TENANT);
        jdbc.update("""
                INSERT INTO hr_leave_type (id, tenant_id, code, name_en, default_days, paid, active, created_at)
                VALUES (?, ?, 'SICK', 'Sick', 21, true, true, CURRENT_TIMESTAMP)
                """, TYPE + 1, TENANT);
        balanceService.generateMissingBalances(EMPLOYEE, 2026);
    }

    @AfterEach
    void cleanCommittedConcurrencyFixtures() {
        if (!TestTransaction.isActive()) {
            jdbc.update("DELETE FROM hr_leave_request WHERE tenant_id = ?", TENANT);
            jdbc.update("DELETE FROM employee_leave_balances WHERE tenant_id = ?", TENANT);
            jdbc.update("DELETE FROM hr_leave_type WHERE tenant_id = ?", TENANT);
            jdbc.update("DELETE FROM hr_employees WHERE tenant_id = ?", TENANT);
            jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", TENANT);
            jdbc.update("DELETE FROM branches WHERE tenant_id = ?", TENANT);
            jdbc.update("DELETE FROM tenants WHERE id = ?", TENANT);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousOverlappingRequestsApproveOnlyOnce(boolean differentTypes) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createConcurrently(TYPE, ready, start));
            var second = executor.submit(() -> createConcurrently(differentTypes ? TYPE + 1 : TYPE, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("APPROVED", "LEAVE_REQUEST_OVERLAP");
        } finally {
            start.countDown();
        }
        assertThat(requests.findByTenantIdAndEmployeeIdOrderByIdDesc(TENANT, EMPLOYEE)).hasSize(1);
        assertThat(balances.findByTenantIdAndEmployeeIdAndYearOrderByIdAsc(TENANT, EMPLOYEE, 2026).stream()
                .map(balance -> balance.getUsedDays()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("3");
    }

    private String createConcurrently(long type, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }
        try {
            return service.createLeaveRequest(request(type, 10, 12)).status();
        } catch (AppException ex) {
            return ex.getErrorCode().getCode();
        }
    }

    @ParameterizedTest
    @CsvSource({"10,12", "9,10", "12,14", "11,11", "9,13"})
    void overlappingApprovedLeaveIsRejectedWithoutChargingBalance(int from, int to) {
        service.createLeaveRequest(request(TYPE, 10, 12));
        assertThatThrownBy(() -> service.createLeaveRequest(request(TYPE, from, to)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo("LEAVE_REQUEST_OVERLAP"));
        assertUsedDays("3");
        assertThat(requests.findByTenantIdAndEmployeeIdOrderByIdDesc(TENANT, EMPLOYEE)).hasSize(1);
    }

    @Test
    void adjacentRequestsAreAllowed() {
        service.createLeaveRequest(request(TYPE, 10, 12));
        service.createLeaveRequest(request(TYPE, 9, 9));
        service.createLeaveRequest(request(TYPE, 13, 13));
        assertUsedDays("5");
    }

    @Test
    void overlappingDifferentLeaveTypeIsRejectedWithoutChangingEitherBalance() {
        service.createLeaveRequest(request(TYPE, 10, 12));
        assertThatThrownBy(() -> service.createLeaveRequest(request(TYPE + 1, 11, 13)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo("LEAVE_REQUEST_OVERLAP"));
        assertUsedDays("3");
        assertThat(balances.findByTenantIdAndEmployeeIdAndLeaveTypeIdAndYear(TENANT, EMPLOYEE, TYPE + 1, 2026)
                .orElseThrow().getUsedDays()).isEqualByComparingTo("0");
    }

    @Test
    void anotherEmployeeCanTakeTheSameDates() {
        long otherEmployee = EMPLOYEE + 10;
        jdbc.update("""
                INSERT INTO hr_employees (id, tenant_id, branch_id, job_id, code, full_name,
                    hire_date, salary, is_active, created_at)
                VALUES (?, ?, ?, ?, 'HR-REG-2', 'Other Employee', '2025-01-01', 5000, true, CURRENT_TIMESTAMP)
                """, otherEmployee, TENANT, EMPLOYEE, EMPLOYEE);
        balanceService.generateMissingBalances(otherEmployee, 2026);
        service.createLeaveRequest(request(TYPE, 10, 12));
        var saved = service.createLeaveRequest(new CreateLeaveRequestRequest(
                otherEmployee, TYPE, date(10), date(12), null, null));
        assertThat(saved.status()).isEqualTo("APPROVED");
        assertUsedDays("3");
    }

    @Test
    void lockedEmployeeLookupStillEnforcesTenantIsolation() {
        when(tenantProvider.getCurrentTenantId()).thenReturn(TENANT + 100);
        assertThatThrownBy(() -> service.createLeaveRequest(request(TYPE, 10, 12)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo("RESOURCE_NOT_FOUND"));
        assertUsedDays("0");
    }

    @Test
    void lockedEmployeeLookupStillRejectsInactiveEmployees() {
        jdbc.update("UPDATE hr_employees SET is_active = false WHERE id = ?", EMPLOYEE);
        entityManager.clear();
        assertThatThrownBy(() -> service.createLeaveRequest(request(TYPE, 10, 12)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo("INACTIVE_REFERENCE"));
        assertUsedDays("0");
    }

    @Test
    void lockedEmployeeLookupStillEnforcesBranchAccess() {
        doThrow(new AuthorizationException(HrErrorCode.BRANCH_SCOPE_REQUIRED, "Branch denied"))
                .when(scopeProvider).ensureCanAccessBranch(EMPLOYEE);
        assertThatThrownBy(() -> service.createLeaveRequest(request(TYPE, 10, 12)))
                .isInstanceOf(AuthorizationException.class);
        assertUsedDays("0");
    }

    @Test
    void cancelledLeaveNoLongerBlocksDatesAndBalanceIsRestored() {
        var saved = service.createLeaveRequest(request(TYPE, 10, 12));
        assertThat(service.cancelLeaveRequest(saved.id()).status()).isEqualTo("CANCELLED");
        service.cancelLeaveRequest(saved.id());
        assertUsedDays("0");
        service.createLeaveRequest(request(TYPE, 10, 12));
        assertUsedDays("3");
    }

    @Test
    void insufficientBalanceDoesNotCreateLeaveOrChargeDays() {
        assertThatThrownBy(() -> service.createLeaveRequest(request(TYPE, 1, 22)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo("INSUFFICIENT_LEAVE_BALANCE"));
        assertUsedDays("0");
        assertThat(requests.findByTenantIdAndEmployeeIdOrderByIdDesc(TENANT, EMPLOYEE)).isEmpty();
    }

    @Test
    void rejectsReversedDatesAndMismatchedDays() {
        assertThatThrownBy(() -> service.createLeaveRequest(request(TYPE, 12, 10)))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.createLeaveRequest(new CreateLeaveRequestRequest(
                EMPLOYEE, TYPE, date(10), date(12), BigDecimal.valueOf(99), null)))
                .isInstanceOf(AppException.class);
        assertUsedDays("0");
    }

    @Test
    void rejectingApprovedLeaveRemainsUnsupported() {
        var saved = service.createLeaveRequest(request(TYPE, 10, 12));
        assertThatThrownBy(() -> service.updateLeaveRequestStatus(saved.id(),
                new UpdateLeaveRequestStatusRequest("REJECTED", null)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo("UNSUPPORTED_OPERATION"));
        assertUsedDays("3");
    }

    private void assertUsedDays(String used) {
        entityManager.flush();
        entityManager.clear();
        var balance = balances.findByTenantIdAndEmployeeIdAndLeaveTypeIdAndYear(TENANT, EMPLOYEE, TYPE, 2026)
                .orElseThrow();
        assertThat(balance.getUsedDays()).isEqualByComparingTo(used);
        assertThat(balance.getRemainingDays()).isEqualByComparingTo(new BigDecimal("21").subtract(new BigDecimal(used)));
    }

    private LocalDate date(int day) {
        return LocalDate.of(2026, 12, day);
    }

    private CreateLeaveRequestRequest request(long type, int from, int to) {
        return new CreateLeaveRequestRequest(EMPLOYEE, type, date(from), date(to), null, null);
    }
}
