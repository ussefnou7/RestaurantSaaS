package com.smart.restaurant_saas.expense.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoidExpenseRequest {

    @Size(max = 500, message = "reason must not exceed 500 characters")
    private String reason;
}
