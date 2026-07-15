package com.smart.restaurant_saas.assets.report.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetSummaryReportResponse {

    private final BigDecimal totalOriginalInvestment;
    private final BigDecimal totalCurrentValue;
}
