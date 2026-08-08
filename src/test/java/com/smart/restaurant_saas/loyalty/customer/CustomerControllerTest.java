package com.smart.restaurant_saas.loyalty.customer;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.loyalty.customer.dto.CustomerResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
        controllers = CustomerController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomerControllerTest.MethodSecurityConfig.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void listRequiresLoyaltyView() throws Exception {
        mockMvc.perform(get("/api/loyalty/customers").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsLoyaltyView() throws Exception {
        securityService.allow("LOYALTY_VIEW");
        when(service.findAll(7L)).thenReturn(List.of(
            CustomerResponse.builder().id(1L).name("Sara").phone("0555000111").build()));

        mockMvc.perform(get("/api/loyalty/customers").header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].phone").value("0555000111"));

        verify(service).findAll(7L);
    }

    @Test
    @WithMockUser
    void listWithSearchAndPageReturnsPaginatedEnvelope() throws Exception {
        securityService.allow("LOYALTY_VIEW");
        when(service.findPage(eq(7L), eq("ahmed"),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 20)))
            .thenReturn(new PageImpl<>(
                List.of(CustomerResponse.builder().id(2L).name("Ahmed").phone("0555000222").build()),
                PageRequest.of(0, 20),
                1));

        mockMvc.perform(get("/api/loyalty/customers")
                .header("X-Tenant-Id", 7L)
                .param("search", "ahmed")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("Ahmed"))
            .andExpect(jsonPath("$.content[0].phone").value("0555000222"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.number").value(0));

        verify(service).findPage(eq(7L), eq("ahmed"),
            argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 20));
    }

    @Test
    @WithMockUser
    void listRejectsManageOnlyUser() throws Exception {
        securityService.allow("LOYALTY_MANAGE");

        mockMvc.perform(get("/api/loyalty/customers").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createRequiresLoyaltyManage() throws Exception {
        mockMvc.perform(post("/api/loyalty/customers")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"0555000111\",\"name\":\"Sara\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createRejectsViewOnlyUser() throws Exception {
        securityService.allow("LOYALTY_VIEW");

        mockMvc.perform(post("/api/loyalty/customers")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"0555000111\",\"name\":\"Sara\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createAllowsLoyaltyManage() throws Exception {
        securityService.allow("LOYALTY_MANAGE");
        Customer customer = new Customer();
        customer.setId(42L);
        customer.setTenantId(7L);
        customer.setName("Sara");
        customer.setPhone("0555000111");
        when(service.findOrCreate(eq(7L), eq("0555000111"), eq("Sara"))).thenReturn(customer);
        when(service.toResponse(customer)).thenReturn(
            CustomerResponse.builder().id(42L).name("Sara").phone("0555000111").build());

        mockMvc.perform(post("/api/loyalty/customers")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"0555000111\",\"name\":\"Sara\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(42L))
            .andExpect(jsonPath("$.phone").value("0555000111"));

        verify(service).findOrCreate(7L, "0555000111", "Sara");
    }

    @Test
    @WithMockUser
    void createRejectsBlankPhone() throws Exception {
        securityService.allow("LOYALTY_MANAGE");

        mockMvc.perform(post("/api/loyalty/customers")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"\",\"name\":\"Sara\"}"))
            .andExpect(status().isBadRequest());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        CustomerService customerService() {
            return mock(CustomerService.class);
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
