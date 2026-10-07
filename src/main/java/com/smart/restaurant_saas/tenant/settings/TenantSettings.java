package com.smart.restaurant_saas.tenant.settings;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Tenant-wide operating configuration; timezone remains on Tenant (D101). */
@Getter
@Setter
@Entity
@Table(name = "tenant_settings", uniqueConstraints =
        @UniqueConstraint(name = "uk_tenant_settings_tenant", columnNames = "tenant_id"))
public class TenantSettings extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tax_enabled", nullable = false)
    private boolean taxEnabled = true;

    /** Percent, not fraction. Retained when tax is disabled. */
    @Column(name = "tax_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal taxRate = new BigDecimal("14.0000");

    /** False: tax on items only. True: include the service charge in the tax base. */
    @Column(name = "tax_on_service_charge", nullable = false)
    private boolean taxOnServiceCharge;

    @Column(name = "service_charge_enabled", nullable = false)
    private boolean serviceChargeEnabled;

    @Column(name = "service_charge_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal serviceChargeRate = new BigDecimal("0.0000");

    @Column(name = "dine_in_enabled", nullable = false)
    private boolean dineInEnabled = true;

    @Column(name = "takeaway_enabled", nullable = false)
    private boolean takeawayEnabled = true;

    @Column(name = "delivery_enabled", nullable = false)
    private boolean deliveryEnabled = true;
}
