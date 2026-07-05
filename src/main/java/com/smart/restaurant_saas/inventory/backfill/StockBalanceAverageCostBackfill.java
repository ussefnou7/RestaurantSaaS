package com.smart.restaurant_saas.inventory.backfill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.repository.OpenBatchTotals;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;

/**
 * One-off backfill that corrects historical {@code stock_balance.average_cost} rows drifted by
 * the removed incremental running formula, re-deriving each from the balance's current OPEN
 * batches — the sole source of truth.
 *
 * <p>Safety: it never blindly overwrites. Before touching a row it compares the stored
 * {@code quantity} against the sum of its open batches' remaining quantity:
 * <ul>
 *   <li><b>Match</b> — recompute {@code averageCost} from the batches and overwrite it (logging
 *       old vs new). The stored quantity is left as-is; only the average is corrected.</li>
 *   <li><b>Mismatch</b> — leave both fields untouched and flag the row for manual review. A
 *       quantity mismatch signals a deeper data issue (a missed batch mutation or an orphaned
 *       transaction) that a cost-formula backfill must not paper over.</li>
 * </ul>
 *
 * <p>Zero-stock rows (no open batches) whose quantity matches (also zero) are left unchanged —
 * the last known average is carried forward, matching the live recalculation's default.
 *
 * <p>Not run automatically; invoked by {@link AverageCostBackfillRunner} only when
 * {@code inventory.backfill.average-cost.enabled=true}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockBalanceAverageCostBackfill {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final StockBalanceRepository stockBalanceRepository;
    private final StockBatchRepository stockBatchRepository;

    @Transactional
    public AverageCostBackfillReport backfill() {
        AverageCostBackfillReport report = new AverageCostBackfillReport();
        List<StockBalance> balances = stockBalanceRepository.findAll();

        for (StockBalance balance : balances) {
            report.incrementChecked();

            OpenBatchTotals totals = stockBatchRepository.sumOpenBatchTotals(balance.getId());
            BigDecimal totalRemaining = totals != null && totals.getTotalRemaining() != null
                ? totals.getTotalRemaining()
                : BigDecimal.ZERO;
            BigDecimal totalValue = totals != null && totals.getTotalValue() != null
                ? totals.getTotalValue()
                : BigDecimal.ZERO;

            BigDecimal storedQuantity = balance.getQuantity() != null
                ? balance.getQuantity()
                : BigDecimal.ZERO;

            // Quantity-mismatch guard (mandatory): compareTo ignores trailing-zero scale.
            if (storedQuantity.compareTo(totalRemaining) != 0) {
                report.addFlagged(balance.getId(), storedQuantity, totalRemaining);
                continue;
            }

            // Quantities agree. With no open stock there is nothing to derive — carry the last
            // known average forward (do not reset to zero).
            if (totalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal newAverage = totalValue.divide(totalRemaining, SCALE, ROUNDING);
            BigDecimal oldAverage = balance.getAverageCost();
            if (oldAverage == null || oldAverage.compareTo(newAverage) != 0) {
                balance.setAverageCost(newAverage);
                stockBalanceRepository.save(balance);
                report.addCorrected(balance.getId(), oldAverage, newAverage);
            }
        }

        report.log(log);
        return report;
    }
}
