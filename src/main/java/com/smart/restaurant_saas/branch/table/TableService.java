package com.smart.restaurant_saas.branch.table;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.branch.table.dto.CreateTableRequest;
import com.smart.restaurant_saas.branch.table.dto.TableResponse;
import com.smart.restaurant_saas.branch.table.dto.UpdateTableRequest;
import com.smart.restaurant_saas.branch.table.dto.UpdateTableStatusRequest;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TableService {

    private final CurrentTenantProvider currentTenantProvider;
    private final TableRepository tableRepository;
    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public List<TableResponse> listTables(Long branchId) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        List<RestaurantTable> tables = branchId != null
                ? tableRepository.findByTenantIdAndBranchIdOrderByTableNoAsc(tenantId, branchId)
                : tableRepository.findByTenantIdOrderByBranchIdAscTableNoAsc(tenantId);
        return tables.stream().map(TableResponse::from).toList();
    }

    @Transactional
    public TableResponse createTable(CreateTableRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Branch branch = loadBranch(request.branchId(), tenantId);

        if (tableRepository.existsByTenantIdAndBranchIdAndTableNo(tenantId, branch.getId(), request.tableNo())) {
            throw new BusinessException(TableErrorCode.DUPLICATE_TABLE_NO,
                    "Table number already exists in this branch: " + request.tableNo(),
                    ErrorParams.of("branchId", branch.getId(), "tableNo", request.tableNo()));
        }

        RestaurantTable table = new RestaurantTable();
        table.setTenantId(tenantId);
        table.setBranch(branch);
        table.setTableNo(request.tableNo());
        table.setCapacity(request.capacity());
        table.setActive(request.active() == null || request.active());

        return TableResponse.from(tableRepository.save(table));
    }

    @Transactional
    public TableResponse updateTable(Long tableId, UpdateTableRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        RestaurantTable table = loadTable(tableId, tenantId);

        if (!table.getTableNo().equals(request.tableNo())
                && tableRepository.existsByTenantIdAndBranchIdAndTableNoAndIdNot(
                        tenantId, table.getBranch().getId(), request.tableNo(), tableId)) {
            throw new BusinessException(TableErrorCode.DUPLICATE_TABLE_NO,
                    "Table number already exists in this branch: " + request.tableNo(),
                    ErrorParams.of("branchId", table.getBranch().getId(), "tableNo", request.tableNo()));
        }

        table.setTableNo(request.tableNo());
        table.setCapacity(request.capacity());

        return TableResponse.from(tableRepository.saveAndFlush(table));
    }

    @Transactional
    public TableResponse updateTableStatus(Long tableId, UpdateTableStatusRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        RestaurantTable table = loadTable(tableId, tenantId);
        table.setActive(request.active());
        return TableResponse.from(tableRepository.saveAndFlush(table));
    }

    private RestaurantTable loadTable(Long tableId, Long tenantId) {
        return tableRepository.findByIdAndTenantId(tableId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(TableErrorCode.TABLE_NOT_FOUND,
                        "Table not found: " + tableId,
                        ErrorParams.of("entityType", "RestaurantTable", "entityId", tableId)));
    }

    private Branch loadBranch(Long branchId, Long tenantId) {
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(TableErrorCode.BRANCH_NOT_FOUND,
                        "Branch not found: " + branchId,
                        ErrorParams.of("entityType", "Branch", "entityId", branchId)));
    }
}
