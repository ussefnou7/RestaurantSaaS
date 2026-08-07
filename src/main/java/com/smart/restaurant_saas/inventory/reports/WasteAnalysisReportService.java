package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.core.UomConversionService;
import com.smart.restaurant_saas.inventory.core.WasteService;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.reports.dto.WasteAnalysisRow;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "What did we deliberately throw away, why, and what did it cost?"
 *
 * <p>Same query shape and the same append-only source as {@link ShrinkageReportService}, differing
 * by {@code reference_type} — and by the one thing that makes it a separate report rather than a
 * filter: here the cause <b>is</b> known and recorded. "80 kg wasted" prompts nothing; "80 kg
 * wasted, 60 of it expired" prompts a purchasing change. Waste is actionable by reason; shrinkage is
 * actionable only by investigation.
 *
 * <p><b>Read-only (D4).</b> Nothing on this path writes to {@code inventory_transaction}.
 */
@Service
@RequiredArgsConstructor
public class WasteAnalysisReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final InventoryTransactionRepository transactionRepository;
    private final MaterialRepository materialRepository;
    private final UomConversionService uomConversionService;

    /**
     * Net write-off per (material, reason) over {@code [dateFrom, dateTo]} (calendar days, both
     * inclusive), sorted by absolute value descending.
     *
     * <p><b>Flat, one row per (material, reason).</b> A material wasted for two reasons yields two
     * rows rather than a nested breakdown — see {@link WasteAnalysisRow} for why the nesting was
     * declined. {@code reasonCode} is also an optional filter; null means all reasons, and
     * {@code UNSPECIFIED} selects the (currently unreachable) reason-less bucket.
     *
     * <p>Filters, sorting, empty/inverted-range handling, and the two-query shape are identical to
     * {@link ShrinkageReportService#shrinkage} — deliberately, so the pair reads as one report
     * family.
     */
    @Transactional(readOnly = true)
    public List<WasteAnalysisRow> wasteAnalysis(Long tenantId, LocalDate dateFrom, LocalDate dateTo,
                                                Long warehouseId, Long categoryId, String reasonCode,
                                                boolean negativesOnly) {
        ReportDateRange range = ReportDateRange.of(dateFrom, dateTo);

        List<WasteAggregate> aggregates = transactionRepository.aggregateWaste(
            tenantId,
            WasteService.REFERENCE_TYPE,
            range.fromInclusive(),
            range.toExclusive(),
            warehouseId,
            categoryId,
            reasonCode,
            negativesOnly);

        if (aggregates.isEmpty()) {
            return List.of();
        }

        // Distinct: a material appears once per reason, but its conversion pair is per material.
        List<Long> materialIds = aggregates.stream()
            .map(WasteAggregate::materialId).distinct().toList();
        Map<Long, Material> materials = materialRepository
            .findAllWithUomsByIdIn(tenantId, materialIds).stream()
            .collect(Collectors.toMap(Material::getId, Function.identity()));

        return aggregates.stream()
            .map(aggregate -> toRow(aggregate, materials.get(aggregate.materialId()), tenantId))
            .toList();
    }

    private WasteAnalysisRow toRow(WasteAggregate aggregate, Material material, Long tenantId) {
        ReportQuantity quantity = ReportQuantity.of(
            uomConversionService, aggregate.netStockQuantity(), material, tenantId);

        return WasteAnalysisRow.builder()
            .materialId(aggregate.materialId())
            .materialCode(aggregate.materialCode())
            .materialName(aggregate.materialName())
            .materialNameAr(aggregate.materialNameAr())
            .reasonCode(aggregate.reasonCode())
            .netQuantity(quantity.text())
            .uomId(quantity.uomId())
            .uomSymbol(quantity.uomSymbol())
            .netValue(aggregate.netValue().setScale(SCALE, ROUNDING).toPlainString())
            .movementCount(aggregate.movementCount())
            .build();
    }
}
