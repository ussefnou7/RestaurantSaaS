package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.searchPattern;

import com.smart.restaurant_saas.inventory.dto.response.StockBalanceResponse;
import com.smart.restaurant_saas.inventory.mapper.StockBalanceMapper;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockBalanceService {

    private final CurrentTenantProvider currentTenantProvider;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockBalanceMapper stockBalanceMapper;

    @Transactional(readOnly = true)
    public List<StockBalanceResponse> listStockBalances(
            Long warehouseId,
            Long materialId,
            Long categoryId,
            Boolean lowStock,
            String search
    ) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return stockBalanceRepository.findByTenantIdAndFilters(
                        tenantId,
                        warehouseId,
                        materialId,
                        categoryId,
                        lowStock,
                        searchPattern(search)
                ).stream()
                .map(stockBalanceMapper::toResponse)
                .toList();
    }
}
