package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.TestZones;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.WarehouseType;
import com.smart.restaurant_saas.inventory.core.enums.WasteReasonCode;
import com.smart.restaurant_saas.inventory.mapper.WasteDocumentMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.purchase.InvoiceSequenceService;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.repository.WasteDocumentRepository;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.inventory.waste.MaterialShortfall;
import com.smart.restaurant_saas.inventory.waste.WasteDocument;
import com.smart.restaurant_saas.inventory.waste.WasteLine;
import com.smart.restaurant_saas.inventory.waste.dto.UncompleteWasteRequest;
import com.smart.restaurant_saas.inventory.waste.dto.WasteDocumentResponse;
import com.smart.restaurant_saas.inventory.waste.dto.WasteUpdateLineRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WasteServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long WASTE_ID = 40L;
    private static final Long LINE_ID = 401L;

    @Mock
    private WasteDocumentRepository wasteRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private UomRepository uomRepository;
    @Mock
    private StockBalanceRepository stockBalanceRepository;
    @Mock
    private InventoryLedgerService ledgerService;
    @Mock
    private InvoiceSequenceService invoiceSequenceService;

    private WasteService service;

    @BeforeEach
    void setUp() {
        service = new WasteService(
            wasteRepository,
            warehouseRepository,
            materialRepository,
            uomRepository,
            stockBalanceRepository,
            ledgerService,
            new UomConversionService(),
            invoiceSequenceService,
            new WasteDocumentMapper(),
            TestZones.cairo()
        );
        lenient().when(wasteRepository.save(any(WasteDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void uncompleteSucceedsFromCompletePreservesCodeAndCompletionAuditAndEnablesLineEditing() {
        Fixture fixture = fixture(DocumentStatus.COMPLETE);
        WasteDocument doc = fixture.doc();
        String code = doc.getCode();
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 3, 11, 0);
        doc.setCompletedAt(completedAt);
        doc.setCompletedBy(55L);
        doc.setStockWarnings(List.of(new MaterialShortfall(
            101L,
            "Cucumber",
            new BigDecimal("45.000000"),
            new BigDecimal("20.000000"),
            new BigDecimal("25.000000"),
            "kg",
            false)));
        UncompleteWasteRequest request = new UncompleteWasteRequest();
        request.setReason("NEEDS_QUANTITY_FIX");

        when(wasteRepository.findByIdAndTenantId(WASTE_ID, TENANT_ID))
            .thenReturn(Optional.of(doc));
        when(uomRepository.findById(1L)).thenReturn(Optional.of(fixture.uom()));

        WasteDocumentResponse response = service.uncomplete(WASTE_ID, request, TENANT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(response.getCode()).isEqualTo(code);
        assertThat(response.getUnCompletedAt()).isNotNull();
        assertThat(response.getUnCompletedBy()).isEqualTo(USER_ID);
        assertThat(response.getStockWarnings()).isEmpty();
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(doc.getCode()).isEqualTo(code);
        assertThat(doc.getCompletedAt()).isEqualTo(completedAt);
        assertThat(doc.getCompletedBy()).isEqualTo(55L);
        assertThat(doc.getStockWarnings()).isEmpty();

        WasteUpdateLineRequest update = new WasteUpdateLineRequest();
        update.setQuantity(new BigDecimal("12.000000"));
        update.setUomId(1L);
        service.updateLine(WASTE_ID, LINE_ID, update, TENANT_ID, USER_ID);

        assertThat(fixture.line().getQuantity()).isEqualByComparingTo("12.000000");
        verifyNoInteractions(stockBalanceRepository, ledgerService);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"DRAFT", "POSTED", "CANCELLED"})
    void uncompleteRejectsNonCompleteStates(DocumentStatus status) {
        WasteDocument doc = fixture(status).doc();
        when(wasteRepository.findByIdAndTenantId(WASTE_ID, TENANT_ID))
            .thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.uncomplete(WASTE_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION);
                assertThat(ex.getParams()).containsEntry("entityType", "WasteDocument");
                assertThat(ex.getParams()).containsEntry("currentStatus", status.name());
                assertThat(ex.getParams()).containsEntry("requiredStatus", "COMPLETE");
                assertThat(ex.getParams()).containsEntry("action", "uncomplete");
            });

        verify(wasteRepository, never()).save(any(WasteDocument.class));
        verifyNoInteractions(stockBalanceRepository, ledgerService);
    }

    private Fixture fixture(DocumentStatus status) {
        Uom uom = new Uom();
        uom.setId(1L);
        uom.setCode("KG");
        uom.setSymbol("kg");
        uom.setFactorToBase(BigDecimal.ONE);

        Material material = new Material();
        material.setId(101L);
        material.setTenantId(TENANT_ID);
        material.setCode("CUCUMBER");
        material.setName("Cucumber");
        material.setStockUom(uom);
        material.setDisplayUom(uom);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(5L);
        warehouse.setTenantId(TENANT_ID);
        warehouse.setCode("MAIN");
        warehouse.setName("Main Warehouse");
        warehouse.setType(WarehouseType.CENTRAL);

        WasteDocument doc = new WasteDocument();
        doc.setId(WASTE_ID);
        doc.setTenantId(TENANT_ID);
        doc.setWarehouse(warehouse);
        doc.setCode("F7AM-WST-2026-00001");
        doc.setWasteDate(LocalDate.of(2026, 7, 3));
        doc.setReasonCode(WasteReasonCode.SPOILED);
        doc.setStatus(status);
        doc.setPostedToInventory(status == DocumentStatus.POSTED);

        WasteLine line = new WasteLine();
        line.setId(LINE_ID);
        line.setWasteDocument(doc);
        line.setMaterial(material);
        line.setQuantity(new BigDecimal("45.000000"));
        line.setUom(uom);
        doc.getLines().add(line);

        return new Fixture(doc, line, uom);
    }

    private record Fixture(WasteDocument doc, WasteLine line, Uom uom) {}
}
