package com.smart.restaurant_saas.tenant.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.tenant.TenantService;
import com.smart.restaurant_saas.tenant.dto.CreateTenantRequest;
import com.smart.restaurant_saas.tenant.support.CrossTenantFixture;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Real JWTs, Flyway schema and committed-to-the-test-transaction writes; no mocked gates. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantSettingsIntegrationTest {

    private static final long BASE = 987_500L;
    private static final String URL = "/api/tenant-settings";
    private static final String BODY = """
            {"taxEnabled":true,"taxRate":15.1250,"taxOnServiceCharge":true,
             "serviceChargeEnabled":true,"serviceChargeRate":10.5,
             "dineInEnabled":false,"takeawayEnabled":true,"deliveryEnabled":false}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private TenantService tenantService;
    @Autowired private TenantSettingsRepository repository;
    @Autowired private DataSource dataSource;

    private CrossTenantFixture fixture;
    private String manager;
    private String viewer;
    private String noPermissions;

    @BeforeEach
    void seed() {
        fixture = new CrossTenantFixture(jdbc, jwtService, BASE);
        fixture.reset(3);
        manager = CrossTenantFixture.bearer(fixture.seedTenantWithUser(0, "SETTINGS_A", "TENANT_SETTINGS_MANAGE"));
        viewer = CrossTenantFixture.bearer(fixture.seedTenantWithUser(1, "SETTINGS_B", "TENANT_SETTINGS_VIEW"));
        noPermissions = CrossTenantFixture.bearer(fixture.seedTenantWithUser(2, "SETTINGS_C"));
        for (int index = 0; index < 3; index++) {
            jdbc.update("INSERT INTO tenant_settings (tenant_id, created_at) VALUES (?, CURRENT_TIMESTAMP)",
                    fixture.tenantId(index));
        }
    }

    @Test
    void readsDefaultsAndIgnoresForgedTenantHeader() throws Exception {
        mvc.perform(get(URL).header("Authorization", viewer).header("X-Tenant-Id", fixture.tenantId(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(fixture.tenantId(1)))
                .andExpect(jsonPath("$.taxEnabled").value(true))
                .andExpect(jsonPath("$.taxRate").value(14))
                .andExpect(jsonPath("$.taxOnServiceCharge").value(false))
                .andExpect(jsonPath("$.serviceChargeEnabled").value(false))
                .andExpect(jsonPath("$.serviceChargeRate").value(0))
                .andExpect(jsonPath("$.dineInEnabled").value(true))
                .andExpect(jsonPath("$.takeawayEnabled").value(true))
                .andExpect(jsonPath("$.deliveryEnabled").value(true))
                .andExpect(jsonPath("$.timezone").doesNotExist());
    }

    @Test
    void updatesOnlyAuthenticatedTenantAndAuditsAuthenticatedActor() throws Exception {
        mvc.perform(put(URL).header("Authorization", manager)
                        .header("X-Tenant-Id", fixture.tenantId(1)).header("X-User-Id", fixture.userId(1))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(fixture.tenantId(0)))
                .andExpect(jsonPath("$.taxRate").value(15.125))
                .andExpect(jsonPath("$.taxOnServiceCharge").value(true))
                .andExpect(jsonPath("$.serviceChargeRate").value(10.5))
                .andExpect(jsonPath("$.serviceChargeEnabled").value(true))
                .andExpect(jsonPath("$.dineInEnabled").value(false))
                .andExpect(jsonPath("$.deliveryEnabled").value(false))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(rate(fixture.tenantId(0))).isEqualByComparingTo("15.1250");
        assertThat(rate(fixture.tenantId(1))).isEqualByComparingTo("14");
        assertThat(jdbc.queryForObject("SELECT updated_by FROM tenant_settings WHERE tenant_id = ?",
                Long.class, fixture.tenantId(0))).isEqualTo(fixture.userId(0));
        assertThat(jdbc.queryForObject("SELECT timezone FROM tenants WHERE id = ?",
                String.class, fixture.tenantId(0))).isEqualTo("Africa/Cairo");

        // MANAGE alone also permits reading; no implicit role/owner bypass.
        mvc.perform(get(URL).header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taxRate").value(15.125));
    }

    @Test
    void viewerCannotUpdateEvenWithForeignTenantHeader() throws Exception {
        mvc.perform(put(URL).header("Authorization", viewer).header("X-Tenant-Id", fixture.tenantId(0))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        assertThat(rate(fixture.tenantId(0))).isEqualByComparingTo("14");
        assertThat(rate(fixture.tenantId(1))).isEqualByComparingTo("14");
    }

    @Test
    void ownerWithoutDirectPermissionCannotReadOrWrite() throws Exception {
        mvc.perform(get(URL).header("Authorization", noPermissions)).andExpect(status().isForbidden());
        mvc.perform(put(URL).header("Authorization", noPermissions)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        assertThat(rate(fixture.tenantId(2))).isEqualByComparingTo("14");
    }

    @Test
    void anonymousCannotReadOrWrite() throws Exception {
        mvc.perform(get(URL)).andExpect(status().is4xxClientError());
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void sysAdminSelectsTenantThroughHeader() throws Exception {
        Long userId = jdbc.queryForObject("SELECT u.id FROM users u JOIN roles r ON r.id = u.role_id "
                + "WHERE u.tenant_id = 0 AND r.code = 'SYS_ADMIN' ORDER BY u.id LIMIT 1", Long.class);
        String admin = CrossTenantFixture.bearer(jwtService.generateAccessToken(userId, 0L, "nou7", "SYS_ADMIN"));
        mvc.perform(put(URL).header("Authorization", admin).header("X-Tenant-Id", fixture.tenantId(1))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(fixture.tenantId(1)));
        assertThat(rate(fixture.tenantId(0))).isEqualByComparingTo("14");
        assertThat(rate(fixture.tenantId(1))).isEqualByComparingTo("15.125");
    }

    @Test
    void allOrderTypesDisabledIsRejectedWithoutChangingSettings() throws Exception {
        mvc.perform(put(URL).header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"takeawayEnabled\":true", "\"takeawayEnabled\":false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TENANT_ORDER_TYPE_REQUIRED"));
        assertThat(rate(fixture.tenantId(0))).isEqualByComparingTo("14");
    }

    @ParameterizedTest
    @CsvSource({"true,false,false", "false,true,false", "false,false,true",
            "true,true,false", "true,false,true", "false,true,true", "true,true,true"})
    void everyNonemptyOrderTypeCombinationIsAccepted(boolean dineIn, boolean takeaway, boolean delivery) throws Exception {
        String body = BODY.replace("\"dineInEnabled\":false", "\"dineInEnabled\":" + dineIn)
                .replace("\"takeawayEnabled\":true", "\"takeawayEnabled\":" + takeaway)
                .replace("\"deliveryEnabled\":false", "\"deliveryEnabled\":" + delivery);
        mvc.perform(put(URL).header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dineInEnabled").value(dineIn))
                .andExpect(jsonPath("$.takeawayEnabled").value(takeaway))
                .andExpect(jsonPath("$.deliveryEnabled").value(delivery));
    }

    @ParameterizedTest
    @CsvSource({"taxRate,-1", "taxRate,100.0001", "taxRate,14.12345", "taxRate,null",
            "serviceChargeRate,-0.01", "serviceChargeRate,101", "serviceChargeRate,1.23456", "serviceChargeRate,null",
            "taxEnabled,null", "taxOnServiceCharge,null", "serviceChargeEnabled,null",
            "dineInEnabled,null", "takeawayEnabled,null", "deliveryEnabled,null"})
    void rejectsInvalidOrNullFields(String field, String value) throws Exception {
        String body = BODY.replaceAll("\"" + field + "\":[^,}]+", "\"" + field + "\":" + value);
        mvc.perform(put(URL).header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(rate(fixture.tenantId(0))).isEqualByComparingTo("14");
    }

    @Test
    void partialPutIsRejectedRatherThanResettingOmittedFlags() throws Exception {
        mvc.perform(put(URL).header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxRate\":20}"))
                .andExpect(status().isBadRequest());
        assertThat(rate(fixture.tenantId(0))).isEqualByComparingTo("14");
    }

    @Test
    void disablingChargesRetainsConfiguredRatesAndTaxBaseFlagCanBeReset() throws Exception {
        String body = BODY.replace("\"taxEnabled\":true", "\"taxEnabled\":false")
                .replace("\"serviceChargeEnabled\":true", "\"serviceChargeEnabled\":false")
                .replace("\"taxOnServiceCharge\":true", "\"taxOnServiceCharge\":false");
        mvc.perform(put(URL).header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxEnabled").value(false))
                .andExpect(jsonPath("$.taxRate").value(15.125))
                .andExpect(jsonPath("$.taxOnServiceCharge").value(false))
                .andExpect(jsonPath("$.serviceChargeEnabled").value(false))
                .andExpect(jsonPath("$.serviceChargeRate").value(10.5));
    }

    @Test
    void missingSettingsFailsLoudlyInsteadOfInventingDefaults() throws Exception {
        jdbc.update("DELETE FROM tenant_settings WHERE tenant_id = ?", fixture.tenantId(0));
        mvc.perform(get(URL).header("Authorization", manager))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TENANT_SETTINGS_NOT_FOUND"));
    }

    @Test
    void onboardingCreatesExactlyOneDefaultSettingsRowWithTenantLocalAuditTime() {
        ZoneId zone = ZoneId.of("Asia/Riyadh");
        LocalDateTime before = LocalDateTime.now(zone);
        var tenant = tenantService.createTenant(new CreateTenantRequest("Settings onboarding", "settings-onboarding", zone.getId()));
        TenantSettings settings = repository.findByTenantId(tenant.id()).orElseThrow();
        assertThat(settings.getTaxRate()).isEqualByComparingTo("14");
        assertThat(settings.isTaxEnabled()).isTrue();
        assertThat(settings.isTaxOnServiceCharge()).isFalse();
        assertThat(settings.isServiceChargeEnabled()).isFalse();
        assertThat(settings.getServiceChargeRate()).isEqualByComparingTo("0");
        assertThat(settings.isDineInEnabled() && settings.isTakeawayEnabled() && settings.isDeliveryEnabled()).isTrue();
        assertThat(settings.getCreatedAt()).isBetween(before.minusSeconds(1), LocalDateTime.now(zone).plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant_settings WHERE tenant_id = ?",
                Long.class, tenant.id())).isEqualTo(1);
    }

    @Test
    void migrationBackfillsExistingTenantsAndExistingOwnerGrantsIdempotently() {
        jdbc.update("DELETE FROM tenant_settings WHERE tenant_id = ?", fixture.tenantId(1));
        jdbc.update("UPDATE tenant_settings SET tax_rate = 9.75, tax_on_service_charge = true WHERE tenant_id = ?",
                fixture.tenantId(0));
        var populator = new ResourceDatabasePopulator(new ClassPathResource("db/migration/V74__tenant_settings.sql"));
        populator.execute(dataSource);
        populator.execute(dataSource);
        assertThat(rate(fixture.tenantId(1))).isEqualByComparingTo("14");
        assertThat(rate(fixture.tenantId(0))).isEqualByComparingTo("9.75");
        assertThat(jdbc.queryForObject("SELECT tax_on_service_charge FROM tenant_settings WHERE tenant_id = ?",
                Boolean.class, fixture.tenantId(0))).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant_settings WHERE tenant_id IN (?, ?, ?)",
                Long.class, fixture.tenantId(0), fixture.tenantId(1), fixture.tenantId(2))).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT p.code FROM user_permissions up JOIN permissions p ON p.id = up.permission_id "
                + "WHERE up.user_id = ? ORDER BY p.code", String.class, fixture.userId(2)))
                .containsExactly("TENANT_SETTINGS_MANAGE", "TENANT_SETTINGS_VIEW");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                + "JOIN permissions p ON p.id = rp.permission_id WHERE r.code IN ('CASHIER', 'BRANCH_MANAGER') "
                + "AND p.code = 'TENANT_SETTINGS_MANAGE'", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tax_rate = -1", "tax_rate = 101", "service_charge_rate = -1", "service_charge_rate = 101",
            "dine_in_enabled = false, takeaway_enabled = false, delivery_enabled = false", "tax_on_service_charge = NULL"})
    void databaseEnforcesConstraintsEvenWithoutTheApi(String assignment) {
        assertThatThrownBy(() -> jdbc.update("UPDATE tenant_settings SET " + assignment + " WHERE tenant_id = ?",
                fixture.tenantId(0))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateTenantSettings() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tenant_settings (tenant_id, created_at) VALUES (?, CURRENT_TIMESTAMP)",
                fixture.tenantId(0))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsSettingsForUnknownTenant() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tenant_settings (tenant_id, created_at) VALUES (-100, CURRENT_TIMESTAMP)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private BigDecimal rate(long tenantId) {
        return jdbc.queryForObject("SELECT tax_rate FROM tenant_settings WHERE tenant_id = ?", BigDecimal.class, tenantId);
    }
}
