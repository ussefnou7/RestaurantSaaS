package com.smart.restaurant_saas.hr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.hr.dto.request.CreateSalaryAdjustmentRequest;
import com.smart.restaurant_saas.hr.enums.SalaryAdjustmentType;
import com.smart.restaurant_saas.hr.repository.SalaryAdjustmentRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SalaryAdjustmentServiceIntegrationTest {
    private static final long TENANT = 986601L;
    private static final long EMPLOYEE = 986602L;
    private static final LocalDate HIRE = LocalDate.of(2025, 1, 1);
    @Autowired private SalaryAdjustmentService service;
    @Autowired private SalaryAdjustmentRepository adjustments;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Validator validator;
    @MockitoBean private CurrentTenantProvider tenantProvider;
    @MockitoBean private CurrentUserScopeProvider scopeProvider;

    @BeforeEach
    void seed() {
        when(tenantProvider.getCurrentTenantId()).thenReturn(TENANT);
        jdbc.update("""
                INSERT INTO tenants (id, name, code, status, created_at, timezone)
                VALUES (?, 'HR adjustments', 'HR_ADJUSTMENTS', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
                """, TENANT);
        jdbc.update("""
                INSERT INTO branches (id, tenant_id, name, code, is_active, created_at, timezone)
                VALUES (?, ?, 'HR Branch', 'HR-BR', true, CURRENT_TIMESTAMP, 'Pacific/Kiritimati')
                """, EMPLOYEE, TENANT);
        jdbc.update("""
                INSERT INTO jobs (id, tenant_id, name, code, is_active, created_at)
                VALUES (?, ?, 'HR Job', 'HR-JOB', true, CURRENT_TIMESTAMP)
                """, EMPLOYEE, TENANT);
        jdbc.update("""
                INSERT INTO hr_employees (id, tenant_id, branch_id, job_id, code, full_name,
                    hire_date, salary, is_active, created_at)
                VALUES (?, ?, ?, ?, 'HR-REG-1', 'Adjustment Employee', ?, 5000, true, CURRENT_TIMESTAMP)
                """, EMPLOYEE, TENANT, EMPLOYEE, EMPLOYEE, HIRE);
    }

    @ParameterizedTest
    @EnumSource(SalaryAdjustmentType.class)
    void rejectsFutureDateWithoutPersisting(SalaryAdjustmentType type) {
        reject(type, "100", today().plusDays(1), "SALARY_ADJUSTMENT_DATE_IN_FUTURE");
    }

    @ParameterizedTest
    @EnumSource(SalaryAdjustmentType.class)
    void rejectsPreHireDateWithoutPersisting(SalaryAdjustmentType type) {
        reject(type, "100", HIRE.minusDays(1), "SALARY_ADJUSTMENT_BEFORE_HIRE");
    }

    @Test
    void rejectsDeductionAboveStartingSalary() {
        reject(SalaryAdjustmentType.DEDUCTION, "5000.01", HIRE, "SALARY_DEDUCTION_EXCEEDS_SALARY");
    }

    @Test
    void deductionUsesHistoricalSalaryEvenThoughItIsNoLongerActive() {
        salary("6000", HIRE.plusMonths(1), HIRE.plusMonths(2).minusDays(1), false);
        salary("7000", HIRE.plusMonths(2), null, true);
        reject(SalaryAdjustmentType.DEDUCTION, "6000.01", HIRE.plusMonths(1),
                "SALARY_DEDUCTION_EXCEEDS_SALARY");
    }

    @Test
    void startingSalaryAppliesBeforeFirstChangeAndNewSalaryOnItsEffectiveDate() {
        salary("6000", HIRE.plusMonths(1), null, true);
        assertThat(service.createSalaryAdjustment(EMPLOYEE,
                request(SalaryAdjustmentType.DEDUCTION, "5000", HIRE.plusMonths(1).minusDays(1))).amount())
                .isEqualByComparingTo("5000");
        assertThat(service.createSalaryAdjustment(EMPLOYEE,
                request(SalaryAdjustmentType.DEDUCTION, "6000", HIRE.plusMonths(1))).amount())
                .isEqualByComparingTo("6000");
    }

    @Test
    void futureSalaryDoesNotIncreaseTodaysDeductionLimit() {
        salary("9000", today().plusDays(10), null, true);
        reject(SalaryAdjustmentType.DEDUCTION, "5000.01", today(), "SALARY_DEDUCTION_EXCEEDS_SALARY");
    }

    @Test
    void additionIsNotCappedAndTodayInBranchZoneIsAllowed() {
        var saved = service.createSalaryAdjustment(EMPLOYEE,
                request(SalaryAdjustmentType.ADDITION, "999999", today()));
        assertThat(saved.amount()).isEqualByComparingTo("999999");
        assertThat(saved.adjustmentDate()).isEqualTo(today());
        assertThat(service.cancelSalaryAdjustment(saved.id()).active()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(SalaryAdjustmentType.class)
    void requestValidationRejectsNonPositiveAmounts(SalaryAdjustmentType type) {
        assertThat(validator.validate(request(type, "0", HIRE))).isNotEmpty();
        assertThat(validator.validate(request(type, "-100", HIRE))).isNotEmpty();
    }

    private void reject(SalaryAdjustmentType type, String amount, LocalDate date, String errorCode) {
        assertThatThrownBy(() -> service.createSalaryAdjustment(EMPLOYEE, request(type, amount, date)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(errorCode));
        assertThat(adjustments.findByTenantIdAndEmployeeIdOrderByAdjustmentDateDescIdDesc(TENANT, EMPLOYEE))
                .isEmpty();
    }

    private void salary(String amount, LocalDate from, LocalDate to, boolean active) {
        jdbc.update("""
                INSERT INTO employee_salaries (tenant_id, employee_id, branch_id, salary_amount,
                    effective_from, effective_to, active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, TENANT, EMPLOYEE, EMPLOYEE, new BigDecimal(amount), from, to, active);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Pacific/Kiritimati"));
    }

    private CreateSalaryAdjustmentRequest request(SalaryAdjustmentType type, String amount, LocalDate date) {
        return new CreateSalaryAdjustmentRequest(type, new BigDecimal(amount), date, null, null);
    }
}
