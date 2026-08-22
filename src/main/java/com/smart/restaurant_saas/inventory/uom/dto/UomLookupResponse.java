package com.smart.restaurant_saas.inventory.uom.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UomLookupResponse {

    private final String version;
    private final List<UomLookupItemResponse> items;
}
