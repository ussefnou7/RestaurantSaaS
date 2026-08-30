package com.smart.restaurant_saas.inventory.core;

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
import com.smart.restaurant_saas.inventory.core.enums.DocumentHistoryAction;
import com.smart.restaurant_saas.inventory.core.enums.DocumentType;

@Getter
@Setter
@Entity
@Table(name = "document_history")
public class DocumentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private DocumentHistoryAction action;

    /**
     * When the action happened, in the tenant's wall clock. Set this explicitly from
     * {@code LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId))} at the call site.
     *
     * <p>There is deliberately no {@code @PrePersist} default. An entity callback cannot see the
     * tenant's zone — it would have to fall back to the JVM's, which writes a plausible-looking
     * wrong value rather than failing, and that silent-default failure mode is exactly what D101
     * exists to prevent. Leaving this null fails loudly on a NOT NULL column instead.
     */
    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "details", columnDefinition = "text")
    private String details;
}
