package com.smart.restaurant_saas.inventory.uom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smart.restaurant_saas.inventory.core.UomService;
import com.smart.restaurant_saas.inventory.uom.dto.UomLookupItemResponse;
import com.smart.restaurant_saas.inventory.uom.dto.UomLookupResponse;
import com.smart.restaurant_saas.inventory.uom.dto.UomRequest;
import com.smart.restaurant_saas.inventory.uom.dto.UomResponse;

@RestController
@RequestMapping("/api/uom")
@RequiredArgsConstructor
@Tag(name = "Inventory - UOM", description = "Tenant-facing Unit of Measure management")
public class UomController {

    private final UomService uomService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_SETUP_VIEW')")
    @Operation(
        summary = "List available UOMs for tenant",
        description = "Returns all active UOMs available to the current tenant: "
                    + "global UOMs (shared across all tenants) and the tenant's own custom UOMs. "
                    + "Global UOMs appear first, ordered by name. "
                    + "Used to populate all UOM dropdowns across the system."
    )
    public List<UomResponse> listAvailable(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return uomService.findAvailableForTenant(tenantId);
    }

    /**
     * Spring Security's default headers include {@code no-store}, which forbids the browser from
     * keeping the representation at all. A {@code 304} then has nothing to revalidate against: the
     * browser treats it as a failed request, XHR reports status 0, and revalidate-on-open fails
     * silently — the picker keeps serving stale units and nothing surfaces. Verified in a browser;
     * curl does not reproduce it because curl has no cache semantics.
     *
     * <p>{@code no-cache, private} still forces revalidation before every use, but permits storage,
     * which is what makes the conditional request work.
     */
    private static CacheControl revalidatableCache() {
        return CacheControl.noCache().cachePrivate();
    }

    @GetMapping("/lookup")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_SETUP_VIEW')")
    @Operation(
        summary = "Lookup all UOMs for tenant display",
        description = "Returns all UOMs resolvable by the current tenant, including inactive "
                    + "historical units, with an opaque cache version."
    )
    public ResponseEntity<UomLookupResponse> lookup(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String version = uomService.lookupVersionForTenant(tenantId);
        String etag = UomLookupVersionService.etagValue(version);

        if (UomLookupVersionService.matchesEtag(ifNoneMatch, version)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .cacheControl(revalidatableCache())
                .header(HttpHeaders.ETAG, etag)
                .build();
        }

        UomLookupResponse response = uomService.findLookupForTenant(tenantId);
        return ResponseEntity.ok()
            .cacheControl(revalidatableCache())
            .header(HttpHeaders.ETAG, etag)
            .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_SETUP_VIEW')")
    @Operation(
        summary = "Resolve one UOM for tenant display",
        description = "Returns one UOM visible to the current tenant, including inactive "
                    + "historical units."
    )
    public UomLookupItemResponse resolve(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return uomService.resolveForTenant(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    @Operation(
        summary = "Create a custom UOM",
        description = "Creates a new Unit of Measure specific to this tenant. "
                    + "Used for non-standard units like 'box of tomatoes' or 'oil can' "
                    + "that have a known factorToBase relative to a standard base unit. "
                    + "Example: 1 box of tomatoes = 6000 grams → baseUom=GRAM, factorToBase=6000."
    )
    public ResponseEntity<UomResponse> create(
            @Valid @RequestBody UomRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(uomService.createForTenant(request, tenantId));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    @Operation(
        summary = "Deactivate a tenant UOM",
        description = "Marks a tenant-specific UOM as inactive. It will no longer appear "
                    + "in dropdowns. Only the owning tenant can deactivate their own UOMs. "
                    + "Global UOMs cannot be deactivated through this endpoint."
    )
    public UomResponse deactivate(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return uomService.deactivate(id, tenantId, false);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    @Operation(
        summary = "Delete a tenant UOM",
        description = "Permanently deletes a tenant-specific UOM. Only allowed if the UOM "
                    + "is not referenced in any material, transaction, or invoice. "
                    + "If referenced, use deactivate instead. Global UOMs cannot be deleted."
    )
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        uomService.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
