package com.smart.restaurant_saas.table.section.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TableSectionRequest {

    @NotNull
    private Long branchId;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String nameAr;
}
