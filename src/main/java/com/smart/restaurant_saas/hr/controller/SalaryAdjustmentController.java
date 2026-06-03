package com.smart.restaurant_saas.hr.controller;

import com.smart.restaurant_saas.hr.dto.request.CreateSalaryAdjustmentRequest;
import com.smart.restaurant_saas.hr.dto.response.SalaryAdjustmentResponse;
import com.smart.restaurant_saas.hr.service.SalaryAdjustmentService;
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
@RequestMapping("/api/hr")
@PreAuthorize("@securityService.isOwnerOrBranchManager()")
public class SalaryAdjustmentController {

    private final SalaryAdjustmentService salaryAdjustmentService;

    @GetMapping("/employees/{employeeId}/salary-adjustments")
    public List<SalaryAdjustmentResponse> listSalaryAdjustments(@PathVariable Long employeeId) {
        return salaryAdjustmentService.listSalaryAdjustments(employeeId);
    }

    @PostMapping("/employees/{employeeId}/salary-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryAdjustmentResponse createSalaryAdjustment(
            @PathVariable Long employeeId,
            @Valid @RequestBody CreateSalaryAdjustmentRequest request
    ) {
        return salaryAdjustmentService.createSalaryAdjustment(employeeId, request);
    }

    @PatchMapping("/salary-adjustments/{id}/cancel")
    public SalaryAdjustmentResponse cancelSalaryAdjustment(@PathVariable Long id) {
        return salaryAdjustmentService.cancelSalaryAdjustment(id);
    }
}
