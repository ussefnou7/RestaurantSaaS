package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocDetailResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocListResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionMaterialsSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/order-consumption-docs")
@RequiredArgsConstructor
@Tag(name = "Inventory - Order Consumption", description = "Order-driven inventory consumption documents")
public class OrderConsumptionController {

    private final OrderConsumptionService service;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(summary = "List order consumption documents")
    public Page<OrderConsumptionDocListResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) OrderConsumptionStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(tenantId, warehouseId, status, dateFrom, dateTo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(summary = "Get order consumption document with lines")
    public OrderConsumptionDocDetailResponse getById(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.getById(id, tenantId);
    }

    @GetMapping("/{id}/materials-summary")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Get material consumption summary for an order consumption document",
        description = "Aggregates all order lines in this doc by material through their frozen recipe. "
                    + "On-demand only — not called during list/detail browsing."
    )
    public OrderConsumptionMaterialsSummaryResponse getMaterialsSummary(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return service.getMaterialsSummary(id, tenantId);
    }

    @PostMapping("/{id}/recalculate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    @Operation(
        summary = "Recalculate order consumption document",
        description = "Testing-phase direct consumption path. Locks the document, aggregates order "
                    + "lines by recipe/material, posts consumption through the inventory ledger, "
                    + "and marks the whole document POSTED or CONFLICT."
    )
    public OrderConsumptionDocResponse recalculate(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.recalculate(id, tenantId, userId);
    }
}
