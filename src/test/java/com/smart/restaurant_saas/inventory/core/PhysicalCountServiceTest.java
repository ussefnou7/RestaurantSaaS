package com.smart.restaurant_saas.inventory.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import com.smart.restaurant_saas.inventory.physicalcount.MaterialConflictProjection;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCount;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountCodeSequenceService;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLine;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountMovementRow;
import com.smart.restaurant_saas.inventory.physicalcount.PostFreezeMovementSummary;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountLineResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountRequest;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.UpdateCountedQuantitiesRequest;
import com.smart.restaurant_saas.inventory.physicalcount.dto.UpdateCountedQuantityRequest;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
        OrderConsumptionStatus.PARTIAL,
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
    private PhysicalCountCodeSequenceService codeSequenceService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private UomConversionService uomConversionService;
    private PhysicalCountService service;

    @BeforeEach
    void setUp() {
        uomConversionService = spy(new UomConversionService());
        service = new PhysicalCountService(
            countRepository,
            countLineRepository,
            warehouseRepository,
            materialRepository,
            stockBalanceRepository,
            transactionRepository,
            consumptionRepository,
            consumptionService,
            uomConversionService,
            ledgerService,
            new PhysicalCountMapper(),
            codeSequenceService,
            transactionManager
        );
    }

    @Test
    void postFreezeMovementsReturnsZeroWithoutAttemptingUomConversion() {
        Uom kg = uom(1L, "KG", "kg", "1", null);
        Uom each = uom(2L, "EA", "ea", "1", null);
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCountLine countLine = line(201L, flour, each, "10.000000", null);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 4, 9, 0);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 4),
            frozenAt, warehouse(), countLine);
        PostFreezeMovementSummary summary = postFreezeSummary(flour, "0.000000", "0.000000");

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.summarizeMovementsAfterFreeze(
            TENANT_ID, WAREHOUSE_ID, frozenAt, COUNT_ID)).thenReturn(List.of(summary));

        var response = service.findPostFreezeMovements(COUNT_ID, TENANT_ID);

        assertThat(response.getMaterials()).singleElement().satisfies(row -> {
            assertThat(row.getQuantityIn()).isEqualByComparingTo("0.000000");
            assertThat(row.getQuantityOut()).isEqualByComparingTo("0.000000");
            assertThat(row.getNetQuantity()).isEqualByComparingTo("0.000000");
            assertThat(row.getUomId()).isEqualTo(each.getId());
            assertThat(row.getUomSymbol()).isEqualTo("ea");
        });
        verify(uomConversionService, never()).convert(any(), any(), any(), any(), any());
    }

    @Test
    void postFreezeMovementsFailsLoudlyWhenTotalsCannotConvertToTheFrozenLineUom() {
        Uom kg = uom(1L, "KG", "kg", "1", null);
        Uom each = uom(2L, "EA", "ea", "1", null);
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCountLine countLine = line(201L, flour, each, "10.000000", null);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 4, 9, 0);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 4),
            frozenAt, warehouse(), countLine);
        PostFreezeMovementSummary summary = postFreezeSummary(flour, "5.000000", "0.000000");

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.summarizeMovementsAfterFreeze(
            TENANT_ID, WAREHOUSE_ID, frozenAt, COUNT_ID)).thenReturn(List.of(summary));

        assertThatThrownBy(() -> service.findPostFreezeMovements(COUNT_ID, TENANT_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.UOM_CONVERSION_FAILED);
                assertThat(ex.getParams()).containsEntry("materialId", flour.getId());
                assertThat(ex.getParams()).containsEntry("materialName", flour.getName());
                assertThat(ex.getParams()).containsEntry("materialCode", flour.getCode());
                assertThat(ex.getParams()).containsEntry("fromUom", "KG");
                assertThat(ex.getParams()).containsEntry("toUom", "EA");
            });
    }

    @Test
    void postFreezeMovementRowFailsLoudlyWhenItCannotConvertToTheFrozenLineUom() {
        Uom kg = uom(1L, "KG", "kg", "1", null);
        Uom each = uom(2L, "EA", "ea", "1", null);
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCountLine countLine = line(201L, flour, each, "10.000000", "10.000000");
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 4, 9, 0);
        LocalDateTime movementDate = frozenAt.plusHours(1);
        LocalDateTime createdAt = frozenAt.plusMinutes(30);
        countLine.setCountedAt(frozenAt.plusHours(2));
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 4),
            frozenAt, warehouse(), countLine);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.summarizeMovementsAfterFreeze(
            TENANT_ID, WAREHOUSE_ID, frozenAt, COUNT_ID)).thenReturn(List.of());
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(flour.getId()), frozenAt, frozenAt, true, COUNT_ID))
            .thenReturn(List.of(new PhysicalCountMovementRow(
                901L, flour.getId(), new BigDecimal("5.000000"),
                InventoryTransactionDirection.IN, movementDate, createdAt, null, null)));

        assertThatThrownBy(() -> service.findPostFreezeMovements(COUNT_ID, TENANT_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.UOM_CONVERSION_FAILED);
                assertThat(ex.getParams()).containsEntry("materialId", flour.getId());
                assertThat(ex.getParams()).containsEntry("materialName", flour.getName());
                assertThat(ex.getParams()).containsEntry("materialCode", flour.getCode());
                assertThat(ex.getParams()).containsEntry("fromUom", "KG");
                assertThat(ex.getParams()).containsEntry("toUom", "EA");
            });
    }

    @Test
    void reconcileDatesEachMovementAtItsLineCountTimeNotThePostingTime() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        Material oil = material(102L, "OIL", kg);
        LocalDate scheduledDate = LocalDate.of(2026, 6, 29);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 6, 30, 9, 30);
        PhysicalCountLine flourLine = line(201L, flour, kg, "10.000000", "12.000000");
        flourLine.setCountedAt(frozenAt.plusHours(2));
        PhysicalCountLine oilLine = line(202L, oil, kg, "10.000000", "8.000000");
        oilLine.setCountedAt(frozenAt.plusDays(1));
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, scheduledDate, frozenAt, warehouse,
            flourLine, oilLine);
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

        assertThat(count.getReconciledAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(count.getReconciledAt()).isNotEqualTo(frozenAt);
        assertThat(commands).extracting(LedgerCommand::getMovementDate)
            .containsExactly(flourLine.getCountedAt(), oilLine.getCountedAt());
        assertThat(commands).allSatisfy(command ->
            assertThat(command.getMovementDate()).isNotEqualTo(scheduledDate.atStartOfDay()));
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
    void reconcilePostsOnlyForCountedLinesThatActuallyDiffer() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        Material sugar = material(102L, "SUGAR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 6, 30, 9, 30);
        // OIL (103) is stocked in this warehouse but was never added to the document, so it is not
        // iterated at all. SUGAR is in the document and counted flat.
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 6, 29),
            frozenAt, warehouse,
            line(201L, flour, kg, "10.000000", "8.000000"),
            line(202L, sugar, kg, "4.000000", "4.000000"));
        StockBalance flourBalance = balance(301L, warehouse, flour, kg);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(ledgerService.record(any(LedgerCommand.class))).thenReturn(transaction(901L, flour,
            InventoryTransactionDirection.OUT, "2.000000", frozenAt));
        when(stockBalanceRepository.findByWarehouseAndMaterials(TENANT_ID, WAREHOUSE_ID, List.of(101L)))
            .thenReturn(List.of(flourBalance));
        when(countRepository.save(count)).thenReturn(count);

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        // Exactly one movement: no zero-quantity row for the flat line, and nothing at all for the
        // material that is not in the document.
        ArgumentCaptor<LedgerCommand> commandCaptor = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledgerService, times(1)).record(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getMaterialId()).isEqualTo(101L);

        PhysicalCountLine flatLine = count.getLines().get(1);
        assertThat(flatLine.getActionTaken()).isEqualTo(CountLineAction.NO_DIFFERENCE);
        assertThat(flatLine.getAdjustmentTransactionId()).isNull();

        // Only the material that moved gets its last-count stamp refreshed.
        verify(stockBalanceRepository)
            .findByWarehouseAndMaterials(TENANT_ID, WAREHOUSE_ID, List.of(101L));
    }

    @Test
    void shortageVarianceValueIsNegativeAndRemainsAnEstimate() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 6, 30, 9, 30);
        // unitCostAtFreeze is 5.00 (see line()); the ledger will value the OUT from whatever the
        // open batches actually cost, which is a different number.
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 6, 29),
            frozenAt, warehouse, line(201L, flour, kg, "10.000000", "8.000000"));
        StockBalance flourBalance = balance(301L, warehouse, flour, kg);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(ledgerService.record(any(LedgerCommand.class))).thenReturn(transaction(901L, flour,
            InventoryTransactionDirection.OUT, "2.000000", frozenAt));
        when(stockBalanceRepository.findByWarehouseAndMaterials(TENANT_ID, WAREHOUSE_ID, List.of(101L)))
            .thenReturn(List.of(flourBalance));
        when(countRepository.save(count)).thenReturn(count);

        PhysicalCountResponse response = service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        PhysicalCountLineResponse line = response.getLines().getFirst();
        assertThat(line.getVarianceValue()).isEqualByComparingTo("-10.000000");
        assertThat(line.getVarianceValueIsEstimate()).isTrue();
        // No unit cost is sent — the batch layer owns costing, and its number is the one reports read.
        ArgumentCaptor<LedgerCommand> commandCaptor = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledgerService).record(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getEnteredUnitCost()).isNull();
    }

    @Test
    void surplusVarianceValueIsPositive() {
        Uom kg = uom();
        PhysicalCountLine line = line(
            201L, material(101L, "FLOUR", kg), kg, "10.000000", "12.000000");

        PhysicalCountResponse response = reconcileForVarianceAssertions(line);

        assertThat(response.getLines().getFirst().getVarianceValue())
            .isEqualByComparingTo("10.000000");
        assertThat(response.getLargeVarianceValue()).isEqualByComparingTo("10.000000");
    }

    @Test
    void zeroVarianceValueIsZero() {
        Uom kg = uom();
        PhysicalCountLine line = line(
            201L, material(101L, "FLOUR", kg), kg, "10.000000", "10.000000");

        PhysicalCountResponse response = reconcileForVarianceAssertions(line);

        assertThat(response.getLines().getFirst().getVarianceValue())
            .isEqualByComparingTo("0.000000");
        assertThat(response.getLargeVarianceValue()).isEqualByComparingTo("0.000000");
        assertThat(response.getHasLargeVariance()).isFalse();
        verifyNoInteractions(ledgerService);
    }

    @Test
    void mixedVarianceValuesNetDocumentTotalToZero() {
        Uom kg = uom();
        PhysicalCountLine shortage = line(
            201L, material(101L, "FLOUR", kg), kg, "10.000000", "2.000000");
        PhysicalCountLine surplus = line(
            202L, material(102L, "OIL", kg), kg, "10.000000", "18.000000");

        PhysicalCountResponse response = reconcileForVarianceAssertions(shortage, surplus);

        assertThat(response.getLines()).extracting(PhysicalCountLineResponse::getVarianceValue)
            .containsExactly(new BigDecimal("-40.000000"), new BigDecimal("40.000000"));
        assertThat(response.getLargeVarianceValue()).isEqualByComparingTo("0.000000");
        assertThat(response.getHasLargeVariance()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"99.000000", "301.000000"})
    void largeVarianceThresholdUsesMagnitudeForLargeShortageAndSurplus(String countedQuantity) {
        Uom kg = uom();
        PhysicalCountLine line = line(
            201L, material(101L, "FLOUR", kg), kg, "200.000000", countedQuantity);

        PhysicalCountResponse response = reconcileForVarianceAssertions(line);

        assertThat(response.getLargeVarianceValue().abs()).isEqualByComparingTo("505.000000");
        assertThat(response.getHasLargeVariance()).isTrue();
    }

    @Test
    void reconcileWorkedExampleNetsSaleThroughCountTimeAndPostsNothing() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine countLine = line(201L, flour, kg, "100.000000", "95.000000");
        countLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse, countLine);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(movement(101L, "-5.000000", frozenAt.plusHours(1))));
        when(countRepository.save(count)).thenReturn(count);

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(countLine.getExpectedQuantity()).isEqualByComparingTo("100.000000");
        assertThat(countLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(countLine.getVariance()).isEqualByComparingTo("0.000000");
        assertThat(countLine.getActionTaken()).isEqualTo(CountLineAction.NO_DIFFERENCE);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void detailReadNetsSaleThroughCountTimeWithoutPersistingCalculatedValues() {
        Uom kg = uom();
        Material flour = material(101L, "FLOUR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine countLine = line(201L, flour, kg, "100.000000", "95.000000");
        countLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse(), countLine);

        when(countRepository.findDetailByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(movement(101L, "-5.000000", frozenAt.plusHours(1))));

        PhysicalCountLineResponse responseLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();

        assertThat(responseLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(responseLine.getVariance()).isEqualByComparingTo("0.000000");
        assertThat(responseLine.getAdjustedExpectedQuantityProvisional()).isFalse();
        assertThat(countLine.getAdjustedExpectedQuantity()).isNull();
        verify(countRepository, never()).save(any());
    }

    @Test
    void uncountedDetailLineUsesNowAndIsMarkedProvisional() {
        Uom kg = uom();
        Material flour = material(101L, "FLOUR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        PhysicalCountLine countLine = line(201L, flour, kg, "100.000000", null);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse(), countLine);

        when(countRepository.findDetailByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(movement(101L, "-5.000000", frozenAt.plusHours(1))));
        LocalDateTime beforeRead = LocalDateTime.now();

        PhysicalCountLineResponse responseLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();
        LocalDateTime afterRead = LocalDateTime.now();

        assertThat(responseLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(responseLine.getVariance()).isNull();
        assertThat(responseLine.getAdjustedExpectedQuantityProvisional()).isTrue();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionRepository).findPhysicalCountMovements(
            org.mockito.ArgumentMatchers.eq(TENANT_ID),
            org.mockito.ArgumentMatchers.eq(WAREHOUSE_ID),
            org.mockito.ArgumentMatchers.eq(List.of(101L)),
            org.mockito.ArgumentMatchers.eq(frozenAt),
            cutoff.capture(),
            org.mockito.ArgumentMatchers.eq(COUNT_ID));
        assertThat(cutoff.getValue()).isBetween(beforeRead, afterRead);
    }

    @Test
    void detailReadAndReconcileUseTheSameCalculatedValues() {
        Uom kg = uom();
        Material flour = material(101L, "FLOUR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine countLine = line(201L, flour, kg, "100.000000", "95.000000");
        countLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse(), countLine);

        when(countRepository.findDetailByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(movement(101L, "-5.000000", frozenAt.plusHours(1))));
        when(countRepository.save(count)).thenReturn(count);

        PhysicalCountLineResponse readLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();
        PhysicalCountLineResponse reconciledLine = service.reconcile(COUNT_ID, TENANT_ID, USER_ID)
            .getLines().getFirst();

        assertThat(reconciledLine.getAdjustedExpectedQuantity())
            .isEqualByComparingTo(readLine.getAdjustedExpectedQuantity());
        assertThat(reconciledLine.getVariance()).isEqualByComparingTo(readLine.getVariance());
        assertThat(reconciledLine.getVarianceValue())
            .isEqualByComparingTo(readLine.getVarianceValue());
        verify(transactionRepository, times(2)).findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID);
    }

    @Test
    void reconciledDetailReturnsStoredValuesWithoutReadingLaterMovements() {
        Uom kg = uom();
        PhysicalCountLine countLine = line(
            201L, material(101L, "FLOUR", kg), kg, "100.000000", "95.000000");
        countLine.setAdjustedExpectedQuantity(new BigDecimal("95.000000"));
        countLine.setVariance(BigDecimal.ZERO.setScale(6));
        countLine.setVarianceValue(BigDecimal.ZERO.setScale(6));
        PhysicalCount count = count(PhysicalCountStatus.RECONCILED, LocalDate.of(2026, 7, 2),
            LocalDateTime.of(2026, 7, 3, 10, 0), warehouse(), countLine);
        when(countRepository.findDetailByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));

        PhysicalCountLineResponse responseLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();

        assertThat(responseLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(responseLine.getVariance()).isEqualByComparingTo("0.000000");
        assertThat(responseLine.getAdjustedExpectedQuantityProvisional()).isFalse();
        verifyNoInteractions(transactionRepository, uomConversionService);
    }

    @Test
    void draftDetailSkipsCalculationWhenThereIsNoFreezeTimestamp() {
        Uom kg = uom();
        PhysicalCount count = count(PhysicalCountStatus.DRAFT, LocalDate.of(2026, 7, 2),
            null, warehouse(), line(201L, material(101L, "FLOUR", kg), kg, "0.000000", null));
        when(countRepository.findDetailByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));

        PhysicalCountLineResponse responseLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();

        assertThat(responseLine.getAdjustedExpectedQuantity()).isNull();
        assertThat(responseLine.getAdjustedExpectedQuantityProvisional()).isFalse();
        verifyNoInteractions(transactionRepository, uomConversionService);
    }

    @Test
    void detailReadConvertsOneNettedStockQuantityIntoTheFrozenLineUom() {
        Uom kg = uom(1L, "KG", "kg", "1", null);
        Uom bag = uom(2L, "BAG", "bag", "5", kg);
        Material flour = material(101L, "FLOUR", kg);
        flour.setDisplayUom(bag);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine countLine = line(201L, flour, bag, "20.000000", "19.000000");
        countLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse(), countLine);
        when(countRepository.findDetailByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(movement(101L, "-5.000000", frozenAt.plusHours(1))));

        PhysicalCountLineResponse responseLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();

        assertThat(responseLine.getUomId()).isEqualTo(bag.getId());
        assertThat(responseLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("19.000000");
        assertThat(responseLine.getVariance()).isEqualByComparingTo("0.000000");
        verify(uomConversionService).convert(
            new BigDecimal("-5.000000"), kg, bag, flour, TENANT_ID);
    }

    @Test
    void detailReadFailsLoudlyWhenMovementNetCannotConvertToLineUom() {
        Uom kg = uom(1L, "KG", "kg", "1", null);
        Uom each = uom(2L, "EA", "ea", "1", null);
        Material flour = material(101L, "FLOUR", kg);
        flour.setDisplayUom(each);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine flourLine = line(201L, flour, each, "20.000000", "19.000000");
        flourLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse(), flourLine);
        when(countRepository.findDetailByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(movement(101L, "-1.000000", frozenAt.plusHours(1))));

        assertThatThrownBy(() -> service.findById(COUNT_ID, TENANT_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.UOM_CONVERSION_FAILED);
                assertThat(ex.getParams()).containsEntry("materialId", flour.getId());
                assertThat(ex.getParams()).containsEntry("materialName", flour.getName());
                assertThat(ex.getParams()).containsEntry("fromUom", "KG");
                assertThat(ex.getParams()).containsEntry("toUom", "EA");
            });
    }

    @Test
    void reconcilePostsOnlyTheShortageOnTopOfLegitimateMovement() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine countLine = line(201L, flour, kg, "100.000000", "93.000000");
        countLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse, countLine);
        StockBalance flourBalance = balance(301L, warehouse, flour, kg);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(movement(101L, "-5.000000", frozenAt.plusHours(1))));
        when(ledgerService.record(any(LedgerCommand.class))).thenReturn(transaction(901L, flour,
            InventoryTransactionDirection.OUT, "2.000000", countedAt));
        when(stockBalanceRepository.findByWarehouseAndMaterials(
            TENANT_ID, WAREHOUSE_ID, List.of(101L))).thenReturn(List.of(flourBalance));
        when(countRepository.save(count)).thenReturn(count);

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        ArgumentCaptor<LedgerCommand> command = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledgerService).record(command.capture());
        assertThat(countLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(countLine.getVariance()).isEqualByComparingTo("-2.000000");
        assertThat(command.getValue().getDirection()).isEqualTo(InventoryTransactionDirection.OUT);
        assertThat(command.getValue().getEnteredQuantity()).isEqualByComparingTo("2.000000");
        assertThat(command.getValue().getEnteredUomId()).isEqualTo(kg.getId());
        assertThat(command.getValue().getEnteredUnitCost()).isNull();
        assertThat(command.getValue().getMovementDate()).isEqualTo(countedAt);
    }

    @Test
    void reconcileIgnoresMovementsAfterTheLineWasCounted() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine countLine = line(201L, flour, kg, "100.000000", "93.000000");
        countLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse, countLine);
        StockBalance flourBalance = balance(301L, warehouse, flour, kg);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(
                movement(101L, "-5.000000", frozenAt.plusHours(1)),
                movement(101L, "-10.000000", countedAt.plusHours(1))));
        when(ledgerService.record(any(LedgerCommand.class))).thenReturn(transaction(901L, flour,
            InventoryTransactionDirection.OUT, "2.000000", countedAt));
        when(stockBalanceRepository.findByWarehouseAndMaterials(
            TENANT_ID, WAREHOUSE_ID, List.of(101L))).thenReturn(List.of(flourBalance));
        when(countRepository.save(count)).thenReturn(count);

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(countLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(countLine.getVariance()).isEqualByComparingTo("-2.000000");
        ArgumentCaptor<LedgerCommand> command = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledgerService).record(command.capture());
        assertThat(command.getValue().getEnteredQuantity()).isEqualByComparingTo("2.000000");
    }

    @Test
    void reconcileAppliesEachMaterialsOwnWindowAndMovementDate() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        Material oil = material(102L, "OIL", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 1, 9, 0);
        LocalDateTime flourCountedAt = frozenAt.plusDays(1);
        LocalDateTime oilCountedAt = frozenAt.plusDays(2);
        PhysicalCountLine flourLine = line(201L, flour, kg, "100.000000", "93.000000");
        flourLine.setCountedAt(flourCountedAt);
        PhysicalCountLine oilLine = line(202L, oil, kg, "50.000000", "47.000000");
        oilLine.setCountedAt(oilCountedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 1),
            frozenAt, warehouse, flourLine, oilLine);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L, 102L), frozenAt, oilCountedAt, COUNT_ID))
            .thenReturn(List.of(
                movement(101L, "-5.000000", flourCountedAt),
                movement(101L, "-7.000000", flourCountedAt.plusHours(1)),
                movement(102L, "-1.000000", flourCountedAt),
                movement(102L, "-1.000000", oilCountedAt)));
        when(ledgerService.record(any(LedgerCommand.class))).thenReturn(transaction(901L, flour,
            InventoryTransactionDirection.OUT, "2.000000", flourCountedAt));
        when(stockBalanceRepository.findByWarehouseAndMaterials(
            TENANT_ID, WAREHOUSE_ID, List.of(101L, 102L))).thenReturn(List.of());
        when(countRepository.save(count)).thenReturn(count);

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(flourLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(flourLine.getVariance()).isEqualByComparingTo("-2.000000");
        assertThat(oilLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("48.000000");
        assertThat(oilLine.getVariance()).isEqualByComparingTo("-1.000000");

        ArgumentCaptor<LedgerCommand> commands = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledgerService, times(2)).record(commands.capture());
        assertThat(commands.getAllValues()).extracting(LedgerCommand::getMovementDate)
            .containsExactly(flourCountedAt, oilCountedAt);
    }

    @Test
    void recountRefreshesCountedAtAndExpandsTheMovementWindow() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        LocalDateTime frozenAt = LocalDateTime.of(2020, 1, 1, 9, 0);
        LocalDateTime originalCountedAt = frozenAt.plusHours(1);
        PhysicalCountLine countLine = line(201L, flour, kg, "100.000000", "100.000000");
        countLine.setCountedAt(originalCountedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2020, 1, 1),
            frozenAt, warehouse, countLine);
        UpdateCountedQuantityRequest lineRequest = new UpdateCountedQuantityRequest();
        lineRequest.setLineId(countLine.getId());
        lineRequest.setCountedQuantity(new BigDecimal("95.000000"));
        UpdateCountedQuantitiesRequest request = new UpdateCountedQuantitiesRequest();
        request.setLines(List.of(lineRequest));

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.save(count)).thenReturn(count);

        service.updateCountedQuantities(COUNT_ID, request, TENANT_ID, USER_ID);
        LocalDateTime refreshedCountedAt = countLine.getCountedAt();
        assertThat(refreshedCountedAt).isAfter(originalCountedAt);

        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, refreshedCountedAt, COUNT_ID))
            .thenReturn(List.of(movement(101L, "-5.000000", originalCountedAt.plusHours(1))));

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(countLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(countLine.getVariance()).isEqualByComparingTo("0.000000");
        verifyNoInteractions(ledgerService);
    }

    @Test
    void reconcileRejectsCountedLineWithoutCountTimestampAndNamesMaterial() {
        Uom kg = uom();
        Material flour = material(101L, "FLOUR", kg);
        PhysicalCountLine countLine = line(201L, flour, kg, "100.000000", "95.000000");
        countLine.setCountedAt(null);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            LocalDateTime.of(2026, 7, 3, 10, 0), warehouse(), countLine);
        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));

        assertThatThrownBy(() -> service.reconcile(COUNT_ID, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.VALIDATION_FAILED);
                assertThat(ex.getParams()).containsEntry("field", "countedAt");
                assertThat(ex.getParams()).containsEntry("materialId", flour.getId());
                assertThat(ex.getParams()).containsEntry("materialName", flour.getName());
            });

        verifyNoInteractions(transactionRepository, ledgerService);
    }

    @Test
    void reconcileConvertsOneStockUomNetIntoTheLineDisplayUom() {
        Uom kg = uom(1L, "KG", "kg", "1", null);
        Uom bag = uom(2L, "BAG", "bag", "5", kg);
        Material flour = material(101L, "FLOUR", kg);
        flour.setDisplayUom(bag);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine countLine = line(201L, flour, bag, "20.000000", "19.000000");
        countLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse(), countLine);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(
                movement(101L, "-2.000000", frozenAt.plusMinutes(30)),
                movement(101L, "-3.000000", frozenAt.plusHours(1))));
        when(countRepository.save(count)).thenReturn(count);

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(countLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("19.000000");
        assertThat(countLine.getVariance()).isEqualByComparingTo("0.000000");
        verify(uomConversionService).convert(
            new BigDecimal("-5.000000"), kg, bag, flour, TENANT_ID);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void reconcileSkipsConversionWhenStockUomNetIsZero() {
        Uom kg = uom(1L, "KG", "kg", "1", null);
        Uom each = uom(2L, "EA", "ea", "1", null);
        Material flour = material(101L, "FLOUR", kg);
        flour.setDisplayUom(each);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine countLine = line(201L, flour, each, "20.000000", "20.000000");
        countLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse(), countLine);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(
                movement(101L, "5.000000", frozenAt.plusMinutes(30)),
                movement(101L, "-5.000000", frozenAt.plusHours(1))));
        when(countRepository.save(count)).thenReturn(count);

        service.reconcile(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(countLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("20.000000");
        assertThat(countLine.getVariance()).isEqualByComparingTo("0.000000");
        verify(uomConversionService, never()).convert(any(), any(), any(), any(), any());
    }

    @Test
    void reconcileFailsLoudlyWhenMovementNetCannotConvertToLineUom() {
        Uom kg = uom(1L, "KG", "kg", "1", null);
        Uom each = uom(2L, "EA", "ea", "1", null);
        Material flour = material(101L, "FLOUR", kg);
        flour.setDisplayUom(each);
        LocalDateTime frozenAt = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime countedAt = frozenAt.plusHours(2);
        PhysicalCountLine countLine = line(201L, flour, each, "20.000000", "19.000000");
        countLine.setCountedAt(countedAt);
        PhysicalCount count = count(PhysicalCountStatus.IN_PROGRESS, LocalDate.of(2026, 7, 2),
            frozenAt, warehouse(), countLine);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(transactionRepository.findPhysicalCountMovements(
            TENANT_ID, WAREHOUSE_ID, List.of(101L), frozenAt, countedAt, COUNT_ID))
            .thenReturn(List.of(movement(101L, "-1.000000", frozenAt.plusHours(1))));

        assertThatThrownBy(() -> service.reconcile(COUNT_ID, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.UOM_CONVERSION_FAILED);
                assertThat(ex.getParams()).containsEntry("materialId", flour.getId());
                assertThat(ex.getParams()).containsEntry("materialName", flour.getName());
                assertThat(ex.getParams()).containsEntry("fromUom", "KG");
                assertThat(ex.getParams()).containsEntry("toUom", "EA");
            });

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
        when(warehouseRepository.findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
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
        when(warehouseRepository.findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
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
        when(warehouseRepository.findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
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
        when(warehouseRepository.findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
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
    void startPinsTheCountLineToTheBalancesPersistedUom() {
        Uom kg = uom(1L, "KG", "kg", "1", null);
        Uom bag = uom(2L, "BAG", "bag", "5", kg);
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        flour.setDisplayUom(bag);
        PhysicalCountLine countLine = line(201L, flour, bag, "0.000000", null);
        PhysicalCount count = count(PhysicalCountStatus.DRAFT, LocalDate.of(2026, 7, 4),
            null, warehouse, countLine);
        StockBalance balance = balance(301L, warehouse, flour, kg);
        balance.setQuantity(new BigDecimal("100.000000"));
        balance.setAverageCost(new BigDecimal("5.000000"));

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(warehouseRepository.findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
        when(countRepository.findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, COUNT_ID, List.of(101L)))
            .thenReturn(List.of());
        when(stockBalanceRepository.findByWarehouseAndMaterials(
            TENANT_ID, WAREHOUSE_ID, List.of(101L))).thenReturn(List.of(balance));
        when(countRepository.save(count)).thenReturn(count);

        service.start(COUNT_ID, TENANT_ID, USER_ID);

        assertThat(countLine.getUom()).isSameAs(kg);
        assertThat(countLine.getExpectedQuantity()).isEqualByComparingTo("100.000000");
    }

    @Test
    void startLocksTheWarehouseRowBeforeCheckingFreezeConflicts() {
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
        when(warehouseRepository.findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
        when(countRepository.findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, COUNT_ID, List.of(101L)))
            .thenReturn(List.of());
        when(stockBalanceRepository.findByWarehouseAndMaterials(TENANT_ID, WAREHOUSE_ID, List.of(101L)))
            .thenReturn(List.of(balance));
        when(countRepository.save(count)).thenReturn(count);

        service.start(COUNT_ID, TENANT_ID, USER_ID);

        // The pre-check is check-then-act; it is only race-free because the warehouse row lock
        // is already held when it runs. Pin the ordering, not just the calls.
        InOrder inOrder = inOrder(warehouseRepository, countRepository);
        inOrder.verify(warehouseRepository).findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID);
        inOrder.verify(countRepository).findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, COUNT_ID, List.of(101L));
    }

    @Test
    void overlappingFreezeIsRejectedWithBlockingCountIdAndCappedMaterialNames() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        PhysicalCount count = count(PhysicalCountStatus.DRAFT, LocalDate.of(2026, 7, 4),
            null, warehouse,
            line(201L, material(101L, "FLOUR", kg), kg, "0.000000", null),
            line(202L, material(102L, "SUGAR", kg), kg, "0.000000", null),
            line(203L, material(103L, "OIL", kg), kg, "0.000000", null),
            line(204L, material(104L, "SALT", kg), kg, "0.000000", null),
            line(205L, material(105L, "RICE", kg), kg, "0.000000", null),
            line(206L, material(106L, "MILK", kg), kg, "0.000000", null));
        List<Long> materialIds = List.of(101L, 102L, 103L, 104L, 105L, 106L);

        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(warehouseRepository.findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
        when(countRepository.findFreezeConflicts(TENANT_ID, WAREHOUSE_ID, COUNT_ID, materialIds))
            .thenReturn(List.of(
                conflict(101L, "FLOUR", 42L, "PC-42"),
                conflict(102L, "SUGAR", 42L, "PC-42"),
                conflict(103L, "OIL", 42L, "PC-42"),
                conflict(104L, "SALT", 42L, "PC-42"),
                conflict(105L, "RICE", 42L, "PC-42"),
                conflict(106L, "MILK", 42L, "PC-42")));

        assertThatThrownBy(() -> service.start(COUNT_ID, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.FREEZE_CONFLICT);
                assertThat(ex.getParams()).containsEntry("blockingCountId", 42L);
                // Display string capped at 5 names with a "… +N" tail — same shape as
                // FREEZE_BLOCKED_BY_CONSUMPTION_CONFLICT's materialNames.
                assertThat(ex.getParams())
                    .containsEntry("materialNames", "FLOUR, SUGAR, OIL, SALT, RICE … +1");
                assertThat(ex.getParams().get("conflicts")).asInstanceOf(LIST)
                    .hasSize(6)
                    .contains(Map.of("materialName", "FLOUR", "conflictingCountCode", "PC-42"));
            });

        assertThat(count.getStatus()).isEqualTo(PhysicalCountStatus.DRAFT);
        assertThat(count.getFrozenAt()).isNull();
        verify(stockBalanceRepository, never()).findByWarehouseAndMaterials(any(), any(), any());
    }

    @Test
    void disjointMaterialCountsOnSameWarehouseAndDayBothCreateAndFreeze() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        Material sugar = material(102L, "SUGAR", kg);

        when(warehouseRepository.findByIdAndTenantId(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
        when(materialRepository.findByIdAndTenantId(101L, TENANT_ID))
            .thenReturn(Optional.of(flour));
        when(materialRepository.findByIdAndTenantId(102L, TENANT_ID))
            .thenReturn(Optional.of(sugar));
        when(codeSequenceService.next(TENANT_ID, WAREHOUSE_ID, LocalDate.of(2026, 7, 4)))
            .thenReturn(1, 2);
        List<PhysicalCount> savedCounts = new ArrayList<>();
        AtomicLong ids = new AtomicLong(60L);
        when(countRepository.saveAndFlush(any(PhysicalCount.class))).thenAnswer(inv -> {
            PhysicalCount saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ids.incrementAndGet());
                savedCounts.add(saved);
            }
            return saved;
        });
        when(countRepository.save(any(PhysicalCount.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        // The removed guard blocked exactly this: two counts, same warehouse, same scheduled
        // date. With disjoint materials both must create AND both must freeze.
        service.create(request(LocalDate.of(2026, 7, 4), List.of(101L)), TENANT_ID, USER_ID);
        service.create(request(LocalDate.of(2026, 7, 4), List.of(102L)), TENANT_ID, USER_ID);
        assertThat(savedCounts).hasSize(2);
        assertThat(savedCounts).extracting(PhysicalCount::getCode).containsExactly(
            "PC-MAIN-2026-07-04-0001",
            "PC-MAIN-2026-07-04-0002");

        PhysicalCount first = savedCounts.get(0);
        PhysicalCount second = savedCounts.get(1);
        when(countRepository.findByIdAndTenantId(first.getId(), TENANT_ID))
            .thenReturn(Optional.of(first));
        when(countRepository.findByIdAndTenantId(second.getId(), TENANT_ID))
            .thenReturn(Optional.of(second));
        when(warehouseRepository.findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
        when(countRepository.findFreezeConflicts(eq(TENANT_ID), eq(WAREHOUSE_ID), any(), any()))
            .thenReturn(List.of());
        when(consumptionRepository.findFirstByTenantIdAndWarehouseIdAndStatusInOrderByIdAsc(
            TENANT_ID, WAREHOUSE_ID, UNSETTLED_CONSUMPTION_STATUSES))
            .thenReturn(Optional.empty());
        when(stockBalanceRepository.findByWarehouseAndMaterials(
            eq(TENANT_ID), eq(WAREHOUSE_ID), any()))
            .thenReturn(List.of());

        service.start(first.getId(), TENANT_ID, USER_ID);
        service.start(second.getId(), TENANT_ID, USER_ID);

        assertThat(first.getStatus()).isEqualTo(PhysicalCountStatus.IN_PROGRESS);
        assertThat(second.getStatus()).isEqualTo(PhysicalCountStatus.IN_PROGRESS);
        assertThat(first.getScheduledDate()).isEqualTo(second.getScheduledDate());
    }

    @Test
    void secondCountForSameWarehouseAndScheduledDateIsAllowed() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);

        when(warehouseRepository.findByIdAndTenantId(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
        when(materialRepository.findByIdAndTenantId(101L, TENANT_ID))
            .thenReturn(Optional.of(flour));
        when(codeSequenceService.next(TENANT_ID, WAREHOUSE_ID, LocalDate.of(2026, 7, 4)))
            .thenReturn(1, 2);
        when(countRepository.saveAndFlush(any(PhysicalCount.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.create(request(LocalDate.of(2026, 7, 4), List.of(101L)), TENANT_ID, USER_ID);
        service.create(request(LocalDate.of(2026, 7, 4), List.of(101L)), TENANT_ID, USER_ID);

        // Straight to allocation and insert, twice — creation carries no existence pre-check.
        verify(countRepository, times(2)).saveAndFlush(any(PhysicalCount.class));
        verifyNoMoreInteractions(countRepository);
    }

    @Test
    void duplicateGeneratedCodeConstraintReturnsStructuredInventoryError() {
        Uom kg = uom();
        Warehouse warehouse = warehouse();
        Material flour = material(101L, "FLOUR", kg);
        LocalDate scheduledDate = LocalDate.of(2026, 7, 4);
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
            "duplicate physical count code", new SQLException(), "uk_physical_count_tenant_code");

        when(warehouseRepository.findByIdAndTenantId(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
        when(materialRepository.findByIdAndTenantId(101L, TENANT_ID))
            .thenReturn(Optional.of(flour));
        when(codeSequenceService.next(TENANT_ID, WAREHOUSE_ID, scheduledDate)).thenReturn(1);
        when(countRepository.saveAndFlush(any(PhysicalCount.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate", constraintViolation));

        assertThatThrownBy(() -> service.create(
                request(scheduledDate, List.of(101L)), TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.DUPLICATE_CODE);
                assertThat(ex.getParams())
                    .containsEntry("entityType", "PhysicalCount")
                    .containsEntry("code", "PC-MAIN-2026-07-04-0001");
            });
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

    @Test
    void reconciledCountIsTerminalAndCannotBeUndone() {
        // There is no unpost/reverse/reopen path on PhysicalCountService by design — a count's
        // corrections stay in the ledger. Cancel and delete are the only ways out of a count, and
        // both refuse a RECONCILED one, so this pins the whole exit surface.
        PhysicalCount count = count(PhysicalCountStatus.RECONCILED);
        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));

        assertThatThrownBy(() -> service.cancel(COUNT_ID, "changed my mind", TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION);
                assertThat(ex.getParams()).containsEntry("currentStatus", "RECONCILED");
                assertThat(ex.getParams()).containsEntry("action", "cancel");
            });

        assertThat(count.getStatus()).isEqualTo(PhysicalCountStatus.RECONCILED);
        verify(countRepository, never()).save(any());
        verifyNoInteractions(ledgerService);
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
        when(warehouseRepository.findByIdAndTenantIdForUpdate(WAREHOUSE_ID, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
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

    private PhysicalCountResponse reconcileForVarianceAssertions(PhysicalCountLine... lines) {
        PhysicalCount count = count(
            PhysicalCountStatus.IN_PROGRESS,
            LocalDate.of(2026, 7, 1),
            LocalDateTime.of(2026, 7, 3, 9, 0),
            warehouse(),
            lines);
        when(countRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
            .thenReturn(Optional.of(count));
        when(countRepository.save(count)).thenReturn(count);

        boolean hasAdjustment = List.of(lines).stream().anyMatch(line ->
            line.getCountedQuantity().compareTo(line.getExpectedQuantity()) != 0);
        if (hasAdjustment) {
            AtomicLong transactionId = new AtomicLong(900L);
            when(ledgerService.record(any(LedgerCommand.class))).thenAnswer(invocation -> {
                InventoryTransaction transaction = new InventoryTransaction();
                transaction.setId(transactionId.incrementAndGet());
                return transaction;
            });
        }

        return service.reconcile(COUNT_ID, TENANT_ID, USER_ID);
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
        line.setCountedAt(countedQuantity != null ? LocalDateTime.of(2026, 7, 3, 12, 0) : null);
        line.setUnitCostAtFreeze(new BigDecimal("5.000000"));
        return line;
    }

    private PhysicalCountMovementRow movement(Long materialId, String signedStockQuantity,
                                              LocalDateTime movementDate) {
        return new PhysicalCountMovementRow(
            materialId, new BigDecimal(signedStockQuantity), movementDate);
    }

    private PostFreezeMovementSummary postFreezeSummary(
            Material material, String quantityIn, String quantityOut) {
        return new PostFreezeMovementSummary() {
            @Override
            public Long getMaterialId() {
                return material.getId();
            }

            @Override
            public String getMaterialCode() {
                return material.getCode();
            }

            @Override
            public String getMaterialName() {
                return material.getName();
            }

            @Override
            public String getMaterialNameAr() {
                return material.getNameAr();
            }

            @Override
            public Long getMovementCount() {
                return 1L;
            }

            @Override
            public BigDecimal getQuantityIn() {
                return new BigDecimal(quantityIn);
            }

            @Override
            public BigDecimal getQuantityOut() {
                return new BigDecimal(quantityOut);
            }
        };
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
        return uom(1L, "KG", "kg", "1", null);
    }

    private Uom uom(Long id, String code, String symbol, String factorToBase, Uom baseUom) {
        Uom uom = new Uom();
        uom.setId(id);
        uom.setCode(code);
        uom.setSymbol(symbol);
        uom.setFactorToBase(new BigDecimal(factorToBase));
        uom.setBaseUom(baseUom);
        return uom;
    }

    private PhysicalCountRequest request(LocalDate scheduledDate, List<Long> materialIds) {
        PhysicalCountRequest request = new PhysicalCountRequest();
        request.setWarehouseId(WAREHOUSE_ID);
        request.setScheduledDate(scheduledDate);
        request.setMaterialIds(materialIds);
        return request;
    }

    private MaterialConflictProjection conflict(Long materialId, String materialName,
                                                Long countId, String countCode) {
        return new MaterialConflictProjection() {
            @Override public Long getMaterialId() { return materialId; }
            @Override public String getMaterialName() { return materialName; }
            @Override public Long getCountId() { return countId; }
            @Override public String getCountCode() { return countCode; }
        };
    }
}
