package com.smart.restaurant_saas.dashboard.alert;

/** How urgently an owner-dashboard condition needs acting on. Three levels, no more: a fourth */
public enum AlertSeverity {

    /** The numbers are wrong now, or money/food is being lost now. */
    CRITICAL,

    /** Needs action today or it becomes {@link #CRITICAL}. */
    WARNING,

    /** Worth knowing this week. */
    INFO
}
