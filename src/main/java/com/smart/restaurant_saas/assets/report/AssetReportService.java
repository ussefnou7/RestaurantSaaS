package com.smart.restaurant_saas.assets.report;

import com.smart.restaurant_saas.assets.asset.Asset;
import com.smart.restaurant_saas.assets.asset.AssetRepository;
import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.disposal.AssetDisposal;
import com.smart.restaurant_saas.assets.disposal.AssetDisposalRepository;
import com.smart.restaurant_saas.assets.report.dto.AssetDisposalReportRow;
import com.smart.restaurant_saas.assets.report.dto.AssetSummaryReportResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssetReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final AssetLineRepository assetLineRepository;
    private final AssetRepository assetRepository;
    private final AssetDisposalRepository assetDisposalRepository;

    /**
     * Summary totals over all lines of the tenant. Interpretation (to confirm in review, since it
     * was not spelled out verbatim): {@code totalOriginalInvestment = SUM(quantity * unitCost)} and
     * {@code totalCurrentValue = SUM(remainingQuantity * unitCost)} — i.e. current value is a
     * straight-line "remaining units at original unit cost", with no depreciation applied.
     */
    @Transactional(readOnly = true)
    public AssetSummaryReportResponse summary(Long tenantId) {
        List<AssetLine> lines = assetLineRepository.findByTenantId(tenantId);
        BigDecimal totalOriginalInvestment = lines.stream()
            .map(l -> l.getQuantity().multiply(l.getUnitCost()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, ROUNDING);
        BigDecimal totalCurrentValue = lines.stream()
            .map(l -> l.getRemainingQuantity().multiply(l.getUnitCost()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, ROUNDING);
        return AssetSummaryReportResponse.builder()
            .totalOriginalInvestment(totalOriginalInvestment)
            .totalCurrentValue(totalCurrentValue)
            .build();
    }

    @Transactional(readOnly = true)
    public Page<AssetDisposalReportRow> disposals(Long tenantId, Pageable pageable) {
        Page<AssetDisposal> page =
            assetDisposalRepository.findByTenantIdOrderByDisposalDateDescIdDesc(tenantId, pageable);

        List<Long> lineIds = page.stream().map(AssetDisposal::getAssetLineId).distinct().toList();
        List<Long> assetIds = page.stream().map(AssetDisposal::getAssetId).distinct().toList();
        Map<Long, AssetLine> linesById = assetLineRepository.findAllById(lineIds).stream()
            .collect(Collectors.toMap(AssetLine::getId, Function.identity()));
        Map<Long, Asset> assetsById = assetRepository.findAllById(assetIds).stream()
            .collect(Collectors.toMap(Asset::getId, Function.identity()));

        return page.map(d -> {
            AssetLine line = linesById.get(d.getAssetLineId());
            Asset asset = assetsById.get(d.getAssetId());
            BigDecimal value = line == null ? null
                : d.getQuantityDisposed().multiply(line.getUnitCost()).setScale(SCALE, ROUNDING);
            return AssetDisposalReportRow.builder()
                .disposalId(d.getId())
                .assetName(asset == null ? null : asset.getName())
                .assetLineLabel(line == null ? null : line.getLabel())
                .quantityDisposed(d.getQuantityDisposed())
                .reason(d.getReason())
                .disposalDate(d.getDisposalDate())
                .value(value)
                .build();
        });
    }
}
