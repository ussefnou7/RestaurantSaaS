package com.smart.restaurant_saas.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.menu.category.MenuCategory;
import com.smart.restaurant_saas.menu.dto.MenuItemResponse;
import com.smart.restaurant_saas.menu.dto.MenuItemType;
import com.smart.restaurant_saas.menu.product.Product;
import com.smart.restaurant_saas.menu.product.ProductAddOn;
import com.smart.restaurant_saas.menu.product.ProductAddOnRepository;
import com.smart.restaurant_saas.menu.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    private static final Long TENANT_ID = 7L;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductAddOnRepository addOnRepository;

    private MenuService service;

    @BeforeEach
    void setUp() {
        service = new MenuService(productRepository, addOnRepository);
    }

    @Test
    void returnsNestedRootsWithDerivedParentPricesAndTwoBoundedReads() {
        MenuCategory pizza = category(8L, "Pizza");
        MenuCategory plates = category(6L, "Plates");
        MenuCategory addOns = category(9L, "اضافات");
        Product parent = product(21L, "Cheese Pizza", null, true, "0.00", pizza);
        Product small = product(22L, "Cheese Pizza Small", 21L, false, "70.00", pizza);
        small.setVariantLabel("Small");
        small.setVariantLabelAr("صغير");
        Product medium = product(16L, "Cheese Pizza Medium", 21L, false, "100.00", pizza);
        medium.setVariantLabel("Medium");
        medium.setVariantLabelAr("وسط");
        Product large = product(23L, "Cheese Pizza Large", 21L, false, "140.00", pizza);
        large.setVariantLabel("Large");
        large.setVariantLabelAr("كبير");
        Product standalone = product(17L, "Chicken Rice", null, true, "85.00", plates);
        Product addOn = product(24L, "اضافة جبن", null, false, "20.00", addOns);
        ProductAddOn link = new ProductAddOn();
        link.setTenantId(TENANT_ID);
        link.setProductId(21L);
        link.setAddOnProductId(24L);

        when(productRepository.findMenuCatalog(TENANT_ID))
            .thenReturn(List.of(parent, small, medium, large, standalone, addOn));
        when(addOnRepository.findByTenantIdOrderByProductIdAscAddOnProductIdAsc(TENANT_ID))
            .thenReturn(List.of(link));

        List<MenuItemResponse> menu = service.findMenu(TENANT_ID);

        assertThat(menu).extracting(MenuItemResponse::getId).containsExactly(21L, 17L);
        MenuItemResponse pizzaItem = menu.getFirst();
        assertThat(pizzaItem.getType()).isEqualTo(MenuItemType.PARENT);
        assertThat(pizzaItem.getSellingPrice()).isNull();
        assertThat(pizzaItem.getMinPrice()).isEqualByComparingTo("70.00");
        assertThat(pizzaItem.getMaxPrice()).isEqualByComparingTo("140.00");
        assertThat(pizzaItem.getVariants()).extracting("id", "variantLabelAr")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(22L, "صغير"),
                org.assertj.core.groups.Tuple.tuple(16L, "وسط"),
                org.assertj.core.groups.Tuple.tuple(23L, "كبير"));
        assertThat(pizzaItem.getAddOns()).extracting("id", "name")
            .containsExactly(org.assertj.core.groups.Tuple.tuple(24L, "اضافة جبن"));

        MenuItemResponse standaloneItem = menu.get(1);
        assertThat(standaloneItem.getType()).isEqualTo(MenuItemType.STANDALONE);
        assertThat(standaloneItem.getSellingPrice()).isEqualByComparingTo("85.00");
        assertThat(standaloneItem.getMinPrice()).isNull();
        assertThat(standaloneItem.getVariants()).isEmpty();

        verify(productRepository).findMenuCatalog(TENANT_ID);
        verify(addOnRepository).findByTenantIdOrderByProductIdAscAddOnProductIdAsc(TENANT_ID);
        verifyNoMoreInteractions(productRepository, addOnRepository);
    }

    private Product product(Long id, String name, Long parentId, boolean isMenu,
                            String price, MenuCategory category) {
        Product product = new Product();
        product.setId(id);
        product.setTenantId(TENANT_ID);
        product.setName(name);
        product.setParentProductId(parentId);
        product.setIsMenu(isMenu);
        product.setSellingPrice(new BigDecimal(price));
        product.setMenuCategory(category);
        return product;
    }

    private MenuCategory category(Long id, String name) {
        MenuCategory category = new MenuCategory();
        category.setId(id);
        category.setName(name);
        category.setSortOrder(id.intValue());
        return category;
    }
}
