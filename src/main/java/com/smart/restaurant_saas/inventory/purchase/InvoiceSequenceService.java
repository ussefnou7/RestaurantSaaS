package com.smart.restaurant_saas.inventory.purchase;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.repository.InvoiceSequenceRepository;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantCodeService;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import com.smart.restaurant_saas.tenant.TenantRepository;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceSequenceService {

    private final InvoiceSequenceRepository sequenceRepository;
    private final TenantRepository tenantRepository;
    private final TenantCodeService tenantCodeService;

    @Transactional
    public String generateInvoiceNumber(Long tenantId) {
        return generateNumber(tenantId, TenantEntityPrefix.PINV);
    }

    @Transactional
    public String generateReturnNumber(Long tenantId) {
        return generateNumber(tenantId, TenantEntityPrefix.PRET);
    }

    @Transactional
    public String generateWasteNumber(Long tenantId) {
        return generateNumber(tenantId, TenantEntityPrefix.WST);
    }

    /**
     * Generates the next per-tenant/per-year number for a document type, formatted as
     * {@code <tenantPrefix><year>-<0000seq>}. The counter is isolated per {@code entityPrefix}
     * (doc_type), so different document types never share or interleave sequences.
     */
    private String generateNumber(Long tenantId, TenantEntityPrefix entityPrefix) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ValidationException(InventoryErrorCode.VALIDATION_FAILED,
                        "Invalid tenant id: " + tenantId,
                        ErrorParams.of("entityType", "Tenant", "entityId", tenantId)));

        short year = (short) Year.now().getValue();
        String docType = entityPrefix.name();

        InvoiceSequence sequence = sequenceRepository.findForUpdate(tenantId, year, docType)
                .orElseGet(() -> {
                    InvoiceSequence created = new InvoiceSequence();
                    created.setTenantId(tenantId);
                    created.setYear(year);
                    created.setDocType(docType);
                    created.setLastSeq(0);
                    return created;
                });

        int next = sequence.getLastSeq() + 1;
        sequence.setLastSeq(next);
        sequenceRepository.save(sequence);

        String prefix = tenantCodeService.buildPrefix(tenant.getCode(), entityPrefix);
        return prefix + year + "-" + String.format("%04d", next);
    }
}
