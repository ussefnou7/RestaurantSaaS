package com.smart.restaurant_saas.hr.service;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.dto.request.CreateSalaryAdditionRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateSalaryAdditionRequest;
import com.smart.restaurant_saas.hr.dto.response.SalaryAdditionResponse;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.SalaryAddition;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.hr.repository.SalaryAdditionRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalaryAdditionService {

    private final CurrentTenantProvider currentTenantProvider;
    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final HrValidationService hrValidationService;
    private final SalaryAdditionRepository salaryAdditionRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<SalaryAdditionResponse> listSalaryAdditions() {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        List<SalaryAddition> salaryAdditions = currentUserScopeProvider.isTenantScoped()
                ? salaryAdditionRepository.findByTenantIdOrderByIdDesc(tenantId)
                : salaryAdditionRepository.findByTenantIdAndBranchIdOrderByIdDesc(
                        tenantId,
                        currentUserScopeProvider.getCurrentBranchId()
                                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Branch scope is required"))
                );
        return salaryAdditions.stream()
                .map(salaryAddition -> toResponse(tenantId, salaryAddition))
                .toList();
    }

    @Transactional
    public SalaryAdditionResponse createSalaryAddition(CreateSalaryAdditionRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, request.employeeId());

        SalaryAddition salaryAddition = new SalaryAddition();
        salaryAddition.setTenantId(tenantId);
        salaryAddition.setBranchId(employee.getBranchId());
        salaryAddition.setEmployeeId(employee.getId());
        salaryAddition.setTitle(request.title().trim());
        salaryAddition.setAmount(request.amount());
        salaryAddition.setSalaryMonth(request.salaryMonth().withDayOfMonth(1));
        salaryAddition.setNotes(trimToNull(request.notes()));
        salaryAddition.setActive(request.active() == null || request.active());
        salaryAddition.setCreatedBy(currentTenantProvider.getActorUserId());

        return SalaryAdditionResponse.from(salaryAdditionRepository.save(salaryAddition), employee);
    }

    @Transactional(readOnly = true)
    public SalaryAdditionResponse getSalaryAddition(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        SalaryAddition salaryAddition = findSalaryAddition(tenantId, id);
        hrValidationService.ensureCanAccessBranch(salaryAddition.getBranchId());
        return toResponse(tenantId, salaryAddition);
    }

    @Transactional
    public SalaryAdditionResponse updateSalaryAddition(Long id, UpdateSalaryAdditionRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        SalaryAddition salaryAddition = findSalaryAddition(tenantId, id);
        hrValidationService.ensureCanAccessBranch(salaryAddition.getBranchId());
        Employee employee = hrValidationService.findActiveEmployee(tenantId, request.employeeId());

        salaryAddition.setBranchId(employee.getBranchId());
        salaryAddition.setEmployeeId(employee.getId());
        salaryAddition.setTitle(request.title().trim());
        salaryAddition.setAmount(request.amount());
        salaryAddition.setSalaryMonth(request.salaryMonth().withDayOfMonth(1));
        salaryAddition.setNotes(trimToNull(request.notes()));
        if (request.active() != null) {
            salaryAddition.setActive(request.active());
        }
        salaryAddition.setUpdatedBy(currentTenantProvider.getActorUserId());

        return SalaryAdditionResponse.from(salaryAdditionRepository.saveAndFlush(salaryAddition), employee);
    }

    @Transactional
    public SalaryAdditionResponse updateSalaryAdditionStatus(Long id, UpdateActiveStatusRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        SalaryAddition salaryAddition = findSalaryAddition(tenantId, id);
        hrValidationService.ensureCanAccessBranch(salaryAddition.getBranchId());
        salaryAddition.setActive(request.active());
        salaryAddition.setUpdatedBy(currentTenantProvider.getActorUserId());

        return toResponse(tenantId, salaryAdditionRepository.saveAndFlush(salaryAddition));
    }

    private SalaryAddition findSalaryAddition(Long tenantId, Long id) {
        return salaryAdditionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Salary addition not found: " + id));
    }

    private SalaryAdditionResponse toResponse(Long tenantId, SalaryAddition salaryAddition) {
        Employee employee = employeeRepository.findByIdAndTenantId(salaryAddition.getEmployeeId(), tenantId).orElse(null);
        return SalaryAdditionResponse.from(salaryAddition, employee);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
