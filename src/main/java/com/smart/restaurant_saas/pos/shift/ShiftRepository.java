package com.smart.restaurant_saas.pos.shift;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    Optional<Shift> findByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"branch", "cashierUser"})
    Optional<Shift> findByCashierUserIdAndTenantIdAndStatus(
            Long cashierUserId, Long tenantId, ShiftStatus status);
}
