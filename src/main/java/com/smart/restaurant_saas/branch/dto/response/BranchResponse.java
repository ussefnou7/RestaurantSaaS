package com.smart.restaurant_saas.branch.dto.response;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.englishOrLegacy;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;

import com.smart.restaurant_saas.branch.Branch;
import java.time.LocalDateTime;

public record BranchResponse(
        Long id,
        String name,
        String nameEn,
        String nameAr,
        String code,
        String address,
        String addressEn,
        String addressAr,
        String phone,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BranchResponse from(Branch branch) {
        String nameEn = englishOrLegacy(branch.getNameEn(), branch.getNameAr(), branch.getName());
        String addressEn = englishOrLegacy(branch.getAddressEn(), branch.getAddressAr(), branch.getAddress());
        return new BranchResponse(
                branch.getId(),
                firstNonBlank(branch.getName(), nameEn, branch.getNameAr()),
                nameEn,
                branch.getNameAr(),
                branch.getCode(),
                firstNonBlank(branch.getAddress(), addressEn, branch.getAddressAr()),
                addressEn,
                branch.getAddressAr(),
                branch.getPhone(),
                branch.getActive(),
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }
}
