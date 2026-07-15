package com.smart.restaurant_saas.assets.asset;

import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.core.enums.AssetStatus;
import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Asset header (D46): a purchased item <em>type</em> (e.g. "Wood Chair", "Oven"). Each purchase
 * event is a separate {@code AssetLine} under it. {@code status} is derived (D48), recomputed as
 * an aggregate over the child lines after every mutation — never free-form writable.
 */
@Getter
@Setter
@Entity
@Table(name = "asset")
public class Asset extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_ar")
    private String nameAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private AssetCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AssetStatus status = AssetStatus.ACTIVE;
}
