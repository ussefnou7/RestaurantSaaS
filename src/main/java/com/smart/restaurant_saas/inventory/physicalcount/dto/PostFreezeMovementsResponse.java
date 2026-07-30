package com.smart.restaurant_saas.inventory.physicalcount.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Inventory activity in a count's warehouse since its freeze cutoff. Purely informational — the
 * frozen snapshot, the variance and the posted movements are unaffected by anything reported here.
 *
 * <p>{@code totalMovementCount} and {@code affectedMaterialCount} span the whole warehouse;
 * {@code materials} is narrowed to the materials this count document actually contains.
 */
@Getter
@Builder
public class PostFreezeMovementsResponse {

    private final Long countId;
    private final Long warehouseId;
    private final LocalDateTime frozenAt;
    private final Integer totalMovementCount;
    private final Integer affectedMaterialCount;
    private final List<PostFreezeMaterialMovementResponse> materials;
}
