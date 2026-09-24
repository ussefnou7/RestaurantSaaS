package com.smart.restaurant_saas.menu.dto;

import com.smart.restaurant_saas.media.dto.MediaSummaryResponse;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MenuVariantResponse {

    private final Long id;
    private final String name;
    private final String variantLabel;
    private final String variantLabelAr;
    private final BigDecimal sellingPrice;
    /**
     * A variant is a product row of its own, so it can carry its own image — a Large pizza may be
     * photographed differently from a Small.
     *
     * <p>Serialized as {@code null} rather than omitted: unlike {@link MenuItemResponse} this class
     * is not {@code NON_NULL}, and making it so would change the shape the POS already parses.
     * Whether to fall back to the parent's image is the consumer's decision, not this projection's.
     */
    private final MediaSummaryResponse image;
}
