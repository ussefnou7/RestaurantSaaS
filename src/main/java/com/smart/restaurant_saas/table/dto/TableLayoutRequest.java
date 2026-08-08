package com.smart.restaurant_saas.table.dto;

import com.smart.restaurant_saas.inventory.core.enums.TableShape;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TableLayoutRequest {

    private BigDecimal posX;

    private BigDecimal posY;

    private Integer rotation;

    @NotNull
    private TableShape shape;
}
