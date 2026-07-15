package com.smart.restaurant_saas.order.intake;

import com.smart.restaurant_saas.order.intake.dto.IncomingOrderRequestCreateRequest;
import com.smart.restaurant_saas.order.intake.dto.IncomingOrderRequestFilters;
import com.smart.restaurant_saas.order.intake.dto.IncomingOrderRequestResponse;
import com.smart.restaurant_saas.order.intake.dto.LinkCompletedOrderRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-requests")
@RequiredArgsConstructor
@Tag(name = "Order Intake", description = "Online and aggregator order intake before POS completion")
public class IncomingOrderRequestController {

    private final IncomingOrderRequestService requestService;

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_CREATE')")
    @Operation(summary = "Create incoming order request")
    public ResponseEntity<IncomingOrderRequestResponse> createRequest(
            @Valid @RequestBody IncomingOrderRequestCreateRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(requestService.createRequest(request, tenantId, userId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_VIEW')")
    @Operation(summary = "Get incoming order request details")
    public IncomingOrderRequestResponse getById(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return requestService.getRequestById(id, tenantId);
    }

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_VIEW')")
    @Operation(summary = "List incoming order requests")
    public Page<IncomingOrderRequestResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) IncomingOrderSource source,
            @RequestParam(required = false) IncomingOrderRequestStatus status,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        IncomingOrderRequestFilters filters =
            new IncomingOrderRequestFilters(source, status, branchId, fromDate, toDate);
        return requestService.listRequests(tenantId, filters, pageable);
    }

    @PatchMapping("/{id}/mark-sent-to-pos")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_CREATE')")
    @Operation(summary = "Mark incoming order request as sent to POS")
    public IncomingOrderRequestResponse markSentToPos(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return requestService.markSentToPos(id, tenantId, userId);
    }

    @PatchMapping("/{id}/link")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ORDERS_CREATE')")
    @Operation(summary = "Link incoming order request to completed order")
    public IncomingOrderRequestResponse linkToCompletedOrder(
            @PathVariable Long id,
            @Valid @RequestBody LinkCompletedOrderRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return requestService.linkToCompletedOrder(id, request.getOrderId(), tenantId, userId);
    }
}
