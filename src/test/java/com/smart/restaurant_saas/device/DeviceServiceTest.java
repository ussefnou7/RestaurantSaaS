package com.smart.restaurant_saas.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.AuthenticationException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.device.dto.DeviceCreateRequest;
import com.smart.restaurant_saas.device.dto.DeviceLoginRequest;
import com.smart.restaurant_saas.device.repository.DeviceRepository;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long BRANCH_ID = 12L;

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private TenantRepository tenantRepository;
    private DeviceSecretHasher secretHasher;
    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        secretHasher = new DeviceSecretHasher();
        deviceService = new DeviceService(deviceRepository, branchRepository, tenantRepository, secretHasher);
    }

    @Test
    void createReturnsRawSecretOnceAndStoresOnlyHash() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(branch()));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device device = invocation.getArgument(0);
            device.setId(55L);
            return device;
        });

        var response = deviceService.create(createRequest(), TENANT_ID, USER_ID);

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device savedDevice = captor.getValue();
        assertThat(response.getSecretKey()).isNotBlank();
        assertThat(savedDevice.getSecretKeyHash()).isNotEqualTo(response.getSecretKey());
        assertThat(savedDevice.getSecretKeyHash()).isEqualTo(secretHasher.sha256Hex(response.getSecretKey()));
        assertThat(savedDevice.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(savedDevice.getTenantId()).isEqualTo(TENANT_ID);

        when(deviceRepository.findByTenantIdOrderByIdDesc(TENANT_ID)).thenReturn(List.of(savedDevice));
        assertThat(deviceService.findAll(TENANT_ID).getFirst().getSecretKey()).isNull();
    }

    @Test
    void loginWithValidActiveSecretUpdatesLastLoginAndReturnsBranchAndTenant() {
        String rawSecret = "device-secret";
        Device device = activeDevice();
        when(deviceRepository.findBySecretKeyHash(secretHasher.sha256Hex(rawSecret))).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));

        var response = deviceService.login(loginRequest(rawSecret));

        assertThat(response.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(response.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(device.getLastLoginAt()).isNotNull();
        verify(deviceRepository).findBySecretKeyHash(secretHasher.sha256Hex(rawSecret));
    }

    @Test
    void loginWithWrongSecretThrowsInvalidDeviceSecret() {
        String rawSecret = "wrong-secret";
        when(deviceRepository.findBySecretKeyHash(secretHasher.sha256Hex(rawSecret))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.login(loginRequest(rawSecret)))
            .isInstanceOfSatisfying(AuthenticationException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(DeviceErrorCode.INVALID_DEVICE_SECRET);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            });
    }

    @Test
    void loginWithInactiveDeviceThrowsDeviceInactive() {
        Device device = activeDevice();
        device.setActive(false);
        String rawSecret = "inactive-secret";
        when(deviceRepository.findBySecretKeyHash(secretHasher.sha256Hex(rawSecret))).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.login(loginRequest(rawSecret)))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_INACTIVE);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ex.getParams()).containsEntry("entityId", 44L);
            });
        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void deactivateIsIdempotentAndSetsUpdatedBy() {
        Device device = activeDevice();
        when(deviceRepository.findByIdAndTenantId(44L, TENANT_ID)).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);

        var response = deviceService.deactivate(44L, TENANT_ID, USER_ID);

        assertThat(response.getActive()).isFalse();
        assertThat(device.getActive()).isFalse();
        assertThat(device.getUpdatedBy()).isEqualTo(USER_ID);
    }

    private DeviceCreateRequest createRequest() {
        DeviceCreateRequest request = new DeviceCreateRequest();
        request.setName("Cashier POS 1");
        request.setBranchId(BRANCH_ID);
        return request;
    }

    private DeviceLoginRequest loginRequest(String secretKey) {
        DeviceLoginRequest request = new DeviceLoginRequest();
        request.setSecretKey(secretKey);
        return request;
    }

    private Device activeDevice() {
        Device device = new Device();
        device.setId(44L);
        device.setTenantId(TENANT_ID);
        device.setName("Cashier POS 1");
        device.setBranch(branch());
        device.setSecretKeyHash(secretHasher.sha256Hex("device-secret"));
        device.setActive(true);
        return device;
    }

    private Branch branch() {
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setTenantId(TENANT_ID);
        branch.setName("Main Branch");
        branch.setActive(true);
        return branch;
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("Demo Tenant");
        tenant.setCode("demo");
        return tenant;
    }
}
