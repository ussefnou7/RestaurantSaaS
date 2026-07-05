package com.smart.restaurant_saas.inventory.uom;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smart.restaurant_saas.inventory.core.UomService;
import com.smart.restaurant_saas.inventory.uom.dto.UomRequest;
import com.smart.restaurant_saas.inventory.uom.dto.UomResponse;

@RestController
@RequestMapping("/sys-admin/uom")
@RequiredArgsConstructor
@PreAuthorize("@securityService.isSysAdmin()")
@Tag(name = "SysAdmin - UOM", description = "Global UOM catalog management (SysAdmin only)")
public class PanelUomController {

    private final UomService uomService;

    @GetMapping
    @Operation(
        summary = "List all global UOMs",
        description = "Returns all global UOMs (tenant_id = NULL) including inactive ones. "
                    + "Accessible by SysAdmin only. Used to manage the global UOM catalog "
                    + "that is available to all tenants."
    )
    public List<UomResponse> listGlobal() {
        return uomService.findAllGlobal();
    }

    @PostMapping
    @Operation(
        summary = "Create a global UOM",
        description = "Creates a new global Unit of Measure visible to all tenants. "
                    + "The code must be unique across all global UOMs. "
                    + "Global UOMs cannot be deleted once created, only deactivated."
    )
    public ResponseEntity<UomResponse> createGlobal(@Valid @RequestBody UomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(uomService.createGlobal(request));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(
        summary = "Deactivate a global UOM",
        description = "Marks a global UOM as inactive. It will no longer appear in any "
                    + "tenant dropdowns or selection screens. Historical records referencing "
                    + "this UOM are not affected. This action cannot be undone via API."
    )
    public UomResponse deactivate(@PathVariable Long id) {
        return uomService.deactivate(id, null, true);
    }
}
