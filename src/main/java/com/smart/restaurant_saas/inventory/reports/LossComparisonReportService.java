package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.core.UomConversionService;
import com.smart.restaurant_saas.inventory.core.WasteService;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.reports.dto.LossComparisonRow;
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
 * "Am I losing stock to the bin, or to the door?"
 *
 * <p>Waste and shrinkage are both losses, but they demand opposite responses, and the ratio between
 * them is the diagnosis — see {@link LossComparisonRow}. {@link ShrinkageReportService} and
 * {@link WasteAnalysisReportService} each answer one half in depth; this one puts the two numbers in
 * the same row so the shape of the problem is visible without arithmetic.
 *
 * <p><b>Read-only (D4).</b> Nothing on this path writes to {@code inventory_transaction}.
 */
@Service
@RequiredArgsConstructor
public class LossComparisonReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final InventoryTransactionRepository transactionRepository;
    private final MaterialRepository materialRepository;
    private final UomConversionService uomConversionService;

    /**
     * Waste beside physical-count variance per material over {@code [dateFrom, dateTo]} (calendar
     * days, both inclusive).
     *
     * <p><b>Sorted by absolute combined loss descending, with zero-activity rows forced last.</b>
     * The partition is explicit rather than a side effect of the value sort: a plain
     * {@code ORDER BY ABS(totalValue)} would drop the clean rows into the middle, between the
     * negatives and the positives, which is the least readable place they could land.
     *
     * <p><b>Every material in the filtered set appears</b>, including those with no loss at all.
     * That is the point of the report when the user is checking a category: a clean material is a
     * result, not an omission. With no category filter this therefore returns one row per material
     * in the tenant.
     *
     * <p>No reason filter — reasons belong to the waste report. Two queries: one aggregate in stock
     * UOM, then one batched material load for the conversion pairs.
     */
    @Transactional(readOnly = true)
    public List<LossComparisonRow> lossComparison(Long tenantId, LocalDate dateFrom, LocalDate dateTo,
                                                  Long warehouseId, Long categoryId) {
        ReportDateRange range = ReportDateRange.of(dateFrom, dateTo);

        List<LossComparisonAggregate> aggregates = transactionRepository.aggregateLossComparison(
            tenantId,
            WasteService.REFERENCE_TYPE,
            PhysicalCountService.REFERENCE_TYPE,
            range.fromInclusive(),
            range.toExclusive(),
            warehouseId,
            categoryId);

        if (aggregates.isEmpty()) {
            return List.of();
        }

        List<Long> materialIds = aggregates.stream().map(LossComparisonAggregate::materialId).toList();
        Map<Long, Material> materials = materialRepository
            .findAllWithUomsByIdIn(tenantId, materialIds).stream()
            .collect(Collectors.toMap(Material::getId, Function.identity()));

        return aggregates.stream()
            .map(aggregate -> toRow(aggregate, materials.get(aggregate.materialId()), tenantId))
            .toList();
    }

    private LossComparisonRow toRow(LossComparisonAggregate aggregate, Material material, Long tenantId) {
        // Both quantities share one material and therefore one conversion pair; they must succeed
        // or fail together, or the row would report one column in stock UOM and one in display UOM.
        ReportQuantity waste = ReportQuantity.of(
            uomConversionService, aggregate.wasteStockQuantity(), material, tenantId);
        ReportQuantity shrinkage = ReportQuantity.of(
            uomConversionService, aggregate.shrinkageStockQuantity(), material, tenantId);

        return LossComparisonRow.builder()
            .materialId(aggregate.materialId())
            .materialCode(aggregate.materialCode())
            .materialName(aggregate.materialName())
            .materialNameAr(aggregate.materialNameAr())
            .wasteQuantity(waste.text())
            .wasteValue(aggregate.wasteValue().setScale(SCALE, ROUNDING).toPlainString())
            .shrinkageQuantity(shrinkage.text())
            .shrinkageValue(aggregate.shrinkageValue().setScale(SCALE, ROUNDING).toPlainString())
            .totalValue(aggregate.totalValue().setScale(SCALE, ROUNDING).toPlainString())
            .uomId(waste.uomId())
            .uomSymbol(waste.uomSymbol())
            .materialActive(aggregate.materialActive())
            .build();
    }
}
