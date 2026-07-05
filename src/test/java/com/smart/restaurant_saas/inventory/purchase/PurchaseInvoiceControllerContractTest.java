package com.smart.restaurant_saas.inventory.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.UnpostRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

class PurchaseInvoiceControllerContractTest {

    @Test
    void postEndpointKeepsCurrentPermissionContractAndUnpostUsesDedicatedPermission()
            throws NoSuchMethodException {
        Method post = PurchaseInvoiceController.class.getMethod(
            "post", Long.class, Long.class, Long.class);
        Method method = PurchaseInvoiceController.class.getMethod(
            "unpost", Long.class, UnpostRequest.class, Long.class, Long.class);

        PostMapping postMapping = post.getAnnotation(PostMapping.class);
        assertThat(postMapping.value()).containsExactly("/{id}/post");
        assertThat(post.getReturnType()).isEqualTo(PurchaseInvoiceResponse.class);
        assertThat(post.getAnnotationsByType(PreAuthorize.class)).isEmpty();

        PostMapping unpostMapping = method.getAnnotation(PostMapping.class);
        assertThat(unpostMapping.value()).containsExactly("/{id}/unpost");
        assertThat(method.getReturnType()).isEqualTo(PurchaseInvoiceResponse.class);
        assertThat(method.getAnnotationsByType(PreAuthorize.class))
            .extracting(PreAuthorize::value)
            .containsExactly("@securityService.isSysAdmin() or @securityService.hasPermission('PURCHASE_INVOICE_UNPOST')");
    }
}
