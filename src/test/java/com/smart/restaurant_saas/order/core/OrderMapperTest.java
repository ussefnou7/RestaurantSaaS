package com.smart.restaurant_saas.order.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OrderMapperTest {

    private final OrderMapper mapper = new OrderMapper();

    @Test
    void toSummaryExposesPaymentMethod() {
        Order order = order(PaymentMethod.CARD);

        OrderSummaryResponse summary = mapper.toSummary(order);

        assertThat(summary.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(summary.getId()).isEqualTo(900L);
        assertThat(summary.getStatus()).isEqualTo(OrderStatus.COMPLETE);
    }

    private Order order(PaymentMethod paymentMethod) {
        Branch branch = new Branch();
        branch.setId(101L);
        branch.setName("Main Branch");

        Warehouse warehouse = new Warehouse();
        warehouse.setId(202L);
        warehouse.setName("Main Warehouse");

        Order order = new Order();
        order.setId(900L);
        order.setOrderType(OrderType.DINE_IN);
        order.setOrderSource(OrderSource.POS);
        order.setStatus(OrderStatus.COMPLETE);
        order.setPaymentMethod(paymentMethod);
        order.setBranch(branch);
        order.setWarehouse(warehouse);
        order.setTotalAmount(new BigDecimal("90.00"));
        order.setOrderDate(LocalDateTime.of(2026, 7, 10, 12, 0));
        return order;
    }
}
