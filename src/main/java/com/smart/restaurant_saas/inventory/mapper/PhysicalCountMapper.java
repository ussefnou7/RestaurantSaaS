package com.smart.restaurant_saas.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCount;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLine;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountLineResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountSummaryResponse;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Component
public class PhysicalCountMapper {

    public PhysicalCountResponse toResponse(PhysicalCount count) {
        Warehouse warehouse = count.getWarehouse();
        List<PhysicalCountLineResponse> lines = count.getLines().stream()
            .map(this::toLineResponse)
            .toList();
        return PhysicalCountResponse.builder()
            .id(count.getId())
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
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

    public PhysicalCountLineResponse toLineResponse(PhysicalCountLine line) {
        Material material = line.getMaterial();
        Uom uom = line.getUom();
        return PhysicalCountLineResponse.builder()
            .id(line.getId())
            .materialId(material != null ? material.getId() : null)
            .materialCode(material != null ? material.getCode() : null)
            .materialName(material != null ? material.getName() : null)
            .materialNameAr(material != null ? material.getNameAr() : null)
            .uomId(uom != null ? uom.getId() : null)
            .uomSymbol(uom != null ? uom.getSymbol() : null)
            .expectedQuantity(line.getExpectedQuantity())
            .adjustedExpectedQuantity(line.getAdjustedExpectedQuantity())
            .countedQuantity(line.getCountedQuantity())
            .variance(line.getVariance())
            .varianceValue(line.getVarianceValue())
            .unitCostAtFreeze(line.getUnitCostAtFreeze())
            .actionTaken(line.getActionTaken())
            .adjustmentTransactionId(line.getAdjustmentTransactionId())
            .wasteTransactionId(line.getWasteTransactionId())
            .countedAt(line.getCountedAt())
            .notes(line.getNotes())
            .build();
    }
}
