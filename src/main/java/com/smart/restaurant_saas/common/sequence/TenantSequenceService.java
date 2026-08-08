package com.smart.restaurant_saas.common.sequence;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
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
public class TenantSequenceService {

    private static final short ENTITY_CODE_YEAR_BUCKET = 0;

    private final TenantSequenceCounterRepository counterRepository;
    private final TenantRepository tenantRepository;
    private final TenantCodeService tenantCodeService;

    @Transactional
    public String generateDocumentNumber(Long tenantId, TenantEntityPrefix entityPrefix) {
        Tenant tenant = findTenant(tenantId);
        short year = (short) Year.now().getValue();
        int next = increment(tenantId, year, entityPrefix.name());
        String prefix = tenantCodeService.buildPrefix(tenant.getCode(), entityPrefix);
        return prefix + year + "-" + String.format("%04d", next);
    }

    @Transactional
    public String generateEntityCode(Long tenantId, TenantEntityPrefix entityPrefix) {
        Tenant tenant = findTenant(tenantId);
        int next = increment(tenantId, ENTITY_CODE_YEAR_BUCKET, entityPrefix.name());
        String prefix = tenantCodeService.buildPrefix(tenant.getCode(), entityPrefix);
        return prefix + String.format("%04d", next);
    }

    private Tenant findTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ValidationException(InventoryErrorCode.VALIDATION_FAILED,
                        "Invalid tenant id: " + tenantId,
                        ErrorParams.of("entityType", "Tenant", "entityId", tenantId)));
    }

    private int increment(Long tenantId, short year, String sequenceKey) {
        TenantSequenceCounter counter = counterRepository.findForUpdate(tenantId, year, sequenceKey)
                .orElseGet(() -> newCounter(tenantId, year, sequenceKey));
        return saveNext(counter);
    }

    private int saveNext(TenantSequenceCounter counter) {
        int next = counter.getLastSeq() + 1;
        counter.setLastSeq(next);
        counterRepository.save(counter);
        return next;
    }

    private TenantSequenceCounter newCounter(Long tenantId, short year, String sequenceKey) {
        TenantSequenceCounter counter = new TenantSequenceCounter();
        counter.setTenantId(tenantId);
        counter.setYear(year);
        counter.setSequenceKey(sequenceKey);
        counter.setLastSeq(0);
        return counter;
    }
}
