package com.smart.restaurant_saas.inventory.physicalcount.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCountedQuantitiesRequest {

    @Valid
    @NotEmpty(message = "at least one line is required")
    private List<UpdateCountedQuantityRequest> lines;
}
