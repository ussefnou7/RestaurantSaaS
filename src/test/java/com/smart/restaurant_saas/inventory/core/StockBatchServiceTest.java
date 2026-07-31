package com.smart.restaurant_saas.inventory.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.core.enums.StockBatchStatus;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StockBatchServiceTest {

    /**
     * Invariant guard for the ledger save-sequence refactor: a FIFO shortfall values the unmatched
     * remainder at the balance's average cost AS PASSED IN. In the ledger flow consumeFifo runs
     * before applyMovement re-derives the average, so the value it reads here is the PRE-movement
     * average — this test pins that the shortfall remainder is priced at exactly that value.
     *
     * Batch has 2 @ 8; consume 5 with a pre-movement average of 10:
     *   matched  = 2 * 8  = 16
     *   shortfall= 3 * 10 = 30   (remainder valued at the balance's average)
     *   total    = 46
     */
    @Test
    void consumeFifoValuesShortfallRemainderAtBalanceAverageCost() {
        StockBatchRepository repository = mock(StockBatchRepository.class);
        UomConversionService uomConversion = mock(UomConversionService.class);
        // Identity conversion (same UOM here): return the quantity argument unchanged.
        when(uomConversion.convert(any(), any(), any(), any(), any()))
            .thenAnswer(inv -> inv.getArgument(0));
        StockBatchService service = new StockBatchService(repository, uomConversion);

        Uom kg = new Uom();
        kg.setId(3L);
        Material material = new Material();
        material.setId(2L);
        material.setStockUom(kg);

        StockBalance balance = new StockBalance();
        balance.setId(1L);
        balance.setUom(kg);
        // The pre-movement average the shortfall remainder must be valued at.
        balance.setAverageCost(new BigDecimal("10.000000"));

        StockBatch only = new StockBatch();
        only.setId(1L);
        only.setStockBalance(balance);
        only.setOriginalQuantity(new BigDecimal("2.000000"));
        only.setRemainingQuantity(new BigDecimal("2.000000"));
        only.setUnitCost(new BigDecimal("8.000000"));
        only.setStatus(StockBatchStatus.OPEN);

        when(repository.findByStockBalanceIdAndStatusOrderByMovementDateAscIdAsc(
            1L, StockBatchStatus.OPEN))
            .thenReturn(new java.util.ArrayList<>(List.of(only)));
        when(repository.save(any(StockBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryTransaction waste = new InventoryTransaction();
        waste.setTenantId(7L);
        waste.setMaterial(material);
        waste.setStockUom(kg);
        waste.setStockQuantity(new BigDecimal("5.000000")); // exceeds the 2 available → shortfall of 3
        waste.setTransactionType(InventoryTransactionType.WASTE);
        waste.setDirection(InventoryTransactionDirection.OUT);

        BigDecimal costOfIssue = service.consumeFifo(waste, balance);

        assertThat(costOfIssue).isEqualByComparingTo("46.000000");
        assertThat(only.getRemainingQuantity()).isEqualByComparingTo("0.000000");
        assertThat(only.getStatus()).isEqualTo(StockBatchStatus.CLOSED);
        // consumeFifo must not mutate the balance's average — that is applyMovement's job, later.
        assertThat(balance.getAverageCost()).isEqualByComparingTo("10.000000");
    }

    @Test
    void restoreSourceBatchIsCommutativeForMultipleReturnsOnSameBatch() {
        StockBatchRepository repository = mock(StockBatchRepository.class);
        StockBatchService service = new StockBatchService(repository, null);
        StockBatch batch = batch("4.000000");

        when(repository.findByStockBalanceIdAndSourceInvoiceLineId(44L, 31L))
            .thenReturn(Optional.of(batch));

        service.restoreSourceBatch(44L, 31L, new BigDecimal("2.000000"), 99L);
        service.restoreSourceBatch(44L, 31L, new BigDecimal("3.000000"), 99L);

        assertThat(batch.getRemainingQuantity()).isEqualByComparingTo("9.000000");

        batch.setRemainingQuantity(new BigDecimal("4.000000"));

        service.restoreSourceBatch(44L, 31L, new BigDecimal("3.000000"), 99L);
        service.restoreSourceBatch(44L, 31L, new BigDecimal("2.000000"), 99L);

        assertThat(batch.getRemainingQuantity()).isEqualByComparingTo("9.000000");
        assertThat(batch.getStatus()).isEqualTo(StockBatchStatus.OPEN);
        assertThat(batch.getUpdatedBy()).isEqualTo(99L);
        verify(repository, org.mockito.Mockito.times(4)).save(batch);
    }

    private StockBatch batch(String remainingQuantity) {
        Material material = new Material();
        material.setId(101L);
        material.setName("Flour");

        StockBalance balance = new StockBalance();
        balance.setId(44L);
        balance.setMaterial(material);

        StockBatch batch = new StockBatch();
        batch.setId(88L);
        batch.setStockBalance(balance);
        batch.setOriginalQuantity(new BigDecimal("10.000000"));
        batch.setRemainingQuantity(new BigDecimal(remainingQuantity));
        batch.setStatus(StockBatchStatus.CLOSED);
        return batch;
    }
}
