package com.smart.restaurant_saas.dashboard.alert;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Everything a rule is allowed to know. Resolved once per request by {@link AlertService} and */
public record AlertContext(
    Long tenantId,
    Long branchFilter,
    ZoneId zone,
    LocalDateTime now
) {

    /** {@code now} shifted back by whole hours — the cutoff form every age-based rule needs. */
    public LocalDateTime hoursAgo(long hours) {
        return now.minusHours(hours);
    }

    /** {@code now} shifted back by whole days. */
    public LocalDateTime daysAgo(long days) {
        return now.minusDays(days);
    }

    /** {@code now} shifted back by whole minutes. */
    public LocalDateTime minutesAgo(long minutes) {
        return now.minusMinutes(minutes);
    }
}
