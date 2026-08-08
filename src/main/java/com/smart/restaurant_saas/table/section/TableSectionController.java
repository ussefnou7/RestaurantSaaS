package com.smart.restaurant_saas.table.section;

import com.smart.restaurant_saas.table.section.dto.TableSectionRequest;
import com.smart.restaurant_saas.table.section.dto.TableSectionResponse;
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
@RequestMapping("/api/table-sections")
@RequiredArgsConstructor
@Tag(name = "Table Sections", description = "Restaurant table section master data")
public class TableSectionController {

    private final TableSectionService sectionService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_VIEW')")
    @Operation(summary = "List table sections", description = "Lists active sections for a branch, with optional inactive rows for management.")
    public List<TableSectionResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam Long branchId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return sectionService.findAll(tenantId, branchId, includeInactive);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_VIEW')")
    @Operation(summary = "Get table section", description = "Returns one tenant-owned table section.")
    public TableSectionResponse get(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return sectionService.findById(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Create table section", description = "Creates a table section for a branch.")
    public ResponseEntity<TableSectionResponse> create(
            @Valid @RequestBody TableSectionRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sectionService.create(request, tenantId, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Update table section", description = "Updates a table section.")
    public TableSectionResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TableSectionRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return sectionService.update(id, request, tenantId, userId);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Activate table section", description = "Marks the section active.")
    public TableSectionResponse activate(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return sectionService.activate(id, tenantId, userId);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Deactivate table section", description = "Marks the section inactive.")
    public TableSectionResponse deactivate(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return sectionService.deactivate(id, tenantId, userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    @Operation(summary = "Delete table section", description = "Deletes a section only when no tables reference it.")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        sectionService.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
