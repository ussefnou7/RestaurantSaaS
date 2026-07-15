package com.smart.restaurant_saas.assets.core;

import com.smart.restaurant_saas.assets.asset.Asset;
import com.smart.restaurant_saas.assets.asset.AssetRepository;
import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.enums.AssetLineStatus;
import com.smart.restaurant_saas.assets.core.enums.AssetStatus;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Recomputes the derived {@code status} on an {@link AssetLine} and its parent {@link Asset}
 * (D48). Line and asset statuses are never written directly by callers — they are always the
 * output of this recalculation, run after every quantity-affecting mutation.
 *
 * <p>Depends only on repositories, so it introduces no dependency cycle between the feature
 * services that both need it (AssetLineService, AssetDisposalService).
 */
@Service
@RequiredArgsConstructor
public class AssetStatusService {

    private final AssetRepository assetRepository;
    private final AssetLineRepository assetLineRepository;

    /** Derives a line status from remaining vs. original quantity (D48). */
    public AssetLineStatus deriveLineStatus(BigDecimal remainingQuantity, BigDecimal quantity) {
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return AssetLineStatus.FULLY_DISPOSED;
        }
        if (remainingQuantity.compareTo(quantity) < 0) {
            return AssetLineStatus.PARTIALLY_DISPOSED;
        }
        return AssetLineStatus.ACTIVE;
    }

    /**
     * Recomputes the line's own status, persists it, then recomputes the parent asset aggregate.
     */
    public void recalculateLineAndAsset(AssetLine line) {
        line.setStatus(deriveLineStatus(line.getRemainingQuantity(), line.getQuantity()));
        assetLineRepository.save(line);
        recalculateAsset(line.getTenantId(), line.getAssetId());
    }

    /**
     * Recomputes the parent asset's aggregate status from all its current lines and persists it.
     * Call this after a line is created or deleted (which changes the aggregation set) even though
     * such changes do not touch an individual line's remaining quantity.
     */
    public void recalculateAsset(Long tenantId, Long assetId) {
        Asset asset = assetRepository.findByIdAndTenantId(assetId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(AssetErrorCode.RESOURCE_NOT_FOUND,
                "Asset not found: " + assetId,
                ErrorParams.of("entityType", "Asset", "entityId", assetId)));
        List<AssetLine> lines = assetLineRepository.findByTenantIdAndAssetIdOrderByIdAsc(tenantId, assetId);
        asset.setStatus(deriveAssetStatus(lines));
        assetRepository.save(asset);
    }

    /**
     * Aggregates line statuses into the header status. This aggregation is a judgment call — it was
     * not spelled out as an explicit prior decision, so the rule chosen here is:
     * <ul>
     *   <li>no lines yet (freshly created asset with no purchases) &rarr; {@code ACTIVE};</li>
     *   <li>every line {@code FULLY_DISPOSED} &rarr; {@code FULLY_DISPOSED};</li>
     *   <li>every line {@code ACTIVE} &rarr; {@code ACTIVE};</li>
     *   <li>any other mix (at least one partial, or a blend of active and disposed) &rarr;
     *       {@code PARTIALLY_DISPOSED}.</li>
     * </ul>
     */
    private AssetStatus deriveAssetStatus(List<AssetLine> lines) {
        if (lines.isEmpty()) {
            return AssetStatus.ACTIVE;
        }
        boolean allFullyDisposed = lines.stream()
            .allMatch(l -> l.getStatus() == AssetLineStatus.FULLY_DISPOSED);
        if (allFullyDisposed) {
            return AssetStatus.FULLY_DISPOSED;
        }
        boolean allActive = lines.stream()
            .allMatch(l -> l.getStatus() == AssetLineStatus.ACTIVE);
        if (allActive) {
            return AssetStatus.ACTIVE;
        }
        return AssetStatus.PARTIALLY_DISPOSED;
    }
}
