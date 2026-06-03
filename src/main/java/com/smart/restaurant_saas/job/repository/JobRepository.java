package com.smart.restaurant_saas.job.repository;

import com.smart.restaurant_saas.job.entity.Job;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByTenantIdOrderByIdDesc(Long tenantId);

    Optional<Job> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Job> findByIdAndTenantIdAndActiveTrue(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);
}
