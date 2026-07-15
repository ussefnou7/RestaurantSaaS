package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.mapper.OrderConsumptionDocMapper;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocDetailResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocListResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionMaterialsSummaryResponse;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.menu.recipe.Recipe;
import com.smart.restaurant_saas.menu.recipe.RecipeItem;
import com.smart.restaurant_saas.menu.recipe.RecipeItemRepository;
import com.smart.restaurant_saas.order.core.Order;
import com.smart.restaurant_saas.order.core.OrderLine;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class OrderConsumptionServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;

    private final OrderConsumptionRepository docRepository = mock(OrderConsumptionRepository.class);
    private final OrderConsumptionLineRepository lineRepository = mock(OrderConsumptionLineRepository.class);
    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);
    private final RecipeItemRepository recipeItemRepository = mock(RecipeItemRepository.class);
    private final InventoryLedgerService ledgerService = mock(InventoryLedgerService.class);
    private final PlatformTransactionManager transactionManager = mockTransactionManager();
    private final OrderConsumptionService service = new OrderConsumptionService(
        docRepository,
        lineRepository,
        warehouseRepository,
        recipeItemRepository,
        ledgerService,
        new OrderConsumptionDocMapper(),
        transactionManager);

    @Test
    void recordCompletedOrderLocksWarehouseAndReusesPendingDoc() {
        Warehouse warehouse = warehouse(10L);
        OrderConsumption existingDoc = doc(50L, warehouse, OrderConsumptionStatus.PENDING);
        Order order = order(100L, warehouse, OrderStatus.COMPLETE, orderLine(501L, 20L, "2.000000"));

        when(warehouseRepository.findByIdAndTenantIdForUpdate(10L, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
        when(docRepository.findByTenantIdAndWarehouseIdAndStatus(
            TENANT_ID, 10L, OrderConsumptionStatus.PENDING))
            .thenReturn(Optional.of(existingDoc));
        when(recipeItemRepository.findByRecipeIds(List.of(20L), TENANT_ID))
            .thenReturn(List.of(recipeItem(20L, 30L, "Flour", 40L, "1.000000")));
        when(lineRepository.findExistingOrderLineIds(List.of(501L))).thenReturn(List.of());

        service.recordCompletedOrder(order, USER_ID);

        verify(warehouseRepository).findByIdAndTenantIdForUpdate(10L, TENANT_ID);
        verify(docRepository, never()).saveAndFlush(any());
        ArgumentCaptor<List<OrderConsumptionLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(lineRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getDoc()).isSameAs(existingDoc);
        assertThat(captor.getValue().getFirst().isConsumed()).isFalse();
    }

    @Test
    void recalculatePostsDocAndMarksAllLinesConsumedOnFullSuccess() {
        Warehouse warehouse = warehouse(10L);
        // D45: recalculate is the manual retry from CONFLICT.
        OrderConsumption doc = doc(50L, warehouse, OrderConsumptionStatus.CONFLICT);

        when(docRepository.findByIdAndTenantIdForUpdate(50L, TENANT_ID)).thenReturn(Optional.of(doc));
        when(lineRepository.sumRecipeQuantitiesByDocId(50L)).thenReturn(List.of(recipeQuantity(20L, "2.000000")));
        when(recipeItemRepository.findByRecipeIds(List.of(20L), TENANT_ID))
            .thenReturn(List.of(recipeItem(20L, 30L, "Flour", 40L, "3.000000")));
        when(docRepository.save(any(OrderConsumption.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderConsumptionDocResponse response = service.recalculate(50L, TENANT_ID, USER_ID);

        verify(ledgerService).record(any(LedgerCommand.class));
        verify(lineRepository).updateConsumedByDocId(50L, true);
        assertThat(response.getStatus()).isEqualTo(OrderConsumptionStatus.POSTED);
        assertThat(doc.getErrorDetails()).isNull();
        assertThat(doc.getProcessedAt()).isNotNull();
    }

    @Test
    void recalculateConflictsAndLeavesAllLinesUnconsumedWhenAnyMaterialFails() {
        Warehouse warehouse = warehouse(10L);
        // D45: recalculate is the manual retry from CONFLICT.
        OrderConsumption doc = doc(50L, warehouse, OrderConsumptionStatus.CONFLICT);

        when(docRepository.findByIdAndTenantIdForUpdate(50L, TENANT_ID)).thenReturn(Optional.of(doc));
        when(lineRepository.sumRecipeQuantitiesByDocId(50L)).thenReturn(List.of(recipeQuantity(20L, "2.000000")));
        when(recipeItemRepository.findByRecipeIds(List.of(20L), TENANT_ID))
            .thenReturn(List.of(recipeItem(20L, 30L, "Flour", 40L, "3.000000")));
        when(docRepository.save(any(OrderConsumption.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerService.record(any(LedgerCommand.class))).thenThrow(new IllegalStateException("ledger failed"));

        OrderConsumptionDocResponse response = service.recalculate(50L, TENANT_ID, USER_ID);

        verify(lineRepository).updateConsumedByDocId(50L, false);
        assertThat(response.getStatus()).isEqualTo(OrderConsumptionStatus.CONFLICT);
        assertThat(doc.getErrorDetails()).contains("Flour", "IllegalStateException", "ledger failed");
        assertThat(doc.getProcessedAt()).isNotNull();
    }

    @Test
    void listMapsFiltersAndLineCountsWithoutFetchingLines() {
        Warehouse warehouse = warehouse(10L);
        warehouse.setName("Main Warehouse");
        OrderConsumption doc = doc(50L, warehouse, OrderConsumptionStatus.PENDING);
        doc.setCreatedAt(LocalDateTime.of(2026, 7, 10, 12, 0));
        PageRequest pageable = PageRequest.of(0, 20);
        when(docRepository.findByFilters(
            TENANT_ID,
            10L,
            OrderConsumptionStatus.PENDING,
            LocalDateTime.of(2026, 7, 1, 0, 0),
            LocalDateTime.of(2026, 8, 1, 0, 0),
            pageable
        )).thenReturn(new PageImpl<>(List.of(doc), pageable, 1));
        when(lineRepository.countLinesByDocIds(List.of(50L))).thenReturn(List.of(docLineCount(50L, 4L)));

        Page<OrderConsumptionDocListResponse> result = service.list(
            TENANT_ID,
            10L,
            OrderConsumptionStatus.PENDING,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getWarehouseName()).isEqualTo("Main Warehouse");
        assertThat(result.getContent().getFirst().getLineCount()).isEqualTo(4);
        verify(lineRepository, never()).findLinesByDocId(any());
    }

    @Test
    void getByIdReturnsLinesAndConflictErrors() {
        Warehouse warehouse = warehouse(10L);
        warehouse.setName("Main Warehouse");
        OrderConsumption doc = doc(50L, warehouse, OrderConsumptionStatus.CONFLICT);
        doc.setErrorDetails("""
            [{"materialId":30,"materialName":"Flour","exceptionClass":"TestException","message":"Missing stock"}]
            """);
        when(docRepository.findByIdAndTenantId(50L, TENANT_ID)).thenReturn(Optional.of(doc));
        when(lineRepository.findLinesByDocId(50L)).thenReturn(List.of(
            lineView(10L, 88L, 42L, true),
            lineView(11L, 89L, 43L, false)));

        OrderConsumptionDocDetailResponse result = service.getById(50L, TENANT_ID);

        assertThat(result.getErrorDetails()).hasSize(1);
        assertThat(result.getErrorDetails().getFirst().message()).isEqualTo("Missing stock");
        assertThat(result.getLines()).hasSize(2);
        assertThat(result.getLines().getFirst().getOrderId()).isEqualTo(88L);
        assertThat(result.getLines().getFirst().getCreatedBy()).isEqualTo(42L);
        assertThat(result.getLines().getFirst().isConsumed()).isTrue();
        assertThat(result.getLines().get(1).isConsumed()).isFalse();
    }

    @Test
    void getMaterialsSummaryAggregatesByMaterialWithDistinctOrderCount() {
        when(docRepository.findByIdAndTenantId(50L, TENANT_ID))
            .thenReturn(Optional.of(doc(50L, warehouse(10L), OrderConsumptionStatus.POSTED)));
        when(lineRepository.summarizeMaterialsByDocId(50L, TENANT_ID))
            .thenReturn(List.of(
                materialSummary(30L, "Flour", "kg", "4.500000", 7L),
                materialSummary(31L, "Sugar", "g", "200.000000", 3L)));

        OrderConsumptionMaterialsSummaryResponse result = service.getMaterialsSummary(50L, TENANT_ID);

        assertThat(result.getDocId()).isEqualTo(50L);
        assertThat(result.getMaterials()).hasSize(2);
        assertThat(result.getMaterials().getFirst().getTotalQtyConsumed())
            .isEqualByComparingTo("4.500000");
        assertThat(result.getMaterials().getFirst().getOrderCount()).isEqualTo(7);
        assertThat(result.getMaterials().get(1).getMaterialName()).isEqualTo("Sugar");
    }

    @Test
    void claimDocTransitionsPendingToInProgressWithoutConsuming() {
        OrderConsumption doc = doc(50L, warehouse(10L), OrderConsumptionStatus.PENDING);
        when(docRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(doc));
        when(docRepository.save(any(OrderConsumption.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean claimed = service.claimDoc(50L, USER_ID);

        assertThat(claimed).isTrue();
        assertThat(doc.getStatus()).isEqualTo(OrderConsumptionStatus.IN_PROGRESS);
        verify(docRepository).save(doc);
        // Claiming must not run any consumption — that is the separate processClaimedDoc transaction.
        verify(ledgerService, never()).record(any());
        verify(lineRepository, never()).updateConsumedByDocId(anyLong(), anyBoolean());
    }

    @Test
    void claimDocReturnsFalseAndDoesNothingWhenDocNotPending() {
        OrderConsumption doc = doc(50L, warehouse(10L), OrderConsumptionStatus.IN_PROGRESS);
        when(docRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(doc));

        boolean claimed = service.claimDoc(50L, USER_ID);

        assertThat(claimed).isFalse();
        verify(docRepository, never()).save(any());
        assertThat(doc.getStatus()).isEqualTo(OrderConsumptionStatus.IN_PROGRESS);
    }

    @Test
    void processClaimedDocRunsD29AndPostsOnFullSuccess() {
        OrderConsumption doc = doc(50L, warehouse(10L), OrderConsumptionStatus.IN_PROGRESS);
        when(docRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(doc));
        when(lineRepository.sumRecipeQuantitiesByDocId(50L)).thenReturn(List.of(recipeQuantity(20L, "2.000000")));
        when(recipeItemRepository.findByRecipeIds(List.of(20L), TENANT_ID))
            .thenReturn(List.of(recipeItem(20L, 30L, "Flour", 40L, "3.000000")));

        service.processClaimedDoc(50L, USER_ID);

        verify(ledgerService).record(any(LedgerCommand.class));
        verify(lineRepository).updateConsumedByDocId(50L, true);
        assertThat(doc.getStatus()).isEqualTo(OrderConsumptionStatus.POSTED);
    }

    @Test
    void processClaimedDocIsNoOpWhenDocNotInProgress() {
        OrderConsumption doc = doc(50L, warehouse(10L), OrderConsumptionStatus.PENDING);
        when(docRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(doc));

        service.processClaimedDoc(50L, USER_ID);

        verify(lineRepository, never()).sumRecipeQuantitiesByDocId(anyLong());
        verify(ledgerService, never()).record(any());
        assertThat(doc.getStatus()).isEqualTo(OrderConsumptionStatus.PENDING);
    }

    @Test
    void recalculateRejectsNonConflictDoc() {
        OrderConsumption doc = doc(50L, warehouse(10L), OrderConsumptionStatus.POSTED);
        when(docRepository.findByIdAndTenantIdForUpdate(50L, TENANT_ID)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.recalculate(50L, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                .isEqualTo(InventoryErrorCode.ORDER_CONSUMPTION_RECALCULATE_NOT_CONFLICT));
        verify(ledgerService, never()).record(any());
    }

    private Order order(Long id, Warehouse warehouse, OrderStatus status, OrderLine... lines) {
        Order order = new Order();
        order.setId(id);
        order.setTenantId(TENANT_ID);
        order.setWarehouse(warehouse);
        order.setStatus(status);
        for (OrderLine line : lines) {
            line.setOrder(order);
            order.getLines().add(line);
        }
        return order;
    }

    private OrderLine orderLine(Long id, Long recipeId, String quantity) {
        Recipe recipe = new Recipe();
        recipe.setId(recipeId);
        OrderLine line = new OrderLine();
        line.setId(id);
        line.setRecipe(recipe);
        line.setQuantity(new BigDecimal(quantity));
        return line;
    }

    private OrderConsumption doc(Long id, Warehouse warehouse, OrderConsumptionStatus status) {
        OrderConsumption doc = new OrderConsumption();
        doc.setId(id);
        doc.setTenantId(TENANT_ID);
        doc.setWarehouse(warehouse);
        doc.setStatus(status);
        return doc;
    }

    private Warehouse warehouse(Long id) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setTenantId(TENANT_ID);
        return warehouse;
    }

    private RecipeItem recipeItem(Long recipeId, Long materialId, String materialName, Long uomId, String quantity) {
        Recipe recipe = new Recipe();
        recipe.setId(recipeId);
        com.smart.restaurant_saas.inventory.material.Material material =
            new com.smart.restaurant_saas.inventory.material.Material();
        material.setId(materialId);
        material.setName(materialName);
        Uom uom = new Uom();
        uom.setId(uomId);
        RecipeItem item = new RecipeItem();
        item.setRecipe(recipe);
        item.setMaterial(material);
        item.setUom(uom);
        item.setQuantity(new BigDecimal(quantity));
        return item;
    }

    private RecipeQuantity recipeQuantity(Long recipeId, String quantity) {
        return new RecipeQuantity() {
            @Override public Long getRecipeId() { return recipeId; }
            @Override public BigDecimal getQuantity() { return new BigDecimal(quantity); }
        };
    }

    private DocLineCount docLineCount(Long docId, Long lineCount) {
        return new DocLineCount() {
            @Override public Long getDocId() { return docId; }
            @Override public Long getLineCount() { return lineCount; }
        };
    }

    private OrderConsumptionLineView lineView(Long id, Long orderId, Long createdBy, boolean consumed) {
        return new OrderConsumptionLineView() {
            @Override public Long getId() { return id; }
            @Override public Long getOrderId() { return orderId; }
            @Override public Long getCreatedBy() { return createdBy; }
            @Override public Boolean getConsumed() { return consumed; }
        };
    }

    private MaterialSummary materialSummary(
            Long materialId, String materialName, String uom, String totalQty, Long orderCount) {
        return new MaterialSummary() {
            @Override public Long getMaterialId() { return materialId; }
            @Override public String getMaterialName() { return materialName; }
            @Override public String getUom() { return uom; }
            @Override public BigDecimal getTotalQtyConsumed() { return new BigDecimal(totalQty); }
            @Override public Long getOrderCount() { return orderCount; }
        };
    }

    private PlatformTransactionManager mockTransactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return manager;
    }
}
