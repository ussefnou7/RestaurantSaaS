package com.smart.restaurant_saas.branch;

import com.smart.restaurant_saas.branch.dto.request.CreateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchStatusRequest;
import com.smart.restaurant_saas.branch.dto.response.BranchResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('BRANCHES_VIEW')")
    public List<BranchResponse> listBranches() {
        return branchService.listBranches();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('BRANCHES_CREATE')")
    public BranchResponse createBranch(@Valid @RequestBody CreateBranchRequest request) {
        return branchService.createBranch(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('BRANCHES_VIEW')")
    public BranchResponse getBranch(@PathVariable Long id) {
        return branchService.getBranch(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('BRANCHES_UPDATE')")
    public BranchResponse updateBranch(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBranchRequest request
    ) {
        return branchService.updateBranch(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('BRANCHES_UPDATE')")
    public BranchResponse updateBranchStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBranchStatusRequest request
    ) {
        return branchService.updateBranchStatus(id, request);
    }
}
