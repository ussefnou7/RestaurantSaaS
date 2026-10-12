package com.smart.restaurant_saas.systemhealth;

import com.smart.restaurant_saas.systemhealth.dto.SystemHealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/system-health")
@PreAuthorize("@securityService.isSysAdmin()")
@Tag(name = "System health", description = "SysAdmin server and database readiness")
public class SystemHealthController {

    private final SystemHealthService service;

    @GetMapping
    @Operation(summary = "Read server and database health")
    public SystemHealthResponse getHealth() {
        return service.getHealth();
    }
}
