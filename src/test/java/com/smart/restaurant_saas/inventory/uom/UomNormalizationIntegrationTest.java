package com.smart.restaurant_saas.inventory.uom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.UomConversionService;
import com.smart.restaurant_saas.inventory.core.UomService;
import com.smart.restaurant_saas.inventory.core.enums.UomType;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.uom.dto.UomRequest;
import com.smart.restaurant_saas.inventory.uom.dto.UomResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers the invariant UomConversionService.baseUomId() has always depended on and
 * nothing ever enforced: factorToBase is measured from the root of the chain, and
 * every UOM's base is a root.
 *
 * The regression that prompted this: a UOM created with base = KILOGRAM and
 * factor = 25 resolved to base KILOGRAM while KILOGRAM resolves to GRAM, so
 * sameBaseUom() failed and the unit converted to nothing at all.
 */
@SpringBootTest
@TestPropertySource(properties = "order-consumption.batching.enabled=false")
@Transactional
class UomNormalizationIntegrationTest {

    private static final Long TENANT_ID = 994_001L;
    private static final Long OTHER_TENANT_ID = 994_002L;

    private static final Long GRAM_ID = 994_101L;
    private static final Long KILOGRAM_ID = 994_102L;
    private static final Long TON_ID = 994_103L;
    private static final Long OTHER_TENANT_UOM_ID = 994_104L;

    @Autowired
    private UomService uomService;

    @Autowired
    private UomConversionService conversionService;

    @Autowired
    private UomRepository uomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedMasterData() {
        insertTenant(TENANT_ID, "UOM Normalization Tenant", "UOM_NORM");
        insertTenant(OTHER_TENANT_ID, "Other Tenant", "UOM_NORM_OTHER");

        // The V6 shape: one root per physical type, everything else calibrated to it.
        insertUom(GRAM_ID, null, null, "UN-GRAM", "g", "WEIGHT", "1", "1");
        insertUom(KILOGRAM_ID, null, GRAM_ID, "UN-KG", "kg", "WEIGHT", "1000", "1000");
        insertUom(TON_ID, null, GRAM_ID, "UN-TON", "t", "WEIGHT", "1000000", "1000000");

        // Private to another tenant — never a legal parent for TENANT_ID.
        insertUom(OTHER_TENANT_UOM_ID, OTHER_TENANT_ID, GRAM_ID, "UN-OTHER", "o", "WEIGHT", "7", "7");
    }

    // =========================================================================
    // Normalization
    // =========================================================================

    @Test
    void createWithNonRootParentNormalizesFactorOntoTheRoot() {
        UomResponse sack = create("UN-SACK", "sack", UomType.WEIGHT, KILOGRAM_ID, "25");

        assertThat(sack.getSymbolAr()).isEqualTo("sack-ar");
        assertThat(symbolArOf(sack.getId())).isEqualTo("sack-ar");

        // The engine's number is root-relative: 25 kg is 25000 g.
        assertThat(baseUomIdOf(sack.getId())).isEqualTo(GRAM_ID);
        assertThat(factorToBaseOf(sack.getId())).isEqualByComparingTo("25000");

        // What the user typed survives untouched, for the edit form.
        assertThat(enteredFactorOf(sack.getId())).isEqualByComparingTo("25");
        assertThat(enteredAgainstOf(sack.getId())).isEqualTo(KILOGRAM_ID);
    }

    @Test
    void createWithRootParentStoresTheFactorUnchanged() {
        UomResponse jar = create("UN-JAR", "jar", UomType.WEIGHT, GRAM_ID, "500");

        // Multiplying by the root's factor of 1 is the same code path, not a special case.
        assertThat(baseUomIdOf(jar.getId())).isEqualTo(GRAM_ID);
        assertThat(factorToBaseOf(jar.getId())).isEqualByComparingTo("500");
        assertThat(enteredFactorOf(jar.getId())).isEqualByComparingTo("500");
        assertThat(enteredAgainstOf(jar.getId())).isEqualTo(GRAM_ID);
    }

    @Test
    void threeLevelEntryFlattensToTheRootInOneCreate() {
        // box = 6 kg, sack = 4 boxes. Nothing here mentions grams.
        UomResponse box = create("UN-BOX", "box", UomType.WEIGHT, KILOGRAM_ID, "6");
        UomResponse sack = create("UN-SACK3", "sack3", UomType.WEIGHT, box.getId(), "4");

        assertThat(baseUomIdOf(box.getId())).isEqualTo(GRAM_ID);
        assertThat(factorToBaseOf(box.getId())).isEqualByComparingTo("6000");

        // 4 x 6 x 1000, compounded in a single create because the parent was
        // already root-calibrated when it was stored.
        assertThat(baseUomIdOf(sack.getId())).isEqualTo(GRAM_ID);
        assertThat(factorToBaseOf(sack.getId())).isEqualByComparingTo("24000");
        assertThat(enteredFactorOf(sack.getId())).isEqualByComparingTo("4");
        assertThat(enteredAgainstOf(sack.getId())).isEqualTo(box.getId());
    }

    @Test
    void typeIsDerivedFromTheParentAndADisagreeingRequestIsIgnored() {
        // The request claims COUNT; the parent is WEIGHT and wins.
        UomResponse sack = create("UN-SACKT", "sackt", UomType.COUNT, KILOGRAM_ID, "25");

        assertThat(sack.getType()).isEqualTo(UomType.WEIGHT);
    }

    // =========================================================================
    // Conversion after normalization — the regression that started this
    // =========================================================================

    @Test
    void normalizedUomConvertsToItsOwnParent() {
        Uom sack = created("UN-SACKP", KILOGRAM_ID, "25");
        Uom kilogram = load(KILOGRAM_ID);

        // Previously threw noConversionFound: the sack's base was KILOGRAM while
        // KILOGRAM's base was GRAM, so the two never compared equal.
        assertThat(conversionService.convert(new BigDecimal("2"), sack, kilogram, null, TENANT_ID))
            .isEqualByComparingTo("50");
    }

    @Test
    void normalizedUomConvertsToASiblingNonRoot() {
        Uom sack = created("UN-SACKS", KILOGRAM_ID, "25");
        Uom ton = load(TON_ID);

        // 40 sacks = 1000 kg = 1 ton.
        assertThat(conversionService.convert(new BigDecimal("40"), sack, ton, null, TENANT_ID))
            .isEqualByComparingTo("1");
    }

    @Test
    void normalizedUomConvertsToTheRoot() {
        Uom sack = created("UN-SACKR", KILOGRAM_ID, "25");
        Uom gram = load(GRAM_ID);

        assertThat(conversionService.convert(BigDecimal.ONE, sack, gram, null, TENANT_ID))
            .isEqualByComparingTo("25000");
    }

    @Test
    void roundTripThroughTheParentReturnsTheOriginalQuantity() {
        Uom sack = created("UN-SACKRT", KILOGRAM_ID, "25");
        Uom kilogram = load(KILOGRAM_ID);

        BigDecimal original = new BigDecimal("3");
        BigDecimal inKilograms = conversionService.convert(original, sack, kilogram, null, TENANT_ID);
        BigDecimal backToSacks = conversionService.convert(inKilograms, kilogram, sack, null, TENANT_ID);

        assertThat(inKilograms).isEqualByComparingTo("75");
        assertThat(backToSacks).isEqualByComparingTo(original);
    }

    // =========================================================================
    // Guards
    // =========================================================================

    @Test
    void everyCreatedUomSharesTheRootOfItsParent() {
        // The property baseUomId() relies on, asserted directly: a created UOM
        // and its parent must resolve to the same calibration point, whether the
        // parent is a root, a global non-root, or another tenant UOM.
        record Case(String code, Long parentId, String factor) {}
        var cases = new Case[] {
            new Case("UN-P1", GRAM_ID, "500"),
            new Case("UN-P2", KILOGRAM_ID, "25"),
            new Case("UN-P3", TON_ID, "2"),
        };

        for (Case c : cases) {
            UomResponse created = create(c.code(), c.code(), UomType.WEIGHT, c.parentId(), c.factor());
            assertThat(rootOf(created.getId()))
                .as("root of %s must match root of its parent %s", c.code(), c.parentId())
                .isEqualTo(rootOf(c.parentId()));
        }

        // And one whose parent is itself a tenant UOM.
        UomResponse box = create("UN-P4", "p4", UomType.WEIGHT, KILOGRAM_ID, "6");
        UomResponse nested = create("UN-P5", "p5", UomType.WEIGHT, box.getId(), "3");
        assertThat(rootOf(nested.getId())).isEqualTo(rootOf(box.getId()));
    }

    @Test
    void createRejectsAnotherTenantsPrivateUomAsParent() {
        UomRequest request = request("UN-STEAL", "steal", UomType.WEIGHT, OTHER_TENANT_UOM_ID, "3");

        // A bare findById used to accept this, persisting an FK that
        // findAvailableForTenant would never return.
        assertThatThrownBy(() -> uomService.createForTenant(request, TENANT_ID))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.UOM_BASE_NOT_AVAILABLE);
    }

    @Test
    void createRejectsANullBaseUomOnTheTenantPath() {
        UomRequest request = request("UN-ORPHAN", "orphan", UomType.WEIGHT, null, "6000");

        // A null parent means "calibration root". Tenants never create roots, so
        // this is a malformed request rather than a new root.
        assertThatThrownBy(() -> uomService.createForTenant(request, TENANT_ID))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.UOM_BASE_REQUIRED);
    }

    @Test
    void databaseRejectsARootCarryingAFactorOtherThanOne() {
        // The row shape the SysAdmin panel produces (O29): baseCode is dropped by
        // Jackson, so the row lands as a claimed root holding a real factor.
        assertThatThrownBy(() ->
            insertUom(994_901L, null, null, "UN-BADROOT", "br", "WEIGHT", "6000", "6000"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_uom_root_factor");
    }

    @Test
    void databaseRejectsAUomThatIsItsOwnBase() {
        assertThatThrownBy(() ->
            insertUom(994_902L, TENANT_ID, 994_902L, "UN-SELF", "sf", "WEIGHT", "2", "2"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_uom_no_self_base");
    }

    // =========================================================================
    // Compatibility — one rule, not three
    // =========================================================================

    @Test
    void convertValueAgreesWithTheConversionService() {
        Uom sack = created("UN-SACKC", KILOGRAM_ID, "25");
        Uom ton = load(TON_ID);
        BigDecimal quantity = new BigDecimal("40");

        // convertValue used to gate on type equality and the conversion service on
        // shared base, so the two could disagree about the same pair.
        assertThat(uomService.convertValue(quantity, sack.getId(), TON_ID))
            .isEqualByComparingTo(conversionService.convert(quantity, sack, ton, null, TENANT_ID));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UomRequest request(String code, String symbol, UomType type, Long baseUom, String factor) {
        UomRequest request = new UomRequest();
        request.setCode(code);
        request.setName(code);
        request.setSymbol(symbol);
        request.setSymbolAr(symbol + "-ar");
        request.setType(type);
        request.setBaseUom(baseUom);
        request.setFactorToBase(new BigDecimal(factor));
        return request;
    }

    private UomResponse create(String code, String symbol, UomType type, Long baseUom, String factor) {
        UomResponse response = uomService.createForTenant(
            request(code, symbol, type, baseUom, factor), TENANT_ID);
        // The assertions below read through JDBC, so the insert has to be on the
        // wire rather than sitting in the persistence context.
        entityManager.flush();
        return response;
    }

    /** Creates and returns the managed entity, for the conversion-service cases. */
    private Uom created(String code, Long baseUom, String factor) {
        return load(create(code, code, UomType.WEIGHT, baseUom, factor).getId());
    }

    private Uom load(Long id) {
        return uomRepository.findById(id).orElseThrow();
    }

    /** Entity-level equivalent of the private UomConversionService.baseUomId(). */
    private Long rootOf(Long id) {
        Long base = baseUomIdOf(id);
        return base == null ? id : base;
    }

    private Long baseUomIdOf(Long id) {
        return jdbcTemplate.queryForObject("SELECT base_uom_id FROM uom WHERE id = ?", Long.class, id);
    }

    private BigDecimal factorToBaseOf(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT factor_to_base FROM uom WHERE id = ?", BigDecimal.class, id);
    }

    private BigDecimal enteredFactorOf(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT entered_factor FROM uom WHERE id = ?", BigDecimal.class, id);
    }

    private String symbolArOf(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT symbol_ar FROM uom WHERE id = ?", String.class, id);
    }

    private Long enteredAgainstOf(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT entered_against_uom_id FROM uom WHERE id = ?", Long.class, id);
    }

    private void insertTenant(Long id, String name, String code) {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, id, name, code);
    }

    private void insertUom(Long id, Long tenantId, Long baseUomId, String code, String symbol,
                           String type, String factorToBase, String enteredFactor) {
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, base_uom_id, code, name, symbol, type,
                             factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric),
                    TRUE, CURRENT_TIMESTAMP)
            """, id, tenantId, baseUomId, code, code, symbol, type, factorToBase, enteredFactor);
    }
}
