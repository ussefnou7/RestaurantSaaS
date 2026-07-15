package com.smart.restaurant_saas.assets.disposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.core.AssetStatusService;
import com.smart.restaurant_saas.assets.core.enums.AssetDisposalReason;
import com.smart.restaurant_saas.assets.core.enums.AssetLineStatus;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalResponse;
import com.smart.restaurant_saas.assets.disposal.dto.CreateAssetDisposalRequest;
import com.smart.restaurant_saas.assets.mapper.AssetDisposalMapper;
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
class AssetDisposalServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long ASSET_ID = 100L;
    private static final Long LINE_ID = 500L;

    @Mock
    private AssetDisposalRepository assetDisposalRepository;
    @Mock
    private AssetLineRepository assetLineRepository;
    @Mock
    private AssetStatusService statusService;

    private AssetDisposalService service;

    @BeforeEach
    void setUp() {
        service = new AssetDisposalService(assetDisposalRepository, assetLineRepository,
            statusService, new AssetDisposalMapper());
    }

    @Test
    void create_decrementsRemainingAndRecalculatesStatus() {
        AssetLine line = line(bd("10"), bd("10"));
        when(assetLineRepository.findByIdAndTenantId(LINE_ID, TENANT_ID)).thenReturn(Optional.of(line));
        when(assetDisposalRepository.save(any(AssetDisposal.class))).thenAnswer(inv -> {
            AssetDisposal d = inv.getArgument(0);
            d.setId(900L);
            return d;
        });

        AssetDisposalResponse response = service.create(ASSET_ID, LINE_ID, request(bd("4")), TENANT_ID, USER_ID);

        assertThat(line.getRemainingQuantity()).isEqualByComparingTo(bd("6"));
        verify(statusService).recalculateLineAndAsset(line);
        ArgumentCaptor<AssetDisposal> captor = ArgumentCaptor.forClass(AssetDisposal.class);
        verify(assetDisposalRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getAssetId()).isEqualTo(ASSET_ID);
        assertThat(captor.getValue().getAssetLineId()).isEqualTo(LINE_ID);
        assertThat(response.getId()).isEqualTo(900L);
    }

    @Test
    void create_quantityExceedingRemaining_isRejected() {
        AssetLine line = line(bd("3"), bd("10"));
        when(assetLineRepository.findByIdAndTenantId(LINE_ID, TENANT_ID)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.create(ASSET_ID, LINE_ID, request(bd("4")), TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(AssetErrorCode.DISPOSAL_EXCEEDS_REMAINING);
        verify(assetDisposalRepository, never()).save(any());
        verify(statusService, never()).recalculateLineAndAsset(any());
    }

    @Test
    void create_bodyAssetIdMismatchingPath_isRejected() {
        CreateAssetDisposalRequest request = request(bd("1"));
        request.setAssetId(ASSET_ID + 1);

        assertThatThrownBy(() -> service.create(ASSET_ID, LINE_ID, request, TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(AssetErrorCode.LINE_ASSET_MISMATCH);
    }

    @Test
    void create_lineBelongingToDifferentAsset_isRejected() {
        AssetLine line = line(bd("10"), bd("10"));
        line.setAssetId(ASSET_ID + 5);
        when(assetLineRepository.findByIdAndTenantId(LINE_ID, TENANT_ID)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.create(ASSET_ID, LINE_ID, request(bd("1")), TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(AssetErrorCode.LINE_ASSET_MISMATCH);
    }

    private static AssetLine line(BigDecimal remaining, BigDecimal quantity) {
        AssetLine line = new AssetLine();
        line.setId(LINE_ID);
        line.setTenantId(TENANT_ID);
        line.setAssetId(ASSET_ID);
        line.setQuantity(quantity);
        line.setRemainingQuantity(remaining);
        line.setUnitCost(bd("2"));
        line.setStatus(AssetLineStatus.ACTIVE);
        return line;
    }

    private static CreateAssetDisposalRequest request(BigDecimal quantityDisposed) {
        CreateAssetDisposalRequest request = new CreateAssetDisposalRequest();
        request.setAssetId(ASSET_ID);
        request.setAssetLineId(LINE_ID);
        request.setQuantityDisposed(quantityDisposed);
        request.setReason(AssetDisposalReason.DAMAGED);
        request.setDisposalDate(LocalDate.of(2026, 7, 13));
        return request;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
