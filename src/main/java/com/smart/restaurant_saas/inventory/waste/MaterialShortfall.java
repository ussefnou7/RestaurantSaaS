package com.smart.restaurant_saas.inventory.waste;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record MaterialShortfall(
    @JsonProperty("materialId")        Long materialId,
    @JsonProperty("materialName")      String materialName,
    @JsonProperty("requiredQty")       BigDecimal requiredQty,
    @JsonProperty("availableQty")      BigDecimal availableQty,
    @JsonProperty("shortfallQty")      BigDecimal shortfallQty,
    @JsonProperty("uomSymbol")         String uomSymbol,
    @JsonProperty("notStockedInWarehouse") boolean notStockedInWarehouse
) {}
