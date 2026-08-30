package com.smart.restaurant_saas.menu.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.menu.category.dto.MenuCategoryRequest;
import com.smart.restaurant_saas.menu.category.dto.MenuCategoryResponse;
import com.smart.restaurant_saas.menu.product.ProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuCategoryServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;

    @Mock
    private MenuCategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;

    private MenuCategoryService service;

    @BeforeEach
    void setUp() {
        service = new MenuCategoryService(categoryRepository, productRepository, new MenuCategoryMapper());
    }

    @Test
    void createStoresOptionalArabicName() {
        when(categoryRepository.save(any(MenuCategory.class))).thenAnswer(invocation -> {
            MenuCategory category = invocation.getArgument(0);
            category.setId(12L);
            return category;
        });

        MenuCategoryResponse response = service.create(request("Pizza", "بيتزا"), TENANT_ID, USER_ID);

        assertThat(response.getName()).isEqualTo("Pizza");
        assertThat(response.getNameAr()).isEqualTo("بيتزا");
    }

    @Test
    void updateClearsBlankArabicNameToNull() {
        MenuCategory category = new MenuCategory();
        category.setId(12L);
        category.setTenantId(TENANT_ID);
        category.setName("Pizza");
        category.setNameAr("بيتزا");
        category.setSortOrder(10);
        category.setActive(true);
        when(categoryRepository.findByIdAndTenantId(12L, TENANT_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        MenuCategoryResponse response = service.update(12L, request("Pizza", "  "), TENANT_ID, USER_ID);

        assertThat(response.getNameAr()).isNull();
    }

    private MenuCategoryRequest request(String name, String nameAr) {
        MenuCategoryRequest request = new MenuCategoryRequest();
        request.setName(name);
        request.setNameAr(nameAr);
        request.setSortOrder(10);
        request.setActive(true);
        return request;
    }
}
