package com.smart.restaurant_saas.hr.controller;

import com.smart.restaurant_saas.hr.dto.request.CreateSalaryAdditionRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateSalaryAdditionRequest;
import com.smart.restaurant_saas.hr.dto.response.SalaryAdditionResponse;
import com.smart.restaurant_saas.hr.service.SalaryAdditionService;
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
@RequestMapping("/api/hr/salary-additions")
public class SalaryAdditionController {

    private final SalaryAdditionService salaryAdditionService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_SALARY_ADDITIONS_VIEW')")
    public List<SalaryAdditionResponse> listSalaryAdditions() {
        return salaryAdditionService.listSalaryAdditions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_SALARY_ADDITIONS_CREATE')")
    public SalaryAdditionResponse createSalaryAddition(@Valid @RequestBody CreateSalaryAdditionRequest request) {
        return salaryAdditionService.createSalaryAddition(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_SALARY_ADDITIONS_VIEW')")
    public SalaryAdditionResponse getSalaryAddition(@PathVariable Long id) {
        return salaryAdditionService.getSalaryAddition(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_SALARY_ADDITIONS_UPDATE')")
    public SalaryAdditionResponse updateSalaryAddition(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSalaryAdditionRequest request
    ) {
        return salaryAdditionService.updateSalaryAddition(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_SALARY_ADDITIONS_UPDATE')")
    public SalaryAdditionResponse updateSalaryAdditionStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveStatusRequest request
    ) {
        return salaryAdditionService.updateSalaryAdditionStatus(id, request);
    }
}
