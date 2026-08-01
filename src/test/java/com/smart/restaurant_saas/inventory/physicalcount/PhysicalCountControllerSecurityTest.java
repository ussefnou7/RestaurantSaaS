package com.smart.restaurant_saas.inventory.physicalcount;

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
import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountLineResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMaterialMovementResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementRowResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementsResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = PhysicalCountController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(PhysicalCountControllerSecurityTest.MethodSecurityConfig.class)
class PhysicalCountControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PhysicalCountService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void revertToDraftRequiresDedicatedPermission() throws Exception {
        mockMvc.perform(post("/api/inventory/physical-counts/{id}/revert-to-draft", 30L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void revertToDraftAllowsDedicatedPermission() throws Exception {
        securityService.allow("PHYSICAL_COUNT_REVERT_TO_DRAFT");
        when(service.revertToDraft(30L, 7L, 99L))
            .thenReturn(PhysicalCountResponse.builder()
                .id(30L)
                .status(PhysicalCountStatus.DRAFT)
                .build());

        mockMvc.perform(post("/api/inventory/physical-counts/{id}/revert-to-draft", 30L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(30L))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(service).revertToDraft(30L, 7L, 99L);
    }

    @Test
    @WithMockUser
    void postFreezeMovementsRequiresStockViewPermission() throws Exception {
        mockMvc.perform(get("/api/inventory/physical-counts/{id}/post-freeze-movements", 30L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void postFreezeMovementsAllowsStockViewPermission() throws Exception {
        securityService.allow("INVENTORY_STOCK_VIEW");
        when(service.findPostFreezeMovements(30L, 7L))
            .thenReturn(PostFreezeMovementsResponse.builder()
                .countId(30L)
                .totalMovementCount(4)
                .affectedMaterialCount(2)
                .materials(List.of(PostFreezeMaterialMovementResponse.builder()
                    .materialId(101L)
                    .uomId(5L)
                    .uomSymbol("bag")
                    .movementCount(1)
                    .quantityIn(new BigDecimal("1.000000"))
                    .quantityOut(new BigDecimal("0.000000"))
                    .netQuantity(new BigDecimal("1.000000"))
                    .build()))
                .build());

        mockMvc.perform(get("/api/inventory/physical-counts/{id}/post-freeze-movements", 30L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.countId").value(30L))
            .andExpect(jsonPath("$.totalMovementCount").value(4))
            .andExpect(jsonPath("$.affectedMaterialCount").value(2))
            .andExpect(jsonPath("$.materials[0].uomId").value(5L))
            .andExpect(jsonPath("$.materials[0].uomSymbol").value("bag"));

        verify(service).findPostFreezeMovements(30L, 7L);
    }

    @Test
    @WithMockUser
    void postFreezeMovementsSerializesIncludedAndAfterCountRows() throws Exception {
        securityService.allow("INVENTORY_STOCK_VIEW");
        LocalDateTime movementDate = LocalDateTime.of(2026, 7, 30, 0, 0);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 31, 10, 30);
        when(service.findPostFreezeMovements(30L, 7L))
            .thenReturn(PostFreezeMovementsResponse.builder()
                .materials(List.of())
                .included(List.of(PostFreezeMovementRowResponse.builder()
                    .materialId(101L)
                    .materialName("Flour")
                    .materialNameAr("دقيق")
                    .quantity(new BigDecimal("1.000000"))
                    .uomId(5L)
                    .uomSymbol("bag")
                    .direction(InventoryTransactionDirection.IN)
                    .movementDate(movementDate)
                    .createdAt(createdAt)
                    .referenceType("PURCHASE_INVOICE")
                    .referenceId(70L)
                    .referenceCode("PINV-70")
                    .build()))
                .afterCount(List.of(PostFreezeMovementRowResponse.builder()
                    .materialId(101L)
                    .quantity(new BigDecimal("2.000000"))
                    .uomId(5L)
                    .uomSymbol("bag")
                    .direction(InventoryTransactionDirection.OUT)
                    .movementDate(movementDate.plusDays(2))
                    .createdAt(createdAt.plusHours(1))
                    .build()))
                .build());

        mockMvc.perform(get("/api/inventory/physical-counts/{id}/post-freeze-movements", 30L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.included[0].materialId").value(101L))
            .andExpect(jsonPath("$.included[0].quantity").value(1.000000))
            .andExpect(jsonPath("$.included[0].uomId").value(5L))
            .andExpect(jsonPath("$.included[0].uomSymbol").value("bag"))
            .andExpect(jsonPath("$.included[0].direction").value("IN"))
            .andExpect(jsonPath("$.included[0].movementDate").value("2026-07-30T00:00:00"))
            .andExpect(jsonPath("$.included[0].createdAt").value("2026-07-31T10:30:00"))
            .andExpect(jsonPath("$.included[0].referenceType").value("PURCHASE_INVOICE"))
            .andExpect(jsonPath("$.included[0].referenceId").value(70L))
            .andExpect(jsonPath("$.included[0].referenceCode").value("PINV-70"))
            .andExpect(jsonPath("$.afterCount[0].direction").value("OUT"))
            .andExpect(jsonPath("$.afterCount[0].quantity").value(2.000000));

        verify(service).findPostFreezeMovements(30L, 7L);
    }

    @Test
    @WithMockUser
    void detailResponseSerializesAdjustedExpectationStateForEveryLine() throws Exception {
        securityService.allow("INVENTORY_STOCK_VIEW");
        when(service.findById(30L, 7L))
            .thenReturn(PhysicalCountResponse.builder()
                .id(30L)
                .status(PhysicalCountStatus.IN_PROGRESS)
                .lines(List.of(
                    PhysicalCountLineResponse.builder()
                        .id(201L)
                        .adjustedExpectedQuantity(new BigDecimal("95.000000"))
                        .adjustedExpectedQuantityProvisional(false)
                        .build(),
                    PhysicalCountLineResponse.builder()
                        .id(202L)
                        .adjustedExpectedQuantityProvisional(true)
                        .build()))
                .build());

        mockMvc.perform(get("/api/inventory/physical-counts/{id}", 30L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[0].adjustedExpectedQuantity").value(95.000000))
            .andExpect(jsonPath("$.lines[0].adjustedExpectedQuantityProvisional").value(false))
            .andExpect(jsonPath("$.lines[1].adjustedExpectedQuantity").doesNotExist())
            .andExpect(jsonPath("$.lines[1].adjustedExpectedQuantityProvisional").value(true));

        verify(service).findById(30L, 7L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        PhysicalCountService physicalCountService() {
            return mock(PhysicalCountService.class);
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
