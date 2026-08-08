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
import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.mapper.PurchaseReturnMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.purchase.InvoiceSequenceService;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoiceLine;
import com.smart.restaurant_saas.inventory.purchase.PurchaseReturn;
import com.smart.restaurant_saas.inventory.purchase.PurchaseReturnLine;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnUpdateLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.UncompleteRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.UnpostRequest;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseInvoiceRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseReturnRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
class PurchaseReturnServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long RETURN_ID = 20L;
    private static final Long ORIGINAL_LINE_ID = 31L;
    private static final Long BALANCE_ID = 44L;

    @Mock
    private PurchaseReturnRepository returnRepository;
    @Mock
    private PurchaseInvoiceRepository invoiceRepository;
    @Mock
    private StockBalanceRepository stockBalanceRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private InventoryLedgerService ledgerService;
    @Mock
    private StockBatchService stockBatchService;
    @Mock
    private StockBalanceService stockBalanceService;
    @Mock
    private InvoiceSequenceService invoiceSequenceService;
    @Mock
    private UomRepository uomRepository;

    private PurchaseReturnService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseReturnService(
            returnRepository,
            invoiceRepository,
            stockBalanceRepository,
            transactionRepository,
            ledgerService,
            stockBatchService,
            stockBalanceService,
            new UomConversionService(),
            invoiceSequenceService,
            uomRepository,
            new PurchaseReturnMapper(),
            TestZones.cairo()
        );
        lenient().when(returnRepository.save(any(PurchaseReturn.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void addLineWithOriginalUomKeepsCurrentLineTotalBehavior() {
        Fixture fixture = fixture(DocumentStatus.DRAFT, DocumentStatus.POSTED);
        fixture.ret().getLines().clear();
        PurchaseReturnLineRequest request = new PurchaseReturnLineRequest();
        request.setOriginalLineId(ORIGINAL_LINE_ID);
        request.setQuantity(new BigDecimal("2.000000"));
        request.setUomId(1L);

        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(uomRepository.findById(1L)).thenReturn(Optional.of(fixture.ret()
            .getOriginalInvoice().getLines().get(0).getUom()));
        when(returnRepository.findPostedReturnLinesByInvoiceId(TENANT_ID, 11L))
            .thenReturn(List.of());

        PurchaseReturnResponse response = service.addLine(RETURN_ID, request, TENANT_ID, USER_ID);

        assertThat(response.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getQuantity()).isEqualByComparingTo("2.000000");
            assertThat(line.getUomId()).isEqualTo(1L);
            assertThat(line.getUnitCost()).isEqualByComparingTo("5.000000");
            assertThat(line.getLineTotal()).isEqualByComparingTo("10.000000");
        });
    }

    @Test
    void addLineWithDifferentConvertibleUomComputesTotalFromConvertedQuantity() {
        Fixture fixture = tonneDraftFixture();
        Uom kg = fixture.ret().getOriginalInvoice().getLines().get(0).getMaterial().getStockUom();
        PurchaseReturnLineRequest request = new PurchaseReturnLineRequest();
        request.setOriginalLineId(ORIGINAL_LINE_ID);
        request.setQuantity(new BigDecimal("100.000000"));
        request.setUomId(kg.getId());

        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(uomRepository.findById(kg.getId())).thenReturn(Optional.of(kg));
        when(returnRepository.findPostedReturnLinesByInvoiceId(TENANT_ID, 11L))
            .thenReturn(List.of());

        PurchaseReturnResponse response = service.addLine(RETURN_ID, request, TENANT_ID, USER_ID);

        assertThat(response.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getQuantity()).isEqualByComparingTo("100.000000");
            assertThat(line.getUomId()).isEqualTo(kg.getId());
            assertThat(line.getUnitCost()).isEqualByComparingTo("24.000000");
            assertThat(line.getLineTotal()).isEqualByComparingTo("2400.000000");
        });
    }

    @Test
    void addLineRejectsOverReturnAfterConvertingSubmittedUom() {
        Fixture fixture = tonneDraftFixture();
        Uom kg = fixture.ret().getOriginalInvoice().getLines().get(0).getMaterial().getStockUom();
        PurchaseReturnLineRequest request = new PurchaseReturnLineRequest();
        request.setOriginalLineId(ORIGINAL_LINE_ID);
        request.setQuantity(new BigDecimal("1500.000000"));
        request.setUomId(kg.getId());

        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(uomRepository.findById(kg.getId())).thenReturn(Optional.of(kg));
        when(returnRepository.findPostedReturnLinesByInvoiceId(TENANT_ID, 11L))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.addLine(RETURN_ID, request, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.RETURN_QUANTITY_EXCEEDED);
                assertThat(ex.getParams()).containsEntry("returnable", new BigDecimal("1.000000"));
                assertThat(ex.getParams()).containsEntry("requested", new BigDecimal("1.500000"));
            });

        assertThat(fixture.ret().getLines()).isEmpty();
        verify(returnRepository, never()).save(any(PurchaseReturn.class));
    }

    @Test
    void updateLineWithUomChangeRecomputesTotalFromConvertedQuantity() {
        Fixture fixture = tonneDraftFixture();
        PurchaseInvoiceLine originalLine = fixture.ret().getOriginalInvoice().getLines().get(0);
        Uom tonne = originalLine.getUom();
        Uom kg = originalLine.getMaterial().getStockUom();
        PurchaseReturnLine line = returnLine(fixture.ret(), originalLine,
            new BigDecimal("0.100000"), tonne, new BigDecimal("2400.000000"));
        fixture.ret().getLines().add(line);
        PurchaseReturnUpdateLineRequest request = new PurchaseReturnUpdateLineRequest();
        request.setQuantity(new BigDecimal("200.000000"));
        request.setUomId(kg.getId());

        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(uomRepository.findById(kg.getId())).thenReturn(Optional.of(kg));
        when(returnRepository.findPostedReturnLinesByInvoiceId(TENANT_ID, 11L))
            .thenReturn(List.of());

        PurchaseReturnResponse response = service.updateLine(
            RETURN_ID, line.getId(), request, TENANT_ID, USER_ID);

        assertThat(response.getLines()).singleElement().satisfies(updated -> {
            assertThat(updated.getQuantity()).isEqualByComparingTo("200.000000");
            assertThat(updated.getUomId()).isEqualTo(kg.getId());
            assertThat(updated.getUnitCost()).isEqualByComparingTo("24.000000");
            assertThat(updated.getLineTotal()).isEqualByComparingTo("4800.000000");
        });
        assertThat(line.getUom()).isSameAs(kg);
        assertThat(line.getUnitCost()).isEqualByComparingTo("24.000000");
    }

    @Test
    void returnLineRequestsCarryUomButNeverUnitCost() {
        List<String> addFields = Arrays.stream(PurchaseReturnLineRequest.class.getDeclaredFields())
            .map(Field::getName)
            .toList();
        List<String> updateFields = Arrays.stream(PurchaseReturnUpdateLineRequest.class.getDeclaredFields())
            .map(Field::getName)
            .toList();

        assertThat(addFields).contains("uomId").doesNotContain("unitCost");
        assertThat(updateFields).contains("uomId").doesNotContain("unitCost");
    }

    @Test
    void unpostSucceedsWhenPostedAndBatchHasOngoingNormalConsumption() {
        Fixture fixture = fixture(DocumentStatus.POSTED, DocumentStatus.POSTED);
        fixture.batch().setRemainingQuantity(new BigDecimal("6.000000"));
        fixture.batch().setUpdatedAt(fixture.ret().getPostedAt().plusSeconds(1));
        InventoryTransaction tx = originalTransaction(701L);
        UnpostRequest request = new UnpostRequest();
        request.setReason("ENTRY_ERROR");

        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(stockBalanceRepository.findByWarehouseAndMaterials(TENANT_ID, 5L, List.of(101L)))
            .thenReturn(List.of(fixture.balance()));
        when(stockBatchService.requireSourceBatch(BALANCE_ID, ORIGINAL_LINE_ID))
            .thenReturn(fixture.batch());
        when(transactionRepository.findOriginalsByReference(TENANT_ID, "PURCHASE_RETURN", RETURN_ID))
            .thenReturn(List.of(tx));

        PurchaseReturnResponse response = service.unpost(RETURN_ID, request, TENANT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.COMPLETE);
        assertThat(response.getPostedToInventory()).isFalse();
        assertThat(response.getUnpostedAt()).isNotNull();
        assertThat(response.getUnpostedBy()).isEqualTo(USER_ID);
        assertThat(fixture.ret().getStatus()).isEqualTo(DocumentStatus.COMPLETE);

        verify(ledgerService).reverse(701L, "ENTRY_ERROR", "UNPOST-RETURN-20-701", USER_ID);
        verify(stockBatchService).restoreSourceBatch(
            BALANCE_ID, ORIGINAL_LINE_ID, new BigDecimal("2.000000"), USER_ID);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"DRAFT", "COMPLETE", "CANCELLED"})
    void unpostRejectsNonPostedStates(DocumentStatus status) {
        PurchaseReturn ret = fixture(status, DocumentStatus.POSTED).ret();
        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID)).thenReturn(Optional.of(ret));

        assertThatThrownBy(() -> service.unpost(RETURN_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseReturn");
                assertThat(ex.getParams()).containsEntry("currentStatus", status.name());
                assertThat(ex.getParams()).containsEntry("requiredStatus", "POSTED");
                assertThat(ex.getParams()).containsEntry("action", "unpost");
            });

        verifyNoInteractions(stockBalanceRepository, transactionRepository, ledgerService, stockBatchService);
    }

    @Test
    void unpostSucceedsWhenSameBatchHasLaterReturns() {
        Fixture fixture = fixture(DocumentStatus.POSTED, DocumentStatus.POSTED);
        fixture.batch().setRemainingQuantity(new BigDecimal("5.000000"));
        fixture.batch().setUpdatedAt(fixture.ret().getPostedAt().plusSeconds(1));
        InventoryTransaction tx = originalTransaction(701L);

        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(stockBalanceRepository.findByWarehouseAndMaterials(TENANT_ID, 5L, List.of(101L)))
            .thenReturn(List.of(fixture.balance()));
        when(stockBatchService.requireSourceBatch(BALANCE_ID, ORIGINAL_LINE_ID))
            .thenReturn(fixture.batch());
        when(transactionRepository.findOriginalsByReference(TENANT_ID, "PURCHASE_RETURN", RETURN_ID))
            .thenReturn(List.of(tx));

        PurchaseReturnResponse response = service.unpost(RETURN_ID, null, TENANT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.COMPLETE);
        verify(ledgerService).reverse(701L, null, "UNPOST-RETURN-20-701", USER_ID);
        verify(stockBatchService).restoreSourceBatch(
            BALANCE_ID, ORIGINAL_LINE_ID, new BigDecimal("2.000000"), USER_ID);
    }

    @Test
    void unpostRejectsWhenOriginalInvoiceIsNoLongerPosted() {
        Fixture fixture = fixture(DocumentStatus.POSTED, DocumentStatus.COMPLETE);
        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));

        assertThatThrownBy(() -> service.unpost(RETURN_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode())
                    .isEqualTo(InventoryErrorCode.UNPOST_BLOCKED_ORIGINAL_INVOICE_NOT_POSTED);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseReturn");
                assertThat(ex.getParams()).containsEntry("returnId", RETURN_ID);
                assertThat(ex.getParams()).containsEntry("originalInvoiceId", 11L);
                assertThat(ex.getParams()).containsEntry("originalInvoiceStatus", "COMPLETE");
            });

        verifyNoInteractions(stockBalanceRepository, stockBatchService);
        verifyNoInteractions(transactionRepository, ledgerService);
        verify(stockBatchService, never())
            .restoreSourceBatch(any(), any(), any(), any());
    }

    @Test
    void secondUnpostCallDoesNotCreateMoreReversals() {
        Fixture fixture = fixture(DocumentStatus.POSTED, DocumentStatus.POSTED);
        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(stockBalanceRepository.findByWarehouseAndMaterials(TENANT_ID, 5L, List.of(101L)))
            .thenReturn(List.of(fixture.balance()));
        when(stockBatchService.requireSourceBatch(BALANCE_ID, ORIGINAL_LINE_ID))
            .thenReturn(fixture.batch());
        when(transactionRepository.findOriginalsByReference(TENANT_ID, "PURCHASE_RETURN", RETURN_ID))
            .thenReturn(List.of(originalTransaction(701L)));

        service.unpost(RETURN_ID, null, TENANT_ID, USER_ID);

        assertThatThrownBy(() -> service.unpost(RETURN_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION));

        verify(ledgerService).reverse(701L, null, "UNPOST-RETURN-20-701", USER_ID);
    }

    @Test
    void deleteDraftWithoutLedgerHistoryDeletesReturn() {
        Fixture fixture = fixture(DocumentStatus.DRAFT, DocumentStatus.POSTED);
        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(transactionRepository.existsByReference(TENANT_ID, "PURCHASE_RETURN", RETURN_ID))
            .thenReturn(false);

        service.delete(RETURN_ID, TENANT_ID);

        verify(transactionRepository).existsByReference(TENANT_ID, "PURCHASE_RETURN", RETURN_ID);
        verify(returnRepository).delete(fixture.ret());
    }

    @Test
    void deleteDraftWithLedgerHistoryIsRejected() {
        Fixture fixture = fixture(DocumentStatus.DRAFT, DocumentStatus.POSTED);
        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(transactionRepository.existsByReference(TENANT_ID, "PURCHASE_RETURN", RETURN_ID))
            .thenReturn(true);

        assertThatThrownBy(() -> service.delete(RETURN_ID, TENANT_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.ALREADY_PROCESSED);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseReturn");
                assertThat(ex.getParams()).containsEntry("returnId", RETURN_ID);
                assertThat(ex.getParams()).containsEntry("referenceType", "PURCHASE_RETURN");
                assertThat(ex.getParams()).containsEntry("action", "delete");
            });

        verify(returnRepository, never()).delete(any(PurchaseReturn.class));
    }

    @Test
    void uncompleteSucceedsFromCompletePreservesReturnNumberAndEnablesLineEditing() {
        Fixture fixture = fixture(DocumentStatus.COMPLETE, DocumentStatus.POSTED);
        String returnNumber = fixture.ret().getReturnNumber();
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 2, 10, 15);
        fixture.ret().setCompletedAt(completedAt);
        fixture.ret().setCompletedBy(55L);
        UncompleteRequest request = new UncompleteRequest();
        request.setReason("NEEDS_EDIT");
        PurchaseReturnUpdateLineRequest lineRequest = new PurchaseReturnUpdateLineRequest();
        lineRequest.setQuantity(new BigDecimal("3.000000"));
        lineRequest.setUomId(1L);

        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(fixture.ret()));
        when(uomRepository.findById(1L)).thenReturn(Optional.of(fixture.ret().getLines().get(0).getUom()));
        when(returnRepository.findPostedReturnLinesByInvoiceId(TENANT_ID, 11L))
            .thenReturn(List.of());

        PurchaseReturnResponse response = service.uncomplete(RETURN_ID, request, TENANT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(response.getReturnNumber()).isEqualTo(returnNumber);
        assertThat(response.getUnCompletedAt()).isNotNull();
        assertThat(response.getUnCompletedBy()).isEqualTo(USER_ID);
        assertThat(fixture.ret().getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(fixture.ret().getReturnNumber()).isEqualTo(returnNumber);
        assertThat(fixture.ret().getCompletedAt()).isEqualTo(completedAt);
        assertThat(fixture.ret().getCompletedBy()).isEqualTo(55L);

        service.updateLine(RETURN_ID, 201L, lineRequest, TENANT_ID, USER_ID);

        assertThat(fixture.ret().getLines()).singleElement().satisfies(line -> {
            assertThat(line.getQuantity()).isEqualByComparingTo("3.000000");
            assertThat(line.getLineTotal()).isEqualByComparingTo("15.000000");
        });
        verifyNoInteractions(stockBalanceRepository, transactionRepository, ledgerService, stockBatchService);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"DRAFT", "POSTED", "CANCELLED"})
    void uncompleteRejectsNonCompleteStates(DocumentStatus status) {
        PurchaseReturn ret = fixture(status, DocumentStatus.POSTED).ret();
        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID)).thenReturn(Optional.of(ret));

        assertThatThrownBy(() -> service.uncomplete(RETURN_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseReturn");
                assertThat(ex.getParams()).containsEntry("currentStatus", status.name());
                assertThat(ex.getParams()).containsEntry("requiredStatus", "COMPLETE");
                assertThat(ex.getParams()).containsEntry("action", "uncomplete");
            });

        verify(returnRepository, never()).save(any(PurchaseReturn.class));
        verifyNoInteractions(stockBalanceRepository, transactionRepository, ledgerService, stockBatchService);
    }

    @Test
    void deleteWorksAfterUncompleteWhenReturnHasNoLedgerHistory() {
        PurchaseReturn ret = fixture(DocumentStatus.COMPLETE, DocumentStatus.POSTED).ret();
        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(ret));
        when(transactionRepository.existsByReference(TENANT_ID, "PURCHASE_RETURN", RETURN_ID))
            .thenReturn(false);

        service.uncomplete(RETURN_ID, null, TENANT_ID, USER_ID);
        service.delete(RETURN_ID, TENANT_ID);

        verify(returnRepository).delete(ret);
    }

    @Test
    void deleteStillRejectsAfterUncompleteWhenReturnHasLedgerHistory() {
        PurchaseReturn ret = fixture(DocumentStatus.COMPLETE, DocumentStatus.POSTED).ret();
        when(returnRepository.findByIdAndTenantId(RETURN_ID, TENANT_ID))
            .thenReturn(Optional.of(ret));
        when(transactionRepository.existsByReference(TENANT_ID, "PURCHASE_RETURN", RETURN_ID))
            .thenReturn(true);

        service.uncomplete(RETURN_ID, null, TENANT_ID, USER_ID);

        assertThatThrownBy(() -> service.delete(RETURN_ID, TENANT_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.ALREADY_PROCESSED);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseReturn");
                assertThat(ex.getParams()).containsEntry("action", "delete");
            });

        verify(returnRepository, never()).delete(any(PurchaseReturn.class));
    }

    private Fixture fixture(DocumentStatus returnStatus, DocumentStatus invoiceStatus) {
        Uom uom = new Uom();
        uom.setId(1L);
        uom.setCode("KG");
        uom.setSymbol("kg");
        uom.setFactorToBase(BigDecimal.ONE);

        Material material = new Material();
        material.setId(101L);
        material.setCode("FLOUR");
        material.setName("Flour");
        material.setStockUom(uom);
        material.setDisplayUom(uom);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(5L);
        warehouse.setName("Main Warehouse");

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setId(11L);
        invoice.setTenantId(TENANT_ID);
        invoice.setInvoiceNumber("PINV-11");
        invoice.setWarehouse(warehouse);
        invoice.setStatus(invoiceStatus);

        PurchaseInvoiceLine originalLine = new PurchaseInvoiceLine();
        originalLine.setId(ORIGINAL_LINE_ID);
        originalLine.setPurchaseInvoice(invoice);
        originalLine.setMaterial(material);
        originalLine.setQuantity(new BigDecimal("10.000000"));
        originalLine.setUom(uom);
        originalLine.setUnitCost(new BigDecimal("5.000000"));
        invoice.getLines().add(originalLine);

        PurchaseReturn ret = new PurchaseReturn();
        ret.setId(RETURN_ID);
        ret.setTenantId(TENANT_ID);
        ret.setOriginalInvoice(invoice);
        ret.setWarehouse(warehouse);
        ret.setReturnNumber("PRET-20");
        ret.setReturnDate(LocalDate.of(2026, 7, 1));
        ret.setStatus(returnStatus);
        ret.setPostedToInventory(returnStatus == DocumentStatus.POSTED);
        ret.setPostedAt(LocalDateTime.of(2026, 7, 1, 12, 0));

        PurchaseReturnLine line = new PurchaseReturnLine();
        line.setId(201L);
        line.setPurchaseReturn(ret);
        line.setOriginalLine(originalLine);
        line.setMaterial(material);
        line.setQuantity(new BigDecimal("2.000000"));
        line.setUom(uom);
        line.setUnitCost(new BigDecimal("5.000000"));
        line.setLineTotal(new BigDecimal("10.000000"));
        ret.getLines().add(line);

        StockBalance balance = new StockBalance();
        balance.setId(BALANCE_ID);
        balance.setTenantId(TENANT_ID);
        balance.setWarehouse(warehouse);
        balance.setMaterial(material);
        balance.setUom(uom);

        StockBatch batch = new StockBatch();
        batch.setId(88L);
        batch.setTenantId(TENANT_ID);
        batch.setStockBalance(balance);
        batch.setOriginalQuantity(new BigDecimal("10.000000"));
        batch.setRemainingQuantity(new BigDecimal("8.000000"));
        batch.setUpdatedAt(ret.getPostedAt().minusSeconds(1));

        return new Fixture(ret, balance, batch);
    }

    private Fixture tonneDraftFixture() {
        Uom kg = new Uom();
        kg.setId(1L);
        kg.setCode("KG");
        kg.setSymbol("kg");
        kg.setFactorToBase(BigDecimal.ONE);

        Uom tonne = new Uom();
        tonne.setId(2L);
        tonne.setCode("T");
        tonne.setSymbol("t");
        tonne.setBaseUom(kg);
        tonne.setFactorToBase(new BigDecimal("1000.000000"));

        Material material = new Material();
        material.setId(101L);
        material.setCode("FLOUR");
        material.setName("Flour");
        material.setStockUom(kg);
        material.setDisplayUom(kg);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(5L);
        warehouse.setName("Main Warehouse");

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setId(11L);
        invoice.setTenantId(TENANT_ID);
        invoice.setInvoiceNumber("PINV-11");
        invoice.setWarehouse(warehouse);
        invoice.setStatus(DocumentStatus.POSTED);

        PurchaseInvoiceLine originalLine = new PurchaseInvoiceLine();
        originalLine.setId(ORIGINAL_LINE_ID);
        originalLine.setPurchaseInvoice(invoice);
        originalLine.setMaterial(material);
        originalLine.setQuantity(new BigDecimal("1.000000"));
        originalLine.setUom(tonne);
        originalLine.setUnitCost(new BigDecimal("24000.000000"));
        invoice.getLines().add(originalLine);

        PurchaseReturn ret = new PurchaseReturn();
        ret.setId(RETURN_ID);
        ret.setTenantId(TENANT_ID);
        ret.setOriginalInvoice(invoice);
        ret.setWarehouse(warehouse);
        ret.setReturnNumber("PRET-20");
        ret.setReturnDate(LocalDate.of(2026, 7, 1));
        ret.setStatus(DocumentStatus.DRAFT);
        ret.setPostedToInventory(false);

        StockBalance balance = new StockBalance();
        balance.setId(BALANCE_ID);
        balance.setTenantId(TENANT_ID);
        balance.setWarehouse(warehouse);
        balance.setMaterial(material);
        balance.setUom(kg);

        StockBatch batch = new StockBatch();
        batch.setId(88L);
        batch.setTenantId(TENANT_ID);
        batch.setStockBalance(balance);
        batch.setOriginalQuantity(new BigDecimal("1000.000000"));
        batch.setRemainingQuantity(new BigDecimal("1000.000000"));

        return new Fixture(ret, balance, batch);
    }

    private PurchaseReturnLine returnLine(PurchaseReturn ret, PurchaseInvoiceLine originalLine,
                                          BigDecimal quantity, Uom uom, BigDecimal lineTotal) {
        PurchaseReturnLine line = new PurchaseReturnLine();
        line.setId(201L);
        line.setPurchaseReturn(ret);
        line.setOriginalLine(originalLine);
        line.setMaterial(originalLine.getMaterial());
        line.setQuantity(quantity);
        line.setUom(uom);
        line.setUnitCost(originalLine.getUnitCost());
        line.setLineTotal(lineTotal);
        return line;
    }

    private InventoryTransaction originalTransaction(Long id) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setId(id);
        tx.setTenantId(TENANT_ID);
        return tx;
    }

    private record Fixture(PurchaseReturn ret, StockBalance balance, StockBatch batch) {}
}
