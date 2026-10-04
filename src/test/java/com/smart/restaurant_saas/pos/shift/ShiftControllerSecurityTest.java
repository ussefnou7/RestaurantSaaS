package com.smart.restaurant_saas.pos.shift;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.pos.shift.dto.CurrentShiftResponse;
import com.smart.restaurant_saas.pos.shift.dto.OpenShiftResult;
import com.smart.restaurant_saas.pos.shift.dto.ShiftListItemResponse;
import com.smart.restaurant_saas.tenant.SliceTenantConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The four read/open gates on {@link ShiftController}, asserted by their absence.
 *
 * <p>Written after a guard-reversion audit (ledger L006) found A207–A210 <b>UNCOVERED</b>: deleting
 * any of them left the suite green. These four guard the drawer — the list, a single shift, the
 * device's current shift, and opening one.
 *
 * <p><b>Deliberately not covered here:</b> {@code POST /{id}/close}. Its expression is a
 * three-way condition over {@code SHIFTS_CLOSE} and {@code SHIFTS_FORCE_CLOSE} (D122: a forced
 * close is derived from {@code closedBy != openedBy}, never accepted from the client), and the
 * audit reported it as the one gate on this controller that already fails a test when reverted.
 * Adding a thin slice assertion next to the real coverage would dilute it.
 *
 * <p>Note what these gates do <em>not</em> do: {@code SHIFTS_VIEW} admits a caller to the endpoint,
 * while {@code SHIFTS_VIEW_VARIANCE} decides whether the money fields come back at all — enforced
 * separately in {@code ShiftQueryService:65,75} and covered by its own tests. A cashier holding
 * {@code SHIFTS_VIEW} alone gets the shift with {@code cashSales}, {@code expensesAtClose} and the
 * counts withheld.
 */
@WebMvcTest(
        controllers = ShiftController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({ShiftControllerSecurityTest.MethodSecurityConfig.class, SliceTenantConfig.class})
class ShiftControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShiftService shiftService;

    @Autowired
    private ShiftQueryService shiftQueryService;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(shiftService, shiftQueryService);
        securityService.reset();
    }

    // ---- A207  GET /api/shifts ----

    @Test
    @WithMockUser
    void listRequiresShiftsView() throws Exception {
        mockMvc.perform(get("/api/shifts"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsShiftsView() throws Exception {
        securityService.allow("SHIFTS_VIEW");

        mockMvc.perform(get("/api/shifts"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void listRejectsOpenOnlyUser() throws Exception {
        // Opening a drawer is not the right to read the tenant's shift history.
        securityService.allow("SHIFTS_OPEN");

        mockMvc.perform(get("/api/shifts"))
            .andExpect(status().isForbidden());
    }

    // ---- A208  GET /api/shifts/{id} ----

    @Test
    @WithMockUser
    void getByIdRequiresShiftsView() throws Exception {
        mockMvc.perform(get("/api/shifts/{id}", 4L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getByIdAllowsShiftsView() throws Exception {
        securityService.allow("SHIFTS_VIEW");

        mockMvc.perform(get("/api/shifts/{id}", 4L))
            .andExpect(status().is2xxSuccessful());
    }

    // ---- A209  POST /api/shifts/open ----

    @Test
    @WithMockUser
    void openRequiresShiftsOpen() throws Exception {
        mockMvc.perform(post("/api/shifts/open")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingCount\":1000}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void openRejectsViewOnlyUser() throws Exception {
        // Reading shifts must not open one. The opening count is the cashier's
        // declaration of what is in the drawer, and it is frozen from here.
        securityService.allow("SHIFTS_VIEW");

        mockMvc.perform(post("/api/shifts/open")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingCount\":1000}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void openAllowsShiftsOpen() throws Exception {
        securityService.allow("SHIFTS_OPEN");
        when(shiftService.openShift(any(), anyLong()))
            .thenReturn(OpenShiftResult.created(null));

        mockMvc.perform(post("/api/shifts/open")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingCount\":1000}"))
            .andExpect(status().is2xxSuccessful());
    }

    // ---- A210  GET /api/shifts/current ----

    @Test
    @WithMockUser
    void currentRequiresShiftsView() throws Exception {
        mockMvc.perform(get("/api/shifts/current"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void currentAllowsShiftsView() throws Exception {
        securityService.allow("SHIFTS_VIEW");

        mockMvc.perform(get("/api/shifts/current"))
            .andExpect(status().is2xxSuccessful());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        ShiftService shiftService() {
            ShiftService service = mock(ShiftService.class);
            // A null shift is an ordinary result for /current — "no open drawer"
            // is not an error — so this stub is the real shape, not a placeholder.
            when(service.getCurrentShift(anyLong())).thenReturn(CurrentShiftResponse.empty());
            return service;
        }

        @Bean
        ShiftQueryService shiftQueryService() {
            ShiftQueryService service = mock(ShiftQueryService.class);
            Page<ShiftListItemResponse> empty = new PageImpl<>(List.of());
            when(service.findAll(anyLong(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(empty);
            return service;
        }

        @Bean("securityService")
        RecordingSecurityService securityService() {
            return new RecordingSecurityService();
        }
    }

    static class RecordingSecurityService extends SecurityService {

        private final Map<String, Boolean> permissions = new HashMap<>();

        RecordingSecurityService() {
            super(null, null);
        }

        @Override
        public boolean isSysAdmin() {
            return false;
        }

        @Override
        public boolean hasPermission(String permissionCode) {
            return permissions.getOrDefault(permissionCode, false);
        }

        private void allow(String permissionCode) {
            permissions.put(permissionCode, true);
        }

        private void reset() {
            permissions.clear();
        }
    }
}
