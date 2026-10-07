package com.smart.restaurant_saas.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One branch's figures for the range.
 *
 * <p><b>Numbers only — no score, no rank, no colour.</b> A composite branch score was designed and
 * then parked, and the reason is worth carrying next to the thing that replaced it: one number is
 * easy to read and easy to fool. The failure that killed it is that a branch which never takes a
 * physical count reports zero shrinkage and therefore scores perfectly on losses — the less a
 * branch records, the better it looks. A plain breakdown cannot be gamed that way because it makes
 * no claim to rank.
 *
 * <p>Both names travel so the frontend renders by locale without a second call.
 */
@Getter
@Builder
public class BranchKpiRow {

    private final Long branchId;
    private final String branchName;
    private final String branchNameAr;

    private final Long orderCount;
    private final String netSales;
    private final String taxAmount;
    private final String totalAmount;

    /** Null when the branch had no orders, which cannot happen for a row that exists. */
    private final String averageOrderValue;

    /** This branch's share of the range's net sales, so the rows read without mental arithmetic. */
    private final String netSalesSharePercent;
}
