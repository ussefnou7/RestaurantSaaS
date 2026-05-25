package com.smart.restaurant_saas.branch;

import com.smart.restaurant_saas.branch.dto.request.CreateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchStatusRequest;
import com.smart.restaurant_saas.branch.dto.response.BranchResponse;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final CurrentTenantProvider currentTenantProvider;
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
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        String code = normalizeCode(request.code());

        if (branchRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Branch code already exists for tenant: " + code);
        }

        Branch branch = new Branch();
        branch.setTenantId(tenantId);
        branch.setName(request.name().trim());
        branch.setCode(code);
        branch.setAddress(trimToNull(request.address()));
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
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Branch branch = findBranch(tenantId, branchId);
        String code = normalizeCode(request.code());

        if (!branch.getCode().equals(code)
                && branchRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, branchId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Branch code already exists for tenant: " + code);
        }

        branch.setName(request.name().trim());
        branch.setCode(code);
        branch.setAddress(trimToNull(request.address()));
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

    private String normalizeCode(String code) {
        String normalizedCode = code.trim().toLowerCase(Locale.ROOT);
        if (normalizedCode.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch code must not be blank");
        }
        return normalizedCode;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
