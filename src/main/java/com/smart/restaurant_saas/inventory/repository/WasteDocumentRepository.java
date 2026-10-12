package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.waste.WasteDocument;

@Repository
public interface WasteDocumentRepository extends JpaRepository<WasteDocument, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM WasteDocument d WHERE d.id = :id AND d.tenantId = :tenantId")
    Optional<WasteDocument> findByIdAndTenantIdForUpdate(
        @Param("id") Long id, @Param("tenantId") Long tenantId);

    Optional<WasteDocument> findByIdAndTenantId(Long id, Long tenantId);

    List<WasteDocument> findByTenantIdOrderByWasteDateDesc(Long tenantId);

    List<WasteDocument> findByTenantIdAndWarehouseIdOrderByWasteDateDesc(
        Long tenantId, Long warehouseId);
}
