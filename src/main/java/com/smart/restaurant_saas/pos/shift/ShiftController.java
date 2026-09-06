package com.smart.restaurant_saas.pos.shift;

import com.smart.restaurant_saas.pos.shift.dto.CloseShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.CurrentShiftResponse;
import com.smart.restaurant_saas.pos.shift.dto.OpenShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.OpenShiftResult;
import com.smart.restaurant_saas.pos.shift.dto.ShiftDetailResponse;
import com.smart.restaurant_saas.pos.shift.dto.ShiftListItemResponse;
import com.smart.restaurant_saas.pos.shift.dto.ShiftResponse;
import com.smart.restaurant_saas.tenant.CurrentTenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The drawer-side of shifts: open, resume, close.
 *
 * <p><b>No {@code X-User-Id} and no device parameter.</b> Both actors come from the signed token.
 * The header is still whitelisted in CORS and still read by other controllers, so it is removed
 * here only -- the wider migration is out of scope (D123).
 *
 * <p>All three endpoints previously required {@code SHIFTS_OPEN}, which the seeded {@code CASHIER}
 * role holds, so any cashier could close any shift in the tenant. Each now requires the permission
 * that names what it does.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shifts")
@Tag(name = "Shifts", description = "A drawer's account of one period. The drawer is the device.")
public class ShiftController {

    private final ShiftService shiftService;
    private final ShiftQueryService shiftQueryService;

    /**
     * An operational list, not a report (D83) — deliberately not built on the reports shell.
     *
     * <p>Ordering is fixed to variance magnitude by the query and is not a {@code Pageable}
     * default, so it cannot be replaced by a date sort from the query string.
     */
    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('SHIFTS_VIEW')")
    @Operation(summary = "List shifts",
        description = "Sorted by the size of the variance, largest first, because the screen "
            + "exists to bring the anomalous to the top — surpluses included, since a drawer that "
            + "runs over is as strong a signal as one that runs short. Variance columns are "
            + "omitted entirely without SHIFTS_VIEW_VARIANCE.")
    public Page<ShiftListItemResponse> list(
            @CurrentTenantId Long tenantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long cashierUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) Boolean forcedClose,
            @PageableDefault(size = 20) Pageable pageable) {
        return shiftQueryService.findAll(tenantId, branchId, deviceId, cashierUserId,
                dateFrom, dateTo, status, forcedClose, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('SHIFTS_VIEW')")
    @Operation(summary = "Shift detail",
        description = "One shift with its orders and its expenses, each showing who recorded it "
            + "and when. Expenses recorded after the shift closed are marked and reported "
            + "separately — they are never added into the stored variance.")
    public ShiftDetailResponse getById(
            @PathVariable Long id,
            @CurrentTenantId Long tenantId) {
        return shiftQueryService.findById(id, tenantId);
    }

    @PostMapping("/open")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('SHIFTS_OPEN')")
    @Operation(summary = "Open or resume a shift",
        description = "Opens a shift on the calling device. If the caller already has an open "
            + "shift on this device it is resumed and returned unchanged, and the submitted count "
            + "is ignored rather than written. If the open shift belongs to someone else the call "
            + "fails with SHIFT_OPEN_BY_ANOTHER_USER and the client's next move is a force close.")
    public ResponseEntity<ShiftResponse> openShift(
            @Valid @RequestBody OpenShiftRequest request,
            @CurrentTenantId Long tenantId) {
        OpenShiftResult result = shiftService.openShift(request, tenantId);
        // 200 on a resume: nothing was created, and saying otherwise would be a lie the client
        // could only detect by comparing counts.
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.shift());
    }

    @GetMapping("/current")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('SHIFTS_VIEW')")
    @Operation(summary = "Current shift on this device",
        description = "Returns the open shift on the calling device, whoever opened it, or a null "
            + "shift when there is none. No open shift is an ordinary result, not an error. "
            + "Returns no expected figure, no variance and no order totals.")
    public CurrentShiftResponse getCurrentShift(@CurrentTenantId Long tenantId) {
        return shiftService.getCurrentShift(tenantId);
    }

    /**
     * Gated on either permission because which one actually applies depends on who opened the
     * shift, and that is not known until it is loaded. {@code ShiftService.requireClosePermission}
     * then enforces the exact one — so holding only {@code SHIFTS_FORCE_CLOSE} passes this gate
     * and is still rejected when closing your own shift, and vice versa.
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("@securityService.isSysAdmin() "
        + "or @securityService.hasPermission('SHIFTS_CLOSE') "
        + "or @securityService.hasPermission('SHIFTS_FORCE_CLOSE')")
    @Operation(summary = "Close a shift",
        description = "Counts and closes the shift on the calling device. Closing your own "
            + "requires SHIFTS_CLOSE; closing a colleague's requires SHIFTS_FORCE_CLOSE and is "
            + "recorded as a forced close. The response carries no expected figure and no "
            + "variance — the count is blind at both ends.")
    public ShiftResponse closeShift(
            @PathVariable Long id,
            @Valid @RequestBody CloseShiftRequest request,
            @CurrentTenantId Long tenantId) {
        return shiftService.closeShift(id, request, tenantId);
    }
}
