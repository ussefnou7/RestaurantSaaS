package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.spi.MediaOwnerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Menu's contribution to the media module. The dependency points this way, never the reverse. */
@Component
@RequiredArgsConstructor
public class ProductMediaOwnerResolver implements MediaOwnerResolver {

    private final ProductRepository productRepository;

    @Override
    public MediaOwnerType ownerType() {
        return MediaOwnerType.PRODUCT;
    }

    @Override
    public boolean exists(Long tenantId, Long ownerId) {
        return productRepository.existsByIdAndTenantId(ownerId, tenantId);
    }

    /**
     * A product has no final state — it deactivates rather than posting — so its image stays
     * replaceable and removable for as long as the product exists.
     */
    @Override
    public boolean isMutable(Long tenantId, Long ownerId) {
        return true;
    }
}
