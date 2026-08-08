package com.smart.restaurant_saas.table;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.TenantAwareEntity;
import com.smart.restaurant_saas.inventory.core.enums.TableShape;
import com.smart.restaurant_saas.table.section.TableSection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "restaurant_table")
public class RestaurantTable extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private TableSection section;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "shape", nullable = false, length = 30)
    private TableShape shape = TableShape.SQUARE;

    @Column(name = "pos_x", precision = 10, scale = 2)
    private BigDecimal posX;

    @Column(name = "pos_y", precision = 10, scale = 2)
    private BigDecimal posY;

    @Column(name = "rotation")
    private Integer rotation;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
}
