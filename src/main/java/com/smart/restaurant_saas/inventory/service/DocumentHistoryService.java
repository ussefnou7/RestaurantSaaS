package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;

import com.smart.restaurant_saas.inventory.entity.DocumentHistory;
import com.smart.restaurant_saas.inventory.enums.DocumentHistoryAction;
import com.smart.restaurant_saas.inventory.enums.DocumentType;
import com.smart.restaurant_saas.inventory.repository.DocumentHistoryRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentHistoryService {

    private final DocumentHistoryRepository documentHistoryRepository;

    @Transactional
    public void record(
            Long tenantId,
            DocumentType documentType,
            Long documentId,
            DocumentHistoryAction action,
            Long performedBy,
            String details
    ) {
        DocumentHistory entry = new DocumentHistory();
        entry.setTenantId(tenantId);
        entry.setDocumentType(documentType);
        entry.setDocumentId(documentId);
        entry.setAction(action);
        entry.setPerformedAt(LocalDateTime.now());
        entry.setPerformedBy(performedBy);
        entry.setDetails(trimToNull(details));
        documentHistoryRepository.save(entry);
    }
}
