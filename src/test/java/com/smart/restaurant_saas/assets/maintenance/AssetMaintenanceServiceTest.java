package com.smart.restaurant_saas.assets.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.mapper.AssetMaintenanceMapper;
import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceResponse;
import com.smart.restaurant_saas.assets.maintenance.dto.CreateAssetMaintenanceRequest;
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
class AssetMaintenanceServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long ASSET_ID = 100L;
    private static final Long LINE_ID = 500L;

    @Mock
    private AssetMaintenanceRepository assetMaintenanceRepository;
    @Mock
    private AssetLineRepository assetLineRepository;

    private AssetMaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new AssetMaintenanceService(assetMaintenanceRepository, assetLineRepository,
            new AssetMaintenanceMapper());
    }

    @Test
    void create_insertsCostRecordWithoutTouchingQuantity() {
        AssetLine line = line();
        BigDecimal remainingBefore = line.getRemainingQuantity();
        when(assetLineRepository.findByIdAndTenantId(LINE_ID, TENANT_ID)).thenReturn(Optional.of(line));
        when(assetMaintenanceRepository.save(any(AssetMaintenance.class))).thenAnswer(inv -> {
            AssetMaintenance m = inv.getArgument(0);
            m.setId(700L);
            return m;
        });

        AssetMaintenanceResponse response = service.create(ASSET_ID, LINE_ID, request(), TENANT_ID, USER_ID);

        // D49: the line quantity/remaining is never altered by maintenance, and it is never saved.
        assertThat(line.getRemainingQuantity()).isEqualByComparingTo(remainingBefore);
        verify(assetLineRepository, never()).save(any());
        ArgumentCaptor<AssetMaintenance> captor = ArgumentCaptor.forClass(AssetMaintenance.class);
        verify(assetMaintenanceRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(USER_ID);
        assertThat(response.getId()).isEqualTo(700L);
    }

    @Test
    void create_lineBelongingToDifferentAsset_isRejected() {
        AssetLine line = line();
        line.setAssetId(ASSET_ID + 9);
        when(assetLineRepository.findByIdAndTenantId(LINE_ID, TENANT_ID)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.create(ASSET_ID, LINE_ID, request(), TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(AssetErrorCode.LINE_ASSET_MISMATCH);
        verify(assetMaintenanceRepository, never()).save(any());
    }

    private static AssetLine line() {
        AssetLine line = new AssetLine();
        line.setId(LINE_ID);
        line.setTenantId(TENANT_ID);
        line.setAssetId(ASSET_ID);
        line.setQuantity(new BigDecimal("10"));
        line.setRemainingQuantity(new BigDecimal("10"));
        line.setUnitCost(new BigDecimal("2"));
        return line;
    }

    private static CreateAssetMaintenanceRequest request() {
        CreateAssetMaintenanceRequest request = new CreateAssetMaintenanceRequest();
        request.setAssetId(ASSET_ID);
        request.setAssetLineId(LINE_ID);
        request.setCost(new BigDecimal("45.00"));
        request.setMaintenanceDate(LocalDate.of(2026, 7, 13));
        request.setVendor("Acme Repairs");
        return request;
    }
}
