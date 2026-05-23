package com.smart.restaurant_saas.tenant;

import com.smart.restaurant_saas.tenant.dto.CreateTenantRequest;
import com.smart.restaurant_saas.tenant.dto.UpdateTenantRequest;
import com.smart.restaurant_saas.tenant.dto.UpdateTenantStatusRequest;
import com.smart.restaurant_saas.tenant.dto.TenantResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/admin/tenants")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return tenantService.createTenant(request);
    }

    @GetMapping
    public List<TenantResponse> listTenants() {
        return tenantService.listTenants();
    }

    @GetMapping("/{id}")
    public TenantResponse getTenant(@PathVariable Long id) {
        return tenantService.getTenant(id);
    }

    @PutMapping("/{id}")
    public TenantResponse updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        return tenantService.updateTenant(id, request);
    }

    @PatchMapping("/{id}/status")
    public TenantResponse updateTenantStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTenantStatusRequest request
    ) {
        return tenantService.updateTenantStatus(id, request);
    }
}
