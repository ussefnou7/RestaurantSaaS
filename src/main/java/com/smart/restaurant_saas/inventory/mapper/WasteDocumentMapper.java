package com.smart.restaurant_saas.inventory.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.inventory.waste.MaterialShortfall;
import com.smart.restaurant_saas.inventory.waste.WasteDocument;
import com.smart.restaurant_saas.inventory.waste.WasteLine;
import com.smart.restaurant_saas.inventory.waste.dto.MaterialShortfallResponse;
import com.smart.restaurant_saas.inventory.waste.dto.WasteDocumentResponse;
import com.smart.restaurant_saas.inventory.waste.dto.WasteLineResponse;

@Component
public class WasteDocumentMapper {

    /** Summary view for list screens — lines are omitted; stock_warnings arrive in the same row. */
    public WasteDocumentResponse toSummary(WasteDocument doc) {
        return baseBuilder(doc).lines(null).build();
    }

    /** Full view including lines, for the detail screen. */
    public WasteDocumentResponse toResponse(WasteDocument doc) {
        List<WasteLineResponse> lines = doc.getLines().stream()
            .map(this::toLineResponse)
            .toList();
        return baseBuilder(doc).lines(lines).build();
    }

    private WasteDocumentResponse.WasteDocumentResponseBuilder baseBuilder(WasteDocument doc) {
        Warehouse warehouse = doc.getWarehouse();
        // stockWarnings is a JSON column on waste_document — no secondary query needed.
        List<MaterialShortfall> stockWarnings = doc.getStockWarnings() != null
            ? doc.getStockWarnings()
            : List.of();
        List<MaterialShortfallResponse> warnings = stockWarnings.stream()
            .map(this::toWarningResponse)
            .toList();
        return WasteDocumentResponse.builder()
            .id(doc.getId())
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .code(doc.getCode())
            .wasteDate(doc.getWasteDate())
            .reasonCode(doc.getReasonCode())
            .status(doc.getStatus())
            .notes(doc.getNotes())
            .postedToInventory(doc.getPostedToInventory())
            .completedAt(doc.getCompletedAt())
            .unCompletedAt(doc.getUnCompletedAt())
            .unCompletedBy(doc.getUnCompletedBy())
            .postedAt(doc.getPostedAt())
            .cancelledAt(doc.getCancelledAt())
            .cancelReason(doc.getCancelReason())
            .stockWarnings(warnings)
            .createdAt(doc.getCreatedAt())
            .updatedAt(doc.getUpdatedAt());
    }

    private WasteLineResponse toLineResponse(WasteLine line) {
        Material material = line.getMaterial();
        Uom uom = line.getUom();
        return WasteLineResponse.builder()
            .id(line.getId())
            .materialId(material != null ? material.getId() : null)
            .materialCode(material != null ? material.getCode() : null)
            .materialName(material != null ? material.getName() : null)
            .quantity(line.getQuantity())
            .uomId(uom != null ? uom.getId() : null)
            .uomSymbol(uom != null ? uom.getSymbol() : null)
            .notes(line.getNotes())
            // Cost is computed at POST and lives on the WASTE ledger transaction; the detail
            // view does not join the ledger. Reports read the cost from the ledger directly.
            .cost(null)
            .build();
    }

    private MaterialShortfallResponse toWarningResponse(MaterialShortfall w) {
        return MaterialShortfallResponse.builder()
            .materialId(w.materialId())
            .materialName(w.materialName())
            .requiredQty(w.requiredQty())
            .availableQty(w.availableQty())
            .shortfallQty(w.shortfallQty())
            .uomSymbol(w.uomSymbol())
            .build();
    }
}
