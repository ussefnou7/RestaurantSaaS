package com.smart.restaurant_saas.order.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.support.TestScopes;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.device.Device;
import com.smart.restaurant_saas.device.repository.DeviceRepository;
import com.smart.restaurant_saas.pos.shift.ShiftErrorCode;
import com.smart.restaurant_saas.auth.service.CurrentUserService;
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
import com.smart.restaurant_saas.order.core.dto.ReceiptLookupRequest;
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
import org.assertj.core.api.ThrowableAssert;
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
    private static final Long DEVICE_ID = 505L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DeviceRepository deviceRepository;
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
    private CurrentUserService currentUserService;
    @Mock
    private OrderMapper mapper;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserService.getCurrentUserId()).thenReturn(USER_ID);
        orderService = new OrderService(
            orderRepository,
            deviceRepository,
            warehouseRepository,
            productRepository,
            recipeRepository,
            recipeService,
            orderConsumptionService,
            customerService,
            shiftRepository,
            tableRepository,
            currentUserService,
            mapper,
            TestScopes.tenantWide()
        );
    }

    @Test
    void createCompletedOrderUsesAuthenticatedActorAndDeviceBranchWithSingleResolvedWarehouse() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
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

        var response = orderService.createCompletedOrder(orderRequest(), TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order savedOrder = captor.getValue();
        assertThat(savedOrder.getBranch().getId()).isEqualTo(BRANCH_ID);
        assertThat(savedOrder.getShift().getDevice().getBranch()).isSameAs(savedOrder.getBranch());
        assertThat(savedOrder.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(savedOrder.getLines()).allSatisfy(line -> assertThat(line.getCreatedBy()).isEqualTo(USER_ID));
        assertThat(savedOrder.getWarehouse().getId()).isEqualTo(WAREHOUSE_ID);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("103.00");
        assertThat(savedOrder.getCashReceived()).isEqualByComparingTo("105.00");
        assertThat(response.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(response.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        verify(orderConsumptionService).recordCompletedOrder(savedOrder, USER_ID);
    }

    @Test
    void createCompletedOrderResolvesDineInTable() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(activeTable(BRANCH_ID)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setTableId(TABLE_ID);
        orderService.createCompletedOrder(request, TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTable().getId()).isEqualTo(TABLE_ID);
    }

    @Test
    void createCompletedOrderRejectsTableFromAnotherBranch() {
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(activeTable(BRANCH_ID + 1)));

        OrderRequest request = orderRequest();
        request.setTableId(TABLE_ID);

        assertThatThrownBy(() -> orderService.createCompletedOrder(request, TENANT_ID))
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

        assertThatThrownBy(() -> orderService.createCompletedOrder(request, TENANT_ID))
            .isInstanceOfSatisfying(ValidationException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_TABLE_FOR_ORDER_TYPE));
    }

    @Test
    void createCompletedOrderRejectsMissingWarehouseForBranch() {
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID))
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
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse(), secondWarehouse));

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID))
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
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(List.of(warehouse));

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID))
            .isInstanceOfSatisfying(ResourceNotFoundException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.WAREHOUSE_NOT_FOUND);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getParams()).containsEntry("branchId", BRANCH_ID);
            });
    }

    @Test
    void createCompletedOrderRejectsMissingShiftForAuthenticatedDevice() {
        when(currentUserService.requireCurrentDeviceId()).thenReturn(DEVICE_ID);
        when(shiftRepository.findByDeviceIdAndTenantIdAndStatus(DEVICE_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(ShiftErrorCode.NO_OPEN_SHIFT_FOR_DEVICE);
                assertThat(ex.getParams()).containsEntry("deviceId", DEVICE_ID);
            });
    }

    @Test
    void createCompletedOrderRejectsInactiveDeviceBranch() {
        Branch branch = activeBranch();
        branch.setActive(false);
        Shift shift = openShift();
        shift.getDevice().setBranch(branch);
        stubDeviceShift(shift);

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID))
            .isInstanceOfSatisfying(ResourceNotFoundException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.BRANCH_NOT_FOUND);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getParams()).containsEntry("entityId", BRANCH_ID);
            });
    }

    @Test
    void createCompletedOrderRejectsProductWithoutActiveRecipeAsValidationFailure() {
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenThrow(new ResourceNotFoundException(MenuErrorCode.RECIPE_NOT_FOUND,
                "Active recipe not found for product: " + PRODUCT_ID,
                ErrorParams.of("productId", PRODUCT_ID)));

        assertThatThrownBy(() -> orderService.createCompletedOrder(orderRequest(), TENANT_ID))
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

        assertThatThrownBy(() -> orderService.createCompletedOrder(request, TENANT_ID))
            .isInstanceOfSatisfying(ValidationException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCELLATION_DETAILS_REQUIRED);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getParams()).containsEntry("status", OrderStatus.CANCELLED.name());
            });
    }

    @Test
    void createCancelledOrderWithOtherReasonButNoNote_rejectsCancellationNoteRequired() {
        OrderRequest request = cancelledOrderRequest(OrderCancellationReason.OTHER);

        assertThatThrownBy(() -> orderService.createCompletedOrder(request, TENANT_ID))
            .isInstanceOfSatisfying(ValidationException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCELLATION_NOTE_REQUIRED_FOR_OTHER);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getParams()).containsEntry("cancellationReason", OrderCancellationReason.OTHER.name());
            });
    }

    @Test
    void createCancelledOrderWithOtherReasonAndNote_succeeds() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = cancelledOrderRequest(OrderCancellationReason.OTHER);
        request.setCancellationReasonNote("Duplicate ticket");

        orderService.createCompletedOrder(request, TENANT_ID);

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
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = cancelledOrderRequest(OrderCancellationReason.ITEM_UNAVAILABLE);

        orderService.createCompletedOrder(request, TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getCancellationReason()).isEqualTo(OrderCancellationReason.ITEM_UNAVAILABLE);
        assertThat(saved.getCancellationReasonNote()).isNull();
    }

    @Test
    void createCompletedOrderWithExistingCustomerPhone_linksResolvedCustomer() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(customerService.findOrCreate(TENANT_ID, "0555000111", null)).thenReturn(customer(555L, "Sara"));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setCustomerPhone("0555000111");

        orderService.createCompletedOrder(request, TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(555L);
    }

    @Test
    void createCompletedOrderWithNewCustomerPhoneAndName_linksCreatedCustomer() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
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

        orderService.createCompletedOrder(request, TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(556L);
    }

    @Test
    void createCompletedOrderWhenCustomerResolutionFails_keepsCustomerIdNull() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
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

        orderService.createCompletedOrder(request, TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getCustomerId()).isNull();
        verify(orderConsumptionService).recordCompletedOrder(saved, USER_ID);
    }

    @Test
    void createCompletedOrderWithNoCustomerPhone_keepsCustomerIdNullAndSkipsLoyalty() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        orderService.createCompletedOrder(orderRequest(), TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isNull();
        verify(customerService, never()).findOrCreate(any(), any(), any());
    }

    @Test
    void createCompletedOrderWithConcurrentSamePhoneDifferentNames_linksWinningCustomer() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
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

        orderService.createCompletedOrder(first, TENANT_ID);
        orderService.createCompletedOrder(second, TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(Order::getCustomerId).containsExactly(777L, 777L);
    }

    @Test
    void createCompletedOrder_storesThePosTotalsVerbatim() {
        // D129: 103.00 is what the POS printed and the customer paid, after rounding the
        // charged total to the whole currency unit. A server that re-derived it would store
        // 102.60 and leave the drawer 0.40 short on this one sale alone.
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        orderService.createCompletedOrder(orderRequest(), TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getSubtotal()).isEqualByComparingTo("90.00");
        assertThat(saved.getTaxAmount()).isEqualByComparingTo("13.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("103.00");
        assertThat(saved.getLines()).hasSize(1);
        assertThat(saved.getLines().get(0).getLineTotal()).isEqualByComparingTo("90.00");
    }

    @Test
    void createCompletedOrderWithTotalsThatDoNotReconcile_stillPersistsThemAsSent() {
        // The sale is over and the cash is in the drawer — quite possibly hours ago on a device
        // that was offline. Refusing it here would strand the order in the POS outbox and leave
        // the drawer short with nothing on record to explain it, so a divergence is logged and
        // the figures are written exactly as they arrived (D129).
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setSubtotal(new BigDecimal("5.00"));   // nothing like the 90.00 of lines
        request.setTaxAmount(new BigDecimal("1.00"));
        request.setTotalAmount(new BigDecimal("9.00")); // nor does 5 + 1 make 9

        orderService.createCompletedOrder(request, TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getSubtotal()).isEqualByComparingTo("5.00");
        assertThat(saved.getTaxAmount()).isEqualByComparingTo("1.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("9.00");
    }

    @Test
    void createCompletedOrder_persistsOrderNo() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(900L).build());

        OrderRequest request = orderRequest();
        request.setOrderNo("POS-1036");

        orderService.createCompletedOrder(request, TENANT_ID);

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
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));
        when(mapper.toResponse(existing)).thenReturn(OrderResponse.builder().id(901L).build());

        OrderRequest request = orderRequest();
        request.setIdempotencyKey("idem-1");

        OrderResponse response = orderService.createCompletedOrder(request, TENANT_ID);

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

    /**
     * qty=2 × 45.00 → lineTotal 90.00 → subtotal 90.00, and a POS that rounds the charged total
     * to the whole currency unit: 90.00 × 1.14 = 102.60 → 103.00 collected, tax 13.00 as the plug.
     * Deliberately <em>not</em> 90 × 0.14 — these are the figures the POS decided on (D129), and a
     * fixture that mirrored a server-side formula would hide the thing under test.
     */
    private OrderRequest orderRequest() {
        OrderLineRequest line = new OrderLineRequest();
        line.setProductId(PRODUCT_ID);
        line.setQuantity(new BigDecimal("2.000000"));
        line.setUnitPrice(new BigDecimal("45.00"));
        line.setLineTotal(new BigDecimal("90.00"));

        OrderRequest request = new OrderRequest();
        request.setOrderType(OrderType.DINE_IN);
        request.setOrderSource(OrderSource.POS);
        request.setStatus(OrderStatus.COMPLETE);
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setOrderDate(LocalDateTime.of(2026, 7, 10, 12, 0));
        request.setLines(List.of(line));
        request.setSubtotal(new BigDecimal("90.00"));
        request.setTaxAmount(new BigDecimal("13.00"));
        request.setTotalAmount(new BigDecimal("103.00"));
        request.setCashReceived(new BigDecimal("105.00"));
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
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setBranch(activeBranch());
        Shift shift = new Shift();
        shift.setDevice(device);
        shift.setId(1L);
        shift.setStatus(ShiftStatus.OPEN);
        return shift;
    }

    private void stubDeviceShift(Shift shift) {
        when(currentUserService.requireCurrentDeviceId()).thenReturn(DEVICE_ID);
        when(shiftRepository.findByDeviceIdAndTenantIdAndStatus(DEVICE_ID, TENANT_ID, ShiftStatus.OPEN))
            .thenReturn(Optional.of(shift));
    }

    // ---- receipt lookup (D123) -------------------------------------------------------------

    private ReceiptLookupRequest receiptRequest(String orderNo, String total) {
        ReceiptLookupRequest request = new ReceiptLookupRequest();
        request.setOrderNo(orderNo);
        request.setTotalAmount(new BigDecimal(total));
        return request;
    }

    private void stubLookupDevice() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setBranch(activeBranch());
        when(currentUserService.requireCurrentDeviceId()).thenReturn(DEVICE_ID);
        when(deviceRepository.findByIdAndTenantId(DEVICE_ID, TENANT_ID))
            .thenReturn(Optional.of(device));
    }

    /**
     * The branch is taken from the caller's signed device, never from the request — a cashier
     * cannot widen their own reach to another branch by asking.
     */
    @Test
    void lookupByReceiptSearchesTheCallersOwnBranchAndReturnsTheWholeOrder() {
        stubLookupDevice();
        Order order = new Order();
        order.setOrderNo("15");
        order.setStatus(OrderStatus.COMPLETE);
        order.setTotalAmount(new BigDecimal("228.00"));
        when(orderRepository.findForReceiptLookup(
            TENANT_ID, "15", new BigDecimal("228.00"), BRANCH_ID)).thenReturn(List.of(order));
        when(mapper.toResponse(order)).thenReturn(OrderResponse.builder()
            .id(900L).orderNo("15").totalAmount(new BigDecimal("228.00")).build());

        OrderResponse response =
            orderService.lookupByReceipt(receiptRequest("15", "228.00"), TENANT_ID);

        // The full receipt comes back: the caller had to quote the printed total to get here, so
        // the contents are something they are already holding.
        assertThat(response.getOrderNo()).isEqualTo("15");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("228.00");
        verify(orderRepository).findForReceiptLookup(
            TENANT_ID, "15", new BigDecimal("228.00"), BRANCH_ID);
    }

    /**
     * {@code orderNo} is an enumerable per-device counter, so the printed amount is the half of
     * the key that proves the receipt is in the caller's hand. A wrong amount therefore has to
     * fail exactly like a number that does not exist, or the endpoint becomes an oracle telling a
     * caller which order numbers are real.
     */
    @Test
    void lookupByReceiptWithTheWrongAmountFailsExactlyLikeAnUnknownOrder() {
        stubLookupDevice();
        when(orderRepository.findForReceiptLookup(any(), any(), any(), any())).thenReturn(List.of());

        ThrowableAssert.ThrowingCallable wrongAmount = () ->
            orderService.lookupByReceipt(receiptRequest("15", "999.99"), TENANT_ID);
        ThrowableAssert.ThrowingCallable unknownOrder = () ->
            orderService.lookupByReceipt(receiptRequest("999999", "12.34"), TENANT_ID);

        for (ThrowableAssert.ThrowingCallable call : List.of(wrongAmount, unknownOrder)) {
            assertThatThrownBy(call)
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode().getCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND.getCode()));
        }
    }

    /**
     * Without a cap a caller could pin the order number and sweep amounts until one matched, which
     * rebuilds the per-order figures the blind count exists to withhold. Failures only, so a
     * cashier working through real receipts never trips it.
     */
    @Test
    void lookupByReceiptThrottlesAfterRepeatedFailuresOnOneDrawer() {
        stubLookupDevice();
        when(orderRepository.findForReceiptLookup(any(), any(), any(), any())).thenReturn(List.of());

        for (int attempt = 1; attempt <= 5; attempt++) {
            String amount = attempt + ".00";
            assertThatThrownBy(() -> orderService.lookupByReceipt(receiptRequest("15", amount), TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        assertThatThrownBy(() -> orderService.lookupByReceipt(receiptRequest("15", "6.00"), TENANT_ID))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode().getCode())
                .isEqualTo(OrderErrorCode.RECEIPT_VERIFICATION_THROTTLED.getCode()));
    }

    // ---- kitchen timings (D132) --------------------------------------------------------------

    /**
     * Stored exactly as measured. The pair's value is the gap between them, and both come off the
     * same device clock — so a skewed clock misreports when a thing happened but never how long
     * it took.
     */
    @Test
    void createCompletedOrderPersistsTheKitchenTimeItWasGiven() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(1L).build());

        OrderRequest request = orderRequest();
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 10, 11, 48);
        // 25 minutes across two tickets on one table — a figure only the device could produce.
        request.setKitchenTimeSeconds(25 * 60);
        request.setOrderStartedAt(startedAt);

        orderService.createCompletedOrder(request, TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getKitchenTimeSeconds()).isEqualTo(1500);
        assertThat(saved.getOrderStartedAt()).isEqualTo(startedAt);
        // Occupancy is the span to payment and is a different figure from the sum above — the
        // table sat for 12 minutes after the first send while the kitchen worked for 25.
        assertThat(java.time.Duration.between(saved.getOrderStartedAt(), saved.getOrderDate()).toMinutes())
            .isEqualTo(12);
    }

    /**
     * A sale is already paid by the time it arrives, so a missing or odd measurement must never
     * cost the order. Absent stays absent — a zero would read as an instant kitchen.
     */
    @Test
    void createCompletedOrderAcceptsAnOrderWithNoKitchenTimingsAtAll() {
        Recipe recipe = activeRecipe();
        stubDeviceShift(openShift());
        when(warehouseRepository.findByBranchIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(List.of(activeWarehouse()));
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(activeProduct()));
        when(recipeService.getActiveRecipe(PRODUCT_ID, TENANT_ID))
            .thenReturn(RecipeResponse.builder().id(recipe.getId()).isActive(true).build());
        when(recipeRepository.findByIdAndTenantId(recipe.getId(), TENANT_ID)).thenReturn(Optional.of(recipe));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Order.class))).thenReturn(OrderResponse.builder().id(1L).build());

        orderService.createCompletedOrder(orderRequest(), TENANT_ID);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getKitchenTimeSeconds()).isNull();
        assertThat(captor.getValue().getOrderStartedAt()).isNull();
    }
}
