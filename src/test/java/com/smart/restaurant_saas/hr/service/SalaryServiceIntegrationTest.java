package com.smart.restaurant_saas.hr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.hr.dto.request.CreateSalaryRequest;
import com.smart.restaurant_saas.hr.repository.SalaryRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SalaryServiceIntegrationTest {
    private static final long TENANT = 986501L;
    private static final long EMPLOYEE = 986502L;
    private static final LocalDate HIRE = LocalDate.of(2025, 1, 1);

    @Autowired private SalaryService service;
    @Autowired private SalaryRepository salaries;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Validator validator;
    @MockitoBean private CurrentTenantProvider tenantProvider;
    @MockitoBean private CurrentUserScopeProvider scopeProvider;

    @BeforeEach
    void seed() {
        when(tenantProvider.getCurrentTenantId()).thenReturn(TENANT);
        jdbc.update("""
                INSERT INTO tenants (id, name, code, status, created_at, timezone)
                VALUES (?, 'HR regression', 'HR_REGRESSION', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
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
                VALUES (?, ?, ?, ?, 'HR-REG-1', 'Salary Employee', ?, 5000, true, CURRENT_TIMESTAMP)
                """, EMPLOYEE, TENANT, EMPLOYEE, EMPLOYEE, HIRE);
    }

    @Test
    void changingSalaryTwiceClosesHistoryBeforeInsertingReplacement() {
        service.createSalary(EMPLOYEE, request("6000", HIRE));
        service.createSalary(EMPLOYEE, request("7000", HIRE.plusMonths(1)));
        service.createSalary(EMPLOYEE, request("6500", HIRE.plusMonths(2)));
        entityManager.flush();
        entityManager.clear();

        var history = salaries.findByTenantIdAndEmployeeIdOrderByEffectiveFromDescIdDesc(TENANT, EMPLOYEE);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getActive()).isTrue();
        assertThat(history.get(0).getEffectiveTo()).isNull();
        assertThat(history.get(1).getActive()).isFalse();
        assertThat(history.get(1).getEffectiveTo()).isEqualTo(HIRE.plusMonths(2).minusDays(1));
        assertThat(history.get(2).getActive()).isFalse();
        assertThat(history.get(2).getEffectiveTo()).isEqualTo(HIRE.plusMonths(1).minusDays(1));
        assertThat(service.getCurrentSalary(EMPLOYEE).salaryAmount()).isEqualByComparingTo("6500");
    }

    @Test
    void sameOrEarlierEffectiveDateDoesNotCloseCurrentSalary() {
        service.createSalary(EMPLOYEE, request("6000", HIRE.plusMonths(1)));
        for (LocalDate date : new LocalDate[]{HIRE, HIRE.plusMonths(1)}) {
            assertThatThrownBy(() -> service.createSalary(EMPLOYEE, request("7000", date)))
                    .isInstanceOf(ValidationException.class);
        }
        assertThat(service.getCurrentSalary(EMPLOYEE).salaryAmount()).isEqualByComparingTo("6000");
        assertThat(salaries.findByTenantIdAndEmployeeIdAndActiveTrue(TENANT, EMPLOYEE).orElseThrow()
                .getEffectiveTo()).isNull();
    }

    @Test
    void firstSalaryCannotPrecedeHire() {
        assertThatThrownBy(() -> service.createSalary(EMPLOYEE, request("6000", HIRE.minusDays(1))))
                .isInstanceOf(ValidationException.class);
        assertThat(salaries.findByTenantIdAndEmployeeIdOrderByEffectiveFromDescIdDesc(TENANT, EMPLOYEE)).isEmpty();
    }

    @Test
    void requestValidationRejectsNonPositiveAmounts() {
        assertThat(validator.validate(request("0", HIRE))).isNotEmpty();
        assertThat(validator.validate(request("-500", HIRE))).isNotEmpty();
    }

    private CreateSalaryRequest request(String amount, LocalDate date) {
        return new CreateSalaryRequest(new BigDecimal(amount), date, null);
    }
}
