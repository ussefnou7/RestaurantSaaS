package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.inventory.orderconsumption.MaterialSummary;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumption;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionLineView;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionMaterial;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocDetailResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocLineResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocListResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocMaterialResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionMaterialSummaryResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionMaterialsSummaryResponse;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumptionDocMapper {

    public OrderConsumptionDocListResponse toListResponse(OrderConsumption doc, long lineCount) {
        Warehouse warehouse = doc.getWarehouse();
        return OrderConsumptionDocListResponse.builder()
            .id(doc.getId())
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .status(doc.getStatus())
            .createdAt(doc.getCreatedAt())
            .processedAt(doc.getProcessedAt())
            .lineCount(lineCount)
            .build();
    }

    public OrderConsumptionDocDetailResponse toDetailResponse(
            OrderConsumption doc,
            List<OrderConsumptionMaterial> materials,
            List<OrderConsumptionLineView> lines) {
        Warehouse warehouse = doc.getWarehouse();
        return OrderConsumptionDocDetailResponse.builder()
            .id(doc.getId())
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .status(doc.getStatus())
            .createdAt(doc.getCreatedAt())
            .processedAt(doc.getProcessedAt())
            .materials(materials.stream().map(this::toMaterialResponse).toList())
            .lines(lines.stream().map(this::toLineResponse).toList())
            .build();
    }

    /** D88: both quantities are display UOM, and the response says so via uomId/uomSymbol. */
    public OrderConsumptionDocMaterialResponse toMaterialResponse(OrderConsumptionMaterial material) {
        Uom uom = material.getRequiredUom();
        return OrderConsumptionDocMaterialResponse.builder()
            .materialId(material.getMaterial().getId())
            .materialName(material.getMaterial().getName())
            .requiredQuantity(material.getRequiredQuantity())
            .uomId(uom.getId())
            .uomSymbol(uom.getSymbol())
            .consumed(material.isConsumed())
            .availableQuantity(material.getAvailableQuantity())
            .failureReason(material.getFailureReason())
            .exceptionClass(material.getExceptionClass())
            .exceptionMessage(material.getExceptionMessage())
            .build();
    }

    public OrderConsumptionMaterialsSummaryResponse toMaterialsSummaryResponse(
            Long docId,
            List<MaterialSummary> summaries) {
        return OrderConsumptionMaterialsSummaryResponse.builder()
            .docId(docId)
            .materials(summaries.stream().map(this::toMaterialSummaryResponse).toList())
            .build();
    }

    public OrderConsumptionDocResponse toResponse(OrderConsumption doc) {
        Warehouse warehouse = doc.getWarehouse();
        return OrderConsumptionDocResponse.builder()
            .id(doc.getId())
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .status(doc.getStatus())
            .processedAt(doc.getProcessedAt())
            .build();
    }

    private OrderConsumptionDocLineResponse toLineResponse(OrderConsumptionLineView view) {
        return OrderConsumptionDocLineResponse.builder()
            .id(view.getId())
            .orderId(view.getOrderId())
            .createdBy(view.getCreatedBy())
            .build();
    }

    private OrderConsumptionMaterialSummaryResponse toMaterialSummaryResponse(MaterialSummary summary) {
        return OrderConsumptionMaterialSummaryResponse.builder()
            .materialId(summary.getMaterialId())
            .materialName(summary.getMaterialName())
            .uom(summary.getUom())
            .totalQtyConsumed(summary.getTotalQtyConsumed())
            .orderCount(summary.getOrderCount())
            .build();
    }
}
