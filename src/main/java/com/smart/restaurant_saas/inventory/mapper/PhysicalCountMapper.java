package com.smart.restaurant_saas.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCount;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLine;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLineCalculation;
import com.smart.restaurant_saas.inventory.physicalcount.PostFreezeMovementSummary;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountLineResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountSummaryResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMaterialMovementResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementRowResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementsResponse;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Component
public class PhysicalCountMapper {

    public PhysicalCountResponse toResponse(PhysicalCount count) {
        return toResponse(count, Map.of());
    }

    public PhysicalCountResponse toResponse(
            PhysicalCount count,
            Map<Long, PhysicalCountLineCalculation> calculations) {
        Warehouse warehouse = count.getWarehouse();
        List<PhysicalCountLineResponse> lines = count.getLines().stream()
            .map(line -> toLineResponse(line, line.getId() != null
                ? calculations.get(line.getId())
                : null))
            .toList();
        return PhysicalCountResponse.builder()
            .id(count.getId())
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .warehouseNameAr(warehouse != null ? warehouse.getNameAr() : null)
            .code(count.getCode())
            .scheduledDate(count.getScheduledDate())
            .status(count.getStatus())
            .notes(count.getNotes())
            .hasLargeVariance(count.getHasLargeVariance())
            .largeVarianceValue(count.getLargeVarianceValue())
            .frozenAt(count.getFrozenAt())
            .reconciledAt(count.getReconciledAt())
            .lines(lines)
            .createdAt(count.getCreatedAt())
            .updatedAt(count.getUpdatedAt())
            .build();
    }

    public PhysicalCountSummaryResponse toSummary(PhysicalCount count) {
        Warehouse warehouse = count.getWarehouse();
        List<PhysicalCountLine> lines = count.getLines();
        int varianceCount = (int) lines.stream()
            .filter(l -> l.getVariance() != null
                && l.getVariance().compareTo(BigDecimal.ZERO) != 0)
            .count();
        return PhysicalCountSummaryResponse.builder()
            .id(count.getId())
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .warehouseNameAr(warehouse != null ? warehouse.getNameAr() : null)
            .code(count.getCode())
            .scheduledDate(count.getScheduledDate())
            .status(count.getStatus())
            .hasLargeVariance(count.getHasLargeVariance())
            .largeVarianceValue(count.getLargeVarianceValue())
            .lineCount(lines.size())
            .varianceCount(varianceCount)
            .createdAt(count.getCreatedAt())
            .build();
    }

    /**
     * Totals span the whole warehouse — the reviewer wants to know how busy it has been since the
     * freeze — while the per-material breakdown is narrowed to the materials this document counts,
     * which are the only ones the review screen can show a line for.
     */
    public PostFreezeMovementsResponse toPostFreezeMovementsResponse(
            PhysicalCount count,
            List<PostFreezeMovementSummary> summaries,
            List<PostFreezeMaterialMovementResponse> materials,
            List<PostFreezeMovementRowResponse> included,
            List<PostFreezeMovementRowResponse> afterCount) {
        Warehouse warehouse = count.getWarehouse();
        int totalMovementCount = summaries.stream()
            .mapToInt(summary -> summary.getMovementCount().intValue())
            .sum();
        return PostFreezeMovementsResponse.builder()
            .countId(count.getId())
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .frozenAt(count.getFrozenAt())
            .totalMovementCount(totalMovementCount)
            .affectedMaterialCount(summaries.size())
            .materials(materials)
            .included(included)
            .afterCount(afterCount)
            .build();
    }

    public PhysicalCountLineResponse toLineResponse(PhysicalCountLine line) {
        return toLineResponse(line, null);
    }

    private PhysicalCountLineResponse toLineResponse(
            PhysicalCountLine line,
            PhysicalCountLineCalculation calculation) {
        Material material = line.getMaterial();
        Uom uom = line.getUom();
        BigDecimal adjustedExpectedQuantity = calculation != null
            ? calculation.adjustedExpectedQuantity()
            : line.getAdjustedExpectedQuantity();
        BigDecimal variance = calculation != null ? calculation.variance() : line.getVariance();
        BigDecimal varianceValue = calculation != null
            ? calculation.varianceValue()
            : line.getVarianceValue();
        return PhysicalCountLineResponse.builder()
            .id(line.getId())
            .materialId(material != null ? material.getId() : null)
            .materialCode(material != null ? material.getCode() : null)
            .materialName(material != null ? material.getName() : null)
            .materialNameAr(material != null ? material.getNameAr() : null)
            .uomId(uom != null ? uom.getId() : null)
            .uomSymbol(uom != null ? uom.getSymbol() : null)
            .expectedQuantity(line.getExpectedQuantity())
            .adjustedExpectedQuantity(adjustedExpectedQuantity)
            .adjustedExpectedQuantityProvisional(
                calculation != null && calculation.provisional())
            .countedQuantity(line.getCountedQuantity())
            .variance(variance)
            .varianceValue(varianceValue)
            // The frozen average cost never matches what the ledger records (FIFO on the way out,
            // the reconcile-time average on the way in), so the figure is always an estimate.
            .varianceValueIsEstimate(varianceValue != null)
            .unitCostAtFreeze(line.getUnitCostAtFreeze())
            .actionTaken(line.getActionTaken())
            .adjustmentTransactionId(line.getAdjustmentTransactionId())
            .countedAt(line.getCountedAt())
            .notes(line.getNotes())
            .build();
    }
}
