package com.smart.restaurant_saas.loyalty.customer.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerResponse {

    private final Long id;
    private final String name;
    private final String phone;
}
