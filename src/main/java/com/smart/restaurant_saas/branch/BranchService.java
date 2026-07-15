package com.smart.restaurant_saas.branch;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.branch.dto.request.CreateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchStatusRequest;
import com.smart.restaurant_saas.branch.dto.response.BranchResponse;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.hr.service.HrErrorCode;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantCodeService;
import com.smart.restaurant_saas.tenant.TenantCodeService.ValidatedCode;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final CurrentTenantProvider currentTenantProvider;
    private final TenantCodeService tenantCodeService;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BranchResponse> listBranches() {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return branchRepository.findByTenantIdOrderByIdDesc(tenantId).stream()
                .map(BranchResponse::from)
                .toList();
    }

    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        ValidatedCode validatedCode = tenantCodeService.validateAndNormalizeCode(
                request.code(),
                TenantEntityPrefix.BR
        );
        Long tenantId = validatedCode.tenantId();
        String code = validatedCode.code();

        if (branchRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "Branch code already exists for tenant: " + code,
                    ErrorParams.of("entityType", "Branch", "code", code));
        }

        Branch branch = new Branch();
        branch.setTenantId(tenantId);
        applyBilingualFields(branch, request.nameEn(), request.nameAr(), request.name(),
                request.addressEn(), request.addressAr(), request.address());
        branch.setCode(code);
        branch.setPhone(trimToNull(request.phone()));
        branch.setActive(request.active() == null || request.active());

        return BranchResponse.from(branchRepository.save(branch));
    }

    @Transactional(readOnly = true)
    public BranchResponse getBranch(Long branchId) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return BranchResponse.from(findBranch(tenantId, branchId));
    }

    @Transactional
    public BranchResponse updateBranch(Long branchId, UpdateBranchRequest request) {
        ValidatedCode validatedCode = tenantCodeService.validateAndNormalizeCode(
                request.code(),
                TenantEntityPrefix.BR
        );
        Long tenantId = validatedCode.tenantId();
        Branch branch = findBranch(tenantId, branchId);
        String code = validatedCode.code();

        if (!branch.getCode().equals(code)
                && branchRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, branchId)) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "Branch code already exists for tenant: " + code,
                    ErrorParams.of("entityType", "Branch", "code", code));
        }

        applyBilingualFields(branch, request.nameEn(), request.nameAr(), request.name(),
                request.addressEn(), request.addressAr(), request.address());
        branch.setCode(code);
        branch.setPhone(trimToNull(request.phone()));
        if (request.active() != null) {
            applyStatusChange(tenantId, branch, request.active());
        }

        return BranchResponse.from(branchRepository.saveAndFlush(branch));
    }

    @Transactional
    public BranchResponse updateBranchStatus(Long branchId, UpdateBranchStatusRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Branch branch = findBranch(tenantId, branchId);

        applyStatusChange(tenantId, branch, request.active());

        return BranchResponse.from(branchRepository.saveAndFlush(branch));
    }

    private Branch findBranch(Long tenantId, Long branchId) {
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Branch not found: " + branchId,
                        ErrorParams.of("entityType", "Branch", "entityId", branchId)));
    }

    private void applyStatusChange(Long tenantId, Branch branch, boolean active) {
        if (Boolean.TRUE.equals(branch.getActive()) && !active) {
            ensureCanDeactivateBranch(tenantId, branch.getId());
        }
        branch.setActive(active);
    }

    private void ensureCanDeactivateBranch(Long tenantId, Long branchId) {
        if (branchRepository.countByTenantIdAndActiveTrue(tenantId) <= 1) {
            throw new BusinessException(HrErrorCode.DEACTIVATION_BLOCKED,
                    "Cannot deactivate the last active branch in a tenant",
                    ErrorParams.of("entityType", "Branch", "blockedByEntityType", "last_active_branch"));
        }

        if (userRepository.existsByTenantIdAndBranchIdAndStatus(tenantId, branchId, UserStatus.ACTIVE)) {
            throw new BusinessException(HrErrorCode.DEACTIVATION_BLOCKED,
                    "Cannot deactivate a branch with active users assigned to it",
                    ErrorParams.of("entityType", "Branch", "blockedByEntityType", "ActiveUser"));
        }
    }

    private void applyBilingualFields(
            Branch branch,
            String requestedNameEn,
            String requestedNameAr,
            String legacyName,
            String requestedAddressEn,
            String requestedAddressAr,
            String legacyAddress
    ) {
        String nameEn = firstNonBlank(requestedNameEn, legacyName);
        String nameAr = trimToNull(requestedNameAr);
        String displayName = firstNonBlank(nameEn, nameAr);
        if (displayName == null) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "At least one of nameEn or nameAr is required",
                    ErrorParams.of("field", "name"));
        }

        String addressEn = firstNonBlank(requestedAddressEn, legacyAddress);
        String addressAr = trimToNull(requestedAddressAr);

        branch.setName(displayName);
        branch.setNameEn(nameEn);
        branch.setNameAr(nameAr);
        branch.setAddress(firstNonBlank(addressEn, addressAr));
        branch.setAddressEn(addressEn);
        branch.setAddressAr(addressAr);
    }
}
