package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.reports.dto.StockValuationRow;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockValuationReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final StockBalanceRepository stockBalanceRepository;

    /**
     * Stock valuation across the tenant: one row per (warehouse, material) balance, valued at the
     * balance's moving average cost. All three filters are optional and AND-ed together; branchId
     * filters on the warehouse's branch. No pagination — the result is bounded by
     * warehouses x materials, and the report is consumed as a single table/export.
     *
     * <p>Only active materials in active warehouses are valued — deactivated rows are treated as
     * retired and excluded from the total. Quantity is not filtered: zero and negative balances
     * still appear, so the report doubles as a way to spot balances that need correcting.
     */
    @Transactional(readOnly = true)
    public List<StockValuationRow> stockValuation(
            Long tenantId, Long branchId, Long warehouseId, Long categoryId) {
        List<StockBalance> balances =
            stockBalanceRepository.findForStockValuation(tenantId, branchId, warehouseId, categoryId);

        return balances.stream().map(this::toRow).toList();
    }

    private StockValuationRow toRow(StockBalance sb) {
        Warehouse warehouse = sb.getWarehouse();
        Material material = sb.getMaterial();
        MaterialCategory category = material.getCategory();

        BigDecimal quantity = sb.getQuantity().setScale(SCALE, ROUNDING);
        BigDecimal averageCost = sb.getAverageCost().setScale(SCALE, ROUNDING);
        BigDecimal totalValue = quantity.multiply(averageCost).setScale(SCALE, ROUNDING);

        return StockValuationRow.builder()
            .warehouseId(warehouse.getId())
            .warehouseName(warehouse.getName())
            .warehouseNameAr(warehouse.getNameAr())
            .materialId(material.getId())
            .materialName(material.getName())
            .materialNameAr(material.getNameAr())
            .categoryId(category.getId())
            .categoryName(category.getName())
            .categoryNameAr(category.getNameAr())
            .quantity(quantity.toPlainString())
            .averageCost(averageCost.toPlainString())
            .totalValue(totalValue.toPlainString())
            .build();
    }
}
