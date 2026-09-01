package com.smart.restaurant_saas.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.batch.dto.StockBatchResponse;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StockBatchMapperTest {

    private final StockBatchMapper mapper = new StockBatchMapper();

    @Test
    void freshBatchReturnsNegativeDaysRemainingWithoutClamping() {
        LocalDate today = LocalDate.of(2026, 9, 1);
        StockBatch batch = batch(false, 3, today.minusDays(5), null);

        StockBatchResponse response = mapper.toResponse(batch, "kg", today);

        assertThat(response.getAgeDays()).isEqualTo(5);
        assertThat(response.getDaysRemaining()).isEqualTo(-2);
    }

    @Test
    void trackedBatchUsesExpiryDateAndIgnoresSmallMaxAge() {
        LocalDate today = LocalDate.of(2026, 9, 1);
        LocalDate expiryDate = LocalDate.of(2027, 12, 31);
        StockBatch batch = batch(true, 1, today.minusDays(60), expiryDate);

        StockBatchResponse response = mapper.toResponse(batch, "kg", today);

        assertThat(response.getAgeDays()).isEqualTo(60);
        assertThat(response.getDaysRemaining()).isEqualTo(486);
    }

    @Test
    void zeroMaxAgeAndTrackedNullExpiryBothReturnNullDaysRemaining() {
        LocalDate today = LocalDate.of(2026, 9, 1);

        StockBatchResponse fresh = mapper.toResponse(
            batch(false, 0, today.minusDays(5), null), "kg", today);
        StockBatchResponse tracked = mapper.toResponse(
            batch(true, 2, today.minusDays(5), null), "kg", today);

        assertThat(fresh.getDaysRemaining()).isNull();
        assertThat(tracked.getDaysRemaining()).isNull();
        assertThat(fresh.getAgeDays()).isEqualTo(5);
        assertThat(tracked.getAgeDays()).isEqualTo(5);
    }

    @Test
    void cairoWallClockDateAdvancesBeforeUtcNearLocalMidnight() {
        Instant nearCairoMidnight = Instant.parse("2026-09-01T21:30:00Z");

        LocalDate tenantToday = nearCairoMidnight.atZone(ZoneId.of("Africa/Cairo")).toLocalDate();
        LocalDate utcToday = nearCairoMidnight.atZone(ZoneOffset.UTC).toLocalDate();
        StockBatchResponse response = mapper.toResponse(
            batch(false, 3, LocalDate.of(2026, 8, 30), null), "kg", tenantToday);

        assertThat(utcToday).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(tenantToday).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(response.getAgeDays()).isEqualTo(3);
        assertThat(response.getDaysRemaining()).isZero();
    }

    private StockBatch batch(boolean expiryTracked, int maxAgeDays,
                             LocalDate warehouseEntryDate, LocalDate expiryDate) {
        Material material = new Material();
        material.setExpiryTracked(expiryTracked);

        StockBalance balance = new StockBalance();
        balance.setMaterial(material);
        balance.setMaxAgeDays(maxAgeDays);

        StockBatch batch = new StockBatch();
        batch.setStockBalance(balance);
        batch.setWarehouseEntryDate(warehouseEntryDate);
        batch.setExpiryDate(expiryDate);
        return batch;
    }
}
