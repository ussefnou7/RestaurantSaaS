package com.smart.restaurant_saas.menu.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.menu.MenuErrorCode;
import com.smart.restaurant_saas.menu.product.dto.ProductAddOnResponse;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductAddOnServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long HOST_ID = 100L;
    private static final Long ADDON_ID = 200L;

    @Mock
    private ProductAddOnRepository addOnRepository;
    @Mock
    private ProductRepository productRepository;

    private ProductAddOnService service;

    @BeforeEach
    void setUp() {
        service = new ProductAddOnService(addOnRepository, productRepository);
    }

    @Test
    void selfLinkIsRejected() {
        when(productRepository.findByIdAndTenantId(HOST_ID, TENANT_ID))
            .thenReturn(Optional.of(product(HOST_ID, null)));

        assertThatThrownBy(() -> service.create(HOST_ID, HOST_ID, TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.ADDON_CANNOT_BE_SELF);

        verify(addOnRepository, never()).save(any());
    }

    @Test
    void nonParentEligibleHostIsRejected() {
        when(productRepository.findByIdAndTenantId(HOST_ID, TENANT_ID))
            .thenReturn(Optional.of(product(HOST_ID, 999L)));

        assertThatThrownBy(() -> service.create(HOST_ID, ADDON_ID, TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.ADDON_HOST_MUST_BE_PARENT_ELIGIBLE);

        verify(addOnRepository, never()).save(any());
    }

    @Test
    void duplicateLinkIsRejected() {
        when(productRepository.findByIdAndTenantId(HOST_ID, TENANT_ID))
            .thenReturn(Optional.of(product(HOST_ID, null)));
        when(productRepository.findByIdAndTenantId(ADDON_ID, TENANT_ID))
            .thenReturn(Optional.of(product(ADDON_ID, null)));
        when(addOnRepository.existsByTenantIdAndProductIdAndAddOnProductId(TENANT_ID, HOST_ID, ADDON_ID))
            .thenReturn(true);

        assertThatThrownBy(() -> service.create(HOST_ID, ADDON_ID, TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.DUPLICATE_ADD_ON);

        verify(addOnRepository, never()).save(any());
    }

    @Test
    void validLinkIsPersisted() {
        when(productRepository.findByIdAndTenantId(HOST_ID, TENANT_ID))
            .thenReturn(Optional.of(product(HOST_ID, null)));
        when(productRepository.findByIdAndTenantId(ADDON_ID, TENANT_ID))
            .thenReturn(Optional.of(product(ADDON_ID, null)));
        when(addOnRepository.existsByTenantIdAndProductIdAndAddOnProductId(TENANT_ID, HOST_ID, ADDON_ID))
            .thenReturn(false);
        when(addOnRepository.save(any(ProductAddOn.class))).thenAnswer(inv -> {
            ProductAddOn link = inv.getArgument(0);
            link.setId(555L);
            return link;
        });

        ProductAddOnResponse response = service.create(HOST_ID, ADDON_ID, TENANT_ID, USER_ID);

        assertThat(response.getId()).isEqualTo(555L);
        assertThat(response.getAddOnProductId()).isEqualTo(ADDON_ID);
        verify(addOnRepository).save(any(ProductAddOn.class));
    }

    private Product product(Long id, Long parentProductId) {
        Product product = new Product();
        product.setId(id);
        product.setTenantId(TENANT_ID);
        product.setName("P" + id);
        product.setSellingPrice(new BigDecimal("5.00"));
        product.setParentProductId(parentProductId);
        return product;
    }
}
