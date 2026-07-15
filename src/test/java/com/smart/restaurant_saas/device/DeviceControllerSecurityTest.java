package com.smart.restaurant_saas.device;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.device.dto.DeviceLoginResponse;
import com.smart.restaurant_saas.device.dto.DeviceResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = DeviceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(DeviceControllerSecurityTest.MethodSecurityConfig.class)
class DeviceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeviceService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void createRequiresDevicesManage() throws Exception {
        mockMvc.perform(post("/api/devices")
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cashier POS 1\",\"branchId\":12}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createAllowsDevicesManage() throws Exception {
        securityService.allow("DEVICES_MANAGE");
        when(service.create(argThat(req -> "Cashier POS 1".equals(req.getName()) && req.getBranchId().equals(12L)),
                eq(7L), eq(99L)))
            .thenReturn(DeviceResponse.builder()
                .id(44L)
                .name("Cashier POS 1")
                .branchId(12L)
                .branchName("Main Branch")
                .active(true)
                .secretKey("raw-secret")
                .build());

        mockMvc.perform(post("/api/devices")
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cashier POS 1\",\"branchId\":12}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(44L))
            .andExpect(jsonPath("$.secretKey").value("raw-secret"));

        verify(service).create(argThat(req -> "Cashier POS 1".equals(req.getName()) && req.getBranchId().equals(12L)),
            eq(7L), eq(99L));
    }

    @Test
    @WithMockUser
    void listRequiresDevicesManage() throws Exception {
        mockMvc.perform(get("/api/devices")
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsSysAdmin() throws Exception {
        securityService.sysAdmin = true;
        when(service.findAll(7L)).thenReturn(List.of(DeviceResponse.builder()
            .id(44L)
            .name("Cashier POS 1")
            .branchId(12L)
            .branchName("Main Branch")
            .active(true)
            .build()));

        mockMvc.perform(get("/api/devices")
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(44L))
            .andExpect(jsonPath("$[0].secretKey").doesNotExist());

        verify(service).findAll(7L);
    }

    @Test
    @WithMockUser
    void deactivateRequiresDevicesManage() throws Exception {
        mockMvc.perform(patch("/api/devices/{id}/deactivate", 44L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deactivateAllowsDevicesManage() throws Exception {
        securityService.allow("DEVICES_MANAGE");
        when(service.deactivate(44L, 7L, 99L)).thenReturn(DeviceResponse.builder()
            .id(44L)
            .name("Cashier POS 1")
            .branchId(12L)
            .active(false)
            .build());

        mockMvc.perform(patch("/api/devices/{id}/deactivate", 44L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        verify(service).deactivate(44L, 7L, 99L);
    }

    @Test
    void loginRequiresNoAuthenticatedUser() throws Exception {
        when(service.login(argThat(req -> "raw-secret".equals(req.getSecretKey()))))
            .thenReturn(DeviceLoginResponse.builder()
                .branchId(12L)
                .tenantId(7L)
                .build());

        mockMvc.perform(post("/api/devices/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"secretKey\":\"raw-secret\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.branchId").value(12L))
            .andExpect(jsonPath("$.tenantId").value(7L));

        verify(service).login(argThat(req -> "raw-secret".equals(req.getSecretKey())));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        DeviceService deviceService() {
            return mock(DeviceService.class);
        }

        @Bean("securityService")
        RecordingSecurityService securityService() {
            return new RecordingSecurityService();
        }
    }

    static class RecordingSecurityService extends SecurityService {

        private final Map<String, Boolean> permissions = new HashMap<>();
        private boolean sysAdmin;

        RecordingSecurityService() {
            super(null, null);
        }

        @Override
        public boolean isSysAdmin() {
            return sysAdmin;
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
            sysAdmin = false;
        }
    }
}
