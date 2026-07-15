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
import com.smart.restaurant_saas.order.core.dto.OrderFilters;
import com.smart.restaurant_saas.order.core.dto.OrderLineRequest;
import com.smart.restaurant_saas.order.core.dto.OrderRequest;
import com.smart.restaurant_saas.order.core.dto.OrderResponse;
import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final OrderConsumptionService orderConsumptionService;
    private final CustomerService customerService;
    private final OrderMapper mapper;

    @Transactional
    public OrderResponse createCompletedOrder(OrderRequest request, Long tenantId, Long userId, Long branchId) {
        validateOrderType(request);
        validateCancellationStage(request);

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
        order.setPaymentMethod(request.getPaymentMethod());
        order.setTableNo(request.getTableNo());
        order.setBranch(branch);
        order.setWarehouse(warehouse);
        order.setOrderDate(request.getOrderDate());
        order.setExternalOrderReference(request.getExternalOrderReference());
        order.setCustomerId(resolveCustomerId(request, tenantId));

        BigDecimal totalAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        for (OrderLineRequest lineRequest : request.getLines()) {
            OrderLine line = buildLine(order, lineRequest, tenantId, userId);
            totalAmount = totalAmount.add(line.getLineTotal()).setScale(MONEY_SCALE, ROUNDING);
            order.getLines().add(line);
        }
        order.setTotalAmount(totalAmount);

        Order saved = orderRepository.save(order);
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
                pageable)
            .map(mapper::toSummary);
    }

    /**
     * Resolves the optional loyalty customer for an order. Absent phone → null (walk-in, a normal
     * case). Any failure resolving/creating the customer is swallowed and logged so a loyalty-side
     * problem can never block or fail order creation — the order simply proceeds with a null link.
     */
    private Long resolveCustomerId(OrderRequest request, Long tenantId) {
        if (request.getCustomerPhone() == null || request.getCustomerPhone().isBlank()) {
            return null;
        }
        try {
            Customer customer = customerService.findOrCreate(
                tenantId, request.getCustomerPhone(), request.getCustomerName());
            return customer.getId();
        } catch (RuntimeException ex) {
            log.warn("Loyalty customer resolution failed for tenant={} phone(masked); order proceeds "
                    + "without a customer link", tenantId, ex);
            return null;
        }
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
        if (request.getOrderType() != OrderType.DINE_IN && request.getTableNo() != null) {
            throw new ValidationException(OrderErrorCode.INVALID_TABLE_NO_FOR_ORDER_TYPE,
                "tableNo is only allowed for DINE_IN orders",
                ErrorParams.of("orderType", request.getOrderType().name(), "tableNo", request.getTableNo()));
        }
    }

    private void validateCancellationStage(OrderRequest request) {
        if (request.getStatus() == OrderStatus.CANCELLED && request.getCancellationStage() == null) {
            throw new ValidationException(OrderErrorCode.CANCELLATION_STAGE_REQUIRED,
                "cancellationStage is required for CANCELLED orders",
                ErrorParams.of("status", request.getStatus().name()));
        }
        if (request.getStatus() == OrderStatus.COMPLETE && request.getCancellationStage() != null) {
            throw new ValidationException(OrderErrorCode.CANCELLATION_STAGE_NOT_ALLOWED,
                "cancellationStage is not allowed for COMPLETE orders",
                ErrorParams.of("status", request.getStatus().name(),
                    "cancellationStage", request.getCancellationStage().name()));
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
