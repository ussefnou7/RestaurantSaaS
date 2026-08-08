package com.smart.restaurant_saas.menu.recipe;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.menu.MenuErrorCode;
import com.smart.restaurant_saas.menu.product.Product;
import com.smart.restaurant_saas.menu.product.ProductRepository;
import com.smart.restaurant_saas.menu.recipe.dto.RecipeItemRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeParentGuardTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long PARENT_ID = 500L;

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeItemRepository recipeItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private UomRepository uomRepository;
    @Mock
    private RecipeMapper recipeMapper;

    private RecipeService service;

    @BeforeEach
    void setUp() {
        service = new RecipeService(recipeRepository, recipeItemRepository, productRepository,
            materialRepository, uomRepository, recipeMapper);
    }

    @Test
    void creatingRecipeOnParentProductIsRejected() {
        Product parent = new Product();
        parent.setId(PARENT_ID);
        parent.setTenantId(TENANT_ID);
        when(productRepository.findWithLockByIdAndTenantId(PARENT_ID, TENANT_ID))
            .thenReturn(Optional.of(parent));
        when(productRepository.existsByParentProductId(PARENT_ID)).thenReturn(true);

        List<RecipeItemRequest> items = List.of(new RecipeItemRequest());

        assertThatThrownBy(() -> service.createNewVersion(PARENT_ID, items, TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(MenuErrorCode.PARENT_PRODUCT_HAS_NO_RECIPE);

        verify(recipeRepository, never()).save(any());
    }
}
