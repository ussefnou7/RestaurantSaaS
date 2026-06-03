package com.smart.restaurant_saas.branch;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.branch.dto.request.CreateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchStatusRequest;
import com.smart.restaurant_saas.branch.dto.response.BranchResponse;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantCodeService;
import com.smart.restaurant_saas.tenant.TenantCodeService.ValidatedCode;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final CurrentTenantProvider currentTenantProvider;
    private final TenantCodeService tenantCodeService;
    private final BranchRepository branchRepository;
    private final UserRoleRepository userRoleRepository;

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
            throw new ApiException(HttpStatus.CONFLICT, "Branch code already exists for tenant: " + code);
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
            throw new ApiException(HttpStatus.CONFLICT, "Branch code already exists for tenant: " + code);
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found: " + branchId));
    }

    private void applyStatusChange(Long tenantId, Branch branch, boolean active) {
        if (Boolean.TRUE.equals(branch.getActive()) && !active) {
            ensureCanDeactivateBranch(tenantId, branch.getId());
        }
        branch.setActive(active);
    }

    private void ensureCanDeactivateBranch(Long tenantId, Long branchId) {
        if (branchRepository.countByTenantIdAndActiveTrue(tenantId) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot deactivate the last active branch in a tenant");
        }

        if (userRoleRepository.existsActiveUserAssignedToBranch(tenantId, branchId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot deactivate a branch with active users assigned to it"
            );
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one of nameEn or nameAr is required");
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
