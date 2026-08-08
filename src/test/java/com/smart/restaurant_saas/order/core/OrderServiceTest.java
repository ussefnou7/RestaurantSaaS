package com.smart.restaurant_saas.order.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.smart.restaurant_saas.order.core.dto.OrderFilters;
import com.smart.restaurant_saas.order.core.dto.OrderLineRequest;
import com.smart.restaurant_saas.pos.shift.Shift;
import com.smart.restaurant_saas.pos.shift.ShiftRepository;
import com.smart.restaurant_saas.pos.shift.ShiftStatus;
import com.smart.restaurant_saas.table.RestaurantTable;
import com.smart.restaurant_saas.table.TableRepository;
import com.smart.restaurant_saas.order.core.dto.OrderRequest;
import com.smart.restaurant_saas.order.core.dto.OrderResponse;
import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderCancellationReason;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 11L;
    private static final Long BRANCH_ID = 101L;
    private static final Long WAREHOUSE_ID = 202L;
    private static final Long PRODUCT_ID = 303L;
    private static final Long TABLE_ID = 404L;

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
    private ShiftRepository shiftRepository;
    @Mock
    private TableRepository tableRepository;
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
            shiftRepository,
            tableRepository,
            mapper
        );
    }

    @Test
    void createCompletedOrderUsesBranchHeaderAndSingleResolvedWarehouse() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder()
            .id(900L)
            .branchId(BRANCH_ID)
            .warehouseId(WAREHOUSE_ID)
            .build());

        var response = orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order savedOrder = captor.getValue();
        assertThat(savedOrder.getBranch().getId()).isEqualTo(BRANCH_ID);
        assertThat(savedOrder.getWarehouse().getId()).isEqualTo(WAREHOUSE_ID);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("102.60");
        assertThat(response.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(response.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        verify(orderConsumptionService).recordCompletedOrder(savedOrder, USER_ID);
    }

    @Test
    void createCompletedOrderResolvesDineInTable() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(activeTable(BRANCH_ID)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setTableId(TABLE_ID);
        orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTable().getId()).isEqualTo(TABLE_ID);
    }

    @Test
    void createCompletedOrderRejectsTableFromAnotherBranch() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(activeTable(BRANCH_ID + 1)));

        OrderRequest request = orderRequest();
        request.setTableId(TABLE_ID);

        assertThatThrownBy(() -> orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.TABLE_BRANCH_MISMATCH);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    void createCompletedOrderRejectsTableIdForNonDineIn() {
        OrderRequest request = orderRequest();
        request.setOrderType(OrderType.TAKEAWAY);
        request.setTableId(TABLE_ID);

        assertThatThrownBy(() -> orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(ValidationException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_TABLE_FOR_ORDER_TYPE));
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
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
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
    void createCancelledOrderWithStageButNoReason_rejectsCancellationDetailsRequired() {
        OrderRequest request = orderRequest();
        request.setStatus(OrderStatus.CANCELLED);
        request.setCancellationStage(CancellationStage.BEFORE_KITCHEN);

        assertThatThrownBy(() -> orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(ValidationException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCELLATION_DETAILS_REQUIRED);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getParams()).containsEntry("status", OrderStatus.CANCELLED.name());
            });
    }

    @Test
    void createCancelledOrderWithOtherReasonButNoNote_rejectsCancellationNoteRequired() {
        OrderRequest request = cancelledOrderRequest(OrderCancellationReason.OTHER);

        assertThatThrownBy(() -> orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID))
            .isInstanceOfSatisfying(ValidationException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCELLATION_NOTE_REQUIRED_FOR_OTHER);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getParams()).containsEntry("cancellationReason", OrderCancellationReason.OTHER.name());
            });
    }

    @Test
    void createCancelledOrderWithOtherReasonAndNote_succeeds() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = cancelledOrderRequest(OrderCancellationReason.OTHER);
        request.setCancellationReasonNote("Duplicate ticket");

        orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(saved.getCancellationReason()).isEqualTo(OrderCancellationReason.OTHER);
        assertThat(saved.getCancellationReasonNote()).isEqualTo("Duplicate ticket");
    }

    @Test
    void createCancelledOrderWithNonOtherReasonAndNoNote_succeeds() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = cancelledOrderRequest(OrderCancellationReason.ITEM_UNAVAILABLE);

        orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getCancellationReason()).isEqualTo(OrderCancellationReason.ITEM_UNAVAILABLE);
        assertThat(saved.getCancellationReasonNote()).isNull();
    }

    @Test
    void createCompletedOrderWithExistingCustomerPhone_linksResolvedCustomer() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(customerService.findOrCreate(TENANT_ID, "0555000111", null)).thenReturn(customer(555L, "Sara"));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setCustomerPhone("0555000111");

        orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(555L);
    }

    @Test
    void createCompletedOrderWithNewCustomerPhoneAndName_linksCreatedCustomer() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(customerService.findOrCreate(TENANT_ID, "0555000222", "Mona")).thenReturn(customer(556L, "Mona"));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setCustomerPhone("0555000222");
        request.setCustomerName("Mona");

        orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(556L);
    }

    @Test
    void createCompletedOrderWhenCustomerResolutionFails_keepsCustomerIdNull() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(customerService.findOrCreate(TENANT_ID, "0555000111", "Sara"))
            .thenThrow(new RuntimeException("loyalty unavailable"));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setCustomerPhone("0555000111");
        request.setCustomerName("Sara");

        orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getCustomerId()).isNull();
        verify(orderConsumptionService).recordCompletedOrder(saved, USER_ID);
    }

    @Test
    void createCompletedOrderWithNoCustomerPhone_keepsCustomerIdNullAndSkipsLoyalty() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isNull();
        verify(customerService, never()).findOrCreate(any(), any(), any());
    }

    @Test
    void createCompletedOrderWithConcurrentSamePhoneDifferentNames_linksWinningCustomer() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        Customer winner = customer(777L, "First Name");
        when(customerService.findOrCreate(TENANT_ID, "0555000333", "First Name")).thenReturn(winner);
        when(customerService.findOrCreate(TENANT_ID, "0555000333", "Second Name")).thenReturn(winner);
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest first = orderRequest();
        first.setCustomerPhone("0555000333");
        first.setCustomerName("First Name");
        OrderRequest second = orderRequest();
        second.setCustomerPhone("0555000333");
        second.setCustomerName("Second Name");

        orderService.createCompletedOrder(first, TENANT_ID, USER_ID, BRANCH_ID);
        orderService.createCompletedOrder(second, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(Order::getCustomerId).containsExactly(777L, 777L);
    }

    @Test
    void createCompletedOrder_computesTaxServerSide() {
        // qty=2, unitPrice=45.00 → lineTotal=90.00 → subtotal=90.000000
        // taxAmount = 90.000000 × 0.14 = 12.600000, totalAmount = 102.60
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        orderService.createCompletedOrder(orderRequest(), TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getSubtotal()).isEqualByComparingTo("90.000000");
        assertThat(saved.getTaxAmount()).isEqualByComparingTo("12.600000");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("102.60");
    }

    @Test
    void createCompletedOrder_persistsOrderNo() {
        Recipe recipe = activeRecipe();
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setOrderNo("POS-1036");

        orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOrderNo()).isEqualTo("POS-1036");
    }

    @Test
    void createCompletedOrder_replaysExistingOrderWhenIdempotencyConstraintWinsRace() {
        Recipe recipe = activeRecipe();
        Order existing = existingOrder();
        when(orderRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, "idem-1"))
            .thenReturn(Optional.empty(), Optional.of(existing));
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(activeBranch()));
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(openShift()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));
        when(mapper.toResponse(existing)).thenReturn(OrderResponse.builder().id(901L).build());

        OrderRequest request = orderRequest();
        request.setIdempotencyKey("idem-1");

        OrderResponse response = orderService.createCompletedOrder(request, TENANT_ID, USER_ID, BRANCH_ID);

        assertThat(response.getId()).isEqualTo(901L);
        verify(orderRepository).saveAndFlush(any(Order.class));
    }

    @Test
    void listOrders_filtersByOrderNo_passesExactMatchToRepository() {
        when(orderRepository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        OrderFilters filters = new OrderFilters(null, null, null, null, null, null, "POS-1036", null, null);
        orderService.listOrders(TENANT_ID, filters, Pageable.unpaged());

        ArgumentCaptor<String> orderNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderRepository).findByFilters(any(), any(), any(), any(), any(), any(), any(),
            orderNoCaptor.capture(), any(), any(), any());
        assertThat(orderNoCaptor.getValue()).isEqualTo("POS-1036");
    }

    @Test
    void listOrders_filtersByCreatedBy_passesUserIdToRepository() {
        when(orderRepository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        OrderFilters filters = new OrderFilters(null, null, null, null, null, null, null, USER_ID, null);
        orderService.listOrders(TENANT_ID, filters, Pageable.unpaged());

        ArgumentCaptor<Long> createdByCaptor = ArgumentCaptor.forClass(Long.class);
        verify(orderRepository).findByFilters(any(), any(), any(), any(), any(), any(), any(),
            any(), createdByCaptor.capture(), any(), any());
        assertThat(createdByCaptor.getValue()).isEqualTo(USER_ID);
    }

    @Test
    void listOrders_filtersByCustomerId_passesCustomerIdToRepository() {
        when(orderRepository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        OrderFilters filters = new OrderFilters(null, null, null, null, null, null, null, null, 123L);
        orderService.listOrders(TENANT_ID, filters, Pageable.unpaged());

        ArgumentCaptor<Long> customerIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(orderRepository).findByFilters(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), customerIdCaptor.capture(), any());
        assertThat(customerIdCaptor.getValue()).isEqualTo(123L);
    }

    @Test
    void listOrders_customerIdWithNoMatches_returnsEmptyPage() {
        when(orderRepository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        OrderFilters filters = new OrderFilters(null, null, null, null, null, null, null, null, 999L);

        Page<OrderSummaryResponse> result = orderService.listOrders(TENANT_ID, filters, Pageable.unpaged());

        assertThat(result.getContent()).isEmpty();
    }

    private Customer customer(Long id, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setTenantId(TENANT_ID);
        customer.setPhone("0555000111");
        customer.setName(name);
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
        request.setOrderDate(LocalDateTime.of(2026, 7, 10, 12, 0));
        request.setLines(List.of(line));
        return request;
    }

    private OrderRequest cancelledOrderRequest(OrderCancellationReason reason) {
        OrderRequest request = orderRequest();
        request.setStatus(OrderStatus.CANCELLED);
        request.setCancellationStage(CancellationStage.BEFORE_KITCHEN);
        request.setCancellationReason(reason);
        return request;
    }

    private RestaurantTable activeTable(Long branchId) {
        Branch branch = new Branch();
        branch.setId(branchId);
        branch.setTenantId(TENANT_ID);
        RestaurantTable table = new RestaurantTable();
        table.setId(TABLE_ID);
        table.setTenantId(TENANT_ID);
        table.setBranch(branch);
        table.setName("T1");
        return table;
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

    private Order existingOrder() {
        Order order = new Order();
        order.setId(901L);
        order.setTenantId(TENANT_ID);
        order.setStatus(OrderStatus.COMPLETE);
        order.setBranch(activeBranch());
        order.setWarehouse(activeWarehouse());
        order.setLines(List.of());
        return order;
    }

    private Shift openShift() {
        Shift shift = new Shift();
        shift.setId(1L);
        shift.setStatus(ShiftStatus.OPEN);
        return shift;
    }
}
