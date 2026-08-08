package com.smart.restaurant_saas.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;
import com.smart.restaurant_saas.inventory.core.enums.UomType;
import com.smart.restaurant_saas.inventory.core.enums.WarehouseType;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.physicalcount.MaterialConflictProjection;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCount;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLine;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins the status semantics of {@link PhysicalCountRepository#findFreezeConflicts} against a real
 * database: only an IN_PROGRESS count holds its materials. The unit tests stub this query, so the
 * JPQL status filter — the reason a RECONCILED or CANCELLED count never blocks a recount — is only
 * pinned here. Rolled back via {@code @Transactional}.
 */
@SpringBootTest
@Transactional
class PhysicalCountRepositoryIntegrationTest {

    private static final Long TENANT_ID = 0L; // seeded "System" tenant (V4).

    @Autowired
    private PhysicalCountRepository countRepository;
    @Autowired
    private UomRepository uomRepository;
    @Autowired
    private MaterialCategoryRepository categoryRepository;
    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;

    private Warehouse warehouse;
    private Material flour;
    private Material sugar;

    @BeforeEach
    void seed() {
        Uom kg = new Uom();
        kg.setTenantId(TENANT_ID);
        kg.setCode("KG-" + suffix());
        kg.setName("Kilogram");
        kg.setSymbol("kg");
        kg.setType(UomType.WEIGHT);
        // A calibration root: no parent, so both factors are 1.
        kg.setFactorToBase(BigDecimal.ONE);
        kg.setEnteredFactor(BigDecimal.ONE);
        kg = uomRepository.save(kg);

        MaterialCategory category = new MaterialCategory();
        category.setTenantId(TENANT_ID);
        category.setCode("CAT-" + suffix());
        category.setName("Dry goods");
        category = categoryRepository.save(category);

        warehouse = warehouse("WH-A-");
        flour = material("FLOUR-", category, kg);
        sugar = material("SUGAR-", category, kg);
    }

    @Test
    void inProgressCountBlocksOverlappingMaterialsAndCarriesItsId() {
        PhysicalCount holder = countWith(PhysicalCountStatus.IN_PROGRESS, warehouse, flour);

        List<MaterialConflictProjection> conflicts = countRepository.findFreezeConflicts(
            TENANT_ID, warehouse.getId(), -1L, List.of(flour.getId(), sugar.getId()));

        assertThat(conflicts).singleElement().satisfies(conflict -> {
            assertThat(conflict.getMaterialId()).isEqualTo(flour.getId());
            assertThat(conflict.getMaterialName()).isEqualTo(flour.getName());
            assertThat(conflict.getCountId()).isEqualTo(holder.getId());
            assertThat(conflict.getCountCode()).isEqualTo(holder.getCode());
        });
    }

    @Test
    void reconciledCancelledAndDraftCountsNeverBlock() {
        PhysicalCount holder = countWith(PhysicalCountStatus.IN_PROGRESS, warehouse, flour);

        for (PhysicalCountStatus status : List.of(PhysicalCountStatus.RECONCILED,
                PhysicalCountStatus.CANCELLED, PhysicalCountStatus.DRAFT)) {
            holder.setStatus(status);
            countRepository.saveAndFlush(holder);

            assertThat(countRepository.findFreezeConflicts(
                    TENANT_ID, warehouse.getId(), -1L, List.of(flour.getId())))
                .as("a %s count must not hold its materials", status)
                .isEmpty();
        }
    }

    @Test
    void freezeConflictsExcludeTheCountBeingFrozenAndOtherWarehouses() {
        PhysicalCount holder = countWith(PhysicalCountStatus.IN_PROGRESS, warehouse, flour);

        // The count being frozen never conflicts with itself.
        assertThat(countRepository.findFreezeConflicts(
                TENANT_ID, warehouse.getId(), holder.getId(), List.of(flour.getId())))
            .isEmpty();

        // A hold in one warehouse does not reach into another.
        Warehouse other = warehouse("WH-B-");
        assertThat(countRepository.findFreezeConflicts(
                TENANT_ID, other.getId(), -1L, List.of(flour.getId())))
            .isEmpty();
    }

    private PhysicalCount countWith(PhysicalCountStatus status, Warehouse warehouse,
                                    Material material) {
        PhysicalCount count = new PhysicalCount();
        count.setTenantId(TENANT_ID);
        count.setWarehouse(warehouse);
        count.setStatus(status);
        count.setScheduledDate(LocalDate.of(2026, 7, 4));
        count.setCode("PC-IT-" + suffix());
        count.setHasLargeVariance(false);

        PhysicalCountLine line = new PhysicalCountLine();
        line.setTenantId(TENANT_ID);
        line.setPhysicalCount(count);
        line.setMaterial(material);
        line.setUom(material.getDisplayUom());
        line.setExpectedQuantity(BigDecimal.ZERO);
        line.setUnitCostAtFreeze(BigDecimal.ZERO);
        count.getLines().add(line);

        return countRepository.saveAndFlush(count);
    }

    private Warehouse warehouse(String codePrefix) {
        Warehouse w = new Warehouse();
        w.setTenantId(TENANT_ID);
        w.setCode(codePrefix + suffix());
        w.setName("Integration warehouse " + codePrefix);
        w.setType(WarehouseType.CENTRAL);
        return warehouseRepository.save(w);
    }

    private Material material(String codePrefix, MaterialCategory category, Uom uom) {
        Material m = new Material();
        m.setTenantId(TENANT_ID);
        m.setCode(codePrefix + suffix());
        m.setName(codePrefix + "material");
        m.setCategory(category);
        m.setStockUom(uom);
        m.setDisplayUom(uom);
        return materialRepository.save(m);
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
