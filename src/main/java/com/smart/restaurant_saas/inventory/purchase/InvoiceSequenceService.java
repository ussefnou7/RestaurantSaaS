package com.smart.restaurant_saas.inventory.purchase;

import com.smart.restaurant_saas.common.sequence.TenantSequenceService;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceSequenceService {

    private final TenantSequenceService tenantSequenceService;

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
        return tenantSequenceService.generateDocumentNumber(tenantId, entityPrefix);
    }
}
