package com.smart.restaurant_saas.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
import com.smart.restaurant_saas.media.spi.MediaOwnerResolver;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The startup guard from D128 §4. A missing resolver is a wiring defect, and the point of these
 * tests is that it surfaces as a failed context rather than as a 500 the first time a user uploads
 * a photo — by which time nobody can act on it.
 */
class MediaOwnerResolverRegistryTest {

    @Test
    void aPurposeWithNoResolverFailsConstruction() {
        assertThatThrownBy(() -> new MediaOwnerResolverRegistry(List.of(resolver(MediaOwnerType.PRODUCT))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMPLOYEE_PHOTO");
    }

    @Test
    void twoResolversForOneOwnerTypeFailConstruction() {
        assertThatThrownBy(() -> new MediaOwnerResolverRegistry(List.of(
                resolver(MediaOwnerType.PRODUCT),
                resolver(MediaOwnerType.PRODUCT),
                resolver(MediaOwnerType.EMPLOYEE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exactly one module owns each owner type");
    }

    @Test
    void everyShippingPurposeHasAResolverWhenAllOwnerTypesAreCovered() {
        MediaOwnerResolverRegistry registry = new MediaOwnerResolverRegistry(
                Arrays.stream(MediaOwnerType.values()).map(this::resolver).toList());

        for (MediaPurpose purpose : MediaPurpose.values()) {
            assertThat(registry.require(purpose.getOwnerType())).isNotNull();
        }
    }

    private MediaOwnerResolver resolver(MediaOwnerType ownerType) {
        return new MediaOwnerResolver() {
            @Override
            public MediaOwnerType ownerType() {
                return ownerType;
            }

            @Override
            public boolean exists(Long tenantId, Long ownerId) {
                return true;
            }

            @Override
            public boolean isMutable(Long tenantId, Long ownerId) {
                return true;
            }
        };
    }
}
