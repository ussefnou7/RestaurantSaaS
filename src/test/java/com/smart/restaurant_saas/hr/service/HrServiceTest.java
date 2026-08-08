package com.smart.restaurant_saas.hr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.common.sequence.TenantSequenceService;
import com.smart.restaurant_saas.hr.dto.request.CreateEmployeeRequest;
import com.smart.restaurant_saas.job.dto.request.CreateJobRequest;
import com.smart.restaurant_saas.hr.dto.request.CreateLeaveRequestRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateLeaveBalanceRequest;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.job.entity.Job;
import com.smart.restaurant_saas.hr.entity.LeaveBalance;
import com.smart.restaurant_saas.hr.entity.LeaveRequest;
import com.smart.restaurant_saas.hr.entity.LeaveType;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.job.repository.JobRepository;
import com.smart.restaurant_saas.hr.repository.LeaveBalanceRepository;
import com.smart.restaurant_saas.hr.repository.LeaveRequestRepository;
import com.smart.restaurant_saas.hr.repository.LeaveTypeRepository;
import com.smart.restaurant_saas.job.service.JobService;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HrServiceTest {

    private final Map<Long, Job> jobs = new HashMap<>();
    private final Map<Long, Employee> employees = new HashMap<>();
    private final Map<Long, LeaveBalance> leaveBalances = new HashMap<>();
    private final Map<Long, LeaveType> leaveTypes = new HashMap<>();
    private final Map<Long, LeaveRequest> leaveRequests = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(100L);

    private StubTenantProvider currentTenantProvider;
    private StubScopeProvider currentUserScopeProvider;
    private StubHrValidationService hrValidationService;

    @BeforeEach
    void setUp() {
        currentTenantProvider = new StubTenantProvider();
        currentUserScopeProvider = new StubScopeProvider();
        hrValidationService = new StubHrValidationService();
        leaveTypes.put(1L, leaveType(1L));
    }

    @Test
    void jobCannotBeDeactivatedWhenUsedByActiveEmployees() {
        Job job = job(3L, 5L, "cashier", true);
        jobs.put(job.getId(), job);
        EmployeeRepository employeeRepository = employeeRepository(true);
        JobService service = new JobService(
                currentTenantProvider,
                sequenceService("KFC-JOB-0001"),
                jobRepository(),
                employeeRepository
        );

        assertThatThrownBy(() -> service.updateJobStatus(3L, new UpdateActiveStatusRequest(false)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(HrErrorCode.DEACTIVATION_BLOCKED);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("active employees");
                });
    }

    @Test
    void createJobUsesTenantAndGeneratedCode() {
        JobService service = new JobService(
                currentTenantProvider,
                sequenceService("KFC-JOB-0001"),
                jobRepository(),
                employeeRepository(false)
        );

        var response = service.createJob(new CreateJobRequest("Cashier", null, true));

        assertThat(response.code()).isEqualTo("KFC-JOB-0001");
        assertThat(response.name()).isEqualTo("Cashier");
        assertThat(response.nameEn()).isEqualTo("Cashier");
        assertThat(jobs.get(response.id()).getTenantId()).isEqualTo(5L);
        assertThat(jobs.get(response.id()).getNameEn()).isEqualTo("Cashier");
        assertThat(jobs.get(response.id()).getCreatedBy()).isEqualTo(10L);
    }

    @Test
    void createJobAcceptsArabicNameWithoutEnglishName() {
        JobService service = new JobService(
                currentTenantProvider,
                sequenceService("KFC-JOB-0001"),
                jobRepository(),
                employeeRepository(false)
        );

        var response = service.createJob(new CreateJobRequest(
                null,
                "كاشير",
                null,
                null,
                true,
                null,
                null
        ));

        assertThat(response.name()).isEqualTo("كاشير");
        assertThat(response.nameEn()).isNull();
        assertThat(response.nameAr()).isEqualTo("كاشير");
        assertThat(jobs.get(response.id()).getName()).isEqualTo("كاشير");
        assertThat(jobs.get(response.id()).getNameAr()).isEqualTo("كاشير");
    }

    @Test
    void createJobRejectsMissingBilingualName() {
        JobService service = new JobService(
                currentTenantProvider,
                sequenceService("KFC-JOB-0001"),
                jobRepository(),
                employeeRepository(false)
        );

        assertThatThrownBy(() -> service.createJob(new CreateJobRequest(
                null,
                null,
                null,
                null,
                true,
                null,
                null
        )))
                .isInstanceOfSatisfying(ValidationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(HrErrorCode.VALIDATION_FAILED);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("nameEn or nameAr");
                });
    }

    @Test
    void createJobSkipsGeneratedCodeCollision() {
        jobs.put(1L, job(1L, 5L, "KFC-JOB-0001", true));
        JobService service = new JobService(
                currentTenantProvider,
                sequenceService("KFC-JOB-0001", "KFC-JOB-0002"),
                jobRepository(),
                employeeRepository(false)
        );

        var response = service.createJob(new CreateJobRequest("Cashier", null, true));

        assertThat(response.code()).isEqualTo("KFC-JOB-0002");
    }

    @Test
    void createEmployeeValidatesBranchAndJobAndUsesGeneratedCode() {
        EmployeeService service = new EmployeeService(
                currentTenantProvider,
                currentUserScopeProvider,
                hrValidationService,
                sequenceService("KFC-EMP-0001"),
                employeeRepository(false)
        );

        var response = service.createEmployee(new CreateEmployeeRequest(
                7L,
                3L,
                null,
                "Employee One",
                "01000000000",
                "E@EXAMPLE.COM",
                null,
                null,
                LocalDate.of(2026, 1, 1),
                BigDecimal.valueOf(5000),
                true,
                null
        ));

        assertThat(response.branchId()).isEqualTo(7L);
        assertThat(response.jobId()).isEqualTo(3L);
        assertThat(response.code()).isEqualTo("KFC-EMP-0001");
        assertThat(response.fullNameEn()).isEqualTo("Employee One");
        assertThat(response.email()).isEqualTo("e@example.com");
        assertThat(hrValidationService.checkedBranchId).isEqualTo(7L);
    }

    @Test
    void createEmployeeSkipsGeneratedCodeCollision() {
        employees.put(1L, employee());
        EmployeeService service = new EmployeeService(
                currentTenantProvider,
                currentUserScopeProvider,
                hrValidationService,
                sequenceService("KFC-EMP-0001", "KFC-EMP-0002"),
                employeeRepository(false)
        );

        var response = service.createEmployee(new CreateEmployeeRequest(
                7L,
                3L,
                null,
                "Employee One",
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                BigDecimal.valueOf(5000),
                true,
                null
        ));

        assertThat(response.code()).isEqualTo("KFC-EMP-0002");
    }

    @Test
    void leaveRequestRejectsMismatchedDaysCount() {
        LeaveRequestService service = new LeaveRequestService(
                currentTenantProvider,
                currentUserScopeProvider,
                hrValidationService,
                null,
                leaveRequestRepository(),
                leaveTypeRepository(),
                employeeRepository(false)
        );

        assertThatThrownBy(() -> service.createLeaveRequest(new CreateLeaveRequestRequest(
                1L,
                1L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 3),
                BigDecimal.valueOf(2),
                null
        )))
                .isInstanceOfSatisfying(ValidationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(HrErrorCode.VALIDATION_FAILED);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("daysCount");
                });
    }

    @Test
    void generateLeaveBalancesUsesOnlyActiveTenantLeaveTypes() {
        leaveTypes.clear();
        LeaveType activeTenantType = leaveType(1L);
        activeTenantType.setDefaultDays(BigDecimal.valueOf(12));
        leaveTypes.put(activeTenantType.getId(), activeTenantType);

        LeaveType inactiveTenantType = leaveType(2L);
        inactiveTenantType.setActive(false);
        inactiveTenantType.setDefaultDays(BigDecimal.valueOf(30));
        leaveTypes.put(inactiveTenantType.getId(), inactiveTenantType);

        LeaveType otherTenantType = leaveType(3L);
        otherTenantType.setTenantId(99L);
        otherTenantType.setDefaultDays(BigDecimal.valueOf(40));
        leaveTypes.put(otherTenantType.getId(), otherTenantType);

        LeaveBalanceService service = new LeaveBalanceService(
                currentTenantProvider,
                hrValidationService,
                leaveTypeRepository(),
                leaveBalanceRepository()
        );

        var response = service.generateMissingBalances(1L, 2026);
        service.generateMissingBalances(1L, 2026);

        assertThat(response).hasSize(1);
        assertThat(leaveBalances).hasSize(1);
        LeaveBalance balance = leaveBalances.values().iterator().next();
        assertThat(balance.getTenantId()).isEqualTo(5L);
        assertThat(balance.getEmployeeId()).isEqualTo(1L);
        assertThat(balance.getBranchId()).isEqualTo(7L);
        assertThat(balance.getLeaveTypeId()).isEqualTo(1L);
        assertThat(balance.getOpeningBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getAssignedDays()).isEqualByComparingTo(BigDecimal.valueOf(12));
        assertThat(balance.getUsedDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getRemainingDays()).isEqualByComparingTo(BigDecimal.valueOf(12));
    }

    @Test
    void generateLeaveBalancesFailsWhenTenantHasNoActiveLeaveTypes() {
        leaveTypes.clear();
        LeaveBalanceService service = new LeaveBalanceService(
                currentTenantProvider,
                hrValidationService,
                leaveTypeRepository(),
                leaveBalanceRepository()
        );

        assertThatThrownBy(() -> service.generateMissingBalances(1L, 2026))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(HrErrorCode.NO_ACTIVE_LEAVE_TYPES);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo(
                            "No active leave types found for this tenant. Please create leave types first."
                    );
                });
    }

    @Test
    void updateLeaveBalanceRecalculatesEmployeeSpecificRemainingDays() {
        LeaveBalance balance = leaveBalance(50L, 1L, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.valueOf(3));
        leaveBalances.put(balance.getId(), balance);
        LeaveBalanceService service = new LeaveBalanceService(
                currentTenantProvider,
                hrValidationService,
                leaveTypeRepository(),
                leaveBalanceRepository()
        );

        var response = service.updateLeaveBalance(
                50L,
                new UpdateLeaveBalanceRequest(BigDecimal.valueOf(2), BigDecimal.valueOf(20), false, "Adjusted")
        );

        assertThat(response.remainingDays()).isEqualByComparingTo(BigDecimal.valueOf(19));
        assertThat(response.active()).isFalse();
        assertThat(balance.getUsedDays()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(balance.getNotes()).isEqualTo("Adjusted");
        assertThat(hrValidationService.checkedBranchId).isEqualTo(7L);
    }

    @Test
    void updateLeaveBalanceRejectsNegativeRemainingDays() {
        LeaveBalance balance = leaveBalance(51L, 1L, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.valueOf(3));
        leaveBalances.put(balance.getId(), balance);
        LeaveBalanceService service = new LeaveBalanceService(
                currentTenantProvider,
                hrValidationService,
                leaveTypeRepository(),
                leaveBalanceRepository()
        );

        assertThatThrownBy(() -> service.updateLeaveBalance(
                51L,
                new UpdateLeaveBalanceRequest(BigDecimal.ZERO, BigDecimal.valueOf(2), true, null)
        ))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(HrErrorCode.NEGATIVE_REMAINING_BALANCE);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("remainingDays cannot be negative");
                });
    }

    private JobRepository jobRepository() {
        return (JobRepository) Proxy.newProxyInstance(
                JobRepository.class.getClassLoader(),
                new Class<?>[]{JobRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndTenantId" -> Optional.ofNullable(jobs.get(args[0]))
                            .filter(job -> job.getTenantId().equals(args[1]));
                    case "findByIdAndTenantIdAndActiveTrue" -> Optional.ofNullable(jobs.get(args[0]))
                            .filter(job -> job.getTenantId().equals(args[1]))
                            .filter(job -> Boolean.TRUE.equals(job.getActive()));
                    case "existsByTenantIdAndCode" -> jobs.values().stream()
                            .anyMatch(job -> job.getTenantId().equals(args[0]) && job.getCode().equals(args[1]));
                    case "existsByTenantIdAndCodeAndIdNot" -> false;
                    case "save", "saveAndFlush" -> saveJob((Job) args[0]);
                    case "toString" -> "JobRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private TenantSequenceService sequenceService(String... codes) {
        return new StubTenantSequenceService(codes);
    }

    private EmployeeRepository employeeRepository(boolean jobUsed) {
        return (EmployeeRepository) Proxy.newProxyInstance(
                EmployeeRepository.class.getClassLoader(),
                new Class<?>[]{EmployeeRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByTenantIdAndJobIdAndActiveTrue" -> jobUsed;
                    case "existsByTenantIdAndCode" -> employees.values().stream()
                            .anyMatch(employee -> employee.getTenantId().equals(args[0])
                                    && employee.getCode().equals(args[1]));
                    case "findByIdAndTenantId" -> Optional.ofNullable(employees.get(args[0]))
                            .filter(employee -> employee.getTenantId().equals(args[1]));
                    case "save", "saveAndFlush" -> saveEmployee((Employee) args[0]);
                    case "toString" -> "EmployeeRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private LeaveTypeRepository leaveTypeRepository() {
        return (LeaveTypeRepository) Proxy.newProxyInstance(
                LeaveTypeRepository.class.getClassLoader(),
                new Class<?>[]{LeaveTypeRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTenantIdOrderByIdDesc" -> leaveTypes.values().stream()
                            .filter(leaveType -> leaveType.getTenantId().equals(args[0]))
                            .sorted(Comparator.comparing(LeaveType::getId).reversed())
                            .toList();
                    case "findByTenantIdAndActiveTrueOrderByIdAsc" -> leaveTypes.values().stream()
                            .filter(leaveType -> leaveType.getTenantId().equals(args[0]))
                            .filter(leaveType -> Boolean.TRUE.equals(leaveType.getActive()))
                            .sorted(Comparator.comparing(LeaveType::getId))
                            .toList();
                    case "findByIdAndTenantIdAndActiveTrue" -> Optional.ofNullable(leaveTypes.get(args[0]))
                            .filter(leaveType -> leaveType.getTenantId().equals(args[1]))
                            .filter(leaveType -> Boolean.TRUE.equals(leaveType.getActive()));
                    case "findByIdAndTenantId" -> Optional.ofNullable(leaveTypes.get(args[0]))
                            .filter(leaveType -> leaveType.getTenantId().equals(args[1]));
                    case "existsByTenantIdAndCode" -> leaveTypes.values().stream()
                            .anyMatch(leaveType -> leaveType.getTenantId().equals(args[0])
                                    && leaveType.getCode().equals(args[1]));
                    case "existsByTenantIdAndCodeAndIdNot" -> leaveTypes.values().stream()
                            .anyMatch(leaveType -> leaveType.getTenantId().equals(args[0])
                                    && leaveType.getCode().equals(args[1])
                                    && !leaveType.getId().equals(args[2]));
                    case "toString" -> "LeaveTypeRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private LeaveBalanceRepository leaveBalanceRepository() {
        return (LeaveBalanceRepository) Proxy.newProxyInstance(
                LeaveBalanceRepository.class.getClassLoader(),
                new Class<?>[]{LeaveBalanceRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTenantIdAndEmployeeIdAndYearOrderByIdAsc" -> leaveBalances.values().stream()
                            .filter(balance -> balance.getTenantId().equals(args[0]))
                            .filter(balance -> balance.getEmployeeId().equals(args[1]))
                            .filter(balance -> balance.getYear().equals(args[2]))
                            .sorted(Comparator.comparing(LeaveBalance::getId))
                            .toList();
                    case "findByTenantIdAndEmployeeIdAndLeaveTypeIdAndYear",
                            "findWithLockByTenantIdAndEmployeeIdAndLeaveTypeIdAndYear" -> leaveBalances.values().stream()
                            .filter(balance -> balance.getTenantId().equals(args[0]))
                            .filter(balance -> balance.getEmployeeId().equals(args[1]))
                            .filter(balance -> balance.getLeaveTypeId().equals(args[2]))
                            .filter(balance -> balance.getYear().equals(args[3]))
                            .findFirst();
                    case "findByIdAndTenantId", "findWithLockByIdAndTenantId" -> Optional.ofNullable(leaveBalances.get(args[0]))
                            .filter(balance -> balance.getTenantId().equals(args[1]));
                    case "save", "saveAndFlush" -> saveLeaveBalance((LeaveBalance) args[0]);
                    case "toString" -> "LeaveBalanceRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private LeaveRequestRepository leaveRequestRepository() {
        return (LeaveRequestRepository) Proxy.newProxyInstance(
                LeaveRequestRepository.class.getClassLoader(),
                new Class<?>[]{LeaveRequestRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save", "saveAndFlush" -> saveLeaveRequest((LeaveRequest) args[0]);
                    case "toString" -> "LeaveRequestRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private Job saveJob(Job job) {
        if (job.getId() == null) {
            job.setId(ids.incrementAndGet());
        }
        jobs.put(job.getId(), job);
        return job;
    }

    private Employee saveEmployee(Employee employee) {
        if (employee.getId() == null) {
            employee.setId(ids.incrementAndGet());
        }
        employees.put(employee.getId(), employee);
        return employee;
    }

    private LeaveRequest saveLeaveRequest(LeaveRequest leaveRequest) {
        if (leaveRequest.getId() == null) {
            leaveRequest.setId(ids.incrementAndGet());
        }
        leaveRequests.put(leaveRequest.getId(), leaveRequest);
        return leaveRequest;
    }

    private LeaveBalance saveLeaveBalance(LeaveBalance leaveBalance) {
        if (leaveBalance.getId() == null) {
            leaveBalance.setId(ids.incrementAndGet());
        }
        leaveBalances.put(leaveBalance.getId(), leaveBalance);
        return leaveBalance;
    }

    private Job job(Long id, Long tenantId, String code, boolean active) {
        Job job = new Job();
        job.setId(id);
        job.setTenantId(tenantId);
        job.setName(code);
        job.setCode(code);
        job.setActive(active);
        return job;
    }

    private LeaveType leaveType(Long id) {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(id);
        leaveType.setTenantId(5L);
        leaveType.setCode("CUSTOM");
        leaveType.setName("Custom Leave");
        leaveType.setDefaultDays(BigDecimal.valueOf(8));
        leaveType.setPaid(true);
        leaveType.setActive(true);
        return leaveType;
    }

    private LeaveBalance leaveBalance(
            Long id,
            Long leaveTypeId,
            BigDecimal openingBalance,
            BigDecimal assignedDays,
            BigDecimal usedDays
    ) {
        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setId(id);
        leaveBalance.setTenantId(5L);
        leaveBalance.setEmployeeId(1L);
        leaveBalance.setBranchId(7L);
        leaveBalance.setLeaveTypeId(leaveTypeId);
        leaveBalance.setYear(2026);
        leaveBalance.setOpeningBalance(openingBalance);
        leaveBalance.setAssignedDays(assignedDays);
        leaveBalance.setUsedDays(usedDays);
        leaveBalance.setRemainingDays(openingBalance.add(assignedDays).subtract(usedDays));
        leaveBalance.setActive(true);
        return leaveBalance;
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setTenantId(5L);
        employee.setBranchId(7L);
        employee.setJobId(3L);
        employee.setCode("KFC-EMP-0001");
        employee.setFullName("Employee One");
        employee.setActive(true);
        return employee;
    }

    private static final class StubTenantProvider extends CurrentTenantProvider {

        private StubTenantProvider() {
            super((HttpServletRequest) null, null);
        }

        @Override
        public Long getCurrentTenantId() {
            return 5L;
        }

        @Override
        public Long getActorUserId() {
            return 10L;
        }
    }

    private static final class StubScopeProvider extends CurrentUserScopeProvider {

        private StubScopeProvider() {
            super(null, null, null);
        }
    }

    private final class StubHrValidationService extends HrValidationService {

        private Long checkedBranchId;

        private StubHrValidationService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Branch findActiveBranch(Long tenantId, Long branchId) {
            Branch branch = new Branch();
            branch.setId(branchId);
            branch.setTenantId(tenantId);
            branch.setName("Main");
            branch.setCode("main");
            branch.setActive(true);
            return branch;
        }

        @Override
        public Job findActiveJob(Long tenantId, Long jobId) {
            return job(jobId, tenantId, "cashier", true);
        }

        @Override
        public Job findJob(Long tenantId, Long jobId) {
            return job(jobId, tenantId, "cashier", true);
        }

        @Override
        public Employee findActiveEmployee(Long tenantId, Long employeeId) {
            return employee();
        }

        @Override
        public void ensureCanAccessBranch(Long branchId) {
            checkedBranchId = branchId;
        }

        @Override
        public void validateOptionalUser(Long tenantId, Long userId, Long currentEmployeeId) {
        }
    }

    private static final class StubTenantSequenceService extends TenantSequenceService {

        private final String[] codes;
        private int index;

        private StubTenantSequenceService(String... codes) {
            super(null, null, null);
            this.codes = codes;
        }

        @Override
        public String generateEntityCode(Long tenantId, com.smart.restaurant_saas.tenant.TenantEntityPrefix entityPrefix) {
            return codes[Math.min(index++, codes.length - 1)];
        }
    }
}
