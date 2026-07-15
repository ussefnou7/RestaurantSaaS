package com.smart.restaurant_saas.loyalty.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByTenantIdOrderByIdDesc(Long tenantId);

    Optional<Customer> findByTenantIdAndPhone(Long tenantId, String phone);
}
