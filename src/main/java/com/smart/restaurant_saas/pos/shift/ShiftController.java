package com.smart.restaurant_saas.pos.shift;

import com.smart.restaurant_saas.pos.shift.dto.CloseShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.OpenShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.ShiftResponse;
import com.smart.restaurant_saas.pos.shift.dto.ShiftSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('SHIFTS_OPEN')")
    public ShiftResponse openShift(
            @Valid @RequestBody OpenShiftRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-Branch-Id") Long branchId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return shiftService.openShift(request, tenantId, branchId, userId);
    }

    @GetMapping("/current")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('SHIFTS_OPEN')")
    public ShiftSummaryResponse getCurrentShift(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return shiftService.getCurrentShiftSummary(tenantId, userId);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('SHIFTS_OPEN')")
    public ShiftSummaryResponse closeShift(
            @PathVariable Long id,
            @Valid @RequestBody CloseShiftRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return shiftService.closeShift(id, request, tenantId, userId);
    }
}
