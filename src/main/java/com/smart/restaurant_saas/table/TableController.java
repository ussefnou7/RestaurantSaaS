package com.smart.restaurant_saas.table;

import com.smart.restaurant_saas.table.dto.TableLayoutRequest;
import com.smart.restaurant_saas.table.dto.TableRequest;
import com.smart.restaurant_saas.table.dto.TableResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
@Tag(name = "Tables", description = "Restaurant table master data and layout management")
public class  TableController {

    private final TableService tableService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_VIEW')")
    @Operation(summary = "List restaurant tables", description = "Lists tenant tables with optional branch and section filters.")
    public List<TableResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long sectionId) {
        return tableService.findAll(tenantId, branchId, sectionId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_VIEW')")
    @Operation(summary = "Get restaurant table", description = "Returns one tenant-owned restaurant table.")
    public TableResponse get(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return tableService.findById(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Create restaurant table", description = "Creates a tenant-owned restaurant table.")
    public ResponseEntity<TableResponse> create(
            @Valid @RequestBody TableRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tableService.create(request, tenantId, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Update restaurant table", description = "Updates table identity fields, not layout coordinates.")
    public TableResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TableRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return tableService.update(id, request, tenantId, userId);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Activate restaurant table", description = "Marks the table active.")
    public TableResponse activate(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return tableService.activate(id, tenantId, userId);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Deactivate restaurant table", description = "Marks the table inactive.")
    public TableResponse deactivate(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return tableService.deactivate(id, tenantId, userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Delete restaurant table", description = "Deletes a table only when no order references it; otherwise 409.")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        tableService.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/layout")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Update table layout", description = "Updates only shape, coordinates, and rotation.")
    public TableResponse updateLayout(
            @PathVariable Long id,
            @Valid @RequestBody TableLayoutRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return tableService.updateLayout(id, request, tenantId, userId);
    }
}
