package com.smart.restaurant_saas.table.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TableRequest {

    @NotNull
    private Long branchId;

    @NotBlank
    @Size(max = 255)
    private String name;

    private Long sectionId;

    @Min(1)
    private Integer capacity;

    private Boolean active = true;
}
