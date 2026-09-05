package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One order as it appears on the shift detail screen (D125). */
public interface ShiftOrderProjection {

    Long getId();
    String getOrderNo();
    LocalDateTime getOrderDate();
    OrderStatus getStatus();
    String getPaymentMethod();
    BigDecimal getTotalAmount();
    Long getCreatedBy();
    String getCreatedByName();
}
