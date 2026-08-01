package com.smart.restaurant_saas.inventory.physicalcount.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Open-ended inventory activity in a count's warehouse since its freeze cutoff. Reconciliation uses
 * the per-line subset through each {@code countedAt}; later activity remains visible here for context.
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
    private final List<PostFreezeMovementRowResponse> included;
    private final List<PostFreezeMovementRowResponse> afterCount;
}
