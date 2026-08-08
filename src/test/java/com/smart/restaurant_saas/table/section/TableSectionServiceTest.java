package com.smart.restaurant_saas.table.section;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.table.RestaurantTable;
import com.smart.restaurant_saas.table.TableErrorCode;
import com.smart.restaurant_saas.table.TableRepository;
import com.smart.restaurant_saas.table.section.dto.TableSectionRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TableSectionServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long BRANCH_ID = 3L;
    private static final Long SECTION_ID = 11L;

    @Mock
    private TableSectionRepository sectionRepository;
    @Mock
    private TableRepository tableRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private OrderRepository orderRepository;

    private TableSectionService service;

    @BeforeEach
    void setUp() {
        service = new TableSectionService(sectionRepository, tableRepository, branchRepository, orderRepository);
    }

    @Test
    void list_defaultsToActiveSectionsForBranch() {
        when(sectionRepository.findActiveByTenantIdAndBranchId(TENANT_ID, BRANCH_ID)).thenReturn(List.of(section()));

        var response = service.findAll(TENANT_ID, BRANCH_ID, false);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().name()).isEqualTo("Outdoor");
        verify(sectionRepository).findActiveByTenantIdAndBranchId(TENANT_ID, BRANCH_ID);
    }

    @Test
    void list_canIncludeInactiveForManagement() {
        when(sectionRepository.findByTenantIdAndBranchId(TENANT_ID, BRANCH_ID)).thenReturn(List.of(section()));

        service.findAll(TENANT_ID, BRANCH_ID, true);

        verify(sectionRepository).findByTenantIdAndBranchId(TENANT_ID, BRANCH_ID);
    }

    @Test
    void create_setsBranchNamesAndAudit() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(branch()));
        when(sectionRepository.save(any(TableSection.class))).thenAnswer(invocation -> {
            TableSection section = invocation.getArgument(0);
            section.setId(SECTION_ID);
            return section;
        });

        var response = service.create(request(), TENANT_ID, USER_ID);

        assertThat(response.id()).isEqualTo(SECTION_ID);
        assertThat(response.branchId()).isEqualTo(BRANCH_ID);
        assertThat(response.name()).isEqualTo("Outdoor");
        assertThat(response.nameAr()).isEqualTo("خارجي");
        assertThat(response.active()).isTrue();
        verify(sectionRepository).save(any(TableSection.class));
    }

    @Test
    void create_unknownBranch_isRejected() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(), TENANT_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(TableErrorCode.RESOURCE_NOT_FOUND);
        verify(sectionRepository, never()).save(any());
    }

    @Test
    void deactivate_marksInactiveWithoutTableGuard() {
        when(sectionRepository.findByIdAndTenantId(SECTION_ID, TENANT_ID)).thenReturn(Optional.of(section()));
        when(sectionRepository.saveAndFlush(any(TableSection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.deactivate(SECTION_ID, TENANT_ID, USER_ID);

        assertThat(response.active()).isFalse();
    }

    @Test
    void delete_rejectsWhenTablesAreReferencedByOrders() {
        when(sectionRepository.findByIdAndTenantId(SECTION_ID, TENANT_ID)).thenReturn(Optional.of(section()));
        when(orderRepository.existsByTableSectionId(SECTION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(SECTION_ID, TENANT_ID))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(TableErrorCode.SECTION_HAS_ORDERS);
        verify(tableRepository, never()).deleteAll(any());
        verify(sectionRepository, never()).delete(any());
    }

    @Test
    void delete_cascadesTablesThenRemovesSection() {
        TableSection section = section();
        RestaurantTable table = new RestaurantTable();
        table.setId(55L);
        when(sectionRepository.findByIdAndTenantId(SECTION_ID, TENANT_ID)).thenReturn(Optional.of(section));
        when(orderRepository.existsByTableSectionId(SECTION_ID)).thenReturn(false);
        when(tableRepository.findAllBySectionId(SECTION_ID)).thenReturn(List.of(table));

        service.delete(SECTION_ID, TENANT_ID);

        verify(tableRepository).deleteAll(List.of(table));
        verify(sectionRepository).delete(section);
    }

    private TableSectionRequest request() {
        TableSectionRequest request = new TableSectionRequest();
        request.setBranchId(BRANCH_ID);
        request.setName(" Outdoor ");
        request.setNameAr(" خارجي ");
        return request;
    }

    private TableSection section() {
        TableSection section = new TableSection();
        section.setId(SECTION_ID);
        section.setTenantId(TENANT_ID);
        section.setBranch(branch());
        section.setName("Outdoor");
        section.setNameAr("خارجي");
        section.setActive(true);
        return section;
    }

    private Branch branch() {
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setTenantId(TENANT_ID);
        branch.setName("Main");
        branch.setCode("MAIN");
        branch.setActive(true);
        return branch;
    }
}
