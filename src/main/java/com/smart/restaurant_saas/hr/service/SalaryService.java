package com.smart.restaurant_saas.hr.service;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.hr.dto.request.CreateSalaryRequest;
import com.smart.restaurant_saas.hr.dto.response.SalaryResponse;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.Salary;
import com.smart.restaurant_saas.hr.repository.SalaryRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalaryService {

    private final CurrentTenantProvider currentTenantProvider;
    private final HrValidationService hrValidationService;
    private final SalaryRepository salaryRepository;

    @Transactional(readOnly = true)
    public List<SalaryResponse> listSalaries(Long employeeId) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, employeeId);
        return salaryRepository.findByTenantIdAndEmployeeIdOrderByEffectiveFromDescIdDesc(
                        tenantId,
                        employee.getId()
                )
                .stream()
                .map(SalaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SalaryResponse getCurrentSalary(Long employeeId) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, employeeId);
        return salaryRepository.findByTenantIdAndEmployeeIdAndActiveTrue(tenantId, employee.getId())
                .map(SalaryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Current salary not found for employee: " + employeeId,
                        ErrorParams.of("entityType", "Salary", "entityId", employeeId)));
    }

    @Transactional
    public SalaryResponse createSalary(Long employeeId, CreateSalaryRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, employeeId);

        if (request.effectiveFrom().isBefore(employee.getHireDate())) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "effectiveFrom must not precede the employee hire date",
                    ErrorParams.of("field", "effectiveFrom", "hireDate", employee.getHireDate()));
        }

        salaryRepository.findByTenantIdAndEmployeeIdAndActiveTrue(tenantId, employee.getId())
                .ifPresent(currentSalary -> {
                    closeCurrentSalary(currentSalary, request.effectiveFrom());
                    // Release the partial unique index before the IDENTITY insert executes.
                    salaryRepository.flush();
                });

        Salary salary = new Salary();
        salary.setTenantId(tenantId);
        salary.setEmployeeId(employee.getId());
        salary.setBranchId(employee.getBranchId());
        salary.setSalaryAmount(request.salaryAmount());
        salary.setEffectiveFrom(request.effectiveFrom());
        salary.setActive(true);
        salary.setNotes(trimToNull(request.notes()));
        salary.setCreatedBy(currentTenantProvider.getActorUserId());

        return SalaryResponse.from(salaryRepository.save(salary));
    }

    private void closeCurrentSalary(Salary currentSalary, LocalDate newEffectiveFrom) {
        if (!newEffectiveFrom.isAfter(currentSalary.getEffectiveFrom())) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "effectiveFrom must be after the current active salary effectiveFrom",
                    ErrorParams.of("field", "effectiveFrom"));
        }
        currentSalary.setEffectiveTo(newEffectiveFrom.minusDays(1));
        currentSalary.setActive(false);
        currentSalary.setUpdatedBy(currentTenantProvider.getActorUserId());
    }
}
