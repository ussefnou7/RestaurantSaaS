package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.tenant.CurrentTenantId;

import com.smart.restaurant_saas.order.core.dto.OrderFilters;
import com.smart.restaurant_saas.order.core.dto.OrderRequest;
import com.smart.restaurant_saas.order.core.dto.OrderResponse;
import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.core.dto.ReceiptLookupRequest;
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
        description = "Persists a POS-completed order using the authenticated device's branch and user, "
                    + "resolves the branch warehouse server-side, and freezes each line's active recipe version. "
                    + "Money is stored verbatim: subtotal, taxAmount, totalAmount and each lineTotal are what the "
                    + "POS printed and collected, and the server neither re-derives them nor holds a tax rate to "
                    + "re-derive them with (D129). Figures that fail to reconcile are logged and still persisted — "
                    + "the sale is already paid, so it is recorded either way."
    )
    public ResponseEntity<OrderResponse> createCompletedOrder(
            @Valid @RequestBody OrderRequest request,
            @CurrentTenantId Long tenantId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.createCompletedOrder(request, tenantId));
    }

    /**
     * Gated on {@code ORDERS_CREATE}, not {@code ORDERS_VIEW}: this is a cashier looking up a
     * receipt a customer has brought back, and the person who takes payments is exactly who needs
     * it. {@code ORDERS_VIEW} would be the wrong gate — it carries the filterable order list,
     * which is the browsing this endpoint exists to provide an alternative to.
     *
     * <p>Kept as its own route rather than a filter on the list for the same reason: a filter can
     * be relaxed a parameter at a time until it is a list again, while a route that takes exactly
     * two values and returns exactly one order cannot drift into one.
     */
    @PostMapping("/lookup-receipt")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_CREATE')")
    @Operation(
        summary = "Look up one order from its printed receipt",
        description = "Takes the order number AND the printed total — both appear on the receipt — "
            + "and returns that single order in full, including its lines, so it can be reviewed "
            + "with the customer or reprinted. Requiring the total is the access control: the "
            + "order number is an enumerable per-device counter, so matching on it alone would let "
            + "a caller read back the per-order amounts a blind count depends on withholding "
            + "(D123). A wrong total is indistinguishable from an order that does not exist, and "
            + "repeated failures are throttled. Scoped to the branch of the caller's signed "
            + "device; a session with no device cannot reach it."
    )
    public OrderResponse lookupByReceipt(
            @Valid @RequestBody ReceiptLookupRequest request,
            @CurrentTenantId Long tenantId) {
        return orderService.lookupByReceipt(request, tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_VIEW')")
    @Operation(summary = "Get order details", description = "Returns an order with all persisted lines.")
    public OrderResponse getById(
            @PathVariable Long id,
            @CurrentTenantId Long tenantId) {
        return orderService.getOrderById(id, tenantId);
    }

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_VIEW')")
    @Operation(
        summary = "List orders",
        description = "Returns a paginated order list filterable by type, source, status, branch, and order date."
    )
    public Page<OrderSummaryResponse> list(
            @CurrentTenantId Long tenantId,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(required = false) OrderSource orderSource,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        OrderFilters filters = new OrderFilters(
            orderType,
            orderSource,
            status,
            branchId,
            fromDate,
            toDate,
            orderNo,
            createdBy,
            customerId);
        return orderService.listOrders(tenantId, filters, pageable);
    }
}
