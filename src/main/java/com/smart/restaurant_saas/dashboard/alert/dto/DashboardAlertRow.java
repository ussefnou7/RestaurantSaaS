package com.smart.restaurant_saas.dashboard.alert.dto;

import com.smart.restaurant_saas.dashboard.alert.AlertLinkTarget;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** One place a condition is true — the row the owner sees when he expands a group. */
@Getter
@Builder
public class DashboardAlertRow {

    /** Null for a scope with no branch — a central warehouse, or a tenant-wide condition. */
    private final Long branchId;
    private final String branchName;
    private final String branchNameAr;

    /** Null unless the condition is per-warehouse. */
    private final Long warehouseId;
    private final String warehouseName;
    private final String warehouseNameAr;

    /** Underlying rows in this condition, in this scope. Always ≥ 1. */
    private final long count;

    /** EGP at scale 6, or null where the condition has no money — never "0" to mean absent. */
    private final String value;

    /** When the oldest instance began, or null where the source records no such moment. */
    private final LocalDateTime oldestAt;

    /** Values for the i18n sentence. The backend ships no user text (D12). */
    private final Map<String, Object> params;

    /** Where this row is cleared, and the filters to land on. */
    private final AlertLinkTarget link;
    private final Map<String, Object> linkParams;
}
