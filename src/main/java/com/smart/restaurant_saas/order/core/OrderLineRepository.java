package com.smart.restaurant_saas.order.core;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByOrderIdAndTenantIdOrderByIdAsc(Long orderId, Long tenantId);
}
