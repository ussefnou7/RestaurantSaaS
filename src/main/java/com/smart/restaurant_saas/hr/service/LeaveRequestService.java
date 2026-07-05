package com.smart.restaurant_saas.hr.service;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.hr.dto.request.CreateLeaveRequestRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateLeaveRequestStatusRequest;
import com.smart.restaurant_saas.hr.dto.response.LeaveRequestResponse;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.LeaveBalance;
import com.smart.restaurant_saas.hr.entity.LeaveRequest;
import com.smart.restaurant_saas.hr.entity.LeaveType;
import com.smart.restaurant_saas.hr.enums.LeaveRequestStatus;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.hr.repository.LeaveRequestRepository;
import com.smart.restaurant_saas.hr.repository.LeaveTypeRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final CurrentTenantProvider currentTenantProvider;
    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final HrValidationService hrValidationService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listLeaveRequests() {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        List<LeaveRequest> leaveRequests = currentUserScopeProvider.isTenantScoped()
                ? leaveRequestRepository.findByTenantIdOrderByIdDesc(tenantId)
                : leaveRequestRepository.findByTenantIdAndBranchIdOrderByIdDesc(
                        tenantId,
                        currentUserScopeProvider.getCurrentBranchId()
                                .orElseThrow(() -> new AuthorizationException(
                                        HrErrorCode.BRANCH_SCOPE_REQUIRED, "Branch scope is required"))
                );
        return leaveRequests.stream()
                .map(leaveRequest -> toResponse(tenantId, leaveRequest))
                .toList();
    }

    @Transactional
    public LeaveRequestResponse createLeaveRequest(CreateLeaveRequestRequest request) {
        if (request.employeeId() == null) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "employeeId is required",
                    ErrorParams.of("field", "employeeId"));
        }
        return createLeaveRequest(request.employeeId(), request);
    }

    @Transactional
    public LeaveRequestResponse createLeaveRequest(Long employeeId, CreateLeaveRequestRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        validateDatesAndDays(request);
        Employee employee = hrValidationService.findActiveEmployee(tenantId, employeeId);
        LeaveType leaveType = leaveTypeRepository.findByIdAndTenantIdAndActiveTrue(request.leaveTypeId(), tenantId)
                .orElseThrow(() -> new BusinessException(HrErrorCode.INACTIVE_REFERENCE,
                        "Invalid or inactive leave type: " + request.leaveTypeId(),
                        ErrorParams.of("entityType", "LeaveType", "entityId", request.leaveTypeId())));
        int year = request.fromDate().getYear();
        LeaveBalance balance = leaveBalanceService.findBalanceForLeaveRequestWithLock(
                tenantId,
                employee.getId(),
                leaveType.getId(),
                year
        );
        if (!Boolean.TRUE.equals(balance.getActive())) {
            throw new BusinessException(HrErrorCode.INACTIVE_REFERENCE,
                    "Leave balance is inactive",
                    ErrorParams.of("entityType", "LeaveBalance"));
        }

        BigDecimal daysCount = calculateDays(request);
        if (balance.getRemainingDays().compareTo(daysCount) < 0) {
            throw new BusinessException(HrErrorCode.INSUFFICIENT_LEAVE_BALANCE,
                    "Insufficient leave balance",
                    ErrorParams.of("remaining", balance.getRemainingDays(), "requested", daysCount));
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setTenantId(tenantId);
        leaveRequest.setBranchId(employee.getBranchId());
        leaveRequest.setEmployeeId(employee.getId());
        leaveRequest.setLeaveTypeId(leaveType.getId());
        leaveRequest.setLeaveBalanceId(balance.getId());
        leaveRequest.setFromDate(request.fromDate());
        leaveRequest.setToDate(request.toDate());
        leaveRequest.setDaysCount(daysCount);
        leaveRequest.setReason(trimToNull(request.reason()));
        leaveRequest.setNotes(trimToNull(request.notes()));
        leaveRequest.setStatus(LeaveRequestStatus.APPROVED);
        leaveRequest.setCreatedBy(currentTenantProvider.getActorUserId());

        balance.setUsedDays(balance.getUsedDays().add(daysCount));
        leaveBalanceService.recalculateRemaining(balance);
        balance.setUpdatedBy(currentTenantProvider.getActorUserId());

        return LeaveRequestResponse.from(leaveRequestRepository.save(leaveRequest), employee, leaveType);
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getLeaveRequest(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        LeaveRequest leaveRequest = findLeaveRequest(tenantId, id);
        hrValidationService.ensureCanAccessBranch(leaveRequest.getBranchId());
        return toResponse(tenantId, leaveRequest);
    }

    @Transactional
    public LeaveRequestResponse updateLeaveRequestStatus(Long id, UpdateLeaveRequestStatusRequest request) {
        LeaveRequestStatus targetStatus = parseStatus(request.status());
        if (targetStatus != LeaveRequestStatus.CANCELLED) {
            throw new BusinessException(HrErrorCode.UNSUPPORTED_OPERATION,
                    "Only CANCELLED status is supported for HR MVP",
                    ErrorParams.of("requestedStatus", targetStatus.name(),
                            "allowedStatuses", List.of("CANCELLED")));
        }
        return cancelLeaveRequest(id);
    }

    @Transactional
    public LeaveRequestResponse cancelLeaveRequest(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        LeaveRequest leaveRequest = findLeaveRequest(tenantId, id);
        hrValidationService.ensureCanAccessBranch(leaveRequest.getBranchId());

        if (leaveRequest.getStatus() == LeaveRequestStatus.CANCELLED) {
            return toResponse(tenantId, leaveRequest);
        }

        LeaveBalance balance = leaveBalanceService.findBalanceByIdWithLock(tenantId, leaveRequest.getLeaveBalanceId());
        balance.setUsedDays(balance.getUsedDays().subtract(leaveRequest.getDaysCount()));
        if (balance.getUsedDays().compareTo(BigDecimal.ZERO) < 0) {
            balance.setUsedDays(BigDecimal.ZERO);
        }
        leaveBalanceService.recalculateRemaining(balance);
        balance.setUpdatedBy(currentTenantProvider.getActorUserId());

        leaveRequest.setStatus(LeaveRequestStatus.CANCELLED);
        leaveRequest.setUpdatedBy(currentTenantProvider.getActorUserId());

        return toResponse(tenantId, leaveRequestRepository.saveAndFlush(leaveRequest));
    }

    private LeaveRequest findLeaveRequest(Long tenantId, Long id) {
        return leaveRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Leave request not found: " + id,
                        ErrorParams.of("entityType", "LeaveRequest", "entityId", id)));
    }

    private void validateDatesAndDays(CreateLeaveRequestRequest request) {
        if (request.fromDate().isAfter(request.toDate())) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "fromDate must be before or equal to toDate",
                    ErrorParams.of("field", "fromDate"));
        }
        if (request.fromDate().getYear() != request.toDate().getYear()) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "Leave requests cannot span multiple years",
                    ErrorParams.of("field", "toDate"));
        }
        BigDecimal expectedDays = calculateDays(request);
        if (request.daysCount() != null && request.daysCount().compareTo(expectedDays) != 0) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "daysCount must match the inclusive date range",
                    ErrorParams.of("field", "daysCount"));
        }
    }

    private BigDecimal calculateDays(CreateLeaveRequestRequest request) {
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(request.fromDate(), request.toDate()) + 1);
    }

    private LeaveRequestResponse toResponse(Long tenantId, LeaveRequest leaveRequest) {
        Employee employee = employeeRepository.findByIdAndTenantId(leaveRequest.getEmployeeId(), tenantId).orElse(null);
        LeaveType leaveType = leaveTypeRepository.findByIdAndTenantId(leaveRequest.getLeaveTypeId(), tenantId).orElse(null);
        return LeaveRequestResponse.from(leaveRequest, employee, leaveType);
    }

    private LeaveRequestStatus parseStatus(String status) {
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        try {
            return LeaveRequestStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "Invalid leave request status: " + status
                            + ". Allowed values: " + Arrays.toString(LeaveRequestStatus.values()),
                    ErrorParams.of("field", "status", "rejectedValue", status,
                            "allowedValues", Arrays.toString(LeaveRequestStatus.values())));
        }
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listEmployeeLeaveRequests(Long employeeId) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, employeeId);
        return leaveRequestRepository.findByTenantIdAndEmployeeIdOrderByIdDesc(tenantId, employee.getId()).stream()
                .map(leaveRequest -> toResponse(tenantId, leaveRequest))
                .toList();
    }
}
