package com.smart.restaurant_saas.inventory.physicalcount.dto;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReconcileCountRequest {

    // Only lines with a variance need to be included.
    // Lines not included default to ADJUSTMENT.
    @Valid
    private List<ReconcileLineAction> lines = new ArrayList<>();
}
