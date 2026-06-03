package com.smart.restaurant_saas.hr.service;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.dto.request.UpdateLeaveBalanceRequest;
import com.smart.restaurant_saas.hr.dto.response.LeaveBalanceResponse;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.LeaveBalance;
import com.smart.restaurant_saas.hr.entity.LeaveType;
import com.smart.restaurant_saas.hr.repository.LeaveBalanceRepository;
import com.smart.restaurant_saas.hr.repository.LeaveTypeRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final CurrentTenantProvider currentTenantProvider;
    private final HrValidationService hrValidationService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> listLeaveBalances(Long employeeId, Integer year) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, employeeId);
        int targetYear = normalizeYear(year);
        return leaveBalanceRepository.findByTenantIdAndEmployeeIdAndYearOrderByIdAsc(
                        tenantId,
                        employee.getId(),
                        targetYear
                )
                .stream()
                .map(balance -> LeaveBalanceResponse.from(balance, findLeaveType(tenantId, balance.getLeaveTypeId())))
                .toList();
    }

    @Transactional
    public List<LeaveBalanceResponse> generateMissingBalances(Long employeeId, Integer year) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = hrValidationService.findActiveEmployee(tenantId, employeeId);
        generateMissingBalancesForEmployee(employee, normalizeYear(year));
        return listLeaveBalances(employee.getId(), year);
    }

    @Transactional
    public void generateMissingBalancesForEmployee(Employee employee, Integer year) {
        int targetYear = normalizeYear(year);
        List<LeaveType> leaveTypes = leaveTypeRepository.findByTenantIdAndActiveTrueOrderByIdAsc(employee.getTenantId());
        if (leaveTypes.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "No active leave types found for this tenant. Please create leave types first."
            );
        }

        for (LeaveType leaveType : leaveTypes) {
            boolean exists = leaveBalanceRepository
                    .findByTenantIdAndEmployeeIdAndLeaveTypeIdAndYear(
                            employee.getTenantId(),
                            employee.getId(),
                            leaveType.getId(),
                            targetYear
                    )
                    .isPresent();
            if (!exists) {
                LeaveBalance balance = newBalance(employee, leaveType, targetYear);
                balance.setCreatedBy(currentTenantProvider.getActorUserId());
                leaveBalanceRepository.save(balance);
            }
        }
    }

    @Transactional
    public LeaveBalanceResponse updateLeaveBalance(Long id, UpdateLeaveBalanceRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        LeaveBalance balance = leaveBalanceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave balance not found: " + id));
        hrValidationService.ensureCanAccessBranch(balance.getBranchId());

        balance.setOpeningBalance(request.openingBalance());
        balance.setAssignedDays(request.assignedDays());
        if (request.active() != null) {
            balance.setActive(request.active());
        }
        balance.setNotes(trimToNull(request.notes()));
        recalculateRemaining(balance);
        validateRemainingNotNegative(balance);
        balance.setUpdatedBy(currentTenantProvider.getActorUserId());

        return LeaveBalanceResponse.from(
                leaveBalanceRepository.saveAndFlush(balance),
                findLeaveType(tenantId, balance.getLeaveTypeId())
        );
    }

    LeaveBalance findBalanceForLeaveRequestWithLock(
            Long tenantId,
            Long employeeId,
            Long leaveTypeId,
            Integer year
    ) {
        return leaveBalanceRepository.findWithLockByTenantIdAndEmployeeIdAndLeaveTypeIdAndYear(
                        tenantId,
                        employeeId,
                        leaveTypeId,
                        year
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Leave balance not found for employee, leave type, and year"
                ));
    }

    LeaveBalance findBalanceByIdWithLock(Long tenantId, Long id) {
        return leaveBalanceRepository.findWithLockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Leave balance not found: " + id));
    }

    void recalculateRemaining(LeaveBalance balance) {
        balance.setRemainingDays(balance.getOpeningBalance()
                .add(balance.getAssignedDays())
                .subtract(balance.getUsedDays()));
    }

    private LeaveBalance newBalance(Employee employee, LeaveType leaveType, int year) {
        LeaveBalance balance = new LeaveBalance();
        balance.setTenantId(employee.getTenantId());
        balance.setEmployeeId(employee.getId());
        balance.setBranchId(employee.getBranchId());
        balance.setLeaveTypeId(leaveType.getId());
        balance.setYear(year);
        balance.setOpeningBalance(BigDecimal.ZERO);
        balance.setAssignedDays(defaultDays(leaveType));
        balance.setUsedDays(BigDecimal.ZERO);
        balance.setActive(true);
        recalculateRemaining(balance);
        return balance;
    }

    private void validateRemainingNotNegative(LeaveBalance balance) {
        if (balance.getRemainingDays().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "remainingDays cannot be negative");
        }
    }

    private LeaveType findLeaveType(Long tenantId, Long leaveTypeId) {
        return leaveTypeRepository.findByIdAndTenantId(leaveTypeId, tenantId).orElse(null);
    }

    private int normalizeYear(Integer year) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        if (targetYear < 2000 || targetYear > 2100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "year must be between 2000 and 2100");
        }
        return targetYear;
    }

    private BigDecimal defaultDays(LeaveType leaveType) {
        return leaveType.getDefaultDays() == null ? BigDecimal.ZERO : leaveType.getDefaultDays();
    }
}
