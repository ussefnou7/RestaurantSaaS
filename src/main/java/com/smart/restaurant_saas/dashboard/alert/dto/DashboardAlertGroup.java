package com.smart.restaurant_saas.dashboard.alert.dto;

import com.smart.restaurant_saas.dashboard.alert.AlertCode;
import com.smart.restaurant_saas.dashboard.alert.AlertLinkTarget;
import com.smart.restaurant_saas.dashboard.alert.AlertSeverity;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/** All occurrences of one condition, as one strip row that expands in place. */
@Getter
@Builder
public class DashboardAlertGroup {

    private final AlertCode code;

    /** Copied from the code so the frontend sorts and colours without a lookup table of its own. */
    private final AlertSeverity severity;

    /** Sum of the rows' counts. */
    private final long count;

    /** Sum of the rows' values at scale 6, or null when no row carries one. */
    private final String value;

    /** Whether {@link #getValue()} is derived from an assumption rather than measured — D90's */
    private final boolean valueIsEstimate;

    /** Earliest {@code oldestAt} across the rows, or null when the condition records no age. */
    private final LocalDateTime oldestAt;

    /** Where the condition is cleared. Rows carry the per-scope filters. */
    private final AlertLinkTarget link;

    /** One per scope the condition holds in. Never empty — an empty group is not emitted. */
    @Singular
    private final List<DashboardAlertRow> rows;
}
