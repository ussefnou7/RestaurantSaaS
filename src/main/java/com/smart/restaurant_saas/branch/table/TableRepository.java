package com.smart.restaurant_saas.branch.table;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByTenantIdOrderByBranchIdAscTableNoAsc(Long tenantId);

    List<RestaurantTable> findByTenantIdAndBranchIdOrderByTableNoAsc(Long tenantId, Long branchId);

    Optional<RestaurantTable> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndBranchIdAndTableNo(Long tenantId, Long branchId, String tableNo);

    boolean existsByTenantIdAndBranchIdAndTableNoAndIdNot(Long tenantId, Long branchId, String tableNo, Long id);
}
