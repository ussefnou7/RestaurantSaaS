package com.smart.restaurant_saas.table.section.dto;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.table.section.TableSection;

public record TableSectionResponse(
        Long id,
        Long branchId,
        String name,
        String nameAr,
        Boolean active
) {

    public static TableSectionResponse from(TableSection section) {
        Branch branch = section.getBranch();
        return new TableSectionResponse(
                section.getId(),
                branch == null ? null : branch.getId(),
                section.getName(),
                section.getNameAr(),
                section.getActive()
        );
    }
}
