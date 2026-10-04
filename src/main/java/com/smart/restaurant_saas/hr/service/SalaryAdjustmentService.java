package com.smart.restaurant_saas.hr.service;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.hr.dto.request.CreateSalaryAdjustmentRequest;
import com.smart.restaurant_saas.hr.dto.response.SalaryAdjustmentResponse;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.SalaryAdjustment;
import com.smart.restaurant_saas.hr.entity.Salary;
import com.smart.restaurant_saas.hr.enums.SalaryAdjustmentType;
import com.smart.restaurant_saas.hr.repository.SalaryAdjustmentRepository;
import com.smart.restaurant_saas.hr.repository.SalaryRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalaryAdjustmentService {

    private final CurrentTenantProvider currentTenantProvider;
    private final HrValidationService hrValidationService;
    private final SalaryAdjustmentRepository salaryAdjustmentRepository;
    private final SalaryRepository salaryRepository;
    private final TenantTimeZoneService timeZoneService;

    @Transactional(readOnly = true)
    public List<SalaryAdjustmentResponse> listSalaryAdjustments(Long employeeId) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, employeeId);
        return salaryAdjustmentRepository.findByTenantIdAndEmployeeIdOrderByAdjustmentDateDescIdDesc(
                        tenantId,
                        employee.getId()
                )
                .stream()
                .map(SalaryAdjustmentResponse::from)
                .toList();
    }

    @Transactional
    public SalaryAdjustmentResponse createSalaryAdjustment(Long employeeId, CreateSalaryAdjustmentRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, employeeId);

        LocalDate today = LocalDate.now(timeZoneService.zoneFor(tenantId, employee.getBranchId()));
        if (request.adjustmentDate().isAfter(today)) {
            throw new ValidationException(HrErrorCode.SALARY_ADJUSTMENT_DATE_IN_FUTURE,
                    "Salary adjustment date must not be in the future",
                    ErrorParams.of("adjustmentDate", request.adjustmentDate(), "today", today));
        }
        if (request.adjustmentDate().isBefore(employee.getHireDate())) {
            throw new ValidationException(HrErrorCode.SALARY_ADJUSTMENT_BEFORE_HIRE,
                    "Salary adjustment date must not precede hire date",
                    ErrorParams.of("adjustmentDate", request.adjustmentDate(), "hireDate", employee.getHireDate()));
        }
        if (request.type() == SalaryAdjustmentType.DEDUCTION) {
            // Closed salary rows still govern adjustments within their effective date range.
            BigDecimal salaryAmount = salaryRepository.findEffectiveOn(tenantId, employee.getId(), request.adjustmentDate())
                    .map(Salary::getSalaryAmount)
                    .orElse(employee.getSalary());
            if (request.amount().compareTo(salaryAmount) > 0) {
                throw new ValidationException(HrErrorCode.SALARY_DEDUCTION_EXCEEDS_SALARY,
                        "Deduction exceeds the salary effective on the adjustment date",
                        ErrorParams.of("amount", request.amount(), "salaryAmount", salaryAmount,
                                "adjustmentDate", request.adjustmentDate()));
            }
        }

        SalaryAdjustment adjustment = new SalaryAdjustment();
        adjustment.setTenantId(tenantId);
        adjustment.setEmployeeId(employee.getId());
        adjustment.setBranchId(employee.getBranchId());
        adjustment.setType(request.type());
        adjustment.setAmount(request.amount());
        adjustment.setAdjustmentDate(request.adjustmentDate());
        adjustment.setReason(trimToNull(request.reason()));
        adjustment.setNotes(trimToNull(request.notes()));
        adjustment.setActive(true);
        adjustment.setCreatedBy(currentTenantProvider.getActorUserId());

        return SalaryAdjustmentResponse.from(salaryAdjustmentRepository.save(adjustment));
    }

    @Transactional
    public SalaryAdjustmentResponse cancelSalaryAdjustment(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        SalaryAdjustment adjustment = salaryAdjustmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Salary adjustment not found: " + id,
                        ErrorParams.of("entityType", "SalaryAdjustment", "entityId", id)));
        hrValidationService.ensureCanAccessBranch(adjustment.getBranchId());
        adjustment.setActive(false);
        adjustment.setUpdatedBy(currentTenantProvider.getActorUserId());
        return SalaryAdjustmentResponse.from(salaryAdjustmentRepository.saveAndFlush(adjustment));
    }
}
