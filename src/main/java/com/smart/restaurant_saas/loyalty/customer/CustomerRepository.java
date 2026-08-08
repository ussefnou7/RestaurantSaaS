package com.smart.restaurant_saas.loyalty.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByTenantIdOrderByIdDesc(Long tenantId);

    @Query("""
        SELECT c FROM Customer c
        WHERE c.tenantId = :tenantId
          AND (CAST(:search AS string) IS NULL
               OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR c.phone LIKE CONCAT('%', CAST(:search AS string), '%'))
        """)
    Page<Customer> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("search") String search,
        Pageable pageable
    );

    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Customer> findByTenantIdAndPhone(Long tenantId, String phone);
}
