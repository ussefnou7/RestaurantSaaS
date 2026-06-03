package com.smart.restaurant_saas.hr.service;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.dto.request.CreateLeaveTypeRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateLeaveTypeRequest;
import com.smart.restaurant_saas.hr.dto.response.LeaveTypeResponse;
import com.smart.restaurant_saas.hr.entity.LeaveType;
import com.smart.restaurant_saas.hr.repository.LeaveTypeRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    private final CurrentTenantProvider currentTenantProvider;
    private final LeaveTypeRepository leaveTypeRepository;

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> listLeaveTypes() {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return leaveTypeRepository.findByTenantIdOrderByIdDesc(tenantId).stream()
                .map(LeaveTypeResponse::from)
                .toList();
    }

    @Transactional
    public LeaveTypeResponse createLeaveType(CreateLeaveTypeRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        String code = normalizeCode(request.code());
        if (leaveTypeRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Leave type code already exists for tenant: " + code);
        }

        LeaveType leaveType = new LeaveType();
        leaveType.setTenantId(tenantId);
        leaveType.setCode(code);
        applyFields(
                leaveType,
                request.nameEn(),
                request.nameAr(),
                request.name(),
                request.descriptionEn(),
                request.descriptionAr(),
                request.description(),
                request.defaultDays(),
                request.paid(),
                request.active()
        );
        leaveType.setCreatedBy(currentTenantProvider.getActorUserId());

        return LeaveTypeResponse.from(leaveTypeRepository.save(leaveType));
    }

    @Transactional(readOnly = true)
    public LeaveTypeResponse getLeaveType(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return LeaveTypeResponse.from(findLeaveType(tenantId, id));
    }

    @Transactional
    public LeaveTypeResponse updateLeaveType(Long id, UpdateLeaveTypeRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        LeaveType leaveType = findLeaveType(tenantId, id);
        String code = normalizeCode(request.code());
        if (!leaveType.getCode().equals(code)
                && leaveTypeRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Leave type code already exists for tenant: " + code);
        }

        leaveType.setCode(code);
        applyFields(
                leaveType,
                request.nameEn(),
                request.nameAr(),
                request.name(),
                request.descriptionEn(),
                request.descriptionAr(),
                request.description(),
                request.defaultDays(),
                request.paid(),
                request.active()
        );
        leaveType.setUpdatedBy(currentTenantProvider.getActorUserId());

        return LeaveTypeResponse.from(leaveTypeRepository.saveAndFlush(leaveType));
    }

    @Transactional
    public LeaveTypeResponse updateLeaveTypeStatus(Long id, UpdateActiveStatusRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        LeaveType leaveType = findLeaveType(tenantId, id);
        leaveType.setActive(request.active());
        leaveType.setUpdatedBy(currentTenantProvider.getActorUserId());

        return LeaveTypeResponse.from(leaveTypeRepository.saveAndFlush(leaveType));
    }

    private LeaveType findLeaveType(Long tenantId, Long id) {
        return leaveTypeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave type not found: " + id));
    }

    private void applyFields(
            LeaveType leaveType,
            String requestedNameEn,
            String requestedNameAr,
            String legacyName,
            String requestedDescriptionEn,
            String requestedDescriptionAr,
            String legacyDescription,
            BigDecimal defaultDays,
            Boolean paid,
            Boolean active
    ) {
        String nameEn = firstNonBlank(requestedNameEn, legacyName);
        String nameAr = trimToNull(requestedNameAr);
        if (firstNonBlank(nameEn, nameAr) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one of nameEn or nameAr is required");
        }

        leaveType.setNameEn(nameEn);
        leaveType.setNameAr(nameAr);
        leaveType.setDescriptionEn(firstNonBlank(requestedDescriptionEn, legacyDescription));
        leaveType.setDescriptionAr(trimToNull(requestedDescriptionAr));
        leaveType.setDefaultDays(defaultDays == null ? BigDecimal.ZERO : defaultDays);
        if (paid != null) {
            leaveType.setPaid(paid);
        }
        if (active != null) {
            leaveType.setActive(active);
        }
    }

    private String normalizeCode(String code) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalizedCode.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code must not be blank");
        }
        if (normalizedCode.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code must be at most 100 characters");
        }
        return normalizedCode;
    }
}
