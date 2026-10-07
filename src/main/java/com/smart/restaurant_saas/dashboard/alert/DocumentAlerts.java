package com.smart.restaurant_saas.dashboard.alert;

import com.smart.restaurant_saas.inventory.reports.PurchasePriceDriftAggregate;
import com.smart.restaurant_saas.inventory.repository.PhysicalCountRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseInvoiceRepository;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Dashboard checks sharing the same domain repositories. */
@Component
@RequiredArgsConstructor
public class DocumentAlerts {

    private static final int PERCENT_SCALE = 1;

    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final PhysicalCountRepository physicalCountRepository;
    private final StockBatchRepository stockBatchRepository;

    public List<AlertOccurrence> unpostedPurchaseInvoice(AlertContext context) {

        return ScopedAlerts.perWarehouse(
            AlertCode.UNPOSTED_PURCHASE_INVOICE,
            purchaseInvoiceRepository.aggregateUnpostedInvoices(
                context.tenantId(),
                context.branchFilter(),
                context.now().toLocalDate().minusDays(AlertThresholds.INVOICE_UNPOSTED_DAYS)),
            row -> Map.of("thresholdDays", AlertThresholds.INVOICE_UNPOSTED_DAYS));
    }

    public List<AlertOccurrence> frozenPhysicalCount(AlertContext context) {
        return ScopedAlerts.perWarehouse(
            AlertCode.FROZEN_PHYSICAL_COUNT,
            physicalCountRepository.aggregateFrozenCounts(
                context.tenantId(),
                context.branchFilter(),
                context.daysAgo(AlertThresholds.COUNT_FROZEN_DAYS)),
            row -> Map.of("thresholdDays", AlertThresholds.COUNT_FROZEN_DAYS));
    }

    public List<AlertOccurrence> largeCountVariance(AlertContext context) {
        return ScopedAlerts.perWarehouse(
            AlertCode.LARGE_COUNT_VARIANCE,
            physicalCountRepository.aggregateLargeVarianceCounts(
                context.tenantId(),
                context.branchFilter(),
                context.daysAgo(AlertThresholds.COUNT_VARIANCE_LOOKBACK_DAYS)),
            row -> Map.of("lookbackDays", AlertThresholds.COUNT_VARIANCE_LOOKBACK_DAYS));
    }

    public List<AlertOccurrence> supplierPriceJump(AlertContext context) {
        List<PurchasePriceDriftAggregate> risen = stockBatchRepository.aggregatePurchasePriceDrift(
                context.tenantId(),
                context.daysAgo(AlertThresholds.PRICE_JUMP_WINDOW_DAYS),
                context.now(),
                context.branchFilter(),
                null,
                null,
                null)
            .stream()

            .filter(row -> row.getChangePercent() != null
                && row.getChangePercent().compareTo(AlertThresholds.PRICE_JUMP_PERCENT) >= 0)
            .toList();

        if (risen.isEmpty()) {
            return List.of();
        }

        PurchasePriceDriftAggregate worst = risen.stream()
            .max(Comparator.comparing(PurchasePriceDriftAggregate::getChangePercent))
            .orElseThrow();

        return List.of(new AlertOccurrence(
            AlertCode.SUPPLIER_PRICE_JUMP,
            null,
            null,
            risen.size(),
            null,

            worst.getLastPurchaseDate(),
            Map.of(
                "materialCount", risen.size(),
                "worstMaterialName", nullToEmpty(worst.getMaterialName()),
                "worstMaterialNameAr", nullToEmpty(worst.getMaterialNameAr()),
                "worstChangePercent", percent(worst.getChangePercent()),
                "thresholdPercent", AlertThresholds.PRICE_JUMP_PERCENT.toPlainString(),
                "windowDays", AlertThresholds.PRICE_JUMP_WINDOW_DAYS),
            Map.of()));
    }

    private String percent(BigDecimal value) {
        return value.setScale(PERCENT_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
