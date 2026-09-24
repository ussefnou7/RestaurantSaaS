package com.smart.restaurant_saas.hr.controller;

import com.smart.restaurant_saas.hr.dto.request.UpdateLeaveBalanceRequest;
import com.smart.restaurant_saas.hr.dto.response.LeaveBalanceResponse;
import com.smart.restaurant_saas.hr.service.LeaveBalanceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr")
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    @GetMapping("/employees/{employeeId}/leave-balances")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_LEAVE_BALANCES_VIEW')")
    public List<LeaveBalanceResponse> listLeaveBalances(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year
    ) {
        return leaveBalanceService.listLeaveBalances(employeeId, year);
    }

    @PostMapping("/employees/{employeeId}/leave-balances/generate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_LEAVE_BALANCES_MANAGE')")
    public List<LeaveBalanceResponse> generateMissingBalances(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year
    ) {
        return leaveBalanceService.generateMissingBalances(employeeId, year);
    }

    @PutMapping("/leave-balances/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_LEAVE_BALANCES_MANAGE')")
    public LeaveBalanceResponse updateLeaveBalance(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLeaveBalanceRequest request
    ) {
        return leaveBalanceService.updateLeaveBalance(id, request);
    }
}
