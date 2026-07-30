package com.smart.restaurant_saas.inventory.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.inventory.core.enums.CountLineAction;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;
import com.smart.restaurant_saas.inventory.mapper.PhysicalCountMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumption;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionErrorDetail;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionRepository;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionService;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionStatus;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCount;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLine;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.PhysicalCountLineRepository;
import com.smart.restaurant_saas.inventory.repository.PhysicalCountRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class PhysicalCountServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long COUNT_ID = 30L;
    private static final Long USER_ID = 99L;
    private static final Long WAREHOUSE_ID = 5L;
    private static final List<OrderConsumptionStatus> UNSETTLED_CONSUMPTION_STATUSES = List.of(
        OrderConsumptionStatus.PENDING,
        OrderConsumptionStatus.IN_PROGRESS,
        OrderConsumptionStatus.CONFLICT);

    @Mock
    private PhysicalCountRepository countRepository;
    @Mock
    private PhysicalCountLineRepository countLineRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private StockBalanceRepository stockBalanceRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private OrderConsumptionRepository consumptionRepository;
    @Mock
    private OrderConsumptionService consumptionService;
    @Mock
    private InventoryLedgerService ledgerService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private PhysicalCountService service;

    @BeforeEach
    void setUp() {
        service = new PhysicalCountService(
            countRepository,
            countLineRepository,
            warehouseRepository,
            materialRepository,
            stockBalanceRepository,
            transactionRepository,
            consumptionRepository,
            consumptionService,
            ledgerService,
            new PhysicalCountMapper(),
            transactionManager
        );
    }

    @Test
    void reconcileDatesEveryMovementAtTheFreezeCutoffNotThePostingTime() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        Material oil = material(102L, "OIL", kg);
        LocalDate scheduledDate = LocalDate.of(2026, 6, 29);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 6, 30, 9, 30);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, scheduledDate, frozenAt, warehouse,
            line(201L, flour, kg, "10.000000", "12.000000"),
            line(202L, oil, kg, "10.000000", "8.000000"));
        StockBalance flourBalance = balance(301L, warehouse, flour, kg);
        StockBalance oilBalance = balance(302L, warehouse, oil, kg);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(ledgerService.record(any(LedgerCommand.class))).thenReturn(transaction(901L, flour,
            InventoryTransactionDirection.IN, "2.000000", frozenAt));
        when(stockBalanceRepository.findByWarehouseAndMaterials(
            TENANT_ID, WAREHOUSE_ID, List.of(101L, 102L)))
            .thenReturn(List.of(flourBalance, oilBalance));
        when(countRepository.save(count)).thenReturn(count);

        LocalDateTime before = LocalDateTime.now();
        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LedgerCommand> commandCaptor = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledgerService, times(2)).record(commandCaptor.capture());
        List<LedgerCommand> commands = commandCaptor.getAllValues();

        // Reconciled two days after the freeze: the movements must still land on the freeze date.
        assertThat(count.getReconciledAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(count.getReconciledAt()).isNotEqualTo(frozenAt);
        assertThat(commands).allSatisfy(command -> {
            assertThat(command.getMovementDate()).isEqualTo(frozenAt);
            assertThat(command.getMovementDate()).isNotEqualTo(count.getReconciledAt());
            assertThat(command.getMovementDate()).isNotEqualTo(scheduledDate.atStartOfDay());
        });
        assertThat(commands).extracting(LedgerCommand::getDirection)
            .containsExactly(InventoryTransactionDirection.IN, InventoryTransactionDirection.OUT);
    }

    @Test
    void reconcilePostsOneMovementTypeInBothDirectionsAndNeverWaste() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        Material oil = material(102L, "OIL", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 6, 30, 9, 30);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 6, 29),
            frozenAt, warehouse,
            line(201L, flour, kg, "10.000000", "12.000000"),   // surplus
            line(202L, oil, kg, "10.000000", "8.000000"));     // shortage
        StockBalance flourBalance = balance(301L, warehouse, flour, kg);
        StockBalance oilBalance = balance(302L, warehouse, oil, kg);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(ledgerService.record(any(LedgerCommand.class))).thenReturn(transaction(901L, flour,
            InventoryTransactionDirection.IN, "2.000000", frozenAt));
        when(stockBalanceRepository.findByWarehouseAndMaterials(
            TENANT_ID, WAREHOUSE_ID, List.of(101L, 102L)))
            .thenReturn(List.of(flourBalance, oilBalance));
        when(countRepository.save(count)).thenReturn(count);

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        ArgumentCaptor<LedgerCommand> commandCaptor = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledgerService, times(2)).record(commandCaptor.capture());
        List<LedgerCommand> commands = commandCaptor.getAllValues();

        // One type, both directions. WASTE must never appear, whichever way the variance points.
        assertThat(commands).extracting(LedgerCommand::getTransactionType)
            .containsExactly(InventoryTransactionType.COUNT_ADJUSTMENT,
                InventoryTransactionType.COUNT_ADJUSTMENT);
        assertThat(commands).extracting(LedgerCommand::getDirection)
            .containsExactly(InventoryTransactionDirection.IN, InventoryTransactionDirection.OUT);
        // reference_type is what marks a movement as a count, and what reports filter on.
        assertThat(commands).extracting(LedgerCommand::getReferenceType)
            .containsOnly("PHYSICAL_COUNT");
        assertThat(commands).extracting(LedgerCommand::getReferenceId).containsOnly(COUNT_ID);

        assertThat(count.getLines()).extracting(PhysicalCountLine::getActionTaken)
            .containsOnly(CountLineAction.ADJUSTMENT);
        assertThat(count.getLines()).allSatisfy(line ->
            assertThat(line.getAdjustmentTransactionId()).isEqualTo(901L));
    }

    @Test
    void reconcileMeasuresVarianceAgainstTheFrozenSnapshotOnly() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        // Counted exactly the frozen quantity. A purchase landing between freeze and count would
        // once have been netted in (adjusted expected 12, variance -2, a phantom OUT movement);
        // the snapshot is now final, so the count is flat and nothing is posted.
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse, line(201L, flour, kg, "10.000000", "10.000000"));

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.save(count)).thenReturn(count);

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        PhysicalCountLine line = count.getLines().get(0);
        assertThat(line.getExpectedQuantity()).isEqualByComparingTo("10.000000");
        assertThat(line.getVariance()).isEqualByComparingTo("0.000000");
        assertThat(line.getAdjustedExpectedQuantity()).isNull();
        assertThat(line.getActionTaken()).isEqualTo(CountLineAction.NO_DIFFERENCE);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void reconcileRejectsACountThatCarriesNoFreezeTimestamp() {
        Uom kg = uom();
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            null, warehouse(), line(201L, material(101L, "FLOUR", kg), kg, "10.000000", "8.000000"));

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));

        assertThatThrownBy(() -> service.reconcile(
                COUNT_ID, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.VALIDATION_FAILED);
                assertThat(ex.getParams()).containsEntry("field", "frozenAt");
            });

        verifyNoInteractions(ledgerService);
    }

    @Test
    void startSettlesAPendingConsumptionDocBeforeTakingTheSnapshot() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCount count = count(PhysicalCountStatus.DRAFT, LocalDate.of(2026, 7, 4),
            null, warehouse, line(201L, flour, kg, "0.000000", null));
        StockBalance balance = balance(301L, warehouse, flour, kg);
        // The balance as it stands only after the pending consumption has been posted.
        balance.setQuantity(new BigDecimal("7.000000"));
        balance.setAverageCost(new BigDecimal("3.000000"));
        OrderConsumption pendingDoc = consumptionDoc(77L, OrderConsumptionStatus.PENDING);
        OrderConsumption postedDoc = consumptionDoc(77L, OrderConsumptionStatus.POSTED);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, COUNT_ID, List.of(101L)))
            .thenReturn(List.of());
        when(consumptionRepository.findFirstByTenantIdAndWarehouseIdAndStatusInOrderByIdAsc(
            TENANT_ID, WAREHOUSE_ID, UNSETTLED_CONSUMPTION_STATUSES))
            .thenReturn(Optional.of(pendingDoc));
        when(consumptionService.claimDoc(77L, USER_ID)).thenReturn(true);
        when(consumptionRepository.findById(77L)).thenReturn(Optional.of(postedDoc));
        when(stockBalanceRepository.findByWarehouseAndMaterials(TENANT_ID, WAREHOUSE_ID, List.of(101L)))
            .thenReturn(List.of(balance));
        when(countRepository.save(count)).thenReturn(count);

        service.start(COUNT_ID, TENANT_ID, USER_ID);

        // Settle, then snapshot — the ordering is the whole point.
        InOrder inOrder = inOrder(consumptionService, stockBalanceRepository);
        inOrder.verify(consumptionService).claimDoc(77L, USER_ID);
        inOrder.verify(consumptionService).processClaimedDoc(77L, USER_ID);
        inOrder.verify(stockBalanceRepository)
            .findByWarehouseAndMaterials(TENANT_ID, WAREHOUSE_ID, List.of(101L));

        assertThat(count.getStatus()).isEqualTo(PhysicalCountStatus.IN_PROGRESS);
        assertThat(count.getLines().get(0).getExpectedQuantity()).isEqualByComparingTo("7.000000");
        assertThat(count.getLines().get(0).getUnitCostAtFreeze()).isEqualByComparingTo("3.000000");
    }

    @Test
    void startIsBlockedByAConflictingConsumptionDocAndNamesTheFailingMaterials() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCount count = count(PhysicalCountStatus.DRAFT, LocalDate.of(2026, 7, 4),
            null, warehouse, line(201L, flour, kg, "0.000000", null));
        OrderConsumption conflictDoc = consumptionDoc(77L, OrderConsumptionStatus.CONFLICT);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, COUNT_ID, List.of(101L)))
            .thenReturn(List.of());
        when(consumptionRepository.findFirstByTenantIdAndWarehouseIdAndStatusInOrderByIdAsc(
            TENANT_ID, WAREHOUSE_ID, UNSETTLED_CONSUMPTION_STATUSES))
            .thenReturn(Optional.of(conflictDoc));
        when(consumptionRepository.findById(77L)).thenReturn(Optional.of(conflictDoc));
        when(consumptionService.findErrorDetails(77L, TENANT_ID)).thenReturn(List.of(
            new OrderConsumptionErrorDetail(101L, "FLOUR", "java.lang.IllegalStateException", "boom"),
            new OrderConsumptionErrorDetail(102L, "OIL", "java.lang.IllegalStateException", "boom")));

        assertThatThrownBy(() -> service.start(COUNT_ID, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode())
                    .isEqualTo(InventoryErrorCode.FREEZE_BLOCKED_BY_CONSUMPTION_CONFLICT);
                assertThat(ex.getParams()).containsEntry("docId", 77L);
                assertThat(ex.getParams()).containsEntry("warehouseId", WAREHOUSE_ID);
                assertThat(ex.getParams().get("materials")).asInstanceOf(LIST)
                    .containsExactly(
                        Map.of("materialId", 101L, "materialName", "FLOUR"),
                        Map.of("materialId", 102L, "materialName", "OIL"));
            });

        assertThat(count.getStatus()).isEqualTo(PhysicalCountStatus.DRAFT);
        assertThat(count.getFrozenAt()).isNull();
        verify(stockBalanceRepository, never())
            .findByWarehouseAndMaterials(any(), any(), any());
    }

    @Test
    void startIsRefusedWhileAConsumptionDocIsStillBeingProcessed() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCount count = count(PhysicalCountStatus.DRAFT, LocalDate.of(2026, 7, 4),
            null, warehouse, line(201L, flour, kg, "0.000000", null));
        // The scheduler claimed it first, so this freeze cannot claim it and must not snapshot
        // balances that are about to move.
        OrderConsumption inFlightDoc = consumptionDoc(77L, OrderConsumptionStatus.IN_PROGRESS);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, COUNT_ID, List.of(101L)))
            .thenReturn(List.of());
        when(consumptionRepository.findFirstByTenantIdAndWarehouseIdAndStatusInOrderByIdAsc(
            TENANT_ID, WAREHOUSE_ID, UNSETTLED_CONSUMPTION_STATUSES))
            .thenReturn(Optional.of(inFlightDoc));
        when(consumptionRepository.findById(77L)).thenReturn(Optional.of(inFlightDoc));

        assertThatThrownBy(() -> service.start(COUNT_ID, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode())
                    .isEqualTo(InventoryErrorCode.FREEZE_CONSUMPTION_NOT_SETTLED);
                assertThat(ex.getParams()).containsEntry("docId", 77L);
                assertThat(ex.getParams()).containsEntry("currentStatus", "IN_PROGRESS");
            });

        verify(consumptionService, never()).processClaimedDoc(any(), any());
        verify(stockBalanceRepository, never())
            .findByWarehouseAndMaterials(any(), any(), any());
    }

    @Test
    void startTakesTheSnapshotDirectlyWhenNothingIsOutstanding() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCount count = count(PhysicalCountStatus.DRAFT, LocalDate.of(2026, 7, 4),
            null, warehouse, line(201L, flour, kg, "0.000000", null));
        StockBalance balance = balance(301L, warehouse, flour, kg);
        balance.setQuantity(new BigDecimal("4.000000"));
        balance.setAverageCost(new BigDecimal("2.500000"));

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, COUNT_ID, List.of(101L)))
            .thenReturn(List.of());
        when(consumptionRepository.findFirstByTenantIdAndWarehouseIdAndStatusInOrderByIdAsc(
            TENANT_ID, WAREHOUSE_ID, UNSETTLED_CONSUMPTION_STATUSES))
            .thenReturn(Optional.empty());
        when(stockBalanceRepository.findByWarehouseAndMaterials(TENANT_ID, WAREHOUSE_ID, List.of(101L)))
            .thenReturn(List.of(balance));
        when(countRepository.save(count)).thenReturn(count);

        service.start(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(count.getStatus()).isEqualTo(PhysicalCountStatus.IN_PROGRESS);
        assertThat(count.getLines().get(0).getExpectedQuantity()).isEqualByComparingTo("4.000000");
        verifyNoInteractions(consumptionService);
    }

    @Test
    void deleteAllowsDraftCount() {
        PhysicalCount count = count(PhysicalCountStatus.DRAFT);
        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));

        service.delete(COUNT_ID, TENANT_ID);

        verify(countRepository).delete(count);
    }

    @Test
    void deleteAllowsInProgressCount() {
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS);
        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));

        service.delete(COUNT_ID, TENANT_ID);

        verify(countRepository).delete(count);
    }

    @ParameterizedTest
    @EnumSource(value = PhysicalCountStatus.class, names = {"RECONCILED", "CANCELLED"})
    void deleteRejectsFinalOrCancelledCount(PhysicalCountStatus status) {
        PhysicalCount count = count(status);
        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));

        assertThatThrownBy(() -> service.delete(COUNT_ID, TENANT_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION);
                assertThat(ex.getParams()).containsEntry("entityType", "PhysicalCount");
                assertThat(ex.getParams()).containsEntry("currentStatus", status.name());
                assertThat(ex.getParams()).containsEntry("requiredStatus", "DRAFT,IN_PROGRESS");
                assertThat(ex.getParams()).containsEntry("action", "delete");
            });

        verify(countRepository, never()).delete(count);
    }

    @Test
    void revertToDraftResetsFreezeCountAndReconcileLineState() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCountLine line = line(201L, flour, kg, "10.000000", "8.000000");
        line.setCountedAt(LocalDateTime.of(2026, 7, 3, 10, 0));
        line.setVariance(new BigDecimal("-2.000000"));
        line.setAdjustedExpectedQuantity(new BigDecimal("10.500000"));
        line.setVarianceValue(new BigDecimal("12.000000"));
        line.setActionTaken(CountLineAction.ADJUSTMENT);
        line.setAdjustmentTransactionId(901L);
        line.setNotes("counted short");
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 1),
            LocalDateTime.of(2026, 7, 3, 9, 0), warehouse, line);
        count.setStartedAt(LocalDateTime.of(2026, 7, 3, 8, 55));

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.save(count)).thenReturn(count);

        var response = service.revertToDraft(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(PhysicalCountStatus.DRAFT);
        assertThat(count.getStatus()).isEqualTo(PhysicalCountStatus.DRAFT);
        assertThat(count.getFrozenAt()).isNull();
        assertThat(count.getStartedAt()).isNull();

        assertThat(line.getExpectedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(line.getUnitCostAtFreeze()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(line.getCountedQuantity()).isNull();
        assertThat(line.getCountedAt()).isNull();
        assertThat(line.getVariance()).isNull();
        assertThat(line.getAdjustedExpectedQuantity()).isNull();
        assertThat(line.getVarianceValue()).isNull();
        assertThat(line.getActionTaken()).isEqualTo(CountLineAction.PENDING);
        assertThat(line.getAdjustmentTransactionId()).isNull();
        assertThat(line.getNotes()).isNull();
        assertThat(line.getMaterial()).isSameAs(flour);
        assertThat(line.getUom()).isSameAs(kg);

        verifyNoInteractions(ledgerService, stockBalanceRepository);
    }

    @ParameterizedTest
    @EnumSource(value = PhysicalCountStatus.class, names = {"DRAFT", "RECONCILED", "CANCELLED"})
    void revertToDraftRejectsNonInProgressCounts(PhysicalCountStatus status) {
        PhysicalCount count = count(status);
        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));

        assertThatThrownBy(() -> service.revertToDraft(COUNT_ID, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION);
                assertThat(ex.getParams()).containsEntry("entityType", "PhysicalCount");
                assertThat(ex.getParams()).containsEntry("currentStatus", status.name());
                assertThat(ex.getParams()).containsEntry("requiredStatus", "IN_PROGRESS");
                assertThat(ex.getParams()).containsEntry("action", "revertToDraft");
            });

        verify(countRepository, never()).save(any());
    }

    @Test
    void revertToDraftMakesDraftMaterialEditingAvailableAgain() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        Material sugar = material(102L, "SUGAR", kg);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 1),
            LocalDateTime.of(2026, 7, 3, 9, 0), warehouse,
            line(201L, flour, kg, "10.000000", "8.000000"));

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.save(count)).thenReturn(count);
        when(materialRepository.findByIdAndTenantId(102L, TENANT_ID))
            .thenReturn(Optional.of(sugar));

        service.revertToDraft(COUNT_ID, TENANT_ID, USER_ID);
        service.addMaterials(COUNT_ID, List.of(102L), TENANT_ID);
        service.removeMaterials(COUNT_ID, List.of(101L), TENANT_ID);

        assertThat(count.getStatus()).isEqualTo(PhysicalCountStatus.DRAFT);
        assertThat(count.getLines()).extracting(line -> line.getMaterial().getId())
            .containsExactly(102L);
    }

    @Test
    void revertedCountNoLongerBlocksAnotherCountStart() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCount firstCount = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 1),
            LocalDateTime.of(2026, 7, 3, 9, 0), warehouse,
            line(201L, flour, kg, "10.000000", "8.000000"));
        PhysicalCount otherCount = count(PhysicalCountStatus.DRAFT, LocalDate.of(2026, 7, 4),
            null, warehouse, line(202L, flour, kg, "0.000000", "0.000000"));
        otherCount.setId(31L);
        StockBalance balance = balance(301L, warehouse, flour, kg);
        balance.setQuantity(new BigDecimal("12.000000"));
        balance.setAverageCost(new BigDecimal("3.000000"));

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(firstCount));
        when(countRepository.findByIdAndTenantId(31L, TENANT_ID))
            .thenReturn(Optional.of(otherCount));
        when(countRepository.save(firstCount)).thenReturn(firstCount);
        when(countRepository.save(otherCount)).thenReturn(otherCount);
        when(countRepository.findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, 31L, List.of(101L)))
            .thenReturn(List.of());
        when(stockBalanceRepository.findByWarehouseAndMaterials(TENANT_ID, WAREHOUSE_ID, List.of(101L)))
            .thenReturn(List.of(balance));

        service.revertToDraft(COUNT_ID, TENANT_ID, USER_ID);
        service.start(31L, TENANT_ID, USER_ID);

        assertThat(firstCount.getStatus()).isEqualTo(PhysicalCountStatus.DRAFT);
        assertThat(otherCount.getStatus()).isEqualTo(PhysicalCountStatus.IN_PROGRESS);
        verify(countRepository).findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, 31L, List.of(101L));
    }

    @Test
    void revertToDraftWorksWhenNoLinesHaveBeenCounted() {
        Uom kg = uom();
        PhysicalCountLine line = line(201L, material(101L, "FLOUR", kg), kg, "10.000000", "0.000000");
        line.setCountedQuantity(null);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 1),
            LocalDateTime.of(2026, 7, 3, 9, 0), warehouse(), line);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.save(count)).thenReturn(count);

        service.revertToDraft(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(count.getStatus()).isEqualTo(PhysicalCountStatus.DRAFT);
        assertThat(line.getCountedQuantity()).isNull();
        assertThat(line.getCountedAt()).isNull();
        assertThat(line.getActionTaken()).isEqualTo(CountLineAction.PENDING);
    }

    private PhysicalCount count(PhysicalCountStatus status) {
        PhysicalCount count = new PhysicalCount();
        count.setId(COUNT_ID);
        count.setTenantId(TENANT_ID);
        count.setCode("PC-30");
        count.setScheduledDate(LocalDate.of(2026, 7, 1));
        count.setStatus(status);
        return count;
    }

    private PhysicalCount count(PhysicalCountStatus status, LocalDate scheduledDate,
                                LocalDateTime frozenAt, Warehouse warehouse,
                                PhysicalCountLine... lines) {
        PhysicalCount count = count(status);
        count.setScheduledDate(scheduledDate);
        count.setFrozenAt(frozenAt);
        count.setWarehouse(warehouse);
        for (PhysicalCountLine line : lines) {
            line.setPhysicalCount(count);
            count.getLines().add(line);
        }
        return count;
    }

    private PhysicalCountLine line(Long id, Material material, Uom uom,
                                   String expectedQuantity, String countedQuantity) {
        PhysicalCountLine line = new PhysicalCountLine();
        line.setId(id);
        line.setTenantId(TENANT_ID);
        line.setMaterial(material);
        line.setUom(uom);
        line.setExpectedQuantity(new BigDecimal(expectedQuantity));
        line.setCountedQuantity(countedQuantity != null ? new BigDecimal(countedQuantity) : null);
        line.setUnitCostAtFreeze(new BigDecimal("5.000000"));
        return line;
    }

    private OrderConsumption consumptionDoc(Long id, OrderConsumptionStatus status) {
        OrderConsumption doc = new OrderConsumption();
        doc.setId(id);
        doc.setTenantId(TENANT_ID);
        doc.setStatus(status);
        return doc;
    }

    private InventoryTransaction transaction(Long id, Material material,
                                             InventoryTransactionDirection direction,
                                             String stockQuantity,
                                             LocalDateTime movementDate) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setId(id);
        transaction.setTenantId(TENANT_ID);
        transaction.setMaterial(material);
        transaction.setDirection(direction);
        transaction.setStockQuantity(new BigDecimal(stockQuantity));
        transaction.setMovementDate(movementDate);
        return transaction;
    }

    private StockBalance balance(Long id, Warehouse warehouse, Material material, Uom uom) {
        StockBalance balance = new StockBalance();
        balance.setId(id);
        balance.setTenantId(TENANT_ID);
        balance.setWarehouse(warehouse);
        balance.setMaterial(material);
        balance.setUom(uom);
        return balance;
    }

    private Material material(Long id, String code, Uom uom) {
        Material material = new Material();
        material.setId(id);
        material.setTenantId(TENANT_ID);
        material.setCode(code);
        material.setName(code);
        material.setStockUom(uom);
        material.setDisplayUom(uom);
        return material;
    }

    private Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setTenantId(TENANT_ID);
        warehouse.setCode("MAIN");
        warehouse.setName("Main Warehouse");
        return warehouse;
    }

    private Uom uom() {
        Uom uom = new Uom();
        uom.setId(1L);
        uom.setCode("KG");
        uom.setSymbol("kg");
        uom.setFactorToBase(BigDecimal.ONE);
        return uom;
    }
}
