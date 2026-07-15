package com.smart.restaurant_saas.order.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionService;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.loyalty.customer.Customer;
import com.smart.restaurant_saas.loyalty.customer.CustomerService;
import com.smart.restaurant_saas.menu.MenuErrorCode;
import com.smart.restaurant_saas.menu.product.Product;
import com.smart.restaurant_saas.menu.product.ProductRepository;
import com.smart.restaurant_saas.menu.recipe.Recipe;
import com.smart.restaurant_saas.menu.recipe.RecipeRepository;
import com.smart.restaurant_saas.menu.recipe.RecipeService;
import com.smart.restaurant_saas.menu.recipe.dto.RecipeResponse;
import com.smart.restaurant_saas.order.OrderErrorCode;
import com.smart.restaurant_saas.order.core.dto.OrderLineRequest;
import com.smart.restaurant_saas.order.core.dto.OrderRequest;
import com.smart.restaurant_saas.order.core.dto.OrderResponse;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 11L;
    private static final Long BRANCH_ID = 101L;
    private static final Long WAREHOUSE_ID = 202L;
    private static final Long PRODUCT_ID = 303L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeService recipeService;
    @Mock
    private OrderConsumptionService orderConsumptionService;
    @Mock
    private CustomerService customerService;
    @Mock
    private OrderMapper mapper;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderRepository,
            branchRepository,
            warehouseRepository,
            productRepository,
            recipeRepository,
            recipeService,
            orderConsumptionService,
            customerService,
            mapper
        );
    }

    @Test
    void createCompletedOrderUsesBranchHeaderAndSingleResolvedWarehouse() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder()
            .id(900L)
            .branchId(BRANCH_ID)
            .warehouseId(WAREHOUSE_ID)
            .build());

        var response = orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order savedOrder = captor.getValue();
        assertThat(savedOrder.getBranch().getId()).isEqualTo(BRANCH_ID);
        assertThat(savedOrder.getWarehouse().getId()).isEqualTo(WAREHOUSE_ID);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("90.00");
        assertThat(response.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(response.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        verify(orderConsumptionService).recordCompletedOrder(savedOrder, USER_ID);
    }

    @Test
    void createCompletedOrderRejectsMissingWarehouseForBranch() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(ResourceNotFoundException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.WAREHOUSE_NOT_FOUND);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getParams()).containsEntry("branchId", BRANCH_ID);
            });
    }

    @Test
    void createCompletedOrderRejectsMultipleWarehousesForBranch() {
        Warehouse secondWarehouse = activeWarehouse();
        secondWarehouse.setId(WAREHOUSE_ID + 1);
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse(), secondWarehouse));

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.AMBIGUOUS_WAREHOUSE_FOR_BRANCH);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getParams()).containsEntry("warehouseCount", 2);
            });
    }

    @Test
    void createCompletedOrderRejectsInactiveResolvedWarehouse() {
        Warehouse warehouse = activeWarehouse();
        warehouse.setActive(false);
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(List.of(warehouse));

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(ResourceNotFoundException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.WAREHOUSE_NOT_FOUND);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getParams()).containsEntry("branchId", BRANCH_ID);
            });
    }

    @Test
    void createCompletedOrderRejectsMissingBranchFromHeader() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(ResourceNotFoundException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.BRANCH_NOT_FOUND);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getParams()).containsEntry("entityId", BRANCH_ID);
            });
    }

    @Test
    void createCompletedOrderRejectsInactiveBranchFromHeader() {
        Branch branch = activeBranch();
        branch.setActive(false);
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(branch));

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(ResourceNotFoundException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.BRANCH_NOT_FOUND);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getParams()).containsEntry("entityId", BRANCH_ID);
            });
    }

    @Test
    void createCompletedOrderRejectsProductWithoutActiveRecipeAsValidationFailure() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenThrow(new ResourceNotFoundException(MenuErrorCode.RECIPE_NOT_FOUND,
                "Active recipe not found for product: " + PRODUCT_ID,
                ErrorParams.of("productId", PRODUCT_ID)));

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(ValidationException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.PRODUCT_HAS_NO_ACTIVE_RECIPE);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getParams()).containsEntry("productId", PRODUCT_ID);
            });
    }

    @Test
    void createCompletedOrderLinksResolvedCustomer() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(customerService.findOrCreate(TENANT_ID, "0555000111", "Sara")).thenReturn(customer(555L));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setCustomerPhone("0555000111");
        request.setCustomerName("Sara");

        orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(555L);
    }

    @Test
    void createCompletedOrderSucceedsWithNullCustomerWhenResolutionThrows() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(customerService.findOrCreate(TENANT_ID, "0555000111", "Sara"))
            .thenThrow(new RuntimeException("loyalty is down"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setCustomerPhone("0555000111");
        request.setCustomerName("Sara");

        orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isNull();
        verify(orderConsumptionService).recordCompletedOrder(captor.getValue(), USER_ID);
    }

    private Customer customer(Long id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setTenantId(TENANT_ID);
        customer.setPhone("0555000111");
        customer.setName("Sara");
        return customer;
    }

    private OrderRequest orderRequest() {
        OrderLineRequest line = new OrderLineRequest();
        line.setProductId(PRODUCT_ID);
        line.setQuantity(new BigDecimal("2.000000"));
        line.setUnitPrice(new BigDecimal("45.00"));

        OrderRequest request = new OrderRequest();
        request.setOrderType(OrderType.DINE_IN);
        request.setOrderSource(OrderSource.POS);
        request.setStatus(OrderStatus.COMPLETE);
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setTableNo("T1");
        request.setOrderDate(LocalDateTime.of(2026, 7, 10, 12, 0));
        request.setLines(List.of(line));
        return request;
    }

    private Branch activeBranch() {
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setTenantId(TENANT_ID);
        branch.setName("Main Branch");
        branch.setActive(true);
        return branch;
    }

    private Warehouse activeWarehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setTenantId(TENANT_ID);
        warehouse.setName("Main Warehouse");
        warehouse.setActive(true);
        warehouse.setBranch(activeBranch());
        return warehouse;
    }

    private Product activeProduct() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setTenantId(TENANT_ID);
        product.setName("Burger");
        product.setActive(true);
        return product;
    }

    private Recipe activeRecipe() {
        Recipe recipe = new Recipe();
        recipe.setId(404L);
        recipe.setTenantId(TENANT_ID);
        recipe.setProduct(activeProduct());
        recipe.setActive(true);
        return recipe;
    }
}
