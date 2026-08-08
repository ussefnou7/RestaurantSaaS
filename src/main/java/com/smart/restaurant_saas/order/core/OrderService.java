package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
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
import com.smart.restaurant_saas.order.core.enums.OrderCancellationReason;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
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
    private static final int TAX_SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal VAT_RATE = new BigDecimal("0.14");

    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final OrderConsumptionService orderConsumptionService;
    private final CustomerService customerService;
    private final ShiftRepository shiftRepository;
    private final TableRepository tableRepository;
    private final OrderMapper mapper;

    @Transactional
    public OrderResponse createCompletedOrder(OrderRequest request, Long tenantId, Long userId, Long branchId) {
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

        Branch branch = loadActiveBranch(branchId, tenantId);
        Warehouse warehouse = resolveWarehouseForBranch(branchId, tenantId);

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
        order.setExternalOrderReference(request.getExternalOrderReference());
        order.setIdempotencyKey(idempotencyKey);
        order.setOrderNo(request.getOrderNo());
        order.setCustomerId(resolveCustomerId(request, tenantId));
        order.setShift(resolveOpenShift(userId, tenantId));

        BigDecimal subtotal = BigDecimal.ZERO.setScale(TAX_SCALE, ROUNDING);
        for (OrderLineRequest lineRequest : request.getLines()) {
            OrderLine line = buildLine(order, lineRequest, tenantId, userId);
            subtotal = subtotal.add(line.getLineTotal()).setScale(TAX_SCALE, ROUNDING);
            order.getLines().add(line);
        }
        BigDecimal taxAmount = subtotal.multiply(VAT_RATE).setScale(TAX_SCALE, ROUNDING);
        order.setSubtotal(subtotal);
        order.setTaxAmount(taxAmount);
        order.setTotalAmount(subtotal.add(taxAmount).setScale(MONEY_SCALE, ROUNDING));

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
                filters.branchId(),
                filters.fromDate(),
                filters.toDate(),
                filters.orderNo(),
                filters.createdBy(),
                filters.customerId(),
                pageable)
            .map(mapper::toSummary);
    }

    private Shift resolveOpenShift(Long userId, Long tenantId) {
        return shiftRepository.findByCashierUserIdAndTenantIdAndStatus(userId, tenantId, ShiftStatus.OPEN)
                .orElseThrow(() -> new BusinessException(ShiftErrorCode.NO_OPEN_SHIFT_FOR_CASHIER,
                        "No open shift for cashier: " + userId,
                        ErrorParams.of("userId", userId)));
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
        line.setLineTotal(calculateLineTotal(request.getQuantity(), request.getUnitPrice()));
        return line;
    }

    private BigDecimal calculateLineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(MONEY_SCALE, ROUNDING);
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

    private Branch loadActiveBranch(Long branchId, Long tenantId) {
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.BRANCH_NOT_FOUND,
                "Branch not found: " + branchId,
                ErrorParams.of("entityType", "Branch", "entityId", branchId)));
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
}
