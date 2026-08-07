package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.reports.dto.PurchasePriceDriftRow;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Which materials are costing me more than they used to, and by how much?"
 *
 * <p>Within one window, the first and last purchase price are compared. One period rather than two
 * is simpler for the user to ask for, and it compares prices that <em>actually occurred</em> rather
 * than averages that nobody ever paid.
 *
 * <p><b>This is the only report in the module whose source is not the ledger.</b> It reads
 * {@code stock_batch}, where each purchase records the price it came in at. The consequence is that
 * <b>no UOM conversion happens anywhere in this class</b> — batch costs are already display-UOM
 * (D87 layer 2). See {@link PurchasePriceDriftRow}.
 *
 * <p><b>Read-only.</b> Nothing on this path writes.
 */
@Service
@RequiredArgsConstructor
public class PurchasePriceDriftReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final StockBatchRepository stockBatchRepository;

    /**
     * Purchase price movement per material over {@code [dateFrom, dateTo]} (calendar days, both
     * inclusive), sorted by absolute {@code changePercent} descending with null percentages last.
     *
     * <p><b>Sorted by percentage, not by absolute money.</b> A 2 EGP material up 50% matters more
     * than a 200 EGP material up 5%: the percentage is what breaks a recipe's costing and prompts a
     * re-price. Sorting by the money delta would bury every cheap ingredient.
     *
     * <p>A material with no purchases in the window does not appear at all — unlike the loss
     * comparison report, silence here is genuinely nothing to say rather than a clean bill of
     * health.
     *
     * <p>One query. No material batch-load is needed because nothing requires conversion.
     */
    @Transactional(readOnly = true)
    public List<PurchasePriceDriftRow> purchasePriceDrift(Long tenantId, LocalDate dateFrom,
                                                          LocalDate dateTo, Long warehouseId,
                                                          Long categoryId, Long supplierId) {
        ReportDateRange range = ReportDateRange.of(dateFrom, dateTo);

        return stockBatchRepository.aggregatePurchasePriceDrift(
                tenantId,
                range.fromInclusive(),
                range.toExclusive(),
                warehouseId,
                categoryId,
                supplierId)
            .stream()
            .map(this::toRow)
            .toList();
    }

    private PurchasePriceDriftRow toRow(PurchasePriceDriftAggregate aggregate) {
        return PurchasePriceDriftRow.builder()
            .materialId(aggregate.getMaterialId())
            .materialCode(aggregate.getMaterialCode())
            .materialName(aggregate.getMaterialName())
            .materialNameAr(aggregate.getMaterialNameAr())
            .firstPrice(scaled(aggregate.getFirstPrice()))
            .firstPurchaseDate(aggregate.getFirstPurchaseDate())
            .lastPrice(scaled(aggregate.getLastPrice()))
            .lastPurchaseDate(aggregate.getLastPurchaseDate())
            .priceChange(scaled(aggregate.getPriceChange()))
            .changePercent(scaled(aggregate.getChangePercent()))
            .purchaseCount(aggregate.getPurchaseCount())
            .uomId(aggregate.getUomId())
            .uomSymbol(aggregate.getUomSymbol())
            .materialActive(aggregate.getMaterialActive())
            .build();
    }

    /**
     * Formats a decimal at the house scale, preserving null. {@code changePercent} is null when the
     * first price was zero, and that null must survive to the API as null — rendering it as
     * "0.000000" would assert the price did not move when the truth is that no percentage exists.
     */
    private String scaled(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING).toPlainString();
    }
}
