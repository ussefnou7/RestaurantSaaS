package com.smart.restaurant_saas.assets.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.assets.asset.Asset;
import com.smart.restaurant_saas.assets.asset.AssetRepository;
import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.enums.AssetLineStatus;
import com.smart.restaurant_saas.assets.core.enums.AssetStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetStatusServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long ASSET_ID = 100L;

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetLineRepository assetLineRepository;

    private AssetStatusService service() {
        return new AssetStatusService(assetRepository, assetLineRepository);
    }

    @Test
    void deriveLineStatus_coversAllThreeBands() {
        AssetStatusService service = service();
        assertThat(service.deriveLineStatus(bd("10"), bd("10"))).isEqualTo(AssetLineStatus.ACTIVE);
        assertThat(service.deriveLineStatus(bd("4"), bd("10"))).isEqualTo(AssetLineStatus.PARTIALLY_DISPOSED);
        assertThat(service.deriveLineStatus(bd("0"), bd("10"))).isEqualTo(AssetLineStatus.FULLY_DISPOSED);
    }

    @Test
    void recalculateAsset_allActiveLines_keepsAssetActive() {
        assertAssetStatus(List.of(line(AssetLineStatus.ACTIVE), line(AssetLineStatus.ACTIVE)),
            AssetStatus.ACTIVE);
    }

    @Test
    void recalculateAsset_allFullyDisposed_marksFullyDisposed() {
        assertAssetStatus(List.of(line(AssetLineStatus.FULLY_DISPOSED), line(AssetLineStatus.FULLY_DISPOSED)),
            AssetStatus.FULLY_DISPOSED);
    }

    @Test
    void recalculateAsset_mixedLines_marksPartiallyDisposed() {
        assertAssetStatus(List.of(line(AssetLineStatus.ACTIVE), line(AssetLineStatus.FULLY_DISPOSED)),
            AssetStatus.PARTIALLY_DISPOSED);
    }

    @Test
    void recalculateAsset_noLines_keepsActive() {
        assertAssetStatus(List.of(), AssetStatus.ACTIVE);
    }

    private void assertAssetStatus(List<AssetLine> lines, AssetStatus expected) {
        Asset asset = new Asset();
        asset.setId(ASSET_ID);
        asset.setTenantId(TENANT_ID);
        asset.setStatus(AssetStatus.ACTIVE);
        when(assetRepository.findByIdAndTenantId(ASSET_ID, TENANT_ID)).thenReturn(Optional.of(asset));
        when(assetLineRepository.findByTenantIdAndAssetIdOrderByIdAsc(TENANT_ID, ASSET_ID)).thenReturn(lines);

        service().recalculateAsset(TENANT_ID, ASSET_ID);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        org.mockito.Mockito.verify(assetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(expected);
    }

    private static AssetLine line(AssetLineStatus status) {
        AssetLine line = new AssetLine();
        line.setStatus(status);
        return line;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
