package com.smart.restaurant_saas.device;

import com.smart.restaurant_saas.tenant.CurrentTenantId;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;

import com.smart.restaurant_saas.device.dto.DeviceCreateRequest;
import com.smart.restaurant_saas.device.dto.DeviceLoginRequest;
import com.smart.restaurant_saas.device.dto.DeviceLoginResponse;
import com.smart.restaurant_saas.device.dto.DevicePairingCodeResponse;
import com.smart.restaurant_saas.device.dto.DeviceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "POS device registration and branch resolution")
public class DeviceController {

    private final CurrentTenantProvider currentTenantProvider;

    private final DeviceService deviceService;

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('DEVICES_MANAGE')")
    @Operation(
        summary = "Create device",
        description = "Registers a POS device and returns a one-time pairing code valid for 10 minutes."
    )
    public ResponseEntity<DeviceResponse> create(
            @Valid @RequestBody DeviceCreateRequest request,
            @CurrentTenantId Long tenantId) {
        Long userId = currentTenantProvider.getActorUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(deviceService.create(request, tenantId, userId));
    }

    @PostMapping("/{id}/pairing-code")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('DEVICES_MANAGE')")
    @Operation(
        summary = "Regenerate device pairing code",
        description = "Invalidates the previous code and returns a new one-time code valid for 10 minutes."
    )
    public DevicePairingCodeResponse regeneratePairingCode(
            @PathVariable Long id,
            @CurrentTenantId Long tenantId) {
        Long userId = currentTenantProvider.getActorUserId();
        return deviceService.regeneratePairingCode(id, tenantId, userId);
    }

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('DEVICES_MANAGE')")
    @Operation(
        summary = "List devices",
        description = "Returns all POS devices for the current tenant without secret material."
    )
    public List<DeviceResponse> list(@CurrentTenantId Long tenantId) {
        return deviceService.findAll(tenantId);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('DEVICES_MANAGE')")
    @Operation(
        summary = "Deactivate device",
        description = "Marks the device as inactive. Repeating the operation on an inactive device is idempotent."
    )
    public DeviceResponse deactivate(
            @PathVariable Long id,
            @CurrentTenantId Long tenantId) {
        Long userId = currentTenantProvider.getActorUserId();
        return deviceService.deactivate(id, tenantId, userId);
    }

    @PostMapping("/login")
    @Operation(
        summary = "Login device",
        description = "Consumes a one-time pairing code and resolves the device tenant and branch."
    )
    public DeviceLoginResponse login(@Valid @RequestBody DeviceLoginRequest request,
                                     HttpServletRequest httpRequest) {
        return deviceService.login(request, httpRequest.getRemoteAddr());
    }
}
