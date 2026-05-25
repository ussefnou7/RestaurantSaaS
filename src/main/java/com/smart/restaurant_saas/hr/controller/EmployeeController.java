package com.smart.restaurant_saas.hr.controller;

import com.smart.restaurant_saas.hr.dto.request.CreateEmployeeRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateEmployeeRequest;
import com.smart.restaurant_saas.hr.dto.response.EmployeeResponse;
import com.smart.restaurant_saas.hr.service.EmployeeService;
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
@RequestMapping("/api/hr/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_EMPLOYEES_VIEW')")
    public List<EmployeeResponse> listEmployees() {
        return employeeService.listEmployees();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_EMPLOYEES_CREATE')")
    public EmployeeResponse createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        return employeeService.createEmployee(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_EMPLOYEES_VIEW')")
    public EmployeeResponse getEmployee(@PathVariable Long id) {
        return employeeService.getEmployee(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_EMPLOYEES_UPDATE')")
    public EmployeeResponse updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request
    ) {
        return employeeService.updateEmployee(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('HR_EMPLOYEES_UPDATE')")
    public EmployeeResponse updateEmployeeStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveStatusRequest request
    ) {
        return employeeService.updateEmployeeStatus(id, request);
    }
}
