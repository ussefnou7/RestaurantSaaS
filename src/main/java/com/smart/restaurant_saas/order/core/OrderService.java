package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.auth.service.CurrentUserService;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.device.Device;
import com.smart.restaurant_saas.device.repository.DeviceRepository;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionService;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.loyalty.customer.Customer;
import com.smart.restaurant_saas.loyalty.customer.CustomerService;
import com.smart.restaurant_saas.menu.product.Product;
import com.smart.restaurant_saas.menu.product.ProductRepository;
import com.smart.restaurant_saas.menu.recipe.Recipe;
import com.smart.restaurant_saas.menu.recipe.RecipeRepository;
import com.smart.restaurant_saas.menu.recipe.RecipeService;
import com.smart.restaurant_saas.menu.recipe.dto.RecipeResponse;
import com.smart.restaurant_saas.order.OrderErrorCode;
import com.smart.restaurant_saas.pos.shift.Shift;
import com.smart.restaurant_saas.pos.shift.ShiftErrorCode;
import com.smart.restaurant_saas.pos.shift.ShiftRepository;
import com.smart.restaurant_saas.pos.shift.ShiftStatus;
import com.smart.restaurant_saas.table.RestaurantTable;
import com.smart.restaurant_saas.table.TableRepository;
import com.smart.restaurant_saas.order.core.dto.OrderFilters;
import com.smart.restaurant_saas.order.core.dto.OrderLineRequest;
import com.smart.restaurant_saas.order.core.dto.OrderRequest;
import com.smart.restaurant_saas.order.core.dto.OrderResponse;
import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.core.dto.ReceiptLookupRequest;
import com.smart.restaurant_saas.order.core.enums.OrderCancellationReason;
import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderLineType;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * How far the POS's own arithmetic may drift from a straight re-derivation before it is worth
     * a log line (D129). One whole currency unit: the POS rounds the charged total to the unit the
     * drawer can actually make change in, so a sub-unit gap is the rounding rule working, not a
     * fault. Anything larger means the money on the receipt no longer relates to the goods on it.
     */
    private static final BigDecimal TOTALS_TOLERANCE = new BigDecimal("1.00");

    /**
     * Slack on the {@code subtotal + taxAmount = totalAmount} identity (D129). Half a piastre:
     * below anything money can express, so a real mistake still trips it. Not zero, because these
     * arrive as JSON numbers off a JavaScript client — {@code 99.50 + 13.93} can serialise as
     * {@code 113.42999999999999}, and an exact comparison would report binary dust as a fault and
     * train the reader to ignore the warning.
     */
    private static final BigDecimal HEADER_TOLERANCE = new BigDecimal("0.005");

    /** Failed receipt checks tolerated per drawer per {@link #RECEIPT_CHECK_WINDOW}. */
    private static final int MAX_FAILED_RECEIPT_CHECKS = 5;
    private static final Duration RECEIPT_CHECK_WINDOW = Duration.ofMinutes(1);

    private final Map<Long, FailedChecks> receiptCheckFailures = new ConcurrentHashMap<>();

    private final OrderRepository orderRepository;
    private final DeviceRepository deviceRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final OrderConsumptionService orderConsumptionService;
    private final CustomerService customerService;
    private final ShiftRepository shiftRepository;
    private final TableRepository tableRepository;
    private final CurrentUserService currentUserService;
    private final OrderMapper mapper;
    private final CurrentUserScopeProvider currentUserScopeProvider;

    @Transactional
    public OrderResponse createCompletedOrder(OrderRequest request, Long tenantId) {
        validateOrderType(request);
        validateCancellationStage(request);

        // O16: a retry after a lost response resends the same idempotencyKey —
        // return the existing order (a safe replay) instead of creating a
        // second one for the same sale.
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existing = orderRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                return mapper.toResponse(existing.get());
            }
        }

        Shift shift = resolveOpenShift(tenantId);
        Branch branch = requireActiveBranch(shift.getDevice().getBranch());
        Warehouse warehouse = resolveWarehouseForBranch(branch.getId(), tenantId);
        Long userId = currentUserService.getCurrentUserId();

        Order order = new Order();
        order.setTenantId(tenantId);
        order.setCreatedBy(userId);
        order.setOrderType(request.getOrderType());
        order.setOrderSource(request.getOrderSource());
        order.setAggregatorName(request.getAggregatorName());
        order.setStatus(request.getStatus());
        order.setCancellationStage(request.getCancellationStage());
        order.setCancellationReason(request.getCancellationReason());
        order.setCancellationReasonNote(request.getCancellationReasonNote());
        order.setPaymentMethod(request.getPaymentMethod());
        order.setTable(resolveTable(request.getTableId(), branch, tenantId));
        order.setBranch(branch);
        order.setWarehouse(warehouse);
        order.setOrderDate(request.getOrderDate());
        // Stored exactly as measured, and not cross-checked against anything (D132). The sale is
        // already paid by the time this arrives, so refusing it over a timing oddity would lose
        // money that changed hands in order to protect a statistic. A device whose clock moved
        // mid-service produces a figure a report should drop, not a reason to lose the order.
        order.setKitchenTimeSeconds(request.getKitchenTimeSeconds());
        order.setOrderStartedAt(request.getOrderStartedAt());
        order.setExternalOrderReference(request.getExternalOrderReference());
        order.setIdempotencyKey(idempotencyKey);
        order.setOrderNo(request.getOrderNo());
        order.setCustomerId(resolveCustomerId(request, tenantId));
        order.setShift(shift);

        for (OrderLineRequest lineRequest : request.getLines()) {
            order.getLines().add(buildLine(order, lineRequest, tenantId, userId));
        }
        // D129: the POS is the authority on the money. What it printed is what the customer
        // handed over, so that is what gets recorded — no re-derivation, and no tax rate on
        // this side to re-derive it with.
        order.setSubtotal(request.getSubtotal());
        order.setTaxAmount(request.getTaxAmount());
        order.setTotalAmount(request.getTotalAmount());
        logTotalsDivergence(request, tenantId);

        Order saved;
        try {
            saved = orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException ex) {
            // Race: a concurrent request with the same key won first. The
            // unique constraint (V24) is the real backstop here — re-resolve
            // to the winner instead of surfacing a constraint-violation error.
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<Order> existing = orderRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
                if (existing.isPresent()) {
                    return mapper.toResponse(existing.get());
                }
            }
            throw ex;
        }
        if (saved.getStatus() == OrderStatus.COMPLETE) {
            orderConsumptionService.recordCompletedOrder(saved, userId);
        }
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long tenantId) {
        return mapper.toResponse(loadOwned(orderId, tenantId));
    }

    @Transactional(readOnly = true)
    public OrderSummaryResponse getOrderSummaryById(Long orderId, Long tenantId) {
        return mapper.toSummary(loadOwned(orderId, tenantId));
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> listOrders(Long tenantId, OrderFilters filters, Pageable pageable) {
        return orderRepository.findByFilters(
                tenantId,
                filters.orderType(),
                filters.orderSource(),
                filters.status(),
                currentUserScopeProvider.resolveBranchFilter(filters.branchId()),
                filters.fromDate(),
                filters.toDate(),
                filters.orderNo(),
                filters.createdBy(),
                filters.customerId(),
                pageable)
            .map(mapper::toSummary);
    }

    /**
     * The open shift on the device that sent this order.
     *
     * <p>The order's branch and warehouse are derived from this shift's device, so its drawer
     * and inventory cannot be selected independently by client headers.
     *
     * <p>Same single query as before, on a sounder key: the device comes from the already-parsed
     * principal, so nothing extra is loaded on the system's hottest endpoint.
     *
     * <p>Selects or fails. Order creation never creates a shift implicitly.
     */
    private Shift resolveOpenShift(Long tenantId) {
        Long deviceId = currentUserService.requireCurrentDeviceId();
        return shiftRepository.findByDeviceIdAndTenantIdAndStatus(deviceId, tenantId, ShiftStatus.OPEN)
                .orElseThrow(() -> new BusinessException(ShiftErrorCode.NO_OPEN_SHIFT_FOR_DEVICE,
                        "No open shift on device: " + deviceId,
                        ErrorParams.of("deviceId", deviceId)));
    }

    private OrderLine buildLine(Order order, OrderLineRequest request, Long tenantId, Long userId) {
        Product product = loadActiveProduct(request.getProductId(), tenantId);
        Recipe recipe = resolveActiveRecipe(product.getId(), tenantId);

        OrderLine line = new OrderLine();
        line.setTenantId(tenantId);
        line.setCreatedBy(userId);
        line.setOrder(order);
        line.setProduct(product);
        line.setRecipe(recipe);
        line.setQuantity(request.getQuantity());
        line.setUnitPrice(request.getUnitPrice());
        line.setLineTotal(request.getLineTotal());
        line.setLineType(request.getLineType() == null ? OrderLineType.SALE : request.getLineType());
        line.setWasteStage(line.getLineType() == OrderLineType.WASTE ? requireWasteStage(request) : null);
        return line;
    }

    /**
     * Only the two cooked stages are waste: anything cancelled earlier consumed nothing, so a line
     * for it would deduct stock that was never used (D20).
     */
    private CancellationStage requireWasteStage(OrderLineRequest request) {
        CancellationStage stage = request.getWasteStage();
        if (stage != CancellationStage.IN_KITCHEN_COOKED && stage != CancellationStage.AFTER_DONE) {
            throw new ValidationException(OrderErrorCode.CANCELLATION_STAGE_NOT_ALLOWED,
                "A waste line must carry a cooked cancellation stage",
                ErrorParams.of("productId", request.getProductId(), "wasteStage", stage));
        }
        return stage;
    }

    /**
     * Reconciliation, not validation (D129).
     *
     * <p>By the time an order reaches this method the sale is over: the food is gone and the cash
     * is in the drawer, possibly since several hours ago on a device that was offline. There is no
     * version of rejecting it that leaves the books in a better state than recording it — a 4xx
     * strands the order in the device's outbox forever and the drawer is then short with nothing
     * to explain it. So this only ever logs.
     *
     * <p>Two checks, both rate-free so they stay correct across a tax-rate change and across
     * whatever rounding rule the POS adopts next:
     *
     * <ul>
     *   <li><b>Does the money relate to the goods?</b> {@code Σ lineTotal} against the header
     *       subtotal, and each line against {@code quantity × unitPrice}.</li>
     *   <li><b>Is the header self-consistent?</b> {@code subtotal + taxAmount = totalAmount}, which
     *       is a definition rather than a calculation and holds to within binary dust.</li>
     * </ul>
     *
     * <p>Deliberately not checked: whether {@code taxAmount} is the right percentage of
     * {@code subtotal}. That would need a tax rate on this side, which is the duplicate authority
     * D129 exists to remove. The residual exposure is a client that under-reports tax — it would
     * lower the drawer expectation along with it and go unseen here. Catching that belongs to the
     * cumulative variance reporting (D125), not to the write path.
     */
    private void logTotalsDivergence(OrderRequest request, Long tenantId) {
        // Waste lines keep their menu price so the loss can be valued, but the customer never paid
        // for them — so only sale lines belong in the sum the header is checked against (D20).
        BigDecimal lineSum = request.getLines().stream()
            .filter(line -> line.getLineType() != OrderLineType.WASTE)
            .map(OrderLineRequest::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotalGap = lineSum.subtract(request.getSubtotal()).abs();
        BigDecimal headerGap = request.getSubtotal()
            .add(request.getTaxAmount())
            .subtract(request.getTotalAmount())
            .abs();

        boolean lineGap = request.getLines().stream().anyMatch(line ->
            line.getQuantity().multiply(line.getUnitPrice()).setScale(MONEY_SCALE, ROUNDING)
                .subtract(line.getLineTotal()).abs().compareTo(TOTALS_TOLERANCE) > 0);

        if (subtotalGap.compareTo(TOTALS_TOLERANCE) > 0
                || headerGap.compareTo(HEADER_TOLERANCE) > 0
                || lineGap) {
            log.warn("POS totals do not reconcile — recorded as sent (D129). "
                    + "tenantId={} orderNo={} idempotencyKey={} "
                    + "subtotal={} taxAmount={} totalAmount={} sumOfLines={} "
                    + "subtotalGap={} headerGap={} lineGap={}",
                tenantId, request.getOrderNo(), request.getIdempotencyKey(),
                request.getSubtotal(), request.getTaxAmount(), request.getTotalAmount(), lineSum,
                subtotalGap, headerGap, lineGap);
        }
    }

    private void validateOrderType(OrderRequest request) {
        if (request.getOrderType() != OrderType.DINE_IN && request.getTableId() != null) {
            throw new ValidationException(OrderErrorCode.INVALID_TABLE_FOR_ORDER_TYPE,
                "tableId is only allowed for DINE_IN orders",
                ErrorParams.of("orderType", request.getOrderType().name(), "tableId", request.getTableId()));
        }
    }

    // Resolves the optional dine-in table (D76). Must be tenant-owned and live in the
    // order's branch, mirroring TableService's SECTION_BRANCH_MISMATCH shape.
    private RestaurantTable resolveTable(Long tableId, Branch branch, Long tenantId) {
        if (tableId == null) {
            return null;
        }
        RestaurantTable table = tableRepository.findByIdAndTenantId(tableId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.TABLE_NOT_FOUND,
                "Table not found: " + tableId,
                ErrorParams.of("entityType", "RestaurantTable", "entityId", tableId)));
        Long tableBranchId = table.getBranch() == null ? null : table.getBranch().getId();
        if (!branch.getId().equals(tableBranchId)) {
            throw new BusinessException(OrderErrorCode.TABLE_BRANCH_MISMATCH,
                "Table belongs to a different branch",
                ErrorParams.of("tableId", tableId, "tableBranchId", tableBranchId, "orderBranchId", branch.getId()));
        }
        return table;
    }

    private void validateCancellationStage(OrderRequest request) {
        if (request.getStatus() == OrderStatus.CANCELLED
            && (request.getCancellationStage() == null || request.getCancellationReason() == null)) {
            throw new ValidationException(OrderErrorCode.CANCELLATION_DETAILS_REQUIRED,
                "cancellationStage and cancellationReason are required for CANCELLED orders",
                ErrorParams.of("status", request.getStatus().name()));
        }
        if (request.getStatus() == OrderStatus.CANCELLED
            && request.getCancellationReason() == OrderCancellationReason.OTHER
            && (request.getCancellationReasonNote() == null || request.getCancellationReasonNote().isBlank())) {
            throw new ValidationException(OrderErrorCode.CANCELLATION_NOTE_REQUIRED_FOR_OTHER,
                "cancellationReasonNote is required when cancellationReason is OTHER",
                ErrorParams.of("cancellationReason", request.getCancellationReason().name()));
        }
        if (request.getStatus() == OrderStatus.COMPLETE && request.getCancellationStage() != null) {
            throw new ValidationException(OrderErrorCode.CANCELLATION_STAGE_NOT_ALLOWED,
                "cancellationStage is not allowed for COMPLETE orders",
                ErrorParams.of("status", request.getStatus().name(),
                    "cancellationStage", request.getCancellationStage().name()));
        }
    }

    private Long resolveCustomerId(OrderRequest request, Long tenantId) {
        if (request.getCustomerPhone() == null || request.getCustomerPhone().isBlank()) {
            return null;
        }

        try {
            Customer customer = customerService.findOrCreate(
                tenantId,
                request.getCustomerPhone(),
                request.getCustomerName());
            return customer.getId();
        } catch (Exception ex) {
            log.warn("Loyalty customer resolution failed during order creation for tenantId={}", tenantId, ex);
            return null;
        }
    }

    private Branch requireActiveBranch(Branch branch) {
        Long branchId = branch.getId();
        if (!Boolean.TRUE.equals(branch.getActive())) {
            throw new ResourceNotFoundException(OrderErrorCode.BRANCH_NOT_FOUND,
                "Branch is inactive: " + branchId,
                ErrorParams.of("entityType", "Branch", "entityId", branchId));
        }
        return branch;
    }

    private Warehouse resolveWarehouseForBranch(Long branchId, Long tenantId) {
        List<Warehouse> warehouses = warehouseRepository.findByBranchIdAndTenantId(branchId, tenantId);
        if (warehouses.isEmpty()) {
            throw new ResourceNotFoundException(OrderErrorCode.WAREHOUSE_NOT_FOUND,
                "No warehouse found for branch: " + branchId,
                ErrorParams.of("entityType", "Warehouse", "branchId", branchId));
        }
        if (warehouses.size() > 1) {
            throw new BusinessException(OrderErrorCode.AMBIGUOUS_WAREHOUSE_FOR_BRANCH,
                "Multiple warehouses found for branch: " + branchId,
                ErrorParams.of("branchId", branchId, "warehouseCount", warehouses.size()));
        }
        Warehouse warehouse = warehouses.get(0);
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ResourceNotFoundException(OrderErrorCode.WAREHOUSE_NOT_FOUND,
                "Warehouse is inactive for branch: " + branchId,
                ErrorParams.of("entityType", "Warehouse", "branchId", branchId));
        }
        return warehouse;
    }

    private Product loadActiveProduct(Long productId, Long tenantId) {
        Product product = productRepository.findByIdAndTenantId(productId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.PRODUCT_NOT_FOUND,
                "Product not found: " + productId,
                ErrorParams.of("entityType", "Product", "entityId", productId)));
        if (!product.isActive()) {
            throw new ResourceNotFoundException(OrderErrorCode.PRODUCT_NOT_FOUND,
                "Product is inactive: " + productId,
                ErrorParams.of("entityType", "Product", "entityId", productId));
        }
        return product;
    }

    private Recipe resolveActiveRecipe(Long productId, Long tenantId) {
        RecipeResponse activeRecipe;
        try {
            activeRecipe = recipeService.getActiveRecipe(productId, tenantId);
        } catch (ResourceNotFoundException ex) {
            throw new ValidationException(OrderErrorCode.PRODUCT_HAS_NO_ACTIVE_RECIPE,
                "Active recipe not found for product: " + productId,
                ErrorParams.of("productId", productId));
        }

        return recipeRepository.findByIdAndTenantId(activeRecipe.getId(), tenantId)
            .orElseThrow(() -> new ValidationException(OrderErrorCode.PRODUCT_HAS_NO_ACTIVE_RECIPE,
                "Active recipe not found for product: " + productId,
                ErrorParams.of("productId", productId)));
    }

    private Order loadOwned(Long orderId, Long tenantId) {
        return orderRepository.findByIdAndTenantId(orderId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.ORDER_NOT_FOUND,
                "Order not found: " + orderId,
                ErrorParams.of("entityType", "Order", "entityId", orderId)));
    }

    /**
     * Pulls one order back up from its printed receipt.
     *
     * <p>This is the cashier's <b>only</b> route to order history: the POS keeps no completed
     * orders once a shift closes, so a customer returning to query a bill is answered from here or
     * not at all. It returns the whole order — lines, prices, tax, total — because the caller had
     * to quote the printed total to reach it, and redacting a receipt from the person holding it
     * protects nothing. What protects the blind count (D123) is that the total must be known
     * exactly, that a wrong one is indistinguishable from an order that never existed, and that
     * repeated failures are throttled.
     *
     * <p>The branch comes from the caller's signed device, never from the request, so a web
     * session — whose token carries no device — cannot reach this at all, and a cashier cannot
     * widen their own reach to another branch.
     */
    @Transactional(readOnly = true)
    public OrderResponse lookupByReceipt(ReceiptLookupRequest request, Long tenantId) {
        Long deviceId = currentUserService.requireCurrentDeviceId();
        requireCheckAllowance(deviceId);

        Device device = deviceRepository.findByIdAndTenantId(deviceId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.ORDER_NOT_FOUND,
                "Device not found: " + deviceId,
                ErrorParams.of("entityType", "Device", "entityId", deviceId)));

        List<Order> matches = orderRepository.findForReceiptLookup(
            tenantId, request.getOrderNo().trim(), request.getTotalAmount(),
            device.getBranch().getId());

        if (matches.isEmpty()) {
            // A wrong amount and a non-existent order fail identically, so the endpoint cannot be
            // used as an oracle telling a caller which order numbers are real.
            recordFailedCheck(deviceId);
            throw new ResourceNotFoundException(OrderErrorCode.ORDER_NOT_FOUND,
                "No order matches that receipt",
                ErrorParams.of("entityType", "Order", "orderNo", request.getOrderNo()));
        }

        receiptCheckFailures.remove(deviceId);
        // orderNo is unique per device, not per branch, so two tills can both have issued this
        // number. The newest match is the one the customer is standing there holding.
        return mapper.toResponse(matches.get(0));
    }

    /**
     * The cap that keeps the receipt check a confirmation rather than a search. Without it a
     * caller could hold an enumerable {@code orderNo} fixed and sweep amounts until one matched —
     * exactly the reconstruction the check is designed to prevent.
     *
     * <p>Failed checks only, so a cashier working through real receipts is never throttled.
     * In memory and therefore per instance: several nodes multiply the budget by the node count.
     * Acceptable for the threat this guards — a cashier at a till, not a distributed attacker —
     * and written down rather than left to be discovered.
     */
    private void requireCheckAllowance(Long deviceId) {
        FailedChecks state = receiptCheckFailures.get(deviceId);
        if (state == null || state.startedAt().isBefore(Instant.now().minus(RECEIPT_CHECK_WINDOW))) {
            return;
        }
        if (state.count() >= MAX_FAILED_RECEIPT_CHECKS) {
            throw new BusinessException(OrderErrorCode.RECEIPT_VERIFICATION_THROTTLED,
                "Too many failed receipt checks on device " + deviceId,
                ErrorParams.of("deviceId", deviceId,
                    "retryAfterSeconds", RECEIPT_CHECK_WINDOW.getSeconds()));
        }
    }

    private void recordFailedCheck(Long deviceId) {
        Instant cutoff = Instant.now().minus(RECEIPT_CHECK_WINDOW);
        receiptCheckFailures.compute(deviceId, (key, existing) ->
            (existing == null || existing.startedAt().isBefore(cutoff))
                ? new FailedChecks(Instant.now(), 1)
                : new FailedChecks(existing.startedAt(), existing.count() + 1));
    }

    private record FailedChecks(Instant startedAt, int count) {
    }
}
