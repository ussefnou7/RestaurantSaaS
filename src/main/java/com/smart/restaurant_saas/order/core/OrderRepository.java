package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"branch", "warehouse", "lines", "lines.product", "lines.recipe"})
    Optional<Order> findByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"branch", "warehouse"})
    @Query("""
        SELECT o FROM RestaurantOrder o
        WHERE o.tenantId = :tenantId
          AND (:orderType IS NULL OR o.orderType = :orderType)
          AND (:orderSource IS NULL OR o.orderSource = :orderSource)
          AND (:status IS NULL OR o.status = :status)
          AND (:branchId IS NULL OR o.branch.id = :branchId)
          AND (CAST(:fromDate AS timestamp) IS NULL OR o.orderDate >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR o.orderDate <= :toDate)
        """)
    Page<Order> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("orderType") OrderType orderType,
        @Param("orderSource") OrderSource orderSource,
        @Param("status") OrderStatus status,
        @Param("branchId") Long branchId,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        Pageable pageable
    );

    /**
     * Aggregates COMPLETE orders for a shift, grouped by payment method.
     * Uses native SQL so this compiles before {@code orders.shift_id} is present
     * on the Java entity — the column is added in V23.
     */
    @Query(nativeQuery = true, value = """
        SELECT payment_method  AS paymentMethod,
               COALESCE(SUM(total_amount), 0) AS total,
               COUNT(*)        AS orderCount
        FROM   orders
        WHERE  shift_id  = :shiftId
          AND  tenant_id = :tenantId
          AND  status    = 'COMPLETE'
        GROUP BY payment_method
        """)
    List<PaymentMethodSummaryProjection> aggregateByShift(
            @Param("shiftId") Long shiftId,
            @Param("tenantId") Long tenantId
    );
}
