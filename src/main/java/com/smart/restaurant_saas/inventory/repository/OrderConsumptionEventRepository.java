package com.smart.restaurant_saas.inventory.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.core.OrderConsumptionEvent;

@Repository
public interface OrderConsumptionEventRepository extends JpaRepository<OrderConsumptionEvent, Long> {

    Optional<OrderConsumptionEvent> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);
}
