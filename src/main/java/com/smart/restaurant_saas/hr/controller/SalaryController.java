package com.smart.restaurant_saas.hr.controller;

import com.smart.restaurant_saas.hr.dto.request.CreateSalaryRequest;
import com.smart.restaurant_saas.hr.dto.response.SalaryResponse;
import com.smart.restaurant_saas.hr.service.SalaryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr/employees/{employeeId}")
@PreAuthorize("@securityService.isOwnerOrBranchManager()")
public class SalaryController {

    private final SalaryService salaryService;

    @GetMapping("/salaries")
    public List<SalaryResponse> listSalaries(@PathVariable Long employeeId) {
        return salaryService.listSalaries(employeeId);
    }

    @GetMapping("/salary/current")
    public SalaryResponse getCurrentSalary(@PathVariable Long employeeId) {
        return salaryService.getCurrentSalary(employeeId);
    }

    @PostMapping("/salaries")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryResponse createSalary(
            @PathVariable Long employeeId,
            @Valid @RequestBody CreateSalaryRequest request
    ) {
        return salaryService.createSalary(employeeId, request);
    }
}
