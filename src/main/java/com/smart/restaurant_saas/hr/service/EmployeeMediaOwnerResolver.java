package com.smart.restaurant_saas.hr.service;

import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.spi.MediaOwnerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** HR's contribution to the media module. */
@Component
@RequiredArgsConstructor
public class EmployeeMediaOwnerResolver implements MediaOwnerResolver {

    private final EmployeeRepository employeeRepository;

    @Override
    public MediaOwnerType ownerType() {
        return MediaOwnerType.EMPLOYEE;
    }

    /**
     * Deliberately not restricted to active employees: a deactivated employee's record is still
     * read, and a photo that vanishes on deactivation would look like data loss.
     */
    @Override
    public boolean exists(Long tenantId, Long ownerId) {
        return employeeRepository.existsByIdAndTenantId(ownerId, tenantId);
    }

    /** An employee never reaches a final state, so the photo stays replaceable. */
    @Override
    public boolean isMutable(Long tenantId, Long ownerId) {
        return true;
    }
}
