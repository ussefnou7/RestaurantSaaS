package com.smart.restaurant_saas.dashboard.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.inventory.core.UomConversionException;
import com.smart.restaurant_saas.inventory.core.UomConversionService;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.uom.Uom;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockAlertsTest {
    private final StockBalanceRepository balances = mock(StockBalanceRepository.class);
    private final StockBatchRepository batches = mock(StockBatchRepository.class);
    private final UomRepository units = mock(UomRepository.class);
    private final StockAlerts checks = new StockAlerts(balances, batches, units, new UomConversionService());
    private final AlertContext context = new AlertContext(7L, 2L, ZoneId.of("Africa/Cairo"),
        LocalDateTime.of(2026, 3, 31, 12, 0));

    @Test
    void oneBalanceReadProducesBothConditionsWithoutChangingCountsOrScope() {
        var row = mock(StockBalanceRepository.LowStockCountProjection.class);
        when(row.getBranchId()).thenReturn(2L);
        when(row.getWarehouseId()).thenReturn(11L);
        when(row.getOutOfStockCount()).thenReturn(3L);
        when(row.getBelowMinimumCount()).thenReturn(4L);
        when(balances.aggregateLowStockCounts(7L, 2L)).thenReturn(List.of(row));
        var result = checks.lowStock(context);
        assertThat(result).extracting(AlertOccurrence::code)
            .containsExactly(AlertCode.STOCK_OUT, AlertCode.BELOW_MINIMUM);
        assertThat(result).extracting(AlertOccurrence::count).containsExactly(3L, 4L);
        assertThat(result).allSatisfy(alert -> {
            assertThat(alert.branchId()).isEqualTo(2L);
            assertThat(alert.linkParams()).containsEntry("warehouseId", 11L);
            assertThat(alert.value()).isNull();
        });
        verify(balances, times(1)).aggregateLowStockCounts(7L, 2L);
    }

    @Test
    void oneBatchReadProducesAllExpiryConditions() {
        var row = mock(StockBatchRepository.ExpiryConditionProjection.class);
        when(row.getWarehouseId()).thenReturn(11L);
        when(row.getExpiredCount()).thenReturn(2L);
        when(row.getExpiredValue()).thenReturn(new BigDecimal("100"));
        when(row.getExpiringSoonCount()).thenReturn(3L);
        when(row.getExpiringSoonValue()).thenReturn(new BigDecimal("200"));
        when(row.getMissingExpiryCount()).thenReturn(4L);
        when(batches.aggregateExpiryConditions(eq(7L), eq(2L), any(), any())).thenReturn(List.of(row));
        var result = checks.expiry(context);
        assertThat(result).extracting(AlertOccurrence::code).containsExactly(
            AlertCode.EXPIRED_STOCK_ON_SHELF, AlertCode.EXPIRING_SOON, AlertCode.MISSING_EXPIRY_DATE);
        assertThat(result).extracting(AlertOccurrence::count).containsExactly(2L, 3L, 4L);
        assertThat(result.get(0).value()).isEqualByComparingTo("100");
        assertThat(result.get(1).value()).isEqualByComparingTo("200");
        assertThat(result.get(2).value()).isNull();
        verify(batches, times(1)).aggregateExpiryConditions(eq(7L), eq(2L), any(), any());
    }

    @Test
    void coverUsesTheBalancesUnitAndDoesNotRoundHealthyStockIntoTheThreshold() {
        var row = mock(StockBalanceRepository.DaysOfCoverProjection.class);
        when(row.getBranchId()).thenReturn(2L);
        when(row.getWarehouseId()).thenReturn(11L);
        when(row.getMaterialId()).thenReturn(5L);
        when(row.getStockUomId()).thenReturn(1L);
        when(row.getBalanceUomId()).thenReturn(2L);
        when(row.getDailyRate()).thenReturn(new BigDecimal("1000"));
        when(balances.findConsumptionRates(eq(7L), eq(2L), any(), any(), any(), eq(14)))
            .thenReturn(List.of(row));
        Uom grams = new Uom(); grams.setId(1L); grams.setFactorToBase(BigDecimal.ONE);
        Uom kilos = new Uom(); kilos.setId(2L); kilos.setBaseUom(grams);
        kilos.setFactorToBase(new BigDecimal("1000"));
        when(units.findAllById(any())).thenReturn(List.of(grams, kilos));
        when(row.getQuantity()).thenReturn(new BigDecimal("10"));
        assertThat(checks.runningOutSoon(context)).isEmpty();
        when(row.getQuantity()).thenReturn(new BigDecimal("2.000001"));
        assertThat(checks.runningOutSoon(context)).isEmpty();
        when(row.getQuantity()).thenReturn(new BigDecimal("2"));
        assertThat(checks.runningOutSoon(context).getFirst().params())
            .containsEntry("soonestDaysOfCover", "2.0");
        kilos.setBaseUom(null);
        assertThatThrownBy(() -> checks.runningOutSoon(context)).isInstanceOf(UomConversionException.class);
    }
}
