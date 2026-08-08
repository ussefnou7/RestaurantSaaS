package com.smart.restaurant_saas.inventory.service.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.common.sequence.TenantSequenceService;
import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.category.dto.MaterialCategoryRequest;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.enums.WarehouseType;
import com.smart.restaurant_saas.inventory.mapper.MaterialCategoryMapper;
import com.smart.restaurant_saas.inventory.mapper.MaterialMapper;
import com.smart.restaurant_saas.inventory.mapper.SupplierMapper;
import com.smart.restaurant_saas.inventory.mapper.WarehouseMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.material.dto.MaterialRequest;
import com.smart.restaurant_saas.inventory.purchase.Supplier;
import com.smart.restaurant_saas.inventory.purchase.dto.SupplierRequest;
import com.smart.restaurant_saas.inventory.repository.GlobalMaterialCategoryRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialCategoryRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.SupplierRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.inventory.warehouse.dto.WarehouseRequest;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventorySetupCodeGenerationTest {

    private static final Long TENANT_ID = 5L;

    @Mock
    private TenantSequenceService tenantSequenceService;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private MaterialCategoryRepository categoryRepository;
    @Mock
    private GlobalMaterialCategoryRepository globalCategoryRepository;
    @Mock
    private UomRepository uomRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private BranchRepository branchRepository;

    @Test
    void materialCreateUsesGeneratedCodeAndCreateRequestHasNoCodeField() {
        when(tenantSequenceService.generateEntityCode(TENANT_ID, TenantEntityPrefix.MAT))
                .thenReturn("KFC-MAT-0001");
        when(materialRepository.existsByTenantIdAndCode(TENANT_ID, "KFC-MAT-0001")).thenReturn(false);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category(10L)));
        when(uomRepository.findById(20L)).thenReturn(Optional.of(uom(20L, "KG")));
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialService service = new MaterialService(
                materialRepository,
                categoryRepository,
                uomRepository,
                new MaterialMapper(),
                tenantSequenceService
        );

        var response = service.create(materialRequest(), TENANT_ID);

        assertThat(response.getCode()).isEqualTo("KFC-MAT-0001");
        assertThat(hasDeclaredField(MaterialRequest.class, "code")).isFalse();
    }

    @Test
    void materialRequestHasNoMinimumStockLevelField() {
        assertThat(hasDeclaredField(MaterialRequest.class, "minimumStockLevel")).isFalse();
        assertThat(hasDeclaredField(Material.class, "minimumStockLevel")).isFalse();
    }

    @Test
    void materialCategoryCreateUsesGeneratedCodeAndCreateRequestHasNoCodeField() {
        when(tenantSequenceService.generateEntityCode(TENANT_ID, TenantEntityPrefix.CAT))
                .thenReturn("KFC-CAT-0001");
        when(categoryRepository.existsByTenantIdAndCode(TENANT_ID, "KFC-CAT-0001")).thenReturn(false);
        when(categoryRepository.save(any(MaterialCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialCategoryService service = new MaterialCategoryService(
                categoryRepository,
                globalCategoryRepository,
                new MaterialCategoryMapper(),
                tenantSequenceService
        );

        var response = service.create(categoryRequest(), TENANT_ID);

        assertThat(response.getCode()).isEqualTo("KFC-CAT-0001");
        assertThat(hasDeclaredField(MaterialCategoryRequest.class, "code")).isFalse();
    }

    @Test
    void supplierCreateUsesGeneratedCodeAndCreateRequestHasNoCodeField() {
        when(tenantSequenceService.generateEntityCode(TENANT_ID, TenantEntityPrefix.SUP))
                .thenReturn("KFC-SUP-0001");
        when(supplierRepository.existsByTenantIdAndCode(TENANT_ID, "KFC-SUP-0001")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierService service = new SupplierService(
                supplierRepository,
                new SupplierMapper(),
                tenantSequenceService
        );

        var response = service.create(supplierRequest(), TENANT_ID);

        assertThat(response.getCode()).isEqualTo("KFC-SUP-0001");
        assertThat(hasDeclaredField(SupplierRequest.class, "code")).isFalse();
    }

    @Test
    void warehouseCreateUsesGeneratedCodeAndCreateRequestHasNoCodeField() {
        when(tenantSequenceService.generateEntityCode(TENANT_ID, TenantEntityPrefix.WH))
                .thenReturn("KFC-WH-0001");
        when(warehouseRepository.existsByTenantIdAndCode(TENANT_ID, "KFC-WH-0001")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseService service = new WarehouseService(
                warehouseRepository,
                branchRepository,
                new WarehouseMapper(),
                tenantSequenceService
        );

        var response = service.create(warehouseRequest(), TENANT_ID);

        assertThat(response.getCode()).isEqualTo("KFC-WH-0001");
        assertThat(hasDeclaredField(WarehouseRequest.class, "code")).isFalse();
    }

    @Test
    void branchWarehouseCreateRequiresBranchId() {
        WarehouseService service = new WarehouseService(
                warehouseRepository,
                branchRepository,
                new WarehouseMapper(),
                tenantSequenceService
        );

        WarehouseRequest request = warehouseRequest();
        request.setType(WarehouseType.BRANCH);
        request.setBranchId(null);

        assertThatThrownBy(() -> service.create(request, TENANT_ID))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(InventoryErrorCode.BRANCH_WAREHOUSE_REQUIRES_BRANCH);

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void branchWarehouseCreateRejectsZeroBranchId() {
        WarehouseService service = new WarehouseService(
                warehouseRepository,
                branchRepository,
                new WarehouseMapper(),
                tenantSequenceService
        );

        WarehouseRequest request = warehouseRequest();
        request.setType(WarehouseType.BRANCH);
        request.setBranchId(0L);

        assertThatThrownBy(() -> service.create(request, TENANT_ID))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(InventoryErrorCode.BRANCH_WAREHOUSE_REQUIRES_BRANCH);

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void branchWarehouseUpdateRequiresBranchId() {
        WarehouseService service = new WarehouseService(
                warehouseRepository,
                branchRepository,
                new WarehouseMapper(),
                tenantSequenceService
        );
        when(warehouseRepository.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.of(warehouse(99L)));

        WarehouseRequest request = warehouseRequest();
        request.setType(WarehouseType.BRANCH);
        request.setBranchId(null);

        assertThatThrownBy(() -> service.update(99L, request, TENANT_ID))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(InventoryErrorCode.BRANCH_WAREHOUSE_REQUIRES_BRANCH);

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void branchWarehouseUpdateRejectsZeroBranchId() {
        WarehouseService service = new WarehouseService(
                warehouseRepository,
                branchRepository,
                new WarehouseMapper(),
                tenantSequenceService
        );
        when(warehouseRepository.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.of(warehouse(99L)));

        WarehouseRequest request = warehouseRequest();
        request.setType(WarehouseType.BRANCH);
        request.setBranchId(0L);

        assertThatThrownBy(() -> service.update(99L, request, TENANT_ID))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(InventoryErrorCode.BRANCH_WAREHOUSE_REQUIRES_BRANCH);

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    private MaterialRequest materialRequest() {
        MaterialRequest request = new MaterialRequest();
        request.setName("Flour");
        request.setCategoryId(10L);
        request.setStockUomId(20L);
        request.setDisplayUomId(20L);
        request.setDefaultUomId(20L);
        request.setActive(true);
        return request;
    }

    private MaterialCategoryRequest categoryRequest() {
        MaterialCategoryRequest request = new MaterialCategoryRequest();
        request.setName("Dry Goods");
        request.setActive(true);
        return request;
    }

    private SupplierRequest supplierRequest() {
        SupplierRequest request = new SupplierRequest();
        request.setName("Main Supplier");
        request.setActive(true);
        return request;
    }

    private WarehouseRequest warehouseRequest() {
        WarehouseRequest request = new WarehouseRequest();
        request.setName("Main Warehouse");
        request.setType(WarehouseType.CENTRAL);
        request.setActive(true);
        return request;
    }

    private Warehouse warehouse(Long id) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setTenantId(TENANT_ID);
        warehouse.setCode("KFC-WH-0001");
        warehouse.setName("Main Warehouse");
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setActive(true);
        return warehouse;
    }

    private MaterialCategory category(Long id) {
        MaterialCategory category = new MaterialCategory();
        category.setId(id);
        category.setTenantId(TENANT_ID);
        category.setCode("KFC-CAT-0001");
        category.setName("Dry Goods");
        category.setActive(true);
        return category;
    }

    private Uom uom(Long id, String code) {
        Uom uom = new Uom();
        uom.setId(id);
        uom.setCode(code);
        uom.setName(code);
        uom.setSymbol(code);
        uom.setActive(true);
        return uom;
    }

    private boolean hasDeclaredField(Class<?> type, String fieldName) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
}
