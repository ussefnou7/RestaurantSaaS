package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.TestZones;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.IdempotencyScope;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InventoryLedgerServiceTest {

    private InventoryTransactionRepository transactionRepository;
    private IdempotencyService idempotencyService;
    private StockBalanceService stockBalanceService;
    private StockBatchService stockBatchService;
    private InventoryLedgerService ledgerService;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(InventoryTransactionRepository.class);
        idempotencyService = mock(IdempotencyService.class);
        stockBalanceService = mock(StockBalanceService.class);
        stockBatchService = mock(StockBatchService.class);
        ledgerService = new InventoryLedgerService(
            transactionRepository,
            null,
            null,
            null,
            null,
            idempotencyService,
            stockBalanceService,
            stockBatchService,
            TestZones.cairo()
        );
    }

    @Test
    void reverseReturnsExistingReversalForSameIdempotencyKeyBeforeAlreadyReversedGuard() {
        InventoryTransaction original = new InventoryTransaction();
        original.setId(10L);
        original.setTenantId(7L);

        InventoryTransaction existingReversal = new InventoryTransaction();
        existingReversal.setId(11L);
        existingReversal.setTenantId(7L);
        existingReversal.setReversesTransactionId(10L);

        when(transactionRepository.findById(10L)).thenReturn(Optional.of(original));
        when(idempotencyService.findExistingId(
            7L, IdempotencyScope.INVENTORY_TRANSACTION, "UNPOST-1-10"))
            .thenReturn(Optional.of(11L));
        when(transactionRepository.findById(11L)).thenReturn(Optional.of(existingReversal));

        InventoryTransaction result = ledgerService.reverse(10L, "ENTRY_ERROR", "UNPOST-1-10", 99L);

        assertThat(result).isSameAs(existingReversal);
        verify(transactionRepository, never()).findReversalOf(10L);
    }

    @Test
    void reverseWritesOppositeDirectionLinkedToOriginalAndAppliesBalance() {
        InventoryTransaction original = originalPurchaseIn();
        StockBalance balance = new StockBalance();

        when(transactionRepository.findById(10L)).thenReturn(Optional.of(original));
        when(idempotencyService.findExistingId(
            7L, IdempotencyScope.INVENTORY_TRANSACTION, "UNPOST-1-10"))
            .thenReturn(Optional.empty());
        when(transactionRepository.findReversalOf(10L)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(InventoryTransaction.class)))
            .thenAnswer(invocation -> {
                InventoryTransaction tx = invocation.getArgument(0);
                tx.setId(11L);
                return tx;
            });
        when(stockBalanceService.resolveBalance(any(InventoryTransaction.class))).thenReturn(balance);

        InventoryTransaction reversal =
            ledgerService.reverse(10L, "ENTRY_ERROR", "UNPOST-1-10", 99L);

        ArgumentCaptor<InventoryTransaction> savedTx =
            ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(savedTx.capture());
        verify(stockBalanceService).resolveBalance(savedTx.getValue());
        verify(stockBatchService).reverseSourceBatchIfOpened(savedTx.getValue());
        verify(stockBatchService).createBatchFromInbound(savedTx.getValue(), balance);
        verify(stockBatchService).consumeFifo(savedTx.getValue(), balance);
        verify(stockBalanceService).applyMovement(balance, savedTx.getValue());

        assertThat(reversal).isSameAs(savedTx.getValue());
        assertThat(savedTx.getValue().getDirection()).isEqualTo(InventoryTransactionDirection.OUT);
        assertThat(savedTx.getValue().getStockQuantity()).isEqualByComparingTo("10.000000");
        assertThat(savedTx.getValue().getReferenceType()).isEqualTo("PURCHASE_INVOICE");
        assertThat(savedTx.getValue().getReferenceId()).isEqualTo(1L);
        assertThat(savedTx.getValue().getReversesTransactionId()).isEqualTo(10L);
        assertThat(savedTx.getValue().getReasonCode()).isEqualTo("ENTRY_ERROR");
        assertThat(savedTx.getValue().getIdempotencyKey()).isEqualTo("UNPOST-1-10");
        assertThat(savedTx.getValue().getCreatedBy()).isEqualTo(99L);
    }

    private InventoryTransaction originalPurchaseIn() {
        Uom uom = new Uom();
        uom.setId(3L);
        uom.setCode("KG");

        Material material = new Material();
        material.setId(2L);
        material.setStockUom(uom);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(4L);

        InventoryTransaction original = new InventoryTransaction();
        original.setId(10L);
        original.setTenantId(7L);
        original.setWarehouse(warehouse);
        original.setMaterial(material);
        original.setTransactionType(InventoryTransactionType.PURCHASE);
        original.setDirection(InventoryTransactionDirection.IN);
        original.setEnteredQuantity(new BigDecimal("10.000000"));
        original.setEnteredUom(uom);
        original.setStockQuantity(new BigDecimal("10.000000"));
        original.setStockUom(uom);
        original.setUnitCost(new BigDecimal("5.000000"));
        original.setTotalCost(new BigDecimal("50.000000"));
        original.setReferenceType("PURCHASE_INVOICE");
        original.setReferenceId(1L);
        return original;
    }
}
