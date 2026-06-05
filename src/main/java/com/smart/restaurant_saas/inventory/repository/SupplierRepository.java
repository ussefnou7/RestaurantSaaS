package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.Supplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    Optional<Supplier> findByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);

    Optional<Supplier> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            select supplier
            from Supplier supplier
            where supplier.tenantId = :tenantId
              and (:active is null or supplier.active = :active)
              and (
                  :search is null
                  or lower(supplier.code) like :search
                  or lower(supplier.name) like :search
                  or lower(supplier.nameAr) like :search
                  or lower(supplier.phone) like :search
                  or lower(supplier.email) like :search
                  or lower(supplier.taxNumber) like :search
              )
            order by supplier.id desc
            """)
    List<Supplier> findByTenantIdAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("search") String search,
            @Param("active") Boolean active
    );
}
