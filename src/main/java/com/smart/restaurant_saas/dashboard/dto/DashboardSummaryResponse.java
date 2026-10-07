package com.smart.restaurant_saas.dashboard.dto;

import com.smart.restaurant_saas.dashboard.TrendBucket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * One request's worth of dashboard, pre-aggregated.
 *
 * <p><b>One endpoint rather than the frontend calling ten report endpoints.</b> That alternative
 * was rejected for four separate reasons, and the third is the one that would have bitten
 * silently: eight to ten round trips on a screen opened from a phone every morning; reports that
 * return rows with no totals, so the client would re-sum them and own a second definition of net
 * sales; report visibility that is granted per user per report, so hiding one report from someone
 * would blank a dashboard block with no explanation; and a report free to change its columns for
 * its own reasons, breaking a consumer it does not know it has.
 *
 * <p><b>Every block carries its own status.</b> The dashboard reads across every module, so one
 * caller can legitimately be allowed the sales blocks and denied the cash ones. A block says
 * whether it was computed, withheld, or had nothing to compute from, so the screen never has to
 * infer which — see {@link BlockStatus}.
 *
 * <p>Alerts are not here. They describe <em>now</em> and are unaffected by the range, so they have
 * their own endpoint; folding them in would mean re-evaluating seventeen conditions every time the
 * owner switched from this month to this year, and returning identical results each time.
 */
@Getter
@Builder
public class DashboardSummaryResponse {

    /** Echoed back so the client can confirm what it is looking at after a race or a retry. */
    private final LocalDate from;
    private final LocalDate to;

    /** Null means every branch the caller may see, not "no branch". */
    private final Long branchId;

    /** The grain the trend was computed at — derived from the range, never requested. */
    private final TrendBucket bucket;

    /**
     * The tenant-local wall clock this was computed at (D101).
     *
     * <p>Needed because the today range ends at "now" and the stock KPI is a snapshot rather than
     * a range figure. Without it the client would date both from the browser's clock, in whatever
     * zone the browser happens to sit.
     */
    private final LocalDateTime generatedAt;

    private final DashboardBlock<DashboardKpis> kpis;

    private final DashboardBlock<SalesTrend> trend;

    private final DashboardBlock<List<BranchKpiRow>> branches;

    /** Dine-in / takeaway / delivery. A shift toward delivery changes margin. */
    private final DashboardBlock<List<MixRow>> orderTypes;

    /** Cash / card / wallet / aggregator — the reconciliation view. */
    private final DashboardBlock<List<MixRow>> paymentMethods;

    /**
     * Cancellations by stage, never by hour.
     *
     * <p>The cancellation timestamp is not reliable, so an hourly view of this data would be
     * confidently wrong. The stage is recorded correctly and carries the finding anyway: a
     * cancellation before the kitchen costs a till correction, one after the food was cooked is
     * pure loss.
     */
    private final DashboardBlock<List<MixRow>> cancellations;

    private final DashboardBlock<CostCoverage> costCoverage;

    private final DashboardBlock<List<ConsumptionPipelineRow>> consumptionPipeline;
}
