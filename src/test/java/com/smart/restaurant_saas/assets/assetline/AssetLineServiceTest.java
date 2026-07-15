package com.smart.restaurant_saas.assets.assetline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.assets.asset.Asset;
import com.smart.restaurant_saas.assets.asset.AssetRepository;
import com.smart.restaurant_saas.assets.assetline.dto.AssetLineResponse;
import com.smart.restaurant_saas.assets.assetline.dto.CreateAssetLineRequest;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.core.AssetStatusService;
import com.smart.restaurant_saas.assets.core.enums.AssetLineStatus;
import com.smart.restaurant_saas.assets.disposal.AssetDisposalRepository;
import com.smart.restaurant_saas.assets.maintenance.AssetMaintenanceRepository;
import com.smart.restaurant_saas.assets.mapper.AssetLineMapper;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.common.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetLineServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long ASSET_ID = 100L;
    private static final Long LINE_ID = 500L;

    @Mock
    private AssetLineRepository assetLineRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetDisposalRepository assetDisposalRepository;
    @Mock
    private AssetMaintenanceRepository assetMaintenanceRepository;
    @Mock
    private AssetStatusService statusService;

    private AssetLineService service;

    @BeforeEach
    void setUp() {
        service = new AssetLineService(assetLineRepository, assetRepository, assetDisposalRepository,
            assetMaintenanceRepository, statusService, new AssetLineMapper());
    }

    @Test
    void create_computesTotalCostAndSeedsRemainingAndStatus() {
        when(assetRepository.findByIdAndTenantId(ASSET_ID, TENANT_ID)).thenReturn(Optional.of(asset()));
        when(assetLineRepository.save(any(AssetLine.class))).thenAnswer(inv -> {
            AssetLine l = inv.getArgument(0);
            l.setId(LINE_ID);
            return l;
        });

        CreateAssetLineRequest request = new CreateAssetLineRequest();
        request.setLabel("Batch 1");
        request.setQuantity(new BigDecimal("5"));
        request.setUnitCost(new BigDecimal("12.5"));
        request.setPurchaseDate(LocalDate.of(2026, 7, 13));

        AssetLineResponse response = service.create(ASSET_ID, request, TENANT_ID);

        ArgumentCaptor<AssetLine> captor = ArgumentCaptor.forClass(AssetLine.class);
        verify(assetLineRepository).save(captor.capture());
        AssetLine saved = captor.getValue();
        assertThat(saved.getTotalCost()).isEqualByComparingTo("62.5");
        assertThat(saved.getRemainingQuantity()).isEqualByComparingTo("5");
        assertThat(saved.getStatus()).isEqualTo(AssetLineStatus.ACTIVE);
        verify(statusService).recalculateAsset(TENANT_ID, ASSET_ID);
        assertThat(response.getId()).isEqualTo(LINE_ID);
    }

    @Test
    void delete_withChildRecords_isRejected() {
        AssetLine line = new AssetLine();
        line.setId(LINE_ID);
        line.setAssetId(ASSET_ID);
        line.setTenantId(TENANT_ID);
        when(assetLineRepository.findByIdAndTenantId(LINE_ID, TENANT_ID)).thenReturn(Optional.of(line));
        when(assetDisposalRepository.countByTenantIdAndAssetLineId(TENANT_ID, LINE_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(ASSET_ID, LINE_ID, TENANT_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(AssetErrorCode.LINE_HAS_CHILD_RECORDS);
        verify(assetLineRepository, never()).delete(any());
    }

    @Test
    void delete_withNoChildRecords_deletesAndRecalculates() {
        AssetLine line = new AssetLine();
        line.setId(LINE_ID);
        line.setAssetId(ASSET_ID);
        line.setTenantId(TENANT_ID);
        when(assetLineRepository.findByIdAndTenantId(LINE_ID, TENANT_ID)).thenReturn(Optional.of(line));
        when(assetDisposalRepository.countByTenantIdAndAssetLineId(TENANT_ID, LINE_ID)).thenReturn(0L);
        when(assetMaintenanceRepository.countByTenantIdAndAssetLineId(TENANT_ID, LINE_ID)).thenReturn(0L);

        service.delete(ASSET_ID, LINE_ID, TENANT_ID);

        verify(assetLineRepository).delete(line);
        verify(statusService).recalculateAsset(TENANT_ID, ASSET_ID);
    }

    @Test
    void getById_lineOfAnotherAsset_isRejected() {
        AssetLine line = new AssetLine();
        line.setId(LINE_ID);
        line.setAssetId(ASSET_ID + 1);
        line.setTenantId(TENANT_ID);
        when(assetLineRepository.findByIdAndTenantId(LINE_ID, TENANT_ID)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.findByAssetAndId(ASSET_ID, LINE_ID, TENANT_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(AssetErrorCode.LINE_ASSET_MISMATCH);
    }

    private static Asset asset() {
        Asset asset = new Asset();
        asset.setId(ASSET_ID);
        asset.setTenantId(TENANT_ID);
        return asset;
    }
}
