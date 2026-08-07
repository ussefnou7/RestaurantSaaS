package com.smart.restaurant_saas.inventory.reports.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * One row of the purchase price drift report: what a material cost at the start of the window
 * versus the end, and the movement between them.
 *
 * <p>In an inflationary market this is what drives menu re-pricing, supplier negotiation, and
 * supplier replacement. A material that has risen 37% while its dish is still priced against the old
 * cost is eating margin silently, and nothing else in the system surfaces it.
 *
 * <p><b>Prices are NOT UOM-converted — this is the difference from every other report here.</b>
 * The shrinkage, waste and comparison reports read the stock-UOM ledger and must convert to the
 * display layer (D88). This one reads {@code stock_batch}, whose {@code unitCost} is already per
 * display UOM (D87 layer 2), so the values pass through untouched. {@link #uomId} /
 * {@link #uomSymbol} state the unit the price is per; they are not evidence of a conversion, and
 * there is deliberately no degraded/null-quantity case here because nothing can fail to convert.
 *
 * <p><b>First and last are resolved by batch insertion order, not by date</b> — two purchases on the
 * same day carry the same {@code movementDate}, so only the id can separate them, and FIFO already
 * breaks that tie the same way. The dates shown are the real purchase dates.
 *
 * <p><b>{@link #purchaseCount} is not decoration.</b> +37.5% across two purchases is noise; across
 * twelve it is a trend. It also explains the single-purchase case without any special flag: a
 * material bought once in the window reports {@code firstPrice == lastPrice}, {@code 0} change, and
 * {@code purchaseCount: 1}, which is self-evident.
 *
 * <p>Decimal fields are {@code String} for the same reason as {@link StockValuationRow}.
 * {@link #changePercent} is null — never zero, never infinity — when {@code firstPrice} is zero, and
 * those rows sort last.
 */
@Getter
@Builder
public class PurchasePriceDriftRow {

    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;

    /** Earliest purchase price in the window, by batch id. */
    private final String firstPrice;

    private final LocalDateTime firstPurchaseDate;

    /** Latest purchase price in the window, by batch id. */
    private final String lastPrice;

    private final LocalDateTime lastPurchaseDate;

    /** {@code lastPrice - firstPrice}, signed. Negative means the material got cheaper. */
    private final String priceChange;

    /** Signed percentage. <b>Null when {@code firstPrice} is zero</b> — not zero, not infinity. */
    private final String changePercent;

    /** Purchases in the window. 1 means first and last are the same purchase. */
    private final Long purchaseCount;

    /** The unit the prices are per. No conversion was applied — see the class javadoc. */
    private final Long uomId;

    /** Symbol of {@link #uomId}. */
    private final String uomSymbol;

    /** False when the material has since been deactivated; its purchase history is reported anyway. */
    private final Boolean materialActive;
}
