package com.smart.restaurant_saas.common.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "invoice_sequence",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_invoice_sequence_tenant_year_doctype",
                columnNames = {"tenant_id", "year", "doc_type"})
)
public class TenantSequenceCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "year", nullable = false)
    private Short year;

    @Column(name = "doc_type", nullable = false, length = 20)
    private String sequenceKey;

    @Column(name = "last_seq", nullable = false)
    private Integer lastSeq = 0;
}
