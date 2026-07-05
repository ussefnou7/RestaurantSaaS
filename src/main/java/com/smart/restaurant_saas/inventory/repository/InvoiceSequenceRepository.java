package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.purchase.InvoiceSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.tenantId = :tenantId "
         + "AND s.year = :year AND s.docType = :docType")
    Optional<InvoiceSequence> findForUpdate(@Param("tenantId") Long tenantId,
                                            @Param("year") short year,
                                            @Param("docType") String docType);
}
