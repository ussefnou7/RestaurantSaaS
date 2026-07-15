package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.order.core.dto.OrderFilters;
import com.smart.restaurant_saas.order.core.dto.OrderRequest;
import com.smart.restaurant_saas.order.core.dto.OrderResponse;
import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "POS-completed order ingestion and review")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_CREATE')")
    @Operation(
        summary = "Create completed order",
        description = "Persists a POS-completed order, resolves its branch from X-Branch-Id, "
                    + "resolves the branch warehouse server-side, and freezes each line's active recipe version."
    )
    public ResponseEntity<OrderResponse> createCompletedOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-Branch-Id") Long branchId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.createCompletedOrder(request, tenantId, userId, branchId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_VIEW')")
    @Operation(summary = "Get order details", description = "Returns an order with all persisted lines.")
    public OrderResponse getById(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return orderService.getOrderById(id, tenantId);
    }

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_VIEW')")
    @Operation(
        summary = "List orders",
        description = "Returns a paginated order list filterable by type, source, status, branch, and order date."
    )
    public Page<OrderSummaryResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(required = false) OrderSource orderSource,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        OrderFilters filters = new OrderFilters(orderType, orderSource, status, branchId, fromDate, toDate);
        return orderService.listOrders(tenantId, filters, pageable);
    }
}
