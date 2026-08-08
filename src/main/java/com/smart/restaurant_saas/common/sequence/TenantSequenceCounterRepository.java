package com.smart.restaurant_saas.common.sequence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantSequenceCounterRepository extends JpaRepository<TenantSequenceCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM TenantSequenceCounter s
            WHERE s.tenantId = :tenantId
              AND s.year = :year
              AND s.sequenceKey = :sequenceKey
            """)
    Optional<TenantSequenceCounter> findForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("year") short year,
            @Param("sequenceKey") String sequenceKey
    );
}
