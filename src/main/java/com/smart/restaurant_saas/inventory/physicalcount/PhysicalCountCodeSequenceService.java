package com.smart.restaurant_saas.inventory.physicalcount;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhysicalCountCodeSequenceService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public int next(Long tenantId, Long warehouseId, LocalDate scheduledDate) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO physical_count_code_sequence (
                tenant_id, warehouse_id, scheduled_date, last_seq)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (tenant_id, warehouse_id, scheduled_date)
            DO UPDATE SET last_seq = physical_count_code_sequence.last_seq + 1
            RETURNING last_seq
            """, Integer.class, tenantId, warehouseId, scheduledDate);
    }
}
