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
import com.smart.restaurant_saas.inventory.core.PurchaseInvoiceService;
import com.smart.restaurant_saas.inventory.purchase.dto.BackdatedConsumptionCheckResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceHeaderRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceUpdateLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.UncompleteRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.UnpostRequest;

@RestController
@RequestMapping("/api/inventory/purchase-invoices")
@RequiredArgsConstructor
@Tag(name = "Inventory - Purchase Invoice", description = "Receiving goods and recording purchase costs")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService service;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_VIEW')")
    @Operation(
        summary = "List purchase invoices",
        description = "Returns all purchase invoices for the current tenant "
                    + "ordered by invoice date descending. Lines are not included "
                    + "in the list view — use GET /{id} for full details."
    )
    public List<PurchaseInvoiceResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.findAll(tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_VIEW')")
    @Operation(
        summary = "Get purchase invoice details",
        description = "Returns full purchase invoice including all lines. "
                    + "Used for the invoice detail screen and edit form."
    )
    public PurchaseInvoiceResponse getById(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.findById(id, tenantId);
    }

    @GetMapping("/{id}/backdated-consumption-check")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_VIEW')")
    @Operation(
        summary = "Check whether an invoice receipt date predates consumption",
        description = "For a COMPLETE invoice, returns invoice materials whose latest stock-consuming "
                    + "movement in the invoice warehouse falls on a later calendar day than "
                    + "receiptDate. A same-day consumption is not backdated and is not reported: the "
                    + "receipt is stamped at the start of its day and ties break on id, so the batch "
                    + "it opens already precedes that consumption. This check is advisory and never "
                    + "changes or blocks posting. Other invoice statuses return an empty list."
    )
    public List<BackdatedConsumptionCheckResponse> getBackdatedConsumptionCheck(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.findBackdatedConsumptionConflicts(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Create purchase invoice",
        description = "Creates a new purchase invoice header in DRAFT status. "
                    + "The invoice starts with no lines — add them via "
                    + "POST /{id}/lines. The header can be edited freely until "
                    + "the invoice is completed."
    )
    public ResponseEntity<PurchaseInvoiceResponse> create(
            @Valid @RequestBody PurchaseInvoiceHeaderRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.create(request, tenantId, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Update purchase invoice header",
        description = "Updates the purchase invoice header fields only. "
                    + "Only allowed in DRAFT status. Lines are managed via the "
                    + "dedicated /{id}/lines endpoints; totals are recalculated "
                    + "from the existing lines."
    )
    public PurchaseInvoiceResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseInvoiceHeaderRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.update(id, request, tenantId, userId);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Add line to purchase invoice",
        description = "Appends a new line to a DRAFT invoice and recalculates totals. "
                    + "Only allowed in DRAFT status."
    )
    public PurchaseInvoiceResponse addLine(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseInvoiceLineRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.addLine(id, request, tenantId);
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Update purchase invoice line",
        description = "Updates an existing line on a DRAFT invoice and recalculates totals. "
                    + "The material cannot be changed — delete and re-add the line instead. "
                    + "Only allowed in DRAFT status."
    )
    public PurchaseInvoiceResponse updateLine(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @Valid @RequestBody PurchaseInvoiceUpdateLineRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.updateLine(id, lineId, request, tenantId);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Delete purchase invoice line",
        description = "Removes a line from a DRAFT invoice and recalculates totals. "
                    + "Only allowed in DRAFT status."
    )
    public ResponseEntity<PurchaseInvoiceResponse> deleteLine(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return ResponseEntity.ok(service.deleteLine(id, lineId, tenantId));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Complete purchase invoice",
        description = "Marks the invoice as COMPLETE — ready for posting. "
                    + "Only allowed from DRAFT status. "
                    + "After completion the invoice can no longer be edited."
    )
    public PurchaseInvoiceResponse complete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.complete(id, tenantId, userId);
    }

    @PostMapping("/{id}/post")
    @Operation(
        summary = "Post purchase invoice to inventory",
        description = "Posts the invoice to inventory — triggers Stock In for all lines. "
                    + "Only allowed from COMPLETE status. "
                    + "Updates stock quantities, average costs, and last purchase prices."
    )
    public PurchaseInvoiceResponse post(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.post(id, tenantId, userId);
    }

    @PostMapping("/{id}/unpost")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PURCHASE_INVOICE_UNPOST')")
    @Operation(
        summary = "Unpost purchase invoice",
        description = "Reverts a POSTED invoice to COMPLETE by recording reversal ledger transactions. "
                    + "Rejected if any purchase return references the invoice or any opened batch "
                    + "has already been consumed."
    )
    public PurchaseInvoiceResponse unpost(
            @PathVariable Long id,
            @RequestBody(required = false) UnpostRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.unpost(id, request, tenantId, userId);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    @Operation(
        summary = "Cancel purchase invoice",
        description = "Cancels the invoice. Only allowed from DRAFT or COMPLETE status. "
                    + "Posted invoices cannot be cancelled — create a Purchase Return instead."
    )
    public PurchaseInvoiceResponse cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelDocumentRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String reason = request != null ? request.getReason() : null;
        return service.cancel(id, reason, tenantId, userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PURCHASE_INVOICE_DELETE')")
    @Operation(
        summary = "Delete purchase invoice",
        description = "Permanently deletes a purchase invoice. Only allowed in DRAFT status "
                    + "when no inventory ledger transactions reference it. "
                    + "Completed, posted, or cancelled invoices cannot be deleted — "
                    + "cancel the invoice instead to preserve the audit trail."
    )
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        service.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/uncomplete")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PURCHASE_INVOICE_UNCOMPLETE')")
    @Operation(
        summary = "UnComplete purchase invoice",
        description = "Moves a COMPLETE invoice back to DRAFT for editing. "
                    + "Does not change the invoice number and does not touch inventory ledger state."
    )
    public PurchaseInvoiceResponse uncomplete(
            @PathVariable Long id,
            @RequestBody(required = false) UncompleteRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.uncomplete(id, request, tenantId, userId);
    }
}
