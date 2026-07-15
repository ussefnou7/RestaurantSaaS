package com.smart.restaurant_saas.inventory.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.core.enums.StockBatchStatus;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.repository.OpenBatchTotals;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code averageCost} is derived from the open batches (never an incremental
 * running formula) — the fix for the drift confirmed in the investigation.
 */
class StockBalanceServiceTest {

    private StockBalanceService newBalanceService(StockBalanceRepository balanceRepo,
                                                  StockBatchRepository batchRepo) {
        // Only the balance + batch repositories participate in recalculateFromOpenBatches;
        // the remaining collaborators are unused by these tests.
        return new StockBalanceService(balanceRepo, null, null, null, null, batchRepo, null, null, null);
    }

    private StockBalance balance(long id) {
        StockBalance b = new StockBalance();
        b.setId(id);
        b.setQuantity(new BigDecimal("3.000000"));
        b.setAverageCost(new BigDecimal("7.076925")); // the previously-buggy drifted value
        return b;
    }

    @Test
    void investigationScenarioDerivesTenNotDriftedValue() {
        // End state of the investigation: the only open batch is 3 kg @ 10.
        StockBalanceRepository balanceRepo = mock(StockBalanceRepository.class);
        StockBatchRepository batchRepo = mock(StockBatchRepository.class);
        when(batchRepo.sumOpenBatchTotals(1L))
            .thenReturn(new OpenBatchTotals(new BigDecimal("3.000000"), new BigDecimal("30.000000")));
        StockBalance balance = balance(1L);

        newBalanceService(balanceRepo, batchRepo).recalculateFromOpenBatches(balance);

        assertThat(balance.getAverageCost()).isEqualByComparingTo("10.000000");
        assertThat(balance.getAverageCost()).isNotEqualByComparingTo("7.076925");
        verify(balanceRepo).save(balance);
    }

    @Test
    void freshPurchaseSingleBatchSetsThatBatchesUnitCost() {
        StockBalanceRepository balanceRepo = mock(StockBalanceRepository.class);
        StockBatchRepository batchRepo = mock(StockBatchRepository.class);
        // Fresh balance, single purchase batch of 10 @ 5.
        when(batchRepo.sumOpenBatchTotals(1L))
            .thenReturn(new OpenBatchTotals(new BigDecimal("10.000000"), new BigDecimal("50.000000")));
        StockBalance balance = balance(1L);

        newBalanceService(balanceRepo, batchRepo).recalculateFromOpenBatches(balance);

        assertThat(balance.getAverageCost()).isEqualByComparingTo("5.000000");
    }

    @Test
    void singleBatchPartialWasteLeavesUnitCostUnchanged() {
        StockBalanceRepository balanceRepo = mock(StockBalanceRepository.class);
        StockBatchRepository batchRepo = mock(StockBatchRepository.class);
        // One batch @ 5, partially wasted: 8 remaining. Average of the remainder is still 5.
        when(batchRepo.sumOpenBatchTotals(1L))
            .thenReturn(new OpenBatchTotals(new BigDecimal("8.000000"), new BigDecimal("40.000000")));
        StockBalance balance = balance(1L);

        newBalanceService(balanceRepo, batchRepo).recalculateFromOpenBatches(balance);

        assertThat(balance.getAverageCost()).isEqualByComparingTo("5.000000");
    }

    @Test
    void zeroStockCarriesLastKnownAverageForward() {
        StockBalanceRepository balanceRepo = mock(StockBalanceRepository.class);
        StockBatchRepository batchRepo = mock(StockBatchRepository.class);
        // No open batches → SUM over zero rows is null.
        when(batchRepo.sumOpenBatchTotals(1L)).thenReturn(new OpenBatchTotals(null, null));
        StockBalance balance = balance(1L);

        newBalanceService(balanceRepo, batchRepo).recalculateFromOpenBatches(balance);

        // Last known average is left untouched; nothing persisted.
        assertThat(balance.getAverageCost()).isEqualByComparingTo("7.076925");
        verify(balanceRepo, never()).save(any());
    }

    /**
     * The load-bearing case: waste FIFO-consumes part of the cheaper (oldest) batch, and the
     * average shifts purely as a consequence of deriving it from the remaining batches — no
     * special "cost-bearing" rule for waste. Composition after waste: batch1 2 @ 8, batch2 5 @ 12
     * → (2*8 + 5*12) / 7 = 76 / 7 = 10.857143.
     */
    @Test
    void multiBatchWasteShiftsDerivedAverageViaFifo() {
        StockBatchRepository batchRepo = mock(StockBatchRepository.class);
        StockBalanceRepository balanceRepo = mock(StockBalanceRepository.class);
        UomConversionService uomConversion = mock(UomConversionService.class);
        // Identity conversion (same UOM in this test): return the quantity argument unchanged.
        when(uomConversion.convert(any(), any(), any(), any(), any()))
            .thenAnswer(inv -> inv.getArgument(0));

        Uom kg = new Uom();
        kg.setId(3L);
        Material material = new Material();
        material.setId(2L);
        material.setStockUom(kg);

        StockBalance balance = new StockBalance();
        balance.setId(1L);
        balance.setUom(kg);
        balance.setQuantity(new BigDecimal("10.000000"));
        balance.setAverageCost(new BigDecimal("10.000000"));

        StockBatch cheaperOldest = openBatch(1L, balance, "5.000000", "8.000000");
        StockBatch dearerNewest = openBatch(2L, balance, "5.000000", "12.000000");
        List<StockBatch> openBatches = new ArrayList<>(List.of(cheaperOldest, dearerNewest));

        when(batchRepo.findByStockBalanceIdAndStatusOrderByIdAsc(1L, StockBatchStatus.OPEN))
            .thenReturn(openBatches);
        when(batchRepo.save(any(StockBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        // sumOpenBatchTotals reflects the live batch state after FIFO depletion.
        when(batchRepo.sumOpenBatchTotals(1L)).thenAnswer(inv -> aggregate(openBatches));
        when(balanceRepo.save(any(StockBalance.class))).thenAnswer(inv -> inv.getArgument(0));

        StockBatchService batchService = new StockBatchService(batchRepo, uomConversion);
        StockBalanceService balanceService = newBalanceServiceWithConversion(balanceRepo, batchRepo);

        InventoryTransaction waste = new InventoryTransaction();
        waste.setTenantId(7L);
        waste.setMaterial(material);
        waste.setStockUom(kg);
        waste.setStockQuantity(new BigDecimal("3.000000")); // waste 3 from the cheaper batch
        waste.setTransactionType(InventoryTransactionType.WASTE);
        waste.setDirection(InventoryTransactionDirection.OUT);

        batchService.consumeFifo(waste, balance);
        balanceService.recalculateFromOpenBatches(balance);

        assertThat(cheaperOldest.getRemainingQuantity()).isEqualByComparingTo("2.000000");
        assertThat(dearerNewest.getRemainingQuantity()).isEqualByComparingTo("5.000000");
        assertThat(balance.getAverageCost()).isEqualByComparingTo("10.857143");
    }

    private StockBalanceService newBalanceServiceWithConversion(StockBalanceRepository balanceRepo,
                                                                StockBatchRepository batchRepo) {
        return new StockBalanceService(balanceRepo, null, null, null, null, batchRepo, null, null, null);
    }

    private StockBatch openBatch(long id, StockBalance balance, String remaining, String unitCost) {
        StockBatch b = new StockBatch();
        b.setId(id);
        b.setStockBalance(balance);
        b.setOriginalQuantity(new BigDecimal(remaining));
        b.setRemainingQuantity(new BigDecimal(remaining));
        b.setUnitCost(new BigDecimal(unitCost));
        b.setStatus(StockBatchStatus.OPEN);
        return b;
    }

    private OpenBatchTotals aggregate(List<StockBatch> batches) {
        BigDecimal remaining = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        for (StockBatch b : batches) {
            if (b.getStatus() == StockBatchStatus.OPEN
                    && b.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                remaining = remaining.add(b.getRemainingQuantity());
                BigDecimal unit = b.getUnitCost() != null ? b.getUnitCost() : BigDecimal.ZERO;
                value = value.add(b.getRemainingQuantity().multiply(unit));
            }
        }
        return new OpenBatchTotals(remaining, value);
    }
}
