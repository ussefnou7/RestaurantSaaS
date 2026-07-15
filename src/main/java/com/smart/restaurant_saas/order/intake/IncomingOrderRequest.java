package com.smart.restaurant_saas.order.intake;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "incoming_order_request")
public class IncomingOrderRequest extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private IncomingOrderSource source;

    @Column(name = "aggregator_name")
    private String aggregatorName;

    @Column(name = "external_reference_id")
    private String externalReferenceId;

    @Column(name = "branch_id")
    private Long branchId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private IncomingOrderRequestStatus status = IncomingOrderRequestStatus.RECEIVED;

    @Column(name = "completed_order_id")
    private Long completedOrderId;

    @Column(name = "sent_to_pos_at")
    private LocalDateTime sentToPosAt;
}
