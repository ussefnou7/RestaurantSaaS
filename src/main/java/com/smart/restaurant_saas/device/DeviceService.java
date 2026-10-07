package com.smart.restaurant_saas.device;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.common.AuthenticationException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.device.dto.DeviceCreateRequest;
import com.smart.restaurant_saas.device.dto.DeviceLoginRequest;
import com.smart.restaurant_saas.device.dto.DeviceLoginResponse;
import com.smart.restaurant_saas.device.dto.DevicePairingCodeResponse;
import com.smart.restaurant_saas.device.dto.DeviceResponse;
import com.smart.restaurant_saas.device.repository.DeviceRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final Duration PAIRING_CODE_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final DeviceRepository deviceRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final DeviceSecretHasher secretHasher;
    private final DevicePairingThrottleService pairingThrottleService;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final CurrentUserScopeProvider currentUserScopeProvider;

    @Transactional
    public DeviceResponse create(DeviceCreateRequest request, Long tenantId, Long userId) {
        currentUserScopeProvider.ensureCanAccessBranch(request.getBranchId());
        Branch branch = loadBranch(request.getBranchId(), tenantId);
        PairingCode pairingCode = generateUniquePairingCode();
        ZoneId zone = tenantTimeZoneService.zoneFor(tenantId, branch.getId());

        Device device = new Device();
        device.setTenantId(tenantId);
        device.setCreatedBy(userId);
        device.setName(request.getName());
        device.setBranch(branch);
        device.setPairingCodeHash(pairingCode.hash());
        device.setPairingCodeExpiresAt(LocalDateTime.now(zone).plus(PAIRING_CODE_LIFETIME));
        device.setActive(true);

        return toResponse(deviceRepository.save(device), pairingCode.raw());
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> findAll(Long tenantId) {
        // No branchId parameter on this endpoint, so the scope is applied to the result rather
        // than to a filter argument: a scoped caller sees only their own branch's devices.
        Long scope = currentUserScopeProvider.getCurrentBranchId().orElse(null);
        return deviceRepository.findByTenantIdOrderByIdDesc(tenantId)
            .stream()
            .filter(device -> scope == null || scope.equals(device.getBranch().getId()))
            .map(device -> toResponse(device, null))
            .toList();
    }

    @Transactional
    public DeviceResponse deactivate(Long id, Long tenantId, Long userId) {
        Device device = loadOwned(id, tenantId);
        currentUserScopeProvider.ensureCanAccessBranch(device.getBranch().getId());
        device.setActive(false);
        device.setPairingCodeHash(null);
        device.setPairingCodeExpiresAt(null);
        device.setUpdatedBy(userId);
        return toResponse(deviceRepository.save(device), null);
    }

    @Transactional
    public DevicePairingCodeResponse regeneratePairingCode(Long id, Long tenantId, Long userId) {
        Device device = loadOwned(id, tenantId);
        currentUserScopeProvider.ensureCanAccessBranch(device.getBranch().getId());
        if (!Boolean.TRUE.equals(device.getActive())) {
            throw new BusinessException(DeviceErrorCode.DEVICE_INACTIVE,
                "Device is inactive: " + device.getId(),
                ErrorParams.of("entityType", "Device", "entityId", device.getId()));
        }

        PairingCode pairingCode = generateUniquePairingCode();
        ZoneId zone = tenantTimeZoneService.zoneFor(tenantId, device.getBranch().getId());
        LocalDateTime expiresAt = LocalDateTime.now(zone).plus(PAIRING_CODE_LIFETIME);
        device.setPairingCodeHash(pairingCode.hash());
        device.setPairingCodeExpiresAt(expiresAt);
        device.setUpdatedBy(userId);
        deviceRepository.save(device);
        return DevicePairingCodeResponse.builder()
            .deviceId(device.getId())
            .pairingCode(pairingCode.raw())
            .expiresAt(expiresAt)
            .build();
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public DeviceLoginResponse login(DeviceLoginRequest request, String clientKey) {
        pairingThrottleService.checkAllowed(clientKey);
        String rawCode = request.getPairingCode().trim();
        String codeHash = secretHasher.sha256Hex(rawCode);
        Device device = deviceRepository.findByPairingCodeHash(codeHash).orElse(null);
        if (device == null) {
            pairingThrottleService.recordFailure(clientKey);
            throw invalidPairingCode();
        }

        if (!Boolean.TRUE.equals(device.getActive())) {
            throw new BusinessException(DeviceErrorCode.DEVICE_INACTIVE,
                "Device is inactive: " + device.getId(),
                ErrorParams.of("entityType", "Device", "entityId", device.getId()));
        }

        ZoneId zone = tenantTimeZoneService.zoneFor(device.getTenantId(), device.getBranch().getId());
        LocalDateTime now = LocalDateTime.now(zone);
        if (device.getPairingCodeExpiresAt() == null || !device.getPairingCodeExpiresAt().isAfter(now)) {
            device.setPairingCodeHash(null);
            device.setPairingCodeExpiresAt(null);
            deviceRepository.saveAndFlush(device);
            pairingThrottleService.recordFailure(clientKey);
            throw new AuthenticationException(DeviceErrorCode.DEVICE_PAIRING_CODE_EXPIRED,
                "Device pairing code has expired",
                ErrorParams.of("entityType", "Device"));
        }

        device.setPairingCodeHash(null);
        device.setPairingCodeExpiresAt(null);
        device.setLastLoginAt(now);
        Device saved = deviceRepository.saveAndFlush(device);
        Tenant tenant = tenantRepository.findById(saved.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException(DeviceErrorCode.DEVICE_NOT_FOUND,
                "Tenant not found for device: " + saved.getId(),
                ErrorParams.of("entityType", "Tenant", "entityId", saved.getTenantId())));
        pairingThrottleService.clear(clientKey);
        return DeviceLoginResponse.builder()
            .id(saved.getId())
            .branchId(saved.getBranch().getId())
            .branchName(saved.getBranch().getName())
            .tenantId(saved.getTenantId())
            .tenantName(tenant.getName())
            .tenantCode(tenant.getCode())
            .timezone(zone.getId())
            .build();
    }

    private Branch loadBranch(Long branchId, Long tenantId) {
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(DeviceErrorCode.BRANCH_NOT_FOUND,
                "Branch not found: " + branchId,
                ErrorParams.of("entityType", "Branch", "entityId", branchId)));
    }

    private Device loadOwned(Long id, Long tenantId) {
        return deviceRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(DeviceErrorCode.DEVICE_NOT_FOUND,
                "Device not found: " + id,
                ErrorParams.of("entityType", "Device", "entityId", id)));
    }

    private PairingCode generateUniquePairingCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String raw = secretHasher.generatePairingCode();
            String hash = secretHasher.sha256Hex(raw);
            if (!deviceRepository.existsByPairingCodeHash(hash)) {
                return new PairingCode(raw, hash);
            }
        }
        throw new IllegalStateException("Unable to generate a unique device pairing code");
    }

    private AuthenticationException invalidPairingCode() {
        return new AuthenticationException(DeviceErrorCode.INVALID_DEVICE_PAIRING_CODE,
            "Invalid device pairing code",
            ErrorParams.of("entityType", "Device"));
    }

    private DeviceResponse toResponse(Device device, String pairingCode) {
        Branch branch = device.getBranch();
        return DeviceResponse.builder()
            .id(device.getId())
            .name(device.getName())
            .branchId(branch.getId())
            .branchName(branch.getName())
            .active(device.getActive())
            .lastLoginAt(device.getLastLoginAt())
            .pairingCode(pairingCode)
            .pairingCodeExpiresAt(pairingCode == null ? null : device.getPairingCodeExpiresAt())
            .build();
    }

    private record PairingCode(String raw, String hash) {
    }
}
