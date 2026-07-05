package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.waste.WasteDocument;

@Repository
public interface WasteDocumentRepository extends JpaRepository<WasteDocument, Long> {

    Optional<WasteDocument> findByIdAndTenantId(Long id, Long tenantId);

    List<WasteDocument> findByTenantIdOrderByWasteDateDesc(Long tenantId);

    List<WasteDocument> findByTenantIdAndWarehouseIdOrderByWasteDateDesc(
        Long tenantId, Long warehouseId);
}
