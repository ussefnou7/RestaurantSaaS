package com.smart.restaurant_saas.dashboard.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * One status of the consumption pipeline.
 *
 * <p>A census rather than a problem list: POSTED and PENDING are included precisely because the
 * owner is reading the shape of the pipeline, not a set of conditions — the alerts already cover
 * the conditions. A pipeline that is almost all POSTED is healthy; one with a growing PENDING tail
 * is a scheduler slowing down, and neither of those facts is visible from the alert strip.
 *
 * <p>{@link #getOldestAt()} is what makes the count readable. Forty pending documents whose oldest
 * is four minutes old is a pipeline working normally; forty whose oldest is nine hours old has
 * stopped, and the count alone cannot tell the two apart.
 */
@Getter
@Builder
public class ConsumptionPipelineRow {

    /** Raw status name — the frontend owns the translation key. */
    private final String status;

    private final Long docCount;

    private final LocalDateTime oldestAt;
}
