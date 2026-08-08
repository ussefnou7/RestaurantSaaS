package com.smart.restaurant_saas.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.time.ZoneId;
import org.mockito.quality.Strictness;

/**
 * A {@link TenantTimeZoneService} pinned to one zone, for unit tests whose subject now needs a zone
 * but whose assertions are about something else entirely.
 *
 * <p>Lenient on purpose: most of these tests exercise one method and never reach a timestamp, and a
 * strict stub would fail them for the unused stubbing rather than for anything real. Tests that
 * actually care which zone was used should assert on the stored value instead — see
 * {@code TenantTimeZoneServiceTest} and {@code TenantTimestampListenerTest}.
 */
public final class TestZones {

    public static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    public static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    public static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");

    private TestZones() {}

    public static TenantTimeZoneService fixedAt(ZoneId zone) {
        TenantTimeZoneService service =
            mock(TenantTimeZoneService.class, withSettings().strictness(Strictness.LENIENT));
        when(service.zoneFor(any())).thenReturn(zone);
        when(service.zoneFor(any(), any())).thenReturn(zone);
        when(service.systemZone()).thenReturn(zone);
        return service;
    }

    /** The common case: tests that need a zone but do not care which. */
    public static TenantTimeZoneService cairo() {
        return fixedAt(CAIRO);
    }
}
