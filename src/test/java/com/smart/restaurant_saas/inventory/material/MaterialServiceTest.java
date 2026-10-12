package com.smart.restaurant_saas.inventory.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.common.sequence.TenantSequenceService;
import com.smart.restaurant_saas.inventory.mapper.MaterialMapper;
import com.smart.restaurant_saas.inventory.repository.MaterialCategoryRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.service.setup.MaterialService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * The invoice filter reveals what a purchase invoice contained, so it needs purchase-view on top
 * of setup-view. That condition depends on a parameter's value, which the endpoint's
 * {@code @PreAuthorize} cannot express without breaking the permission-manifest contract — so it
 * is enforced here, and tested here.
 */
@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long INVOICE_ID = 42L;

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private MaterialCategoryRepository categoryRepository;
    @Mock
    private UomRepository uomRepository;
    @Mock
    private TenantSequenceService tenantSequenceService;
    @Mock
    private SecurityService securityService;

    private MaterialService service;

    @BeforeEach
    void setUp() {
        service = new MaterialService(materialRepository, categoryRepository, uomRepository,
            new MaterialMapper(), tenantSequenceService, securityService);
    }

    @Test
    void invoiceFilterRequiresPurchaseView() {
        when(securityService.isSysAdmin()).thenReturn(false);
        when(securityService.hasPermission("INVENTORY_PURCHASE_VIEW")).thenReturn(false);

        assertThatThrownBy(() -> service.findAll(TENANT_ID, null, null, null, null, INVOICE_ID))
            .isInstanceOf(AccessDeniedException.class);

        verify(materialRepository, never())
            .findByFilters(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invoiceFilterIsAllowedWithPurchaseView() {
        when(securityService.isSysAdmin()).thenReturn(false);
        when(securityService.hasPermission("INVENTORY_PURCHASE_VIEW")).thenReturn(true);
        when(materialRepository.findByFilters(TENANT_ID, null, null, null, null, INVOICE_ID))
            .thenReturn(List.of());

        assertThat(service.findAll(TENANT_ID, null, null, null, null, INVOICE_ID)).isEmpty();
    }

    @Test
    void sysAdminBypassesThePurchaseViewRequirement() {
        when(securityService.isSysAdmin()).thenReturn(true);
        when(materialRepository.findByFilters(TENANT_ID, null, null, null, null, INVOICE_ID))
            .thenReturn(List.of());

        assertThat(service.findAll(TENANT_ID, null, null, null, null, INVOICE_ID)).isEmpty();
    }

    @Test
    void anUnfilteredListNeverConsultsThePurchasePermission() {
        when(materialRepository.findByFilters(TENANT_ID, null, null, null, null, null))
            .thenReturn(List.of());

        assertThat(service.findAll(TENANT_ID, null, null, null, null, null)).isEmpty();

        verify(securityService, never()).hasPermission("INVENTORY_PURCHASE_VIEW");
    }
}
