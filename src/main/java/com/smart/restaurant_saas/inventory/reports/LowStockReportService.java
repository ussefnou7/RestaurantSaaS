package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.reports.dto.LowStockRow;
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
public class LowStockReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final StockBalanceRepository stockBalanceRepository;

    /**
     * Materials that have fallen below their configured minimum, one row per (warehouse, material),
     * with the shortfall to reorder. Same filters and join shape as the stock valuation report:
     * all three filters optional and AND-ed, branchId matched against the warehouse's branch,
     * active materials in active warehouses only, no pagination.
     *
     * <p>A balance with no minimum configured (stored as 0) is never reported as low — see
     * {@code StockBalanceRepository.findForLowStock}.
     */
    @Transactional(readOnly = true)
    public List<LowStockRow> lowStock(Long tenantId, Long branchId, Long warehouseId, Long categoryId) {
        List<StockBalance> balances =
            stockBalanceRepository.findForLowStock(tenantId, branchId, warehouseId, categoryId);

        return balances.stream().map(this::toRow).toList();
    }

    private LowStockRow toRow(StockBalance sb) {
        Warehouse warehouse = sb.getWarehouse();
        Material material = sb.getMaterial();
        MaterialCategory category = material.getCategory();

        BigDecimal quantity = sb.getQuantity().setScale(SCALE, ROUNDING);
        BigDecimal minQuantity = sb.getMinimumQuantity().setScale(SCALE, ROUNDING);
        BigDecimal shortfall = minQuantity.subtract(quantity).setScale(SCALE, ROUNDING);

        return LowStockRow.builder()
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
            .minQuantity(minQuantity.toPlainString())
            .shortfall(shortfall.toPlainString())
            .build();
    }
}
