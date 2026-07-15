package com.smart.restaurant_saas.device.repository;

import com.smart.restaurant_saas.device.Device;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    @EntityGraph(attributePaths = "branch")
    List<Device> findByTenantIdOrderByIdDesc(Long tenantId);

    @EntityGraph(attributePaths = "branch")
    Optional<Device> findByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = "branch")
    Optional<Device> findBySecretKeyHash(String secretKeyHash);
}
