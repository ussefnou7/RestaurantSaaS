package com.smart.restaurant_saas.assets.disposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.core.AssetStatusService;
import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.core.enums.AssetDisposalReason;
import com.smart.restaurant_saas.assets.core.enums.AssetLineStatus;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalListItemResponse;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalResponse;
import com.smart.restaurant_saas.assets.disposal.dto.CreateAssetDisposalRequest;
import com.smart.restaurant_saas.assets.mapper.AssetDisposalMapper;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void listDisposals_returnsDenormalizedRowsWithDisposalValue() {
        PageRequest pageable = PageRequest.of(0, 20);
        AssetDisposalListItemResponse row = listItem();
        when(assetDisposalRepository.findListItems(TENANT_ID, null, null, null, null, null, null, pageable))
            .thenReturn(new PageImpl<>(List.of(row)));

        Page<AssetDisposalListItemResponse> result =
            service.listDisposals(TENANT_ID, null, null, null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        AssetDisposalListItemResponse response = result.getContent().get(0);
        assertThat(response.getAssetId()).isEqualTo(ASSET_ID);
        assertThat(response.getAssetName()).isEqualTo("Oven");
        assertThat(response.getAssetNameAr()).isEqualTo("فرن");
        assertThat(response.getCategory()).isEqualTo(AssetCategory.KITCHEN_EQUIPMENT);
        assertThat(response.getBranchId()).isEqualTo(3L);
        assertThat(response.getAssetLineId()).isEqualTo(LINE_ID);
        assertThat(response.getAssetLineLabel()).isEqualTo("Main unit");
        assertThat(response.getDisposalValue()).isEqualByComparingTo("7.037034");
    }

    @Test
    void listDisposals_passesEachFilterInIsolation() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(assetDisposalRepository.findListItems(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());
        LocalDate dateFrom = LocalDate.of(2026, 7, 1);
        LocalDate dateTo = LocalDate.of(2026, 7, 31);

        service.listDisposals(TENANT_ID, ASSET_ID, null, null, null, null, null, pageable);
        service.listDisposals(TENANT_ID, null, LINE_ID, null, null, null, null, pageable);
        service.listDisposals(TENANT_ID, null, null, AssetCategory.FURNITURE, null, null, null, pageable);
        service.listDisposals(TENANT_ID, null, null, null, 3L, null, null, pageable);
        service.listDisposals(TENANT_ID, null, null, null, null, dateFrom, null, pageable);
        service.listDisposals(TENANT_ID, null, null, null, null, null, dateTo, pageable);

        verify(assetDisposalRepository).findListItems(TENANT_ID, ASSET_ID, null, null, null, null, null, pageable);
        verify(assetDisposalRepository).findListItems(TENANT_ID, null, LINE_ID, null, null, null, null, pageable);
        verify(assetDisposalRepository).findListItems(TENANT_ID, null, null, AssetCategory.FURNITURE,
            null, null, null, pageable);
        verify(assetDisposalRepository).findListItems(TENANT_ID, null, null, null, 3L, null, null, pageable);
        verify(assetDisposalRepository).findListItems(TENANT_ID, null, null, null, null, dateFrom, null, pageable);
        verify(assetDisposalRepository).findListItems(TENANT_ID, null, null, null, null, null, dateTo, pageable);
    }

    @Test
    void listDisposals_combinedFiltersAndTenantArePassedTogether() {
        PageRequest pageable = PageRequest.of(0, 20);
        LocalDate dateFrom = LocalDate.of(2026, 7, 1);
        LocalDate dateTo = LocalDate.of(2026, 7, 31);
        when(assetDisposalRepository.findListItems(eq(TENANT_ID), eq(ASSET_ID), eq(LINE_ID),
                eq(AssetCategory.KITCHEN_EQUIPMENT), eq(3L), eq(dateFrom), eq(dateTo), eq(pageable)))
            .thenReturn(Page.empty());

        service.listDisposals(TENANT_ID, ASSET_ID, LINE_ID, AssetCategory.KITCHEN_EQUIPMENT,
            3L, dateFrom, dateTo, pageable);

        verify(assetDisposalRepository).findListItems(TENANT_ID, ASSET_ID, LINE_ID,
            AssetCategory.KITCHEN_EQUIPMENT, 3L, dateFrom, dateTo, pageable);
    }

    @Test
    void listDisposals_dateFromAfterDateTo_isRejected() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> service.listDisposals(TENANT_ID, null, null, null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1), pageable))
            .isInstanceOf(ValidationException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(AssetErrorCode.INVALID_DATE_RANGE);
        verify(assetDisposalRepository, never()).findListItems(any(), any(), any(), any(), any(),
            any(), any(), any());
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

    private static AssetDisposalListItemResponse listItem() {
        return new AssetDisposalListItemResponse(900L, ASSET_ID, "Oven", "فرن",
            AssetCategory.KITCHEN_EQUIPMENT, 3L, LINE_ID, "Main unit", bd("2.345678"),
            bd("3"), LocalDate.of(2026, 7, 13), AssetDisposalReason.DAMAGED, "Broken");
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
