package com.smart.restaurant_saas.dashboard.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** One scope's worth of a condition — what a rule returns. */
public record AlertOccurrence(
    AlertCode code,
    Long branchId,
    Long warehouseId,
    long count,
    BigDecimal value,
    LocalDateTime oldestAt,
    Map<String, Object> params,
    Map<String, Object> linkParams
) {

}
