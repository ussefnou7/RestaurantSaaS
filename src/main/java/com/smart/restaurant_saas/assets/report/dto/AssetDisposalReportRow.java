package com.smart.restaurant_saas.assets.report.dto;

import com.smart.restaurant_saas.assets.core.enums.AssetDisposalReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetDisposalReportRow {

    private final Long disposalId;
    private final String assetName;
    private final String assetLineLabel;
    private final BigDecimal quantityDisposed;
    private final AssetDisposalReason reason;
    private final LocalDate disposalDate;
    private final BigDecimal value;
}
