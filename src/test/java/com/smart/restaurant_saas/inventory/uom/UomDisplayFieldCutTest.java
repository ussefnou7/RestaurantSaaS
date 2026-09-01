package com.smart.restaurant_saas.inventory.uom;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocMaterialResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountLineResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMaterialMovementResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementRowResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceLineResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnLineResponse;
import com.smart.restaurant_saas.inventory.stock.dto.StockBalanceResponse;
import com.smart.restaurant_saas.inventory.waste.dto.WasteLineResponse;
import com.smart.restaurant_saas.menu.recipe.dto.RecipeItemResponse;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins D111 phase 3: the five non-D88 row responses carry {@code uomId} alone, and the client
 * resolves the name from its lookup cache.
 *
 * <p>This is a structural test on purpose. Re-adding {@code uomSymbol} to one of the five is a
 * one-line change that no behavioural test would catch — the field would simply reappear on the
 * wire and the frontend, which already prefers its cache, would keep rendering correctly while the
 * payload quietly regrew. It also pins the opposite direction: the three D88 responses must
 * <em>keep</em> {@code uomSymbol}, so a later "tidy-up" sweep cannot mistake them for stragglers.
 */
class UomDisplayFieldCutTest {

    /** Any field whose name would carry a UOM's display text rather than its identity. */
    private static final List<String> DISPLAY_FIELDS =
        List.of("uomSymbol", "uomName", "uomNameAr", "uomCode");

    static Stream<Class<?>> cutResponses() {
        return Stream.of(
            PurchaseReturnLineResponse.class,
            WasteLineResponse.class,
            RecipeItemResponse.class);
    }

    /**
     * Cut from the web frontend's point of view, but <em>held back</em> on the wire: the Flutter app
     * consumes both endpoints, parses no {@code uomId}, and has no lookup cache, so it renders
     * {@code uomSymbol} directly beside a quantity. Removing these would produce bare numbers on
     * mobile with no error and no log — the exact failure D111's phasing exists to prevent, one
     * client further out than phase 2 reached.
     *
     * <p>These are the entries most at risk of a well-meant tidy-up, because the web frontend no
     * longer reads them and a repo-local search makes them look dead. See O42.
     */
    static Stream<Class<?>> heldForMobile() {
        return Stream.of(StockBalanceResponse.class, PurchaseInvoiceLineResponse.class);
    }

    /**
     * The three responses D88 covers. D88 requires a ledger-sourced quantity to carry a converted
     * value <em>and</em> an explicit UOM field, and says in as many words that neither alone is
     * sufficient. O38 asks whether {@code uomId} now satisfies that; until it is settled these keep
     * {@code uomSymbol}. See D111, "D88 is not amended".
     */
    static Stream<Class<?>> d88Responses() {
        return Stream.of(
            PhysicalCountLineResponse.class,
            OrderConsumptionDocMaterialResponse.class,
            PostFreezeMovementRowResponse.class,
            PostFreezeMaterialMovementResponse.class);
    }

    @ParameterizedTest(name = "{0} carries uomId and no UOM display field")
    @MethodSource("cutResponses")
    @DisplayName("the five non-D88 row responses send the id alone")
    void cutResponsesCarryTheIdAlone(Class<?> response) {
        assertThat(fieldNames(response))
            .as("%s must still identify the unit", response.getSimpleName())
            .contains("uomId");
        assertThat(fieldNames(response))
            .as("%s must not re-add a UOM display field (D111 phase 3)", response.getSimpleName())
            .doesNotContainAnyElementsOf(DISPLAY_FIELDS);
    }

    @ParameterizedTest(name = "{0} still sends uomSymbol for the Flutter app")
    @MethodSource("heldForMobile")
    @DisplayName("the two mobile-facing responses keep the symbol until mobile can resolve ids")
    void mobileFacingResponsesKeepTheSymbol(Class<?> response) {
        assertThat(fieldNames(response))
            .as("%s feeds a Flutter screen that cannot resolve a uomId (O42)", response.getSimpleName())
            .contains("uomId", "uomSymbol");
    }

    @ParameterizedTest(name = "{0} keeps uomId and uomSymbol")
    @MethodSource("d88Responses")
    @DisplayName("the D88 responses keep the explicit symbol")
    void d88ResponsesKeepTheSymbol(Class<?> response) {
        assertThat(fieldNames(response))
            .as("%s is a D88 carve-out, not a phase-3 straggler", response.getSimpleName())
            .contains("uomId", "uomSymbol");
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(f -> !f.isSynthetic())
            .map(Field::getName)
            .toList();
    }
}
