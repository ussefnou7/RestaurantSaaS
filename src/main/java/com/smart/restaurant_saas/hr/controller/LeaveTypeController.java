package com.smart.restaurant_saas.hr.controller;

import com.smart.restaurant_saas.hr.dto.request.CreateLeaveTypeRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateLeaveTypeRequest;
import com.smart.restaurant_saas.hr.dto.response.LeaveTypeResponse;
import com.smart.restaurant_saas.hr.service.LeaveTypeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_LEAVES_VIEW')")
    public List<LeaveTypeResponse> listLeaveTypes() {
        return leaveTypeService.listLeaveTypes();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.isOwner()")
    public LeaveTypeResponse createLeaveType(@Valid @RequestBody CreateLeaveTypeRequest request) {
        return leaveTypeService.createLeaveType(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_LEAVES_VIEW')")
    public LeaveTypeResponse getLeaveType(@PathVariable Long id) {
        return leaveTypeService.getLeaveType(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.isOwner()")
    public LeaveTypeResponse updateLeaveType(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLeaveTypeRequest request
    ) {
        return leaveTypeService.updateLeaveType(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.isOwner()")
    public LeaveTypeResponse updateLeaveTypeStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveStatusRequest request
    ) {
        return leaveTypeService.updateLeaveTypeStatus(id, request);
    }
}
