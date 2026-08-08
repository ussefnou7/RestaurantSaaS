package com.smart.restaurant_saas.menu.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.menu.MenuErrorCode;
import com.smart.restaurant_saas.menu.category.MenuCategory;
import com.smart.restaurant_saas.menu.category.MenuCategoryRepository;
import com.smart.restaurant_saas.menu.product.dto.ProductRequest;
import com.smart.restaurant_saas.menu.product.dto.ProductResponse;
import com.smart.restaurant_saas.menu.recipe.Recipe;
import com.smart.restaurant_saas.menu.recipe.RecipeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long CATEGORY_ID = 12L;
    private static final Long PARENT_ID = 500L;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private MenuCategoryRepository categoryRepository;
    @Mock
    private RecipeRepository recipeRepository;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, categoryRepository, recipeRepository,
            new ProductMapper());
    }

    @Test
    void createVariantWithIsMenuTrueIsRejected() {
        stubActiveCategory();
        when(productRepository.existsByNameAndTenantId("Large", TENANT_ID)).thenReturn(false);

        ProductRequest request = request("Large");
        request.setParentProductId(PARENT_ID);
        request.setIsMenu(true);

        assertThatThrownBy(() -> service.create(request, TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.VARIANT_CANNOT_BE_MENU_ITEM);

        verify(productRepository, never()).save(any());
    }

    @Test
    void updateVariantWithIsMenuTrueIsRejected() {
        Product existing = product(1L, "Large");
        when(productRepository.findByIdAndTenantId(1L, TENANT_ID)).thenReturn(Optional.of(existing));
        stubActiveCategory();

        ProductRequest request = request("Large");
        request.setParentProductId(PARENT_ID);
        request.setIsMenu(true);

        assertThatThrownBy(() -> service.update(1L, request, TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.VARIANT_CANNOT_BE_MENU_ITEM);

        verify(productRepository, never()).save(any());
    }

    @Test
    void linkingChildToProductWithActiveRecipeIsRejected() {
        stubActiveCategory();
        when(productRepository.existsByNameAndTenantId("Coleslaw", TENANT_ID)).thenReturn(false);
        when(productRepository.findByIdAndTenantId(PARENT_ID, TENANT_ID))
            .thenReturn(Optional.of(product(PARENT_ID, "Combo")));
        when(recipeRepository.findByProductIdAndTenantIdAndActiveTrue(PARENT_ID, TENANT_ID))
            .thenReturn(Optional.of(new Recipe()));

        ProductRequest request = request("Coleslaw");
        request.setParentProductId(PARENT_ID);

        assertThatThrownBy(() -> service.create(request, TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.PRODUCT_WITH_RECIPE_CANNOT_BE_PARENT);

        verify(productRepository, never()).save(any());
    }

    @Test
    void creatingVariantChildForcesIsMenuFalse() {
        stubActiveCategory();
        when(productRepository.existsByNameAndTenantId("Coleslaw", TENANT_ID)).thenReturn(false);
        when(productRepository.findByIdAndTenantId(PARENT_ID, TENANT_ID))
            .thenReturn(Optional.of(product(PARENT_ID, "Combo")));
        when(recipeRepository.findByProductIdAndTenantIdAndActiveTrue(PARENT_ID, TENANT_ID))
            .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(900L);
            return p;
        });
        when(productRepository.existsByParentProductIdAndTenantId(900L, TENANT_ID)).thenReturn(false);

        ProductRequest request = request("Coleslaw");
        request.setParentProductId(PARENT_ID);
        request.setIsMenu(null);

        ProductResponse response = service.create(request, TENANT_ID, USER_ID);

        assertThat(response.getIsMenu()).isFalse();
        assertThat(response.getParentProductId()).isEqualTo(PARENT_ID);
    }

    @Test
    void findByIdDerivesIsParentFromRepository() {
        Product parent = product(PARENT_ID, "Combo");
        when(productRepository.findByIdAndTenantId(PARENT_ID, TENANT_ID)).thenReturn(Optional.of(parent));
        when(productRepository.existsByParentProductIdAndTenantId(PARENT_ID, TENANT_ID)).thenReturn(true);

        ProductResponse response = service.findById(PARENT_ID, TENANT_ID);

        assertThat(response.isParent()).isTrue();
    }

    @Test
    void findAllComputesIsParentInMemory() {
        Product parent = product(PARENT_ID, "Combo");
        Product child = product(901L, "Large");
        child.setParentProductId(PARENT_ID);
        when(productRepository.findByTenantIdOrderByNameAsc(TENANT_ID))
            .thenReturn(List.of(parent, child));

        List<ProductResponse> responses = service.findAll(TENANT_ID, null);

        assertThat(responses).extracting(ProductResponse::getId, ProductResponse::isParent)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(PARENT_ID, true),
                org.assertj.core.groups.Tuple.tuple(901L, false));
    }

    @Test
    void assertOrderableRejectsParentShell() {
        when(productRepository.findByIdAndTenantId(PARENT_ID, TENANT_ID))
            .thenReturn(Optional.of(product(PARENT_ID, "Combo")));
        when(productRepository.existsByParentProductIdAndTenantId(PARENT_ID, TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.assertOrderable(PARENT_ID, TENANT_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.PARENT_PRODUCT_NOT_ORDERABLE);
    }

    @Test
    void deleteProductDeletesOwnedProduct() {
        Product product = product(1L, "Burger");
        when(productRepository.findByIdAndTenantId(1L, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.existsByParentProductIdAndTenantId(1L, TENANT_ID)).thenReturn(false);

        service.deleteProduct(TENANT_ID, 1L);

        verify(productRepository).delete(product);
    }

    @Test
    void deleteProductRejectsParentWithVariants() {
        Product product = product(PARENT_ID, "Combo");
        when(productRepository.findByIdAndTenantId(PARENT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.existsByParentProductIdAndTenantId(PARENT_ID, TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteProduct(TENANT_ID, PARENT_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.PRODUCT_HAS_VARIANTS);

        verify(productRepository, never()).delete(any());
    }

    @Test
    void deleteProductMissingProductThrowsNotFound() {
        when(productRepository.findByIdAndTenantId(404L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProduct(TENANT_ID, 404L))
            .isInstanceOf(ResourceNotFoundException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.PRODUCT_NOT_FOUND);

        verify(productRepository, never()).delete(any());
    }

    private void stubActiveCategory() {
        MenuCategory category = new MenuCategory();
        category.setId(CATEGORY_ID);
        category.setName("Mains");
        category.setActive(true);
        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID))
            .thenReturn(Optional.of(category));
    }

    private ProductRequest request(String name) {
        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setSellingPrice(new BigDecimal("10.00"));
        request.setMenuCategoryId(CATEGORY_ID);
        return request;
    }

    private Product product(Long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setTenantId(TENANT_ID);
        product.setName(name);
        product.setSellingPrice(new BigDecimal("10.00"));
        MenuCategory category = new MenuCategory();
        category.setId(CATEGORY_ID);
        category.setName("Mains");
        category.setActive(true);
        product.setMenuCategory(category);
        return product;
    }
}
