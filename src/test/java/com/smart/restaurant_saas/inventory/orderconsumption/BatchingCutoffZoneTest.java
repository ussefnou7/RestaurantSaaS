package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * D101 §7: the batching age trigger across tenants in different zones.
 *
 * <p>{@code order_consumption.created_at} is stored in the owning tenant's wall clock, so a single
 * cutoff cannot be correct for several zones at once. The scheduler over-selects with a widened
 * cutoff and then re-checks each row against its own tenant's clock. These tests pin both halves —
 * this is the behaviour that regresses invisibly, because the count trigger masks it in practice.
 */
class BatchingCutoffZoneTest {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final int THRESHOLD = 50;
    private static final Duration MAX_AGE = Duration.ofHours(8);

    private final OrderConsumptionRepository docRepository = mock(OrderConsumptionRepository.class);
    private final OrderConsumptionService consumptionService = mock(OrderConsumptionService.class);
    private final LockingTaskExecutor lockingTaskExecutor =
        new DefaultLockingTaskExecutor(lockConfig -> Optional.of(() -> {}));

    private OrderConsumptionBatchingScheduler schedulerFor(ZoneId tenantZone) {
        OrderConsumptionBatchingProperties properties = new OrderConsumptionBatchingProperties();
        properties.setThresholdCount(THRESHOLD);
        properties.setMaxAge(MAX_AGE);

        TenantTimeZoneService zones = mock(TenantTimeZoneService.class);
        when(zones.systemZone()).thenReturn(CAIRO);
        when(zones.zoneFor(any())).thenReturn(tenantZone);

        return new OrderConsumptionBatchingScheduler(
            docRepository, consumptionService, properties, lockingTaskExecutor, zones);
    }

    /**
     * Verification 6. A Dubai doc eight real hours old carries a stored value of {@code now-8+4} in
     * Cairo terms, which sits above an unwidened Cairo cutoff of {@code now-8}. Before the fix it
     * was never selected and waited roughly twelve hours instead of eight.
     */
    @Test
    void dubaiDocEightRealHoursOldIsPickedUpDespiteTheCairoClockedQuery() {
        OrderConsumptionBatchingScheduler scheduler = schedulerFor(DUBAI);
        // Eight real hours ago, as Dubai stamped it.
        LocalDateTime stampedByDubai = LocalDateTime.now(DUBAI).minus(MAX_AGE);
        BatchingCandidate dubaiDoc = new BatchingCandidate(101L, 7L, stampedByDubai, 0L);

        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq((long) THRESHOLD)))
            .thenReturn(List.of(dubaiDoc));
        when(consumptionService.claimDoc(eq(101L), any())).thenReturn(true);

        scheduler.pollAndBatch();

        verify(consumptionService).processClaimedDoc(eq(101L), any());
    }

    /** The widened cutoff is what makes the row above reachable by the query in the first place. */
    @Test
    void queryCutoffIsWidenedByTheSupportedOffsetSpread() {
        OrderConsumptionBatchingScheduler scheduler = schedulerFor(CAIRO);
        when(docRepository.findBatchingCandidates(any(), any(), anyLong()))
            .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now(CAIRO).minus(MAX_AGE).plusHours(2);
        scheduler.pollAndBatch();
        LocalDateTime after = LocalDateTime.now(CAIRO).minus(MAX_AGE).plusHours(2);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(docRepository).findBatchingCandidates(
            eq(OrderConsumptionStatus.PENDING), cutoff.capture(), eq((long) THRESHOLD));
        assertThat(cutoff.getValue())
            .isBetween(before.minusSeconds(5), after.plusSeconds(5));
    }

    /**
     * The other half: over-selection must not turn into early batching. A doc only four hours old
     * in its own zone is inside the widened query window but has not really crossed the age
     * threshold, so the per-tenant re-check has to discard it.
     */
    @Test
    void docInsideTheWidenedWindowButNotYetOldEnoughIsDiscarded() {
        OrderConsumptionBatchingScheduler scheduler = schedulerFor(DUBAI);
        LocalDateTime onlyFourHoursOld = LocalDateTime.now(DUBAI).minusHours(4);
        BatchingCandidate youngDoc = new BatchingCandidate(202L, 7L, onlyFourHoursOld, 0L);

        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq((long) THRESHOLD)))
            .thenReturn(List.of(youngDoc));

        scheduler.pollAndBatch();

        verify(consumptionService, never()).claimDoc(eq(202L), any());
    }

    /** The count arm is zone-independent and must still fire on its own for a young doc. */
    @Test
    void countTriggerStillFiresRegardlessOfZone() {
        OrderConsumptionBatchingScheduler scheduler = schedulerFor(DUBAI);
        BatchingCandidate busyDoc =
            new BatchingCandidate(303L, 7L, LocalDateTime.now(DUBAI), THRESHOLD);

        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq((long) THRESHOLD)))
            .thenReturn(List.of(busyDoc));
        when(consumptionService.claimDoc(eq(303L), any())).thenReturn(true);

        scheduler.pollAndBatch();

        verify(consumptionService).processClaimedDoc(eq(303L), any());
    }
}
