package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.common.BaseEntity;
import com.smart.restaurant_saas.order.core.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "order_consumption_line")
public class OrderConsumptionLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", nullable = false)
    private OrderConsumption doc;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_line_id", nullable = false)
    private OrderLine orderLine;

    @Column(name = "is_consumed", nullable = false)
    private boolean consumed = false;
}
