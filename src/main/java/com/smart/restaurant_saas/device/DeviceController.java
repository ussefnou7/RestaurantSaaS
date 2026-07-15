package com.smart.restaurant_saas.device;

import com.smart.restaurant_saas.device.dto.DeviceCreateRequest;
import com.smart.restaurant_saas.device.dto.DeviceLoginRequest;
import com.smart.restaurant_saas.device.dto.DeviceLoginResponse;
import com.smart.restaurant_saas.device.dto.DeviceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "POS device registration and branch resolution")
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('DEVICES_MANAGE')")
    @Operation(
        summary = "Create device",
        description = "Registers a POS device for a tenant branch and returns its one-time raw secret key."
    )
    public ResponseEntity<DeviceResponse> create(
            @Valid @RequestBody DeviceCreateRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(deviceService.create(request, tenantId, userId));
    }

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('DEVICES_MANAGE')")
    @Operation(
        summary = "List devices",
        description = "Returns all POS devices for the current tenant without secret material."
    )
    public List<DeviceResponse> list(@RequestHeader("X-Tenant-Id") Long tenantId) {
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
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return deviceService.deactivate(id, tenantId, userId);
    }

    @PostMapping("/login")
    @Operation(
        summary = "Login device",
        description = "Authenticates a POS device by secret key and resolves its tenant and branch."
    )
    public DeviceLoginResponse login(@Valid @RequestBody DeviceLoginRequest request) {
        return deviceService.login(request);
    }
}
