package com.smart.restaurant_saas.hr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.dto.request.CreateEmployeeRequest;
import com.smart.restaurant_saas.hr.dto.request.CreateJobTitleRequest;
import com.smart.restaurant_saas.hr.dto.request.CreateLeaveRequestRequest;
import com.smart.restaurant_saas.hr.dto.request.CreateSalaryAdditionRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.JobTitle;
import com.smart.restaurant_saas.hr.entity.LeaveRequest;
import com.smart.restaurant_saas.hr.entity.LeaveType;
import com.smart.restaurant_saas.hr.entity.SalaryAddition;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.hr.repository.JobTitleRepository;
import com.smart.restaurant_saas.hr.repository.LeaveRequestRepository;
import com.smart.restaurant_saas.hr.repository.LeaveTypeRepository;
import com.smart.restaurant_saas.hr.repository.SalaryAdditionRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HrServiceTest {

    private final Map<Long, JobTitle> jobTitles = new HashMap<>();
    private final Map<Long, Employee> employees = new HashMap<>();
    private final Map<Long, LeaveType> leaveTypes = new HashMap<>();
    private final Map<Long, LeaveRequest> leaveRequests = new HashMap<>();
    private final Map<Long, SalaryAddition> salaryAdditions = new HashMap<>();
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
    void jobTitleCannotBeDeactivatedWhenUsedByActiveEmployees() {
        JobTitle jobTitle = jobTitle(3L, 5L, "cashier", true);
        jobTitles.put(jobTitle.getId(), jobTitle);
        EmployeeRepository employeeRepository = employeeRepository(true);
        JobTitleService service = new JobTitleService(currentTenantProvider, jobTitleRepository(), employeeRepository);

        assertThatThrownBy(() -> service.updateJobTitleStatus(3L, new UpdateActiveStatusRequest(false)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("active employees");
                });
    }

    @Test
    void createJobTitleUsesTenantAndNormalizesCode() {
        JobTitleService service = new JobTitleService(currentTenantProvider, jobTitleRepository(), employeeRepository(false));

        var response = service.createJobTitle(new CreateJobTitleRequest("Cashier", " CASHIER ", null, true));

        assertThat(response.code()).isEqualTo("cashier");
        assertThat(jobTitles.get(response.id()).getTenantId()).isEqualTo(5L);
        assertThat(jobTitles.get(response.id()).getCreatedBy()).isEqualTo(10L);
    }

    @Test
    void createEmployeeValidatesBranchAndJobTitle() {
        EmployeeService service = new EmployeeService(
                currentTenantProvider,
                currentUserScopeProvider,
                hrValidationService,
                employeeRepository(false)
        );

        var response = service.createEmployee(new CreateEmployeeRequest(
                7L,
                3L,
                null,
                " EMP-1 ",
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
        assertThat(response.jobTitleId()).isEqualTo(3L);
        assertThat(response.employeeCode()).isEqualTo("emp-1");
        assertThat(response.email()).isEqualTo("e@example.com");
        assertThat(hrValidationService.checkedBranchId).isEqualTo(7L);
    }

    @Test
    void leaveRequestRejectsMismatchedDaysCount() {
        LeaveRequestService service = new LeaveRequestService(
                currentTenantProvider,
                currentUserScopeProvider,
                hrValidationService,
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
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("daysCount");
                });
    }

    @Test
    void salaryAdditionStoresSalaryMonthAsFirstDayOfMonth() {
        SalaryAdditionService service = new SalaryAdditionService(
                currentTenantProvider,
                currentUserScopeProvider,
                hrValidationService,
                salaryAdditionRepository(),
                employeeRepository(false)
        );

        var response = service.createSalaryAddition(new CreateSalaryAdditionRequest(
                1L,
                "Attendance allowance",
                BigDecimal.valueOf(500),
                LocalDate.of(2026, 5, 25),
                null,
                true
        ));

        assertThat(response.salaryMonth()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.branchId()).isEqualTo(7L);
    }

    private JobTitleRepository jobTitleRepository() {
        return (JobTitleRepository) Proxy.newProxyInstance(
                JobTitleRepository.class.getClassLoader(),
                new Class<?>[]{JobTitleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndTenantId" -> Optional.ofNullable(jobTitles.get(args[0]))
                            .filter(jobTitle -> jobTitle.getTenantId().equals(args[1]));
                    case "findByIdAndTenantIdAndActiveTrue" -> Optional.ofNullable(jobTitles.get(args[0]))
                            .filter(jobTitle -> jobTitle.getTenantId().equals(args[1]))
                            .filter(jobTitle -> Boolean.TRUE.equals(jobTitle.getActive()));
                    case "existsByTenantIdAndCode" -> jobTitles.values().stream()
                            .anyMatch(jobTitle -> jobTitle.getTenantId().equals(args[0]) && jobTitle.getCode().equals(args[1]));
                    case "existsByTenantIdAndCodeAndIdNot" -> false;
                    case "save", "saveAndFlush" -> saveJobTitle((JobTitle) args[0]);
                    case "toString" -> "JobTitleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private EmployeeRepository employeeRepository(boolean jobTitleUsed) {
        return (EmployeeRepository) Proxy.newProxyInstance(
                EmployeeRepository.class.getClassLoader(),
                new Class<?>[]{EmployeeRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByTenantIdAndJobTitleIdAndActiveTrue" -> jobTitleUsed;
                    case "existsByTenantIdAndEmployeeCode" -> employees.values().stream()
                            .anyMatch(employee -> employee.getTenantId().equals(args[0])
                                    && employee.getEmployeeCode().equals(args[1]));
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
                    case "findByIdAndActiveTrue" -> Optional.ofNullable(leaveTypes.get(args[0]))
                            .filter(leaveType -> Boolean.TRUE.equals(leaveType.getActive()));
                    case "toString" -> "LeaveTypeRepositoryStub";
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

    private SalaryAdditionRepository salaryAdditionRepository() {
        return (SalaryAdditionRepository) Proxy.newProxyInstance(
                SalaryAdditionRepository.class.getClassLoader(),
                new Class<?>[]{SalaryAdditionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save", "saveAndFlush" -> saveSalaryAddition((SalaryAddition) args[0]);
                    case "toString" -> "SalaryAdditionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private JobTitle saveJobTitle(JobTitle jobTitle) {
        if (jobTitle.getId() == null) {
            jobTitle.setId(ids.incrementAndGet());
        }
        jobTitles.put(jobTitle.getId(), jobTitle);
        return jobTitle;
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

    private SalaryAddition saveSalaryAddition(SalaryAddition salaryAddition) {
        if (salaryAddition.getId() == null) {
            salaryAddition.setId(ids.incrementAndGet());
        }
        salaryAdditions.put(salaryAddition.getId(), salaryAddition);
        return salaryAddition;
    }

    private JobTitle jobTitle(Long id, Long tenantId, String code, boolean active) {
        JobTitle jobTitle = new JobTitle();
        jobTitle.setId(id);
        jobTitle.setTenantId(tenantId);
        jobTitle.setName(code);
        jobTitle.setCode(code);
        jobTitle.setActive(active);
        return jobTitle;
    }

    private LeaveType leaveType(Long id) {
        LeaveType leaveType = new LeaveType();
        leaveType.setId(id);
        leaveType.setCode("ANNUAL");
        leaveType.setName("Annual Leave");
        leaveType.setPaid(true);
        leaveType.setActive(true);
        return leaveType;
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setTenantId(5L);
        employee.setBranchId(7L);
        employee.setJobTitleId(3L);
        employee.setEmployeeCode("emp-1");
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
            super(null, null);
        }
    }

    private final class StubHrValidationService extends HrValidationService {

        private Long checkedBranchId;

        private StubHrValidationService() {
            super(null, null, null, null, null, null, null);
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
        public JobTitle findActiveJobTitle(Long tenantId, Long jobTitleId) {
            return jobTitle(jobTitleId, tenantId, "cashier", true);
        }

        @Override
        public JobTitle findJobTitle(Long tenantId, Long jobTitleId) {
            return jobTitle(jobTitleId, tenantId, "cashier", true);
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
        public void validateOptionalAppUser(Long tenantId, Long appUserId, Long currentEmployeeId) {
        }
    }
}
