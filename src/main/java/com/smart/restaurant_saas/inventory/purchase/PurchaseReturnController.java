package com.smart.restaurant_saas.inventory.purchase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smart.restaurant_saas.inventory.core.CancelDocumentRequest;
import com.smart.restaurant_saas.inventory.core.PurchaseReturnService;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnUpdateLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.ReturnableLineResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.UncompleteRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.UnpostRequest;

@RestController
@RequestMapping("/api/inventory/purchase-returns")
@RequiredArgsConstructor
@Tag(name = "Inventory - Purchase Return", description = "Returning purchased goods to suppliers")
public class PurchaseReturnController {

    private final PurchaseReturnService service;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_VIEW')")
    @Operation(
        summary = "List purchase returns",
        description = "Returns all purchase returns for the current tenant."
    )
    public List<PurchaseReturnResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.findAll(tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_VIEW')")
    @Operation(
        summary = "Get purchase return details",
        description = "Returns full purchase return including all lines and "
                    + "reference to the original invoice."
    )
    public PurchaseReturnResponse getById(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.findById(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Create purchase return header",
        description = "Creates a new purchase return header in DRAFT status against a POSTED "
                    + "invoice and generates its return number. The return starts with no "
                    + "lines — fetch returnable lines via GET /{id}/returnable-lines, then add "
                    + "them via POST /{id}/lines."
    )
    public ResponseEntity<PurchaseReturnResponse> create(
            @Valid @RequestBody PurchaseReturnRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.create(request, tenantId, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Update purchase return header",
        description = "Updates the return header fields (returnDate, reason, notes). Only "
                    + "allowed in DRAFT status. The original invoice cannot be changed."
    )
    public PurchaseReturnResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseReturnRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.update(id, request, tenantId, userId);
    }

    @GetMapping("/{id}/returnable-lines")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_VIEW')")
    @Operation(
        summary = "List returnable lines",
        description = "Returns the still-returnable lines of the original invoice with their "
                    + "remaining returnable quantities, for building the return form after the "
                    + "header is saved. Lines fully returned by POSTED returns are omitted."
    )
    public List<ReturnableLineResponse> returnableLines(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.getReturnableLines(id, tenantId);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Add line to purchase return",
        description = "Appends a line referencing an original invoice line and recalculates "
                    + "totals. The return quantity cannot exceed the line's remaining "
                    + "returnable quantity. Only allowed in DRAFT status."
    )
    public PurchaseReturnResponse addLine(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseReturnLineRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.addLine(id, request, tenantId, userId);
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Update purchase return line",
        description = "Updates the quantity/notes of a return line and recalculates totals. "
                    + "The referenced original line cannot change. Only allowed in DRAFT status."
    )
    public PurchaseReturnResponse updateLine(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @Valid @RequestBody PurchaseReturnUpdateLineRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.updateLine(id, lineId, request, tenantId, userId);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Delete purchase return line",
        description = "Removes a line from a DRAFT return and recalculates totals. "
                    + "Only allowed in DRAFT status."
    )
    public ResponseEntity<PurchaseReturnResponse> deleteLine(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(service.deleteLine(id, lineId, tenantId, userId));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Complete purchase return",
        description = "Marks the return as COMPLETE — ready for posting."
    )
    public PurchaseReturnResponse complete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.complete(id, tenantId, userId);
    }

    @PostMapping("/{id}/post")
    @Operation(
        summary = "Post purchase return to inventory",
        description = "Posts the return — triggers Stock Out for all lines "
                    + "at the original purchase cost. "
                    + "Updates stock quantities and restores last purchase price "
                    + "to the previous valid purchase."
    )
    public PurchaseReturnResponse post(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.post(id, tenantId, userId);
    }

    @PostMapping("/{id}/unpost")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PURCHASE_RETURN_UNPOST')")
    @Operation(
        summary = "Unpost purchase return",
        description = "Reverts a POSTED purchase return to COMPLETE by recording reversal "
                    + "ledger transactions and restoring the exact source batch quantity."
    )
    public PurchaseReturnResponse unpost(
            @PathVariable Long id,
            @RequestBody(required = false) UnpostRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.unpost(id, request, tenantId, userId);
    }

    @PostMapping("/{id}/uncomplete")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PURCHASE_RETURN_UNCOMPLETE')")
    @Operation(
        summary = "UnComplete purchase return",
        description = "Moves a COMPLETE purchase return back to DRAFT for editing. "
                    + "Does not change the return number and does not touch inventory ledger state."
    )
    public PurchaseReturnResponse uncomplete(
            @PathVariable Long id,
            @RequestBody(required = false) UncompleteRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.uncomplete(id, request, tenantId, userId);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Cancel purchase return",
        description = "Cancels the return. Only allowed from DRAFT or COMPLETE status."
    )
    public PurchaseReturnResponse cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelDocumentRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String reason = request != null ? request.getReason() : null;
        return service.cancel(id, reason, tenantId, userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PURCHASE_RETURN_DELETE')")
    @Operation(
        summary = "Delete purchase return",
        description = "Permanently deletes a purchase return. Only allowed in DRAFT status "
                    + "when no inventory ledger transactions reference it."
    )
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        service.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
