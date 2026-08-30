package com.smart.restaurant_saas.inventory.uom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.UomService;
import com.smart.restaurant_saas.inventory.core.enums.UomType;
import com.smart.restaurant_saas.inventory.uom.dto.UomLookupResponse;
import com.smart.restaurant_saas.inventory.uom.dto.UomRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.SecurityService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UomLookupIntegrationTest {

    private static final Long TENANT_ID = 988_001L;
    private static final Long OTHER_TENANT_ID = 988_002L;
    private static final Long GLOBAL_ACTIVE_UOM_ID = 988_101L;
    private static final Long GLOBAL_INACTIVE_UOM_ID = 988_102L;
    private static final Long TENANT_ACTIVE_UOM_ID = 988_201L;
    private static final Long TENANT_INACTIVE_UOM_ID = 988_202L;
    private static final Long OTHER_TENANT_UOM_ID = 988_301L;

    @Autowired
    private UomService uomService;

    @Autowired
    private UomController uomController;

    @MockitoBean
    private SecurityService securityService;

    @Autowired
    private UomLookupVersionService versionService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedUoms() {
        versionService.evictAll();
        jdbcTemplate.update("""
            DELETE FROM uom
            WHERE id IN (?, ?, ?, ?, ?)
            """, GLOBAL_ACTIVE_UOM_ID, GLOBAL_INACTIVE_UOM_ID, TENANT_ACTIVE_UOM_ID,
            TENANT_INACTIVE_UOM_ID, OTHER_TENANT_UOM_ID);
        jdbcTemplate.update("""
            DELETE FROM tenants
            WHERE id IN (?, ?)
            """, TENANT_ID, OTHER_TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'UOM Lookup Tenant', 'UOM_LOOKUP_TENANT', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo'),
                   (?, 'Other UOM Tenant', 'OTHER_UOM_TENANT', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID, OTHER_TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, base_uom_id, code, name, name_ar, symbol, symbol_ar, type,
                             factor_to_base, entered_factor, active, created_at, updated_at)
            VALUES
              (?, NULL, NULL, 'LOOKUP_GLOBAL_ACTIVE', 'Lookup Global Active', 'عام نشط',
               'lga', 'ل-ن', 'COUNT', 1, 1, TRUE, TIMESTAMP '2026-01-01 08:00:00', NULL),
              (?, NULL, NULL, 'LOOKUP_GLOBAL_INACTIVE', 'Lookup Global Inactive', 'عام غير نشط',
               'lgi', 'ل-غن', 'COUNT', 1, 1, FALSE, TIMESTAMP '2026-01-01 08:01:00', TIMESTAMP '2026-01-01 08:02:00'),
              (?, ?, ?, 'LOOKUP_TENANT_ACTIVE', 'Lookup Tenant Active', 'مستأجر نشط',
               'lta', 'ل-من', 'COUNT', 2, 2, TRUE, TIMESTAMP '2026-01-01 08:03:00', NULL),
              (?, ?, ?, 'LOOKUP_TENANT_INACTIVE', 'Lookup Tenant Inactive', 'مستأجر غير نشط',
               'lti', 'ل-مغن', 'COUNT', 3, 3, FALSE, TIMESTAMP '2026-01-01 08:04:00', TIMESTAMP '2026-01-01 08:05:00'),
              (?, ?, ?, 'LOOKUP_OTHER_TENANT', 'Lookup Other Tenant', 'مستأجر آخر',
               'lot', 'ل-آخر', 'COUNT', 4, 4, TRUE, TIMESTAMP '2026-01-01 08:06:00', NULL)
            """, GLOBAL_ACTIVE_UOM_ID, GLOBAL_INACTIVE_UOM_ID,
            TENANT_ACTIVE_UOM_ID, TENANT_ID, GLOBAL_ACTIVE_UOM_ID,
            TENANT_INACTIVE_UOM_ID, TENANT_ID, GLOBAL_ACTIVE_UOM_ID,
            OTHER_TENANT_UOM_ID, OTHER_TENANT_ID, GLOBAL_ACTIVE_UOM_ID);
    }

    @Test
    void lookupIncludesInactiveGlobalsTenantRowsAndExcludesOtherTenantRows() {
        UomLookupResponse response = uomService.findLookupForTenant(TENANT_ID);

        List<Long> ids = response.getItems().stream()
            .map(item -> item.getId())
            .toList();

        assertThat(ids)
            .contains(GLOBAL_ACTIVE_UOM_ID, GLOBAL_INACTIVE_UOM_ID, TENANT_ACTIVE_UOM_ID,
                TENANT_INACTIVE_UOM_ID)
            .doesNotContain(OTHER_TENANT_UOM_ID);
        assertThat(response.getItems())
            .filteredOn(item -> item.getId().equals(TENANT_INACTIVE_UOM_ID))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.getActive()).isFalse();
                assertThat(item.getCode()).isEqualTo("LOOKUP_TENANT_INACTIVE");
                assertThat(item.getSymbol()).isEqualTo("lti");
                assertThat(item.getSymbolAr()).isEqualTo("ل-مغن");
                assertThat(item.getName()).isEqualTo("Lookup Tenant Inactive");
                assertThat(item.getNameAr()).isEqualTo("مستأجر غير نشط");
                assertThat(item.getFactorToBase()).isEqualByComparingTo("3.000000");
                assertThat(item.getBaseUomId()).isEqualTo(GLOBAL_ACTIVE_UOM_ID);
                assertThat(item.getType()).isEqualTo(UomType.COUNT);
            });
    }

    @Test
    void availablePickerEndpointRemainsActiveOnly() {
        List<Long> ids = uomService.findAvailableForTenant(TENANT_ID).stream()
            .map(response -> response.getId())
            .toList();

        assertThat(ids)
            .contains(GLOBAL_ACTIVE_UOM_ID, TENANT_ACTIVE_UOM_ID)
            .doesNotContain(GLOBAL_INACTIVE_UOM_ID, TENANT_INACTIVE_UOM_ID, OTHER_TENANT_UOM_ID);
    }

    @Test
    void createAndDeactivateMoveVersionAndDeactivateUpdatesAuditTimestamp() {
        String beforeCreate = versionService.versionForTenant(TENANT_ID);

        UomRequest request = new UomRequest();
        request.setCode("LOOKUP_CREATED_" + System.nanoTime());
        request.setName("Lookup Created");
        request.setNameAr("منشأ");
        request.setSymbol("lc");
        request.setSymbolAr("ل-م");
        request.setType(UomType.COUNT);
        request.setBaseUom(GLOBAL_ACTIVE_UOM_ID);
        request.setFactorToBase(new BigDecimal("5.000000"));
        assertThat(uomService.createForTenant(request, TENANT_ID).getSymbolAr()).isEqualTo("ل-م");
        entityManager.flush();

        String afterCreate = versionService.versionForTenant(TENANT_ID);
        assertThat(afterCreate).isNotEqualTo(beforeCreate);

        LocalDateTime updatedBeforeDeactivate = updatedAt(TENANT_ACTIVE_UOM_ID);
        String beforeDeactivate = afterCreate;

        uomService.deactivate(TENANT_ACTIVE_UOM_ID, TENANT_ID, false);
        entityManager.flush();
        entityManager.clear();

        String afterDeactivate = versionService.versionForTenant(TENANT_ID);
        assertThat(afterDeactivate).isNotEqualTo(beforeDeactivate);

        LocalDateTime updatedAfterDeactivate = updatedAt(TENANT_ACTIVE_UOM_ID);
        assertThat(updatedBeforeDeactivate).isNull();
        assertThat(updatedAfterDeactivate).isNotNull();
        assertThat(active(TENANT_ACTIVE_UOM_ID)).isFalse();
    }

    @Test
    void globalDeactivateMovesTenantVersionAndUpdatesAuditTimestamp() {
        String beforeDeactivate = versionService.versionForTenant(TENANT_ID);
        LocalDateTime updatedBeforeDeactivate = updatedAt(GLOBAL_ACTIVE_UOM_ID);

        uomService.deactivate(GLOBAL_ACTIVE_UOM_ID, null, true);
        entityManager.flush();
        entityManager.clear();

        String afterDeactivate = versionService.versionForTenant(TENANT_ID);
        LocalDateTime updatedAfterDeactivate = updatedAt(GLOBAL_ACTIVE_UOM_ID);

        assertThat(afterDeactivate).isNotEqualTo(beforeDeactivate);
        assertThat(updatedBeforeDeactivate).isNull();
        assertThat(updatedAfterDeactivate).isNotNull();
        assertThat(active(GLOBAL_ACTIVE_UOM_ID)).isFalse();
    }

    // Calls the controller bean directly, so the INVENTORY_SETUP_VIEW gate added to
    // UomController applies here too. This test asserts ETag/304 semantics, not authorization,
    // so the gate is satisfied by stubbing SecurityService rather than by building a full
    // authenticated tenant context. Authorization itself is covered by UomControllerSecurityTest.
    @Test
    void lookupHonorsIfNoneMatchWithNotModifiedAndNoBody() {
        when(securityService.hasPermission("INVENTORY_SETUP_VIEW")).thenReturn(true);
        UomLookupResponse lookup = uomService.findLookupForTenant(TENANT_ID);

        ResponseEntity<UomLookupResponse> response = uomController.lookup(
            TENANT_ID,
            UomLookupVersionService.etagValue(lookup.getVersion()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getHeaders().getETag())
            .isEqualTo(UomLookupVersionService.etagValue(lookup.getVersion()));
        assertThat(response.getBody()).isNull();
    }

    @Test
    @WithMockUser
    void ordinaryUnrelatedResponseIncludesLookupVersionHeader() throws Exception {
        String header = mockMvc.perform(get("/actuator")
                .header("X-Tenant-Id", TENANT_ID))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(UomLookupVersionService.RESPONSE_HEADER);

        assertThat(header).startsWith("uom=");
    }

    @Test
    void resolveSingleUomIncludesInactiveAndRejectsOtherTenantRows() {
        assertThat(uomService.resolveForTenant(TENANT_INACTIVE_UOM_ID, TENANT_ID))
            .satisfies(item -> {
                assertThat(item.getId()).isEqualTo(TENANT_INACTIVE_UOM_ID);
                assertThat(item.getActive()).isFalse();
            });

        assertThatThrownBy(() -> uomService.resolveForTenant(OTHER_TENANT_UOM_ID, TENANT_ID))
            .isInstanceOfSatisfying(ResourceNotFoundException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.RESOURCE_NOT_FOUND);
                assertThat(ex.getParams()).containsEntry("uomId", OTHER_TENANT_UOM_ID);
            });
    }

    private LocalDateTime updatedAt(Long uomId) {
        return jdbcTemplate.queryForObject(
            "SELECT updated_at FROM uom WHERE id = ?",
            LocalDateTime.class,
            uomId);
    }

    private Boolean active(Long uomId) {
        return jdbcTemplate.queryForObject(
            "SELECT active FROM uom WHERE id = ?",
            Boolean.class,
            uomId);
    }
}
