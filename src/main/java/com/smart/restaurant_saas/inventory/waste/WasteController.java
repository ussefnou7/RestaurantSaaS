package com.smart.restaurant_saas.inventory.waste;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smart.restaurant_saas.inventory.core.CancelDocumentRequest;
import com.smart.restaurant_saas.inventory.core.WasteService;
import com.smart.restaurant_saas.inventory.waste.dto.UncompleteWasteRequest;
import com.smart.restaurant_saas.inventory.waste.dto.WasteDocumentRequest;
import com.smart.restaurant_saas.inventory.waste.dto.WasteDocumentResponse;
import com.smart.restaurant_saas.inventory.waste.dto.WasteLineRequest;
import com.smart.restaurant_saas.inventory.waste.dto.WasteUpdateLineRequest;

@RestController
@RequestMapping("/api/inventory/waste-documents")
@RequiredArgsConstructor
@Tag(name = "Inventory - Waste", description = "Writing off spoiled, expired or damaged stock")
public class WasteController {

    private final WasteService service;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_VIEW')")
    @Operation(
        summary = "List waste documents",
        description = "Returns all waste documents for the current tenant ordered by waste date "
                    + "descending, optionally filtered by warehouse. Lines not included — use "
                    + "GET /{id} for full details."
    )
    public List<WasteDocumentResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) Long warehouseId) {
        return warehouseId != null
            ? service.findAllByWarehouse(tenantId, warehouseId)
            : service.findAll(tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_VIEW')")
    @Operation(
        summary = "Get waste document details",
        description = "Returns the full waste document including all lines."
    )
    public WasteDocumentResponse getById(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.findById(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Create waste document header",
        description = "Creates a new waste document in DRAFT status for a warehouse and generates "
                    + "its code. The document starts with no lines — add them via POST /{id}/lines."
    )
    public ResponseEntity<WasteDocumentResponse> create(
            @Valid @RequestBody WasteDocumentRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.create(request, tenantId, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Update waste document header",
        description = "Updates the header fields (wasteDate, reasonCode, notes). Only allowed in "
                    + "DRAFT status. The warehouse cannot be changed."
    )
    public WasteDocumentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody WasteDocumentRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.update(id, request, tenantId, userId);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Add line to waste document",
        description = "Appends a line for a material and quantity to write off. No cost is "
                    + "supplied — it is computed at POST via FIFO depletion. Only allowed in "
                    + "DRAFT status."
    )
    public WasteDocumentResponse addLine(
            @PathVariable Long id,
            @Valid @RequestBody WasteLineRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.addLine(id, request, tenantId, userId);
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Update waste document line",
        description = "Updates the quantity/UOM/notes of a line. The material cannot change — "
                    + "delete and re-add the line instead. Only allowed in DRAFT status."
    )
    public WasteDocumentResponse updateLine(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @Valid @RequestBody WasteUpdateLineRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.updateLine(id, lineId, request, tenantId, userId);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Delete waste document line",
        description = "Removes a line from a DRAFT waste document. Only allowed in DRAFT status."
    )
    public ResponseEntity<WasteDocumentResponse> deleteLine(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(service.deleteLine(id, lineId, tenantId, userId));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Complete waste document",
        description = "Marks the waste document as COMPLETE — ready for posting."
    )
    public WasteDocumentResponse complete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.complete(id, tenantId, userId);
    }

    @PostMapping("/{id}/uncomplete")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('WASTE_UNCOMPLETE')")
    @Operation(
        summary = "UnComplete waste document",
        description = "Moves a COMPLETE waste document back to DRAFT for editing. "
                    + "Does not change the document code and does not touch inventory ledger state."
    )
    public WasteDocumentResponse uncomplete(
            @PathVariable Long id,
            @RequestBody(required = false) UncompleteWasteRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.uncomplete(id, request, tenantId, userId);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Post waste document to inventory",
        description = "Posts the document — first verifies every material's waste quantity does "
                    + "not exceed its available stock, then issues WASTE / OUT ledger "
                    + "transactions that FIFO-deplete batches at the ledger-computed cost."
    )
    public WasteDocumentResponse post(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.post(id, tenantId, userId);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Cancel waste document",
        description = "Cancels the document. Only allowed from DRAFT or COMPLETE status."
    )
    public WasteDocumentResponse cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelDocumentRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String reason = request != null ? request.getReason() : null;
        return service.cancel(id, reason, tenantId, userId);
    }
}
