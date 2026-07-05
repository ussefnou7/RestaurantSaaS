package com.smart.restaurant_saas.inventory.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.inventory.repository.OpenBatchTotals;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockBalanceAverageCostBackfillTest {

    private StockBalance balance(long id, String quantity, String averageCost) {
        StockBalance b = new StockBalance();
        b.setId(id);
        b.setQuantity(new BigDecimal(quantity));
        b.setAverageCost(new BigDecimal(averageCost));
        return b;
    }

    @Test
    void correctsAverageWhenStoredQuantityMatchesBatches() {
        StockBalanceRepository balanceRepo = mock(StockBalanceRepository.class);
        StockBatchRepository batchRepo = mock(StockBatchRepository.class);

        // Stored quantity 3 matches sum(remaining) 3; drifted average 7.076925 → derived 10.
        StockBalance drifted = balance(1L, "3.000000", "7.076925");
        when(balanceRepo.findAll()).thenReturn(List.of(drifted));
        when(batchRepo.sumOpenBatchTotals(1L))
            .thenReturn(new OpenBatchTotals(new BigDecimal("3.000000"), new BigDecimal("30.000000")));

        AverageCostBackfillReport report =
            new StockBalanceAverageCostBackfill(balanceRepo, batchRepo).backfill();

        assertThat(drifted.getAverageCost()).isEqualByComparingTo("10.000000");
        assertThat(report.getChecked()).isEqualTo(1);
        assertThat(report.getCorrectedCount()).isEqualTo(1);
        assertThat(report.getFlaggedCount()).isEqualTo(0);
        assertThat(report.getCorrected().get(0).newAverageCost()).isEqualByComparingTo("10.000000");
        verify(balanceRepo).save(drifted);
    }

    @Test
    void flagsAndDoesNotOverwriteWhenQuantityMismatches() {
        StockBalanceRepository balanceRepo = mock(StockBalanceRepository.class);
        StockBatchRepository batchRepo = mock(StockBatchRepository.class);

        // Corrupted row: stored quantity 5 but open batches only sum to 3.
        StockBalance mismatched = balance(2L, "5.000000", "9.999999");
        when(balanceRepo.findAll()).thenReturn(List.of(mismatched));
        when(batchRepo.sumOpenBatchTotals(2L))
            .thenReturn(new OpenBatchTotals(new BigDecimal("3.000000"), new BigDecimal("30.000000")));

        AverageCostBackfillReport report =
            new StockBalanceAverageCostBackfill(balanceRepo, batchRepo).backfill();

        // Neither field overwritten.
        assertThat(mismatched.getAverageCost()).isEqualByComparingTo("9.999999");
        assertThat(mismatched.getQuantity()).isEqualByComparingTo("5.000000");
        assertThat(report.getCorrectedCount()).isEqualTo(0);
        assertThat(report.getFlaggedCount()).isEqualTo(1);
        AverageCostBackfillReport.Flagged flagged = report.getFlagged().get(0);
        assertThat(flagged.balanceId()).isEqualTo(2L);
        assertThat(flagged.storedQuantity()).isEqualByComparingTo("5.000000");
        assertThat(flagged.batchRemaining()).isEqualByComparingTo("3.000000");
        verify(balanceRepo, never()).save(any());
    }

    @Test
    void leavesZeroStockRowUnchanged() {
        StockBalanceRepository balanceRepo = mock(StockBalanceRepository.class);
        StockBatchRepository batchRepo = mock(StockBatchRepository.class);

        // Empty balance (quantity 0, no open batches) — carry last known average forward.
        StockBalance empty = balance(3L, "0.000000", "4.500000");
        when(balanceRepo.findAll()).thenReturn(List.of(empty));
        when(batchRepo.sumOpenBatchTotals(3L)).thenReturn(new OpenBatchTotals(null, null));

        AverageCostBackfillReport report =
            new StockBalanceAverageCostBackfill(balanceRepo, batchRepo).backfill();

        assertThat(empty.getAverageCost()).isEqualByComparingTo("4.500000");
        assertThat(report.getChecked()).isEqualTo(1);
        assertThat(report.getCorrectedCount()).isEqualTo(0);
        assertThat(report.getFlaggedCount()).isEqualTo(0);
        verify(balanceRepo, never()).save(any());
    }
}
