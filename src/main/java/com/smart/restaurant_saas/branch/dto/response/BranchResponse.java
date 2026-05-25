package com.smart.restaurant_saas.branch.dto.response;

import com.smart.restaurant_saas.branch.Branch;
import java.time.LocalDateTime;

public record BranchResponse(
        Long id,
        String name,
        String code,
        String address,
        String phone,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getCode(),
                branch.getAddress(),
                branch.getPhone(),
                branch.getActive(),
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }
}
