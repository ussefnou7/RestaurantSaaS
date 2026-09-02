package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.inventory.core.enums.DocumentType;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentSequenceService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final Clock clock;

    /**
     * Must stay annotated: the {@code Clock} overload below makes this a two-constructor bean, and
     * Spring only infers a constructor when there is exactly one. Without this it falls back to
     * looking for a no-arg constructor and the whole application context fails to start.
     */
    @Autowired
    public DocumentSequenceService(JdbcTemplate jdbcTemplate,
                                   TenantTimeZoneService tenantTimeZoneService) {
        this(jdbcTemplate, tenantTimeZoneService, Clock.systemUTC());
    }

    /** Package-private, for tests that need a deterministic tenant-year. */
    DocumentSequenceService(JdbcTemplate jdbcTemplate,
                            TenantTimeZoneService tenantTimeZoneService,
                            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(Long tenantId, DocumentType documentType) {
        int year = LocalDate.now(clock.withZone(tenantTimeZoneService.zoneFor(tenantId))).getYear();
        int sequence = jdbcTemplate.queryForObject("""
            INSERT INTO document_sequence (tenant_id, document_type, year, seq)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (tenant_id, document_type, year)
            DO UPDATE SET seq = document_sequence.seq + 1
            RETURNING seq
            """, Integer.class, tenantId, documentType.name(), year);
        return "%s/%02d/%06d".formatted(documentType.codePrefix(), year % 100, sequence);
    }
}
