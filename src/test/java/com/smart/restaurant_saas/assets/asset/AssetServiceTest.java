package com.smart.restaurant_saas.assets.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.assets.asset.dto.AssetResponse;
import com.smart.restaurant_saas.assets.asset.dto.CreateAssetRequest;
import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.mapper.AssetMapper;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long ASSET_ID = 100L;
    private static final Long BRANCH_ID = 3L;

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetLineRepository assetLineRepository;
    @Mock
    private BranchRepository branchRepository;

    private AssetService service;

    @BeforeEach
    void setUp() {
        service = new AssetService(assetRepository, assetLineRepository, branchRepository, new AssetMapper());
    }

    @Test
    void create_unknownBranch_isRejected() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(), TENANT_ID))
            .isInstanceOf(ResourceNotFoundException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(AssetErrorCode.RESOURCE_NOT_FOUND);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void create_computesLineCountAndCurrentValueInResponse() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(Optional.of(new Branch()));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> {
            Asset a = inv.getArgument(0);
            a.setId(ASSET_ID);
            return a;
        });
        when(assetLineRepository.findByTenantIdAndAssetIdOrderByIdAsc(TENANT_ID, ASSET_ID))
            .thenReturn(List.of(line("4", "10"), line("2", "5")));

        AssetResponse response = service.create(request(), TENANT_ID);

        assertThat(response.getLineCount()).isEqualTo(2);
        // 4*10 + 2*5 = 50
        assertThat(response.getTotalCurrentValue()).isEqualByComparingTo("50");
    }

    @Test
    void delete_withLines_isRejected() {
        Asset asset = new Asset();
        asset.setId(ASSET_ID);
        asset.setTenantId(TENANT_ID);
        when(assetRepository.findByIdAndTenantId(ASSET_ID, TENANT_ID)).thenReturn(Optional.of(asset));
        when(assetLineRepository.countByTenantIdAndAssetId(TENANT_ID, ASSET_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(ASSET_ID, TENANT_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((AppException) e).getErrorCode())
            .isEqualTo(AssetErrorCode.ASSET_HAS_LINES);
        verify(assetRepository, never()).delete(any());
    }

    @Test
    void delete_withNoLines_deletes() {
        Asset asset = new Asset();
        asset.setId(ASSET_ID);
        asset.setTenantId(TENANT_ID);
        when(assetRepository.findByIdAndTenantId(ASSET_ID, TENANT_ID)).thenReturn(Optional.of(asset));
        when(assetLineRepository.countByTenantIdAndAssetId(TENANT_ID, ASSET_ID)).thenReturn(0L);

        service.delete(ASSET_ID, TENANT_ID);

        verify(assetRepository).delete(asset);
    }

    private static CreateAssetRequest request() {
        CreateAssetRequest request = new CreateAssetRequest();
        request.setBranchId(BRANCH_ID);
        request.setName("Oven");
        request.setCategory(AssetCategory.KITCHEN_EQUIPMENT);
        return request;
    }

    private static AssetLine line(String remaining, String unitCost) {
        AssetLine line = new AssetLine();
        line.setRemainingQuantity(new BigDecimal(remaining));
        line.setUnitCost(new BigDecimal(unitCost));
        return line;
    }
}
