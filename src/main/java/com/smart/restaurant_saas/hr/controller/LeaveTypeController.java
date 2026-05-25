package com.smart.restaurant_saas.hr.controller;

import com.smart.restaurant_saas.hr.dto.response.LeaveTypeResponse;
import com.smart.restaurant_saas.hr.service.LeaveTypeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
