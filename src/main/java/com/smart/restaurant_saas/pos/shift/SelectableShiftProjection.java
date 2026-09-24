package com.smart.restaurant_saas.pos.shift;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of the list a manager picks from when attributing an expense to a drawer (D124).
 *
 * <p>Carries exactly what D124 says the manager needs to tell one shift from another -- cashier
 * name, business date, open/close times, device, status -- and deliberately no money at all. The
 * manager choosing which drawer paid for a plumber does not need that drawer's opening count, and
 * this list is reachable by anyone who can record an expense.
 *
 * <p>{@code cashierName} is resolved from {@code openedByUserId} on read, never stored on the
 * shift: a denormalised name is a second copy that goes stale when a user is renamed.
 */
public interface SelectableShiftProjection {

    Long getId();
    LocalDate getBusinessDate();
    Long getDeviceId();
    String getDeviceName();
    Long getBranchId();
    Long getBranchDeviceCount();
    Long getCashierUserId();
    String getCashierName();
    LocalDateTime getOpenedAt();
    LocalDateTime getClosedAt();
    ShiftStatus getStatus();
}
