package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.inventory.core.enums.DocumentType;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentSequenceService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final Clock clock;

    public DocumentSequenceService(JdbcTemplate jdbcTemplate,
                                   TenantTimeZoneService tenantTimeZoneService) {
        this(jdbcTemplate, tenantTimeZoneService, Clock.systemUTC());
    }

    DocumentSequenceService(JdbcTemplate jdbcTemplate,
                            TenantTimeZoneService tenantTimeZoneService,
                            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.clock = clock;
    }

    @Transactional
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
