package com.smart.restaurant_saas.inventory.reports;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projection returned by the purchase price drift query in {@code StockBatchRepository}: one row per
 * material that was purchased at least once in the window.
 *
 * <p><b>These prices need no UOM conversion.</b> {@code StockBatch.unitCost} is already stored per
 * the material's display UOM (D87 layer 2), unlike the ledger-sourced reports which read stock UOM
 * and must convert. The values pass through to the API untouched; {@code uomId}/{@code uomSymbol}
 * are carried so the unit is explicit (D88's intent), not because anything was converted.
 *
 * <p>{@code firstPrice}/{@code lastPrice} are resolved by batch {@code id}, not by date — see the
 * query javadoc. {@code changePercent} is computed in SQL, so the value that is sorted on and the
 * value that is rendered are the same expression; it is null when {@code firstPrice} is zero or
 * absent, which is why the sort is {@code NULLS LAST}.
 */
public interface PurchasePriceDriftAggregate {

    Long getMaterialId();

    String getMaterialCode();

    String getMaterialName();

    String getMaterialNameAr();

    Boolean getMaterialActive();

    Long getUomId();

    String getUomSymbol();

    BigDecimal getFirstPrice();

    LocalDateTime getFirstPurchaseDate();

    BigDecimal getLastPrice();

    LocalDateTime getLastPurchaseDate();

    BigDecimal getPriceChange();

    /** Null when {@code firstPrice} is zero or absent — never infinity, never a fabricated zero. */
    BigDecimal getChangePercent();

    Long getPurchaseCount();
}
