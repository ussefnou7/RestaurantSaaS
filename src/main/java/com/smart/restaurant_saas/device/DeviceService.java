package com.smart.restaurant_saas.device;

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
import com.smart.restaurant_saas.device.dto.DeviceResponse;
import com.smart.restaurant_saas.device.repository.DeviceRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final DeviceSecretHasher secretHasher;
    private final TenantTimeZoneService tenantTimeZoneService;

    @Transactional
    public DeviceResponse create(DeviceCreateRequest request, Long tenantId, Long userId) {
        Branch branch = loadBranch(request.getBranchId(), tenantId);
        String rawSecret = secretHasher.generateSecret();

        Device device = new Device();
        device.setTenantId(tenantId);
        device.setCreatedBy(userId);
        device.setName(request.getName());
        device.setBranch(branch);
        device.setSecretKeyHash(secretHasher.sha256Hex(rawSecret));
        device.setActive(true);

        return toResponse(deviceRepository.save(device), rawSecret);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> findAll(Long tenantId) {
        return deviceRepository.findByTenantIdOrderByIdDesc(tenantId)
            .stream()
            .map(device -> toResponse(device, null))
            .toList();
    }

    @Transactional
    public DeviceResponse deactivate(Long id, Long tenantId, Long userId) {
        Device device = loadOwned(id, tenantId);
        device.setActive(false);
        device.setUpdatedBy(userId);
        return toResponse(deviceRepository.save(device), null);
    }

    @Transactional
    public DeviceLoginResponse login(DeviceLoginRequest request) {
        String secretHash = secretHasher.sha256Hex(request.getSecretKey());
        Device device = deviceRepository.findBySecretKeyHash(secretHash)
            .orElseThrow(() -> new AuthenticationException(DeviceErrorCode.INVALID_DEVICE_SECRET,
                "Invalid device secret",
                ErrorParams.of("entityType", "Device")));

        if (!Boolean.TRUE.equals(device.getActive())) {
            throw new BusinessException(DeviceErrorCode.DEVICE_INACTIVE,
                "Device is inactive: " + device.getId(),
                ErrorParams.of("entityType", "Device", "entityId", device.getId()));
        }

        ZoneId zone = tenantTimeZoneService.zoneFor(device.getTenantId(), device.getBranch().getId());
        device.setLastLoginAt(LocalDateTime.now(zone));
        Device saved = deviceRepository.save(device);
        Tenant tenant = tenantRepository.findById(saved.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException(DeviceErrorCode.DEVICE_NOT_FOUND,
                "Tenant not found for device: " + saved.getId(),
                ErrorParams.of("entityType", "Tenant", "entityId", saved.getTenantId())));
        return DeviceLoginResponse.builder()
            .id(saved.getId())
            .branchId(saved.getBranch().getId())
            .branchName(saved.getBranch().getName())
            .tenantId(saved.getTenantId())
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

    private DeviceResponse toResponse(Device device, String rawSecret) {
        Branch branch = device.getBranch();
        return DeviceResponse.builder()
            .id(device.getId())
            .name(device.getName())
            .branchId(branch.getId())
            .branchName(branch.getName())
            .active(device.getActive())
            .lastLoginAt(device.getLastLoginAt())
            .secretKey(rawSecret)
            .build();
    }
}
