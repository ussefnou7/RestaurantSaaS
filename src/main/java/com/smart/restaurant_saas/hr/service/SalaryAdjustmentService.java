package com.smart.restaurant_saas.hr.service;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.dto.request.CreateSalaryAdjustmentRequest;
import com.smart.restaurant_saas.hr.dto.response.SalaryAdjustmentResponse;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.SalaryAdjustment;
import com.smart.restaurant_saas.hr.repository.SalaryAdjustmentRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalaryAdjustmentService {

    private final CurrentTenantProvider currentTenantProvider;
    private final HrValidationService hrValidationService;
    private final SalaryAdjustmentRepository salaryAdjustmentRepository;

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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Salary adjustment not found: " + id));
        hrValidationService.ensureCanAccessBranch(adjustment.getBranchId());
        adjustment.setActive(false);
        adjustment.setUpdatedBy(currentTenantProvider.getActorUserId());
        return SalaryAdjustmentResponse.from(salaryAdjustmentRepository.saveAndFlush(adjustment));
    }
}
