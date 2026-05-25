package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.JobTitle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobTitleRepository extends JpaRepository<JobTitle, Long> {

    List<JobTitle> findByTenantIdOrderByIdDesc(Long tenantId);

    Optional<JobTitle> findByIdAndTenantId(Long id, Long tenantId);

    Optional<JobTitle> findByIdAndTenantIdAndActiveTrue(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);
}
