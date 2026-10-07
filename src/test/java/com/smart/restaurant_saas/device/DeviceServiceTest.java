package com.smart.restaurant_saas.device;

import com.smart.restaurant_saas.auth.support.TestScopes;
import com.smart.restaurant_saas.common.TestZones;
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
import java.time.LocalDateTime;
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
        deviceService = new DeviceService(deviceRepository, branchRepository, tenantRepository, secretHasher,
            new DevicePairingThrottleService(),
            TestZones.cairo(), TestScopes.tenantWide());
    }

    @Test
    void createReturnsEightDigitPairingCodeWithTenMinuteExpiryAndStoresOnlyHash() {
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
        assertThat(response.getPairingCode()).matches("\\d{8}");
        assertThat(savedDevice.getPairingCodeHash()).isNotEqualTo(response.getPairingCode());
        assertThat(savedDevice.getPairingCodeHash()).isEqualTo(secretHasher.sha256Hex(response.getPairingCode()));
        assertThat(response.getPairingCodeExpiresAt()).isEqualTo(savedDevice.getPairingCodeExpiresAt());
        assertThat(savedDevice.getPairingCodeExpiresAt())
            .isAfter(LocalDateTime.now(TestZones.CAIRO).plusMinutes(9));
        assertThat(savedDevice.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(savedDevice.getTenantId()).isEqualTo(TENANT_ID);

        when(deviceRepository.findByTenantIdOrderByIdDesc(TENANT_ID)).thenReturn(List.of(savedDevice));
        assertThat(deviceService.findAll(TENANT_ID).getFirst().getPairingCode()).isNull();
    }

    @Test
    void loginWithValidActiveCodeConsumesItAndReturnsBranchAndTenant() {
        String rawCode = "12345678";
        Device device = activeDevice();
        when(deviceRepository.findByPairingCodeHash(secretHasher.sha256Hex(rawCode))).thenReturn(Optional.of(device));
        when(deviceRepository.saveAndFlush(device)).thenReturn(device);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));

        var response = deviceService.login(loginRequest(rawCode), "client-valid");

        assertThat(response.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(response.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(device.getLastLoginAt()).isNotNull();
        assertThat(device.getPairingCodeHash()).isNull();
        assertThat(device.getPairingCodeExpiresAt()).isNull();
        verify(deviceRepository).findByPairingCodeHash(secretHasher.sha256Hex(rawCode));
    }

    @Test
    void loginWithWrongCodeThrowsInvalidPairingCode() {
        String rawCode = "87654321";
        when(deviceRepository.findByPairingCodeHash(secretHasher.sha256Hex(rawCode))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.login(loginRequest(rawCode), "client-wrong"))
            .isInstanceOfSatisfying(AuthenticationException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(DeviceErrorCode.INVALID_DEVICE_PAIRING_CODE);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            });
    }

    @Test
    void loginWithExpiredCodeClearsItAndRejectsPairing() {
        String rawCode = "12345678";
        Device device = activeDevice();
        device.setPairingCodeExpiresAt(LocalDateTime.now(TestZones.CAIRO).minusSeconds(1));
        when(deviceRepository.findByPairingCodeHash(secretHasher.sha256Hex(rawCode))).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.login(loginRequest(rawCode), "client-expired"))
            .isInstanceOfSatisfying(AuthenticationException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_PAIRING_CODE_EXPIRED));

        assertThat(device.getPairingCodeHash()).isNull();
        assertThat(device.getPairingCodeExpiresAt()).isNull();
        verify(deviceRepository).saveAndFlush(device);
    }

    @Test
    void loginWithInactiveDeviceThrowsDeviceInactive() {
        Device device = activeDevice();
        device.setActive(false);
        String rawCode = "12345678";
        when(deviceRepository.findByPairingCodeHash(secretHasher.sha256Hex(rawCode))).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.login(loginRequest(rawCode), "client-inactive"))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_INACTIVE);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ex.getParams()).containsEntry("entityId", 44L);
            });
        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void regeneratePairingCodeInvalidatesPreviousCode() {
        Device device = activeDevice();
        String previousHash = device.getPairingCodeHash();
        when(deviceRepository.findByIdAndTenantId(44L, TENANT_ID)).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);

        var response = deviceService.regeneratePairingCode(44L, TENANT_ID, USER_ID);

        assertThat(response.getPairingCode()).matches("\\d{8}");
        assertThat(device.getPairingCodeHash()).isNotEqualTo(previousHash);
        assertThat(device.getPairingCodeHash()).isEqualTo(secretHasher.sha256Hex(response.getPairingCode()));
        assertThat(device.getUpdatedBy()).isEqualTo(USER_ID);
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

    private DeviceLoginRequest loginRequest(String pairingCode) {
        DeviceLoginRequest request = new DeviceLoginRequest();
        request.setPairingCode(pairingCode);
        return request;
    }

    private Device activeDevice() {
        Device device = new Device();
        device.setId(44L);
        device.setTenantId(TENANT_ID);
        device.setName("Cashier POS 1");
        device.setBranch(branch());
        device.setPairingCodeHash(secretHasher.sha256Hex("12345678"));
        device.setPairingCodeExpiresAt(LocalDateTime.now(TestZones.CAIRO).plusMinutes(10));
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
