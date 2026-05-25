package com.smart.restaurant_saas.hr.service;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.dto.request.CreateLeaveRequestRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateLeaveRequestStatusRequest;
import com.smart.restaurant_saas.hr.dto.response.LeaveRequestResponse;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.LeaveRequest;
import com.smart.restaurant_saas.hr.entity.LeaveType;
import com.smart.restaurant_saas.hr.enums.LeaveRequestStatus;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.hr.repository.LeaveRequestRepository;
import com.smart.restaurant_saas.hr.repository.LeaveTypeRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final CurrentTenantProvider currentTenantProvider;
    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final HrValidationService hrValidationService;
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
                                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Branch scope is required"))
                );
        return leaveRequests.stream()
                .map(leaveRequest -> toResponse(tenantId, leaveRequest))
                .toList();
    }

    @Transactional
    public LeaveRequestResponse createLeaveRequest(CreateLeaveRequestRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, request.employeeId());
        LeaveType leaveType = leaveTypeRepository.findByIdAndActiveTrue(request.leaveTypeId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or inactive leave type: " + request.leaveTypeId()));
        validateDatesAndDays(request);

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setTenantId(tenantId);
        leaveRequest.setBranchId(employee.getBranchId());
        leaveRequest.setEmployeeId(employee.getId());
        leaveRequest.setLeaveTypeId(leaveType.getId());
        leaveRequest.setFromDate(request.fromDate());
        leaveRequest.setToDate(request.toDate());
        leaveRequest.setDaysCount(request.daysCount());
        leaveRequest.setReason(trimToNull(request.reason()));
        leaveRequest.setStatus(LeaveRequestStatus.PENDING);
        leaveRequest.setCreatedBy(currentTenantProvider.getActorUserId());

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
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        LeaveRequest leaveRequest = findLeaveRequest(tenantId, id);
        hrValidationService.ensureCanAccessBranch(leaveRequest.getBranchId());

        LeaveRequestStatus targetStatus = parseStatus(request.status());
        if (targetStatus == LeaveRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot change leave request status back to PENDING");
        }
        if (leaveRequest.getStatus() != LeaveRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only PENDING leave requests can change status");
        }

        leaveRequest.setStatus(targetStatus);
        leaveRequest.setStatusNote(trimToNull(request.statusNote()));
        leaveRequest.setStatusChangedBy(currentTenantProvider.getActorUserId());
        leaveRequest.setStatusChangedAt(LocalDateTime.now());
        leaveRequest.setUpdatedBy(currentTenantProvider.getActorUserId());

        return toResponse(tenantId, leaveRequestRepository.saveAndFlush(leaveRequest));
    }

    private LeaveRequest findLeaveRequest(Long tenantId, Long id) {
        return leaveRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found: " + id));
    }

    private void validateDatesAndDays(CreateLeaveRequestRequest request) {
        if (request.fromDate().isAfter(request.toDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fromDate must be before or equal to toDate");
        }
        BigDecimal expectedDays = BigDecimal.valueOf(ChronoUnit.DAYS.between(request.fromDate(), request.toDate()) + 1);
        if (request.daysCount().compareTo(expectedDays) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "daysCount must match the inclusive date range");
        }
    }

    private LeaveRequestResponse toResponse(Long tenantId, LeaveRequest leaveRequest) {
        Employee employee = employeeRepository.findByIdAndTenantId(leaveRequest.getEmployeeId(), tenantId).orElse(null);
        LeaveType leaveType = leaveTypeRepository.findById(leaveRequest.getLeaveTypeId()).orElse(null);
        return LeaveRequestResponse.from(leaveRequest, employee, leaveType);
    }

    private LeaveRequestStatus parseStatus(String status) {
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        try {
            return LeaveRequestStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid leave request status: " + status
                    + ". Allowed values: " + Arrays.toString(LeaveRequestStatus.values()));
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
