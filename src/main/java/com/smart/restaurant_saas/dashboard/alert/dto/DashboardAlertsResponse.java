package com.smart.restaurant_saas.dashboard.alert.dto;

import com.smart.restaurant_saas.dashboard.alert.AlertCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/** The attention strip, whole. */
@Getter
@Builder
public class DashboardAlertsResponse {

    /** Conditions that currently hold, ordered by severity then by money at stake then by count. */
    @Singular
    private final List<DashboardAlertGroup> groups;

    /** Codes not evaluated because the caller lacks the source module's permission. Not an error, */
    // Explicit singular: Lombok cannot derive one from "withheld", and the generated
    // add-one method is unused anyway — the service builds the whole list at once.
    @Singular("withheldCode")
    private final List<AlertCode> withheld;

    /** The tenant-local wall clock the conditions were evaluated against (D101). */
    private final LocalDateTime evaluatedAt;
}
