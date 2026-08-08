package com.smart.restaurant_saas.order.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.menu.product.Product;
import com.smart.restaurant_saas.menu.recipe.Recipe;
import com.smart.restaurant_saas.order.core.dto.OrderResponse;
import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderCancellationReason;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void toSummary_exposesOrderNoAndCreatedBy() {
        Order order = order(PaymentMethod.CASH);
        order.setOrderNo("POS-1036");
        order.setCreatedBy(11L);

        OrderSummaryResponse summary = mapper.toSummary(order);

        assertThat(summary.getOrderNo()).isEqualTo("POS-1036");
        assertThat(summary.getCreatedBy()).isEqualTo(11L);
    }

    @Test
    void toSummary_nullOrderNoAndCreatedBy_returnedAsNull() {
        Order order = order(PaymentMethod.CASH);

        OrderSummaryResponse summary = mapper.toSummary(order);

        assertThat(summary.getOrderNo()).isNull();
        assertThat(summary.getCreatedBy()).isNull();
    }

    @Test
    void toSummary_populatesLines() {
        Order order = order(PaymentMethod.CASH);
        order.getLines().add(line(order, "Burger", 2, "15.00"));

        OrderSummaryResponse summary = mapper.toSummary(order);

        assertThat(summary.getLines()).hasSize(1);
        assertThat(summary.getLines().get(0).getProductName()).isEqualTo("Burger");
        assertThat(summary.getLines().get(0).getQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void toResponse_includesOrderNo() {
        Order order = order(PaymentMethod.CASH);
        order.setOrderNo("POS-2000");

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.getOrderNo()).isEqualTo("POS-2000");
    }

    @Test
    void toResponse_includesCancellationReasonAndCustomerId() {
        Order order = order(PaymentMethod.CASH);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationStage(CancellationStage.BEFORE_KITCHEN);
        order.setCancellationReason(OrderCancellationReason.OTHER);
        order.setCancellationReasonNote("Customer changed their mind");
        order.setCustomerId(555L);

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.getCancellationStage()).isEqualTo(CancellationStage.BEFORE_KITCHEN);
        assertThat(response.getCancellationReason()).isEqualTo(OrderCancellationReason.OTHER);
        assertThat(response.getCancellationReasonNote()).isEqualTo("Customer changed their mind");
        assertThat(response.getCustomerId()).isEqualTo(555L);
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

    private OrderLine line(Order order, String productName, int qty, String unitPrice) {
        Product product = new Product();
        product.setId(1L);
        product.setName(productName);

        Recipe recipe = new Recipe();
        recipe.setId(10L);

        BigDecimal q = new BigDecimal(qty);
        BigDecimal up = new BigDecimal(unitPrice);

        OrderLine line = new OrderLine();
        line.setOrder(order);
        line.setProduct(product);
        line.setRecipe(recipe);
        line.setQuantity(q);
        line.setUnitPrice(up);
        line.setLineTotal(q.multiply(up));
        return line;
    }
}
