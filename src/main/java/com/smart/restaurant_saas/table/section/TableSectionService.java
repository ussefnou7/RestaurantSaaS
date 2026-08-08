package com.smart.restaurant_saas.table.section;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.table.TableErrorCode;
import com.smart.restaurant_saas.table.TableRepository;
import com.smart.restaurant_saas.table.section.dto.TableSectionRequest;
import com.smart.restaurant_saas.table.section.dto.TableSectionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TableSectionService {

    private final TableSectionRepository sectionRepository;
    private final TableRepository tableRepository;
    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<TableSectionResponse> findAll(Long tenantId, Long branchId, boolean includeInactive) {
        var sections = includeInactive
                ? sectionRepository.findByTenantIdAndBranchId(tenantId, branchId)
                : sectionRepository.findActiveByTenantIdAndBranchId(tenantId, branchId);
        return sections.stream().map(TableSectionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TableSectionResponse findById(Long id, Long tenantId) {
        return TableSectionResponse.from(loadSection(id, tenantId));
    }

    @Transactional
    public TableSectionResponse create(TableSectionRequest request, Long tenantId, Long userId) {
        Branch branch = loadBranch(request.getBranchId(), tenantId);

        TableSection section = new TableSection();
        section.setTenantId(tenantId);
        section.setBranch(branch);
        applyFields(section, request);
        section.setCreatedBy(userId);

        return TableSectionResponse.from(sectionRepository.save(section));
    }

    @Transactional
    public TableSectionResponse update(Long id, TableSectionRequest request, Long tenantId, Long userId) {
        TableSection section = loadSection(id, tenantId);
        section.setBranch(loadBranch(request.getBranchId(), tenantId));
        applyFields(section, request);
        section.setUpdatedBy(userId);

        return TableSectionResponse.from(sectionRepository.saveAndFlush(section));
    }

    @Transactional
    public TableSectionResponse activate(Long id, Long tenantId, Long userId) {
        return setActive(id, tenantId, userId, true);
    }

    @Transactional
    public TableSectionResponse deactivate(Long id, Long tenantId, Long userId) {
        return setActive(id, tenantId, userId, false);
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        TableSection section = loadSection(id, tenantId);
        // D78 (updated): deleting a section cascades to its tables, but is blocked when
        // any of those tables is referenced by an order — orders are permanent records.
        if (orderRepository.existsByTableSectionId(id)) {
            throw new BusinessException(TableErrorCode.SECTION_HAS_ORDERS,
                    "Cannot delete table section while its tables are referenced by orders",
                    ErrorParams.of("sectionId", id, "sectionName", section.getName()));
        }
        tableRepository.deleteAll(tableRepository.findAllBySectionId(id));
        sectionRepository.delete(section);
    }

    private TableSectionResponse setActive(Long id, Long tenantId, Long userId, boolean active) {
        TableSection section = loadSection(id, tenantId);
        section.setActive(active);
        section.setUpdatedBy(userId);
        return TableSectionResponse.from(sectionRepository.saveAndFlush(section));
    }

    private void applyFields(TableSection section, TableSectionRequest request) {
        section.setName(request.getName().trim());
        section.setNameAr(blankToNull(request.getNameAr()));
    }

    private TableSection loadSection(Long id, Long tenantId) {
        return sectionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(TableErrorCode.SECTION_NOT_FOUND,
                        "Table section not found: " + id,
                        ErrorParams.of("entityType", "TableSection", "entityId", id)));
    }

    private Branch loadBranch(Long branchId, Long tenantId) {
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(TableErrorCode.RESOURCE_NOT_FOUND,
                        "Branch not found: " + branchId,
                        ErrorParams.of("entityType", "Branch", "entityId", branchId)));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
