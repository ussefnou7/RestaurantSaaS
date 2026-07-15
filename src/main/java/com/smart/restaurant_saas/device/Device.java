package com.smart.restaurant_saas.device;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "device",
    uniqueConstraints = @UniqueConstraint(name = "uk_device_secret_key_hash", columnNames = "secret_key_hash")
)
public class Device extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "secret_key_hash", nullable = false, unique = true, length = 64)
    private String secretKeyHash;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
