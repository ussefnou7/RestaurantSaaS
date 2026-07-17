package com.smart.restaurant_saas.branch.table;

import com.smart.restaurant_saas.branch.table.dto.CreateTableRequest;
import com.smart.restaurant_saas.branch.table.dto.TableResponse;
import com.smart.restaurant_saas.branch.table.dto.UpdateTableRequest;
import com.smart.restaurant_saas.branch.table.dto.UpdateTableStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_VIEW')")
    public List<TableResponse> listTables(@RequestParam(required = false) Long branchId) {
        return tableService.listTables(branchId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    public TableResponse createTable(@Valid @RequestBody CreateTableRequest request) {
        return tableService.createTable(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    public TableResponse updateTable(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableRequest request
    ) {
        return tableService.updateTable(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TABLES_MANAGE')")
    public TableResponse updateTableStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableStatusRequest request
    ) {
        return tableService.updateTableStatus(id, request);
    }
}
