package com.smart.restaurant_saas.dashboard.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.ScopedConditionAggregate;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionBatchingProperties;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionRepository;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionMaterialRepository;
import com.smart.restaurant_saas.inventory.orderconsumption.UnpostedConsumptionCostRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsumptionAlertsTest {
    @Test
    void outstandingCostUsesDisplayQuantityTimesDisplayUnitCost() {
        var docs = mock(OrderConsumptionRepository.class);
        var rows = mock(OrderConsumptionMaterialRepository.class);
        var doc = mock(ScopedConditionAggregate.class);
        when(doc.getWarehouseId()).thenReturn(11L);
        when(doc.getItemCount()).thenReturn(1L);
        when(docs.aggregateUnpostedConsumption(7L, null)).thenReturn(List.of(doc));
        var row = mock(UnpostedConsumptionCostRow.class);
        when(row.getWarehouseId()).thenReturn(11L);
        when(row.getMaterialId()).thenReturn(3L);
        when(row.getRequiredQuantity()).thenReturn(new BigDecimal("2"));
        when(row.getAverageCost()).thenReturn(new BigDecimal("10"));
        when(rows.findUnpostedCostRows(7L, null)).thenReturn(List.of(row));
        var checks = new ConsumptionAlerts(docs, rows, new OrderConsumptionBatchingProperties());
        var context = new AlertContext(7L, null, ZoneId.of("Africa/Cairo"), LocalDateTime.of(2026, 3, 31, 12, 0));
        assertThat(checks.unpostedConsumption(context).getFirst().value()).isEqualByComparingTo("20");
    }
}
