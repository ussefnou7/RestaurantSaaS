package com.smart.restaurant_saas.order.intake;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomingOrderRequestRepository extends JpaRepository<IncomingOrderRequest, Long> {

    Optional<IncomingOrderRequest> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
        SELECT r FROM IncomingOrderRequest r
        WHERE r.tenantId = :tenantId
          AND (:source IS NULL OR r.source = :source)
          AND (:status IS NULL OR r.status = :status)
          AND (:branchId IS NULL OR r.branchId = :branchId)
          AND (:fromDate IS NULL OR r.createdAt >= :fromDate)
          AND (:toDate IS NULL OR r.createdAt <= :toDate)
        """)
    Page<IncomingOrderRequest> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("source") IncomingOrderSource source,
        @Param("status") IncomingOrderRequestStatus status,
        @Param("branchId") Long branchId,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        Pageable pageable
    );
}
