package com.smart.restaurant_saas.order.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * The two things printed on the customer's receipt, submitted together to pull that one order back
 * up — the cashier's only route to order history once the POS stops keeping any.
 *
 * <p><b>Both fields are required, and that is the whole access control.</b> {@code orderNo} is a
 * per-device display counter — 1, 2, 3 — so a lookup keyed on it alone would let a cashier walk the
 * numbers and read off the per-order amounts that {@code SHIFTS_VIEW_VARIANCE} exists to withhold
 * (D123): summing the CASH ones and adding the opening float is the expected figure. Requiring the
 * printed total inverts that. The caller asserts what they are already holding, so a match returns
 * a receipt they can already read, and a caller without the receipt has to guess the one number
 * being protected.
 *
 * <p>Because possession is what the pair proves, the response is the full order — lines, prices,
 * tax, total — rather than a redacted one. Withholding the contents of a receipt from the person
 * holding it protects nothing; the match is the gate, not the payload. What still protects the
 * blind count is that a wrong amount is indistinguishable from an order that does not exist, and
 * that repeated failures are throttled.
 */
@Getter
@Setter
public class ReceiptLookupRequest {

    @NotBlank(message = "orderNo is required")
    @Size(max = 50, message = "orderNo must be at most 50 characters")
    private String orderNo;

    /**
     * The printed total, matched exactly. Scale does not matter — the comparison is numeric, so
     * 228, 228.0 and 228.00 are the same figure.
     */
    @NotNull(message = "totalAmount is required")
    @DecimalMin(value = "0.00", message = "totalAmount must be non-negative")
    @Digits(integer = 16, fraction = 2, message = "totalAmount must have at most 2 decimal places")
    private BigDecimal totalAmount;
}
