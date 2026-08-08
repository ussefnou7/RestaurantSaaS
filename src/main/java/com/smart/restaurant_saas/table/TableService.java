package com.smart.restaurant_saas.table;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.table.dto.TableLayoutRequest;
import com.smart.restaurant_saas.table.dto.TableRequest;
import com.smart.restaurant_saas.table.dto.TableResponse;
import com.smart.restaurant_saas.table.section.TableSection;
import com.smart.restaurant_saas.table.section.TableSectionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;
    private final BranchRepository branchRepository;
    private final TableSectionRepository sectionRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<TableResponse> findAll(Long tenantId, Long branchId, Long sectionId) {
        return tableRepository.findByFilters(tenantId, branchId, sectionId).stream()
                .map(TableResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TableResponse findById(Long id, Long tenantId) {
        return TableResponse.from(loadTable(id, tenantId));
    }

    @Transactional
    public TableResponse create(TableRequest request, Long tenantId, Long userId) {
        Branch branch = loadBranch(request.getBranchId(), tenantId);

        RestaurantTable table = new RestaurantTable();
        table.setTenantId(tenantId);
        table.setBranch(branch);
        table.setSection(resolveSection(request.getSectionId(), tenantId, branch.getId()));
        applyEditableFields(table, request);
        table.setCreatedBy(userId);

        return TableResponse.from(tableRepository.save(table));
    }

    @Transactional
    public TableResponse update(Long id, TableRequest request, Long tenantId, Long userId) {
        RestaurantTable table = loadTable(id, tenantId);
        Branch branch = loadBranch(request.getBranchId(), tenantId);
        table.setBranch(branch);
        table.setSection(resolveSection(request.getSectionId(), tenantId, branch.getId()));
        applyEditableFields(table, request);
        table.setUpdatedBy(userId);

        return TableResponse.from(tableRepository.saveAndFlush(table));
    }

    @Transactional
    public TableResponse activate(Long id, Long tenantId, Long userId) {
        return setActive(id, tenantId, userId, true);
    }

    @Transactional
    public TableResponse deactivate(Long id, Long tenantId, Long userId) {
        return setActive(id, tenantId, userId, false);
    }

    @Transactional
    public TableResponse updateLayout(Long id, TableLayoutRequest request, Long tenantId, Long userId) {
        RestaurantTable table = loadTable(id, tenantId);
        table.setPosX(request.getPosX());
        table.setPosY(request.getPosY());
        table.setRotation(request.getRotation());
        table.setShape(request.getShape());
        table.setUpdatedBy(userId);

        return TableResponse.from(tableRepository.saveAndFlush(table));
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        RestaurantTable table = loadTable(id, tenantId);
        if (orderRepository.existsByTableId(id)) {
            throw new BusinessException(TableErrorCode.TABLE_HAS_ORDERS,
                    "Cannot delete a table while orders reference it",
                    ErrorParams.of("tableId", id, "tableName", table.getName()));
        }
        tableRepository.delete(table);
    }

    private TableResponse setActive(Long id, Long tenantId, Long userId, boolean active) {
        RestaurantTable table = loadTable(id, tenantId);
        table.setActive(active);
        table.setUpdatedBy(userId);
        return TableResponse.from(tableRepository.saveAndFlush(table));
    }

    private void applyEditableFields(RestaurantTable table, TableRequest request) {
        table.setName(request.getName());
        table.setCapacity(request.getCapacity());
        table.setActive(request.getActive() == null || request.getActive());
    }

    private RestaurantTable loadTable(Long id, Long tenantId) {
        return tableRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(TableErrorCode.RESOURCE_NOT_FOUND,
                        "Restaurant table not found: " + id,
                        ErrorParams.of("entityType", "RestaurantTable", "entityId", id)));
    }

    private Branch loadBranch(Long branchId, Long tenantId) {
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(TableErrorCode.RESOURCE_NOT_FOUND,
                        "Branch not found: " + branchId,
                        ErrorParams.of("entityType", "Branch", "entityId", branchId)));
    }

    private TableSection resolveSection(Long sectionId, Long tenantId, Long branchId) {
        if (sectionId == null) {
            return null;
        }
        TableSection section = sectionRepository.findByIdAndTenantId(sectionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(TableErrorCode.SECTION_NOT_FOUND,
                        "Table section not found: " + sectionId,
                        ErrorParams.of("entityType", "TableSection", "entityId", sectionId)));
        Long sectionBranchId = section.getBranch() == null ? null : section.getBranch().getId();
        if (!branchId.equals(sectionBranchId)) {
            throw new BusinessException(TableErrorCode.SECTION_BRANCH_MISMATCH,
                    "Table section belongs to a different branch",
                    ErrorParams.of("sectionId", sectionId, "sectionBranchId", sectionBranchId, "tableBranchId", branchId));
        }
        return section;
    }
}
