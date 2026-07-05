package com.smart.restaurant_saas.inventory.backfill;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * Outcome of an {@link StockBalanceAverageCostBackfill} run: how many balances were checked,
 * which had their {@code averageCost} corrected (with old vs new for audit), and which were
 * flagged for manual review because their stored quantity did not match the sum of their open
 * batches' remaining quantity (a discrepancy the backfill refuses to auto-correct).
 */
public class AverageCostBackfillReport {

    /** A balance whose average cost was safely recomputed from its batches. */
    public record Corrected(Long balanceId, BigDecimal oldAverageCost, BigDecimal newAverageCost) {}

    /** A balance skipped because stored quantity != sum(open batch remaining). */
    public record Flagged(Long balanceId, BigDecimal storedQuantity, BigDecimal batchRemaining) {}

    private int checked;
    private final List<Corrected> corrected = new ArrayList<>();
    private final List<Flagged> flagged = new ArrayList<>();

    void incrementChecked() {
        checked++;
    }

    void addCorrected(Long balanceId, BigDecimal oldAverageCost, BigDecimal newAverageCost) {
        corrected.add(new Corrected(balanceId, oldAverageCost, newAverageCost));
    }

    void addFlagged(Long balanceId, BigDecimal storedQuantity, BigDecimal batchRemaining) {
        flagged.add(new Flagged(balanceId, storedQuantity, batchRemaining));
    }

    public int getChecked() {
        return checked;
    }

    public int getCorrectedCount() {
        return corrected.size();
    }

    public int getFlaggedCount() {
        return flagged.size();
    }

    public List<Corrected> getCorrected() {
        return List.copyOf(corrected);
    }

    public List<Flagged> getFlagged() {
        return List.copyOf(flagged);
    }

    /** Emits the summary and every corrected/flagged row so the run leaves an audit trail. */
    public void log(Logger log) {
        log.info("Average-cost backfill complete: checked={} corrected={} flaggedForReview={}",
            checked, corrected.size(), flagged.size());
        for (Corrected c : corrected) {
            log.info("  corrected balance={} averageCost {} -> {}",
                c.balanceId(), c.oldAverageCost(), c.newAverageCost());
        }
        for (Flagged f : flagged) {
            log.warn("  FLAGGED balance={} needs manual review: storedQuantity={} but"
                    + " openBatchRemaining={} (quantity mismatch — not auto-corrected)",
                f.balanceId(), f.storedQuantity(), f.batchRemaining());
        }
    }
}
