package com.smart.restaurant_saas.inventory.physicalcount.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.CountLineAction;

@Getter
@Setter
public class ReconcileLineAction {

    @NotNull(message = "lineId is required")
    private Long lineId;

    // ADJUSTMENT or WASTE only — WASTE valid only when variance < 0
    @NotNull(message = "action is required")
    private CountLineAction action;
}
