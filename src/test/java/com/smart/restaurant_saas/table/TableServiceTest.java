package com.smart.restaurant_saas.table;

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
import com.smart.restaurant_saas.inventory.core.enums.TableShape;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.table.dto.TableLayoutRequest;
import com.smart.restaurant_saas.table.dto.TableRequest;
import com.smart.restaurant_saas.table.section.TableSection;
import com.smart.restaurant_saas.table.section.TableSectionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long TABLE_ID = 10L;
    private static final Long BRANCH_ID = 3L;
    private static final Long OTHER_BRANCH_ID = 4L;
    private static final Long SECTION_ID = 11L;

    @Mock
    private TableRepository tableRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private TableSectionRepository sectionRepository;
    @Mock
    private OrderRepository orderRepository;

    private TableService service;

    @BeforeEach
    void setUp() {
        service = new TableService(tableRepository, branchRepository, sectionRepository, orderRepository);
    }

    @Test
    void list_filtersByBranchAndSectionId() {
        RestaurantTable table = table();
        when(tableRepository.findByFilters(TENANT_ID, BRANCH_ID, SECTION_ID)).thenReturn(List.of(table));

        var response = service.findAll(TENANT_ID, BRANCH_ID, SECTION_ID);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().name()).isEqualTo("T1");
        verify(tableRepository).findByFilters(TENANT_ID, BRANCH_ID, SECTION_ID);
    }

    @Test
    void create_setsIdentityFieldsAndAudit() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(branch()));
        when(sectionRepository.findByIdAndTenantId(SECTION_ID, TENANT_ID)).thenReturn(Optional.of(section()));
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> {
            RestaurantTable table = invocation.getArgument(0);
            table.setId(TABLE_ID);
            return table;
        });

        var response = service.create(request(), TENANT_ID, USER_ID);

        assertThat(response.id()).isEqualTo(TABLE_ID);
        assertThat(response.name()).isEqualTo("T1");
        assertThat(response.sectionId()).isEqualTo(SECTION_ID);
        assertThat(response.sectionName()).isEqualTo("Outdoor");
        assertThat(response.capacity()).isEqualTo(4);
        assertThat(response.shape()).isEqualTo(TableShape.SQUARE);
        assertThat(response.active()).isTrue();
        verify(tableRepository).save(any(RestaurantTable.class));
    }

    @Test
    void create_unknownBranch_isRejected() {
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(), TENANT_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(TableErrorCode.RESOURCE_NOT_FOUND);
        verify(tableRepository, never()).save(any());
    }

    @Test
    void update_preservesLayoutFields() {
        RestaurantTable table = table();
        table.setPosX(new BigDecimal("12.50"));
        table.setPosY(new BigDecimal("18.75"));
        table.setRotation(45);
        table.setShape(TableShape.ROUND);
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(table));
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(branch()));
        when(sectionRepository.findByIdAndTenantId(SECTION_ID, TENANT_ID)).thenReturn(Optional.of(section()));
        when(tableRepository.saveAndFlush(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(TABLE_ID, request(), TENANT_ID, USER_ID);

        assertThat(response.posX()).isEqualByComparingTo("12.50");
        assertThat(response.posY()).isEqualByComparingTo("18.75");
        assertThat(response.rotation()).isEqualTo(45);
        assertThat(response.shape()).isEqualTo(TableShape.ROUND);
    }

    @Test
    void update_sectionFromDifferentBranch_isRejected() {
        RestaurantTable table = table();
        TableSection section = section();
        section.setBranch(branch(OTHER_BRANCH_ID));
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(table));
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(branch()));
        when(sectionRepository.findByIdAndTenantId(SECTION_ID, TENANT_ID)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.update(TABLE_ID, request(), TENANT_ID, USER_ID))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(TableErrorCode.SECTION_BRANCH_MISMATCH);
    }

    @Test
    void updateLayout_writesOnlyLayoutFields() {
        RestaurantTable table = table();
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(table));
        when(tableRepository.saveAndFlush(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TableLayoutRequest request = new TableLayoutRequest();
        request.setPosX(new BigDecimal("100.25"));
        request.setPosY(new BigDecimal("220.50"));
        request.setRotation(90);
        request.setShape(TableShape.RECTANGLE);

        var response = service.updateLayout(TABLE_ID, request, TENANT_ID, USER_ID);

        assertThat(response.name()).isEqualTo("T1");
        assertThat(response.capacity()).isEqualTo(4);
        assertThat(response.posX()).isEqualByComparingTo("100.25");
        assertThat(response.posY()).isEqualByComparingTo("220.50");
        assertThat(response.rotation()).isEqualTo(90);
        assertThat(response.shape()).isEqualTo(TableShape.RECTANGLE);
    }

    @Test
    void deactivate_marksInactive() {
        RestaurantTable table = table();
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(table));
        when(tableRepository.saveAndFlush(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.deactivate(TABLE_ID, TENANT_ID, USER_ID);

        assertThat(response.active()).isFalse();
    }

    @Test
    void delete_rejectsWhenOrdersReferenceTable() {
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(table()));
        when(orderRepository.existsByTableId(TABLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(TABLE_ID, TENANT_ID))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(TableErrorCode.TABLE_HAS_ORDERS);
        verify(tableRepository, never()).delete(any());
    }

    @Test
    void delete_removesUnreferencedTable() {
        RestaurantTable table = table();
        when(tableRepository.findByIdAndTenantId(TABLE_ID, TENANT_ID)).thenReturn(Optional.of(table));
        when(orderRepository.existsByTableId(TABLE_ID)).thenReturn(false);

        service.delete(TABLE_ID, TENANT_ID);

        verify(tableRepository).delete(table);
    }

    private TableRequest request() {
        TableRequest request = new TableRequest();
        request.setBranchId(BRANCH_ID);
        request.setName("T1");
        request.setSectionId(SECTION_ID);
        request.setCapacity(4);
        request.setActive(true);
        return request;
    }

    private RestaurantTable table() {
        RestaurantTable table = new RestaurantTable();
        table.setId(TABLE_ID);
        table.setTenantId(TENANT_ID);
        table.setBranch(branch());
        table.setName("T1");
        table.setSection(section());
        table.setCapacity(4);
        table.setShape(TableShape.SQUARE);
        table.setActive(true);
        return table;
    }

    private Branch branch() {
        return branch(BRANCH_ID);
    }

    private Branch branch(Long id) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setTenantId(TENANT_ID);
        branch.setName("Main");
        branch.setCode("MAIN");
        branch.setActive(true);
        return branch;
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
}
