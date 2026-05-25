package com.smart.restaurant_saas.hr.controller;

import com.smart.restaurant_saas.hr.dto.request.CreateLeaveRequestRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateLeaveRequestStatusRequest;
import com.smart.restaurant_saas.hr.dto.response.LeaveRequestResponse;
import com.smart.restaurant_saas.hr.service.LeaveRequestService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_LEAVES_VIEW')")
    public List<LeaveRequestResponse> listLeaveRequests() {
        return leaveRequestService.listLeaveRequests();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_LEAVES_CREATE')")
    public LeaveRequestResponse createLeaveRequest(@Valid @RequestBody CreateLeaveRequestRequest request) {
        return leaveRequestService.createLeaveRequest(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_LEAVES_VIEW')")
    public LeaveRequestResponse getLeaveRequest(@PathVariable Long id) {
        return leaveRequestService.getLeaveRequest(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_LEAVES_UPDATE_STATUS')")
    public LeaveRequestResponse updateLeaveRequestStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLeaveRequestStatusRequest request
    ) {
        return leaveRequestService.updateLeaveRequestStatus(id, request);
    }
}
