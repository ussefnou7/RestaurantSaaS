package com.smart.restaurant_saas.inventory.physicalcount;

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
import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.material.dto.AddMaterialsRequest;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountRequest;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountSummaryResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementsResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.UpdateCountedQuantitiesRequest;

@RestController
@RequestMapping("/api/inventory/physical-counts")
@RequiredArgsConstructor
@Tag(name = "Inventory - Physical Count", description = "Verify and correct actual stock quantities")
public class PhysicalCountController {

    private final PhysicalCountService service;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_VIEW')")
    @Operation(
        summary = "List physical counts",
        description = "Returns all physical counts for the current tenant "
                    + "ordered by scheduled date descending. "
                    + "Lines not included — use GET /{id} for full details."
    )
    public List<PhysicalCountSummaryResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) Long warehouseId) {
        return warehouseId != null
            ? service.findAllByWarehouse(tenantId, warehouseId)
            : service.findAll(tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_VIEW')")
    @Operation(
        summary = "Get physical count details",
        description = "Returns full physical count with all lines including "
                    + "frozen and adjusted expected quantities, counted quantities, variances, "
                    + "and actions taken. For unreconciled frozen counts, adjusted values are "
                    + "calculated live through countedAt, or through now when "
                    + "adjustedExpectedQuantityProvisional is true. "
                    + "A movement that cannot be converted to the line's frozen UOM fails with "
                    + "UOM_CONVERSION_FAILED. Reconciled counts return their stored audit values. "
                    + "Used for the count detail screen. "
                    + "Line varianceValue is |variance| x the freeze-time average cost and is "
                    + "flagged varianceValueIsEstimate: the ledger values the same movement from "
                    + "the FIFO batches it consumes (shortage) or the average at reconcile time "
                    + "(surplus), so the two figures differ. Reports read the ledger."
    )
    public PhysicalCountResponse getById(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.findById(id, tenantId);
    }

    @GetMapping("/{id}/post-freeze-movements")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_VIEW')")
    @Operation(
        summary = "Report inventory movements recorded after the count's freeze",
        description = "Lists open-ended activity in this count's warehouse since frozenAt: the total "
                    + "movement count and number of distinct materials affected across the whole "
                    + "warehouse, plus a per-material breakdown limited to the materials in this "
                    + "count document. Breakdown quantities use each count line's frozen UOM, "
                    + "identified by uomId and uomSymbol. Reconciliation uses only each counted "
                    + "material's subset through that line's countedAt; later activity remains visible here for "
                    + "context. This count's own corrections are excluded. No time limit and no "
                    + "same-day rule. Requires a frozen count "
                    + "(IN_PROGRESS or RECONCILED); 409 otherwise."
    )
    public PostFreezeMovementsResponse getPostFreezeMovements(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.findPostFreezeMovements(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Create physical count",
        description = "Creates a new physical count in DRAFT status. "
                    + "Materials to count are selected upfront. "
                    + "Expected quantities are NOT set yet — they are captured "
                    + "when the count transitions to IN_PROGRESS."
    )
    public ResponseEntity<PhysicalCountResponse> create(
            @Valid @RequestBody PhysicalCountRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.create(request, tenantId, userId));
    }

    @PostMapping("/{id}/add-materials")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Add materials to count",
        description = "Adds more materials to a DRAFT count. "
                    + "Materials already in the count are silently skipped. "
                    + "Not allowed after count has started."
    )
    public PhysicalCountResponse addMaterials(
            @PathVariable Long id,
            @Valid @RequestBody AddMaterialsRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.addMaterials(id, request.getMaterialIds(), tenantId);
    }

    @PostMapping("/{id}/remove-materials")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Remove materials from count",
        description = "Removes materials from a DRAFT count. "
                    + "Materials not in the count are silently skipped. "
                    + "Not allowed after count has started."
    )
    public PhysicalCountResponse removeMaterials(
            @PathVariable Long id,
            @Valid @RequestBody AddMaterialsRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.removeMaterials(id, request.getMaterialIds(), tenantId);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Start physical count",
        description = "Transitions count to IN_PROGRESS. "
                    + "Records frozenAt timestamp and takes a snapshot of "
                    + "current stock quantities and average costs for all lines. "
                    + "frozenAt is the exclusive lower bound for movements that advance each "
                    + "line's expected quantity to its eventual countedAt. "
                    + "The warehouse's outstanding order consumption is settled first, so the "
                    + "snapshot already accounts for everything sold: a PENDING document is "
                    + "processed before the snapshot is taken, and a CONFLICT document blocks "
                    + "the freeze with 409 FREEZE_BLOCKED_BY_CONSUMPTION_CONFLICT (params carry "
                    + "docId and the failing materials). Order intake is never blocked."
    )
    public PhysicalCountResponse start(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.start(id, tenantId, userId);
    }

    @PostMapping("/{id}/revert-to-draft")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PHYSICAL_COUNT_REVERT_TO_DRAFT')")
    @Operation(
        summary = "Revert physical count to draft",
        description = "Resets an IN_PROGRESS count back to DRAFT by clearing freeze and counted quantities. "
                    + "No ledger entries or stock balances are changed because reconciliation has not run."
    )
    public PhysicalCountResponse revertToDraft(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.revertToDraft(id, tenantId, userId);
    }

    @PutMapping("/{id}/counted-quantities")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Update counted quantities",
        description = "Records the physical quantities counted by the team. "
                    + "Can be called multiple times — each call updates "
                    + "the provided lines and refreshes their countedAt timestamps. "
                    + "Only allowed in IN_PROGRESS status."
    )
    public PhysicalCountResponse updateCountedQuantities(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCountedQuantitiesRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.updateCountedQuantities(id, request, tenantId, userId);
    }

    @PostMapping("/{id}/reconcile")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Reconcile physical count",
        description = "Finalizes the count and posts variances to inventory. Takes no body: a "
                    + "count produces exactly one kind of movement (COUNT_ADJUSTMENT) and the "
                    + "sign carries the meaning — a shortage posts OUT, a surplus posts IN. "
                    + "There is no per-line waste/adjustment choice and a count never creates a "
                    + "waste document. All lines must have counted quantities before reconciling. "
                    + "For each line, expected quantity is advanced by signed movements after "
                    + "frozenAt through countedAt, then variance is measured against that adjusted "
                    + "expectation. Each movement is dated at its line's countedAt, so one document "
                    + "can produce movements with different dates. "
                    + "Lines with a zero variance post nothing. "
                    + "This action is irreversible."
    )
    public PhysicalCountResponse reconcile(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.reconcile(id, tenantId, userId);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Cancel physical count",
        description = "Cancels the count. Only allowed from DRAFT or IN_PROGRESS. "
                    + "Reconciled counts cannot be cancelled."
    )
    public PhysicalCountResponse cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelDocumentRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String reason = request != null ? request.getReason() : null;
        return service.cancel(id, reason, tenantId, userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PHYSICAL_COUNT_DELETE')")
    @Operation(
        summary = "Delete physical count",
        description = "Permanently deletes a DRAFT or IN_PROGRESS physical count and its lines. "
                    + "RECONCILED counts are final and cannot be deleted."
    )
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        service.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
