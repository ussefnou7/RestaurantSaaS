package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
import com.smart.restaurant_saas.media.spi.MediaOwnerResolver;
import java.util.EnumMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Every {@link MediaOwnerResolver}, keyed by owner type, validated at startup.
 *
 * <p><strong>A purpose whose resolver is missing fails the application context, not the first
 * upload.</strong> A missing resolver is a wiring defect, and discovering it when a user uploads a
 * photo is discovering it in the worst possible place — the user cannot act on it, and the stack
 * trace arrives in a support ticket instead of a build.
 */
@Component
public class MediaOwnerResolverRegistry {

    private final Map<MediaOwnerType, MediaOwnerResolver> resolvers =
            new EnumMap<>(MediaOwnerType.class);

    public MediaOwnerResolverRegistry(List<MediaOwnerResolver> beans) {
        for (MediaOwnerResolver resolver : beans) {
            MediaOwnerResolver previous = resolvers.put(resolver.ownerType(), resolver);
            if (previous != null) {
                throw new IllegalStateException(
                        ("Two MediaOwnerResolver beans claim owner type %s: %s and %s. "
                                + "Exactly one module owns each owner type.")
                                .formatted(resolver.ownerType(),
                                        previous.getClass().getName(),
                                        resolver.getClass().getName()));
            }
        }
        // Validated per purpose rather than per owner type: an owner type with no purpose is
        // harmless dead weight, whereas a purpose with no resolver is an endpoint that 500s.
        List<MediaPurpose> unresolvable = Arrays.stream(MediaPurpose.values())
                .filter(purpose -> !resolvers.containsKey(purpose.getOwnerType()))
                .toList();
        if (!unresolvable.isEmpty()) {
            throw new IllegalStateException(
                    "No MediaOwnerResolver bean for the owner type of these media purposes: "
                            + unresolvable + ". Each owning module must contribute one.");
        }
    }

    public MediaOwnerResolver require(MediaOwnerType ownerType) {
        MediaOwnerResolver resolver = resolvers.get(ownerType);
        if (resolver == null) {
            // Unreachable while every purpose is validated above; kept because the map is also
            // reachable by owner type from the reconciliation path.
            throw new IllegalStateException("No MediaOwnerResolver for owner type " + ownerType);
        }
        return resolver;
    }
}
