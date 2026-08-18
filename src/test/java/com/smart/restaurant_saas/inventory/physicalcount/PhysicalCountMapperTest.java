package com.smart.restaurant_saas.inventory.physicalcount;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.mapper.PhysicalCountMapper;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountSummaryResponse;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import org.junit.jupiter.api.Test;

class PhysicalCountMapperTest {

    private final PhysicalCountMapper mapper = new PhysicalCountMapper();

    @Test
    void mapsWarehouseArabicNameToDetailAndSummaryResponses() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(12L);
        warehouse.setName("Main Warehouse");
        warehouse.setNameAr("المستودع الرئيسي");
        PhysicalCount count = new PhysicalCount();
        count.setWarehouse(warehouse);

        PhysicalCountResponse detail = mapper.toResponse(count);
        PhysicalCountSummaryResponse summary = mapper.toSummary(count);

        assertThat(detail.getWarehouseNameAr()).isEqualTo("المستودع الرئيسي");
        assertThat(summary.getWarehouseNameAr()).isEqualTo("المستودع الرئيسي");
    }

    @Test
    void keepsWarehouseFieldsNullWhenWarehouseIsAbsent() {
        PhysicalCount count = new PhysicalCount();

        PhysicalCountResponse detail = mapper.toResponse(count);
        PhysicalCountSummaryResponse summary = mapper.toSummary(count);

        assertThat(detail.getWarehouseId()).isNull();
        assertThat(detail.getWarehouseName()).isNull();
        assertThat(detail.getWarehouseNameAr()).isNull();
        assertThat(summary.getWarehouseId()).isNull();
        assertThat(summary.getWarehouseName()).isNull();
        assertThat(summary.getWarehouseNameAr()).isNull();
    }
}
