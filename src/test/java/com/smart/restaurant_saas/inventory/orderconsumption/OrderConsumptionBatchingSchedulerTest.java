package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.common.TestZones;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class OrderConsumptionBatchingSchedulerTest {

    private final OrderConsumptionRepository docRepository = mock(OrderConsumptionRepository.class);
    private final OrderConsumptionService consumptionService = mock(OrderConsumptionService.class);
    private final OrderConsumptionBatchingProperties properties = properties();
    // Always-grant lock provider so batchOne() runs inline; the locking behaviour itself is ShedLock's contract.
    private final LockingTaskExecutor lockingTaskExecutor =
        new DefaultLockingTaskExecutor(lockConfig -> Optional.of(() -> {}));

    private final OrderConsumptionBatchingScheduler scheduler =
        new OrderConsumptionBatchingScheduler(docRepository, consumptionService, properties,
            lockingTaskExecutor, TestZones.cairo());

    /**
     * A candidate that clears the count arm of the dual trigger, so these tests exercise the
     * claim/process wiring rather than the per-tenant age re-check (which has its own test).
     */
    private static BatchingCandidate candidate(Long docId) {
        return new BatchingCandidate(docId, 1L, LocalDateTime.now(), 50L);
    }

    @Test
    void queriesWithBothTriggerParametersDerivedFromConfig() {
        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq(50L)))
            .thenReturn(List.of());
        // now - maxAge + MAX_OFFSET_SPREAD: the cutoff is deliberately widened by the supported
        // Cairo <-> Dubai spread so a doc stamped in a further-ahead zone is not missed (D101/O33).
        LocalDateTime before = LocalDateTime.now().minusHours(8).plusHours(2);

        scheduler.pollAndBatch();

        LocalDateTime after = LocalDateTime.now().minusHours(8).plusHours(2);
        ArgumentCaptor<LocalDateTime> ageCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        // Count trigger is wired via the threshold param (50); timeout trigger via the age cutoff (now - 8h).
        verify(docRepository).findBatchingCandidates(
            eq(OrderConsumptionStatus.PENDING), ageCutoff.capture(), eq(50L));
        assertThat(ageCutoff.getValue()).isBetween(before.minusSeconds(5), after.plusSeconds(5));
    }

    @Test
    void claimsThenProcessesEachReadyDocInThatOrder() {
        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq(50L)))
            .thenReturn(List.of(candidate(101L), candidate(202L)));
        when(consumptionService.claimDoc(eq(101L), any())).thenReturn(true);
        when(consumptionService.claimDoc(eq(202L), any())).thenReturn(true);

        scheduler.pollAndBatch();

        // The claim (which commits IN_PROGRESS) must happen before processing for each doc.
        InOrder order = inOrder(consumptionService);
        order.verify(consumptionService).claimDoc(eq(101L), any());
        order.verify(consumptionService).processClaimedDoc(eq(101L), any());
        order.verify(consumptionService).claimDoc(eq(202L), any());
        order.verify(consumptionService).processClaimedDoc(eq(202L), any());
    }

    @Test
    void skipsProcessingWhenClaimReturnsFalse() {
        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq(50L)))
            .thenReturn(List.of(candidate(101L)));
        when(consumptionService.claimDoc(eq(101L), any())).thenReturn(false);

        scheduler.pollAndBatch();

        verify(consumptionService, never()).processClaimedDoc(eq(101L), any());
    }

    @Test
    void oneDocFailureDoesNotStopTheRest() {
        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq(50L)))
            .thenReturn(List.of(candidate(101L), candidate(202L)));
        when(consumptionService.claimDoc(eq(101L), any())).thenThrow(new RuntimeException("boom"));
        when(consumptionService.claimDoc(eq(202L), any())).thenReturn(true);

        scheduler.pollAndBatch();

        verify(consumptionService).processClaimedDoc(eq(202L), any());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static OrderConsumptionBatchingProperties properties() {
        OrderConsumptionBatchingProperties props = new OrderConsumptionBatchingProperties();
        props.setThresholdCount(50);
        props.setMaxAge(Duration.ofHours(8));
        props.setPollInterval(Duration.ofSeconds(60));
        return props;
    }

    @Test
    void returnsDocsAbandonedInProgressSoTheyAreRetried() {
        // The poll only selects PENDING, so a doc left IN_PROGRESS by an instance that died
        // between claim and process would never be picked up again and its stock never leaves.
        when(docRepository.findStuckDocIds(eq(OrderConsumptionStatus.IN_PROGRESS), any()))
            .thenReturn(List.of(77L));
        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq(50L)))
            .thenReturn(List.of());

        scheduler.pollAndBatch();

        verify(consumptionService).releaseStuckDoc(eq(77L), any());
    }

    @Test
    void stuckCutoffIsOlderThanTheLockSoALiveClaimIsNeverReclaimed() {
        when(docRepository.findStuckDocIds(eq(OrderConsumptionStatus.IN_PROGRESS), any()))
            .thenReturn(List.of());
        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq(50L)))
            .thenReturn(List.of());

        scheduler.pollAndBatch();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(docRepository).findStuckDocIds(eq(OrderConsumptionStatus.IN_PROGRESS), cutoff.capture());
        // lockAtMost (10m) plus the offset spread (2h): updatedAt is tenant-local, so a tenant
        // running ahead must not look stuck early. Slack goes the conservative way here, unlike
        // the age arm which widens to over-select.
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusHours(2));
    }

    @Test
    void aReleasedDocIsNotProcessedInTheSameTick() {
        // It goes back to PENDING and waits for the next poll, which re-applies the dual trigger.
        when(docRepository.findStuckDocIds(eq(OrderConsumptionStatus.IN_PROGRESS), any()))
            .thenReturn(List.of(77L));
        when(consumptionService.releaseStuckDoc(eq(77L), any())).thenReturn(true);
        when(docRepository.findBatchingCandidates(eq(OrderConsumptionStatus.PENDING), any(), eq(50L)))
            .thenReturn(List.of());

        scheduler.pollAndBatch();

        verify(consumptionService, never()).claimDoc(eq(77L), any());
    }
}
