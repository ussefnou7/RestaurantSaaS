package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.TestZones;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.DocumentType;
import com.smart.restaurant_saas.inventory.mapper.PurchaseInvoiceMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoiceLine;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceHeaderRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.UncompleteRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.UnpostRequest;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseInvoiceRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseReturnRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.repository.SupplierRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseInvoiceServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 99L;
    private static final Long INVOICE_ID = 10L;

    @Mock
    private PurchaseInvoiceRepository invoiceRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private UomRepository uomRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private StockBalanceRepository stockBalanceRepository;
    @Mock
    private StockBatchRepository stockBatchRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private PurchaseReturnRepository returnRepository;
    @Mock
    private InventoryLedgerService ledgerService;
    @Mock
    private DocumentSequenceService documentSequenceService;

    private PurchaseInvoiceService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseInvoiceService(
            invoiceRepository,
            warehouseRepository,
            materialRepository,
            uomRepository,
            supplierRepository,
            stockBalanceRepository,
            stockBatchRepository,
            transactionRepository,
            returnRepository,
            ledgerService,
            documentSequenceService,
            new PurchaseInvoiceMapper(),
            TestZones.cairo()
        );
        lenient().when(invoiceRepository.save(any(PurchaseInvoice.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * D112: the allocator is shared, so the only thing keeping this service's numbers in its own
     * counter is the {@link DocumentType} it passes. The stub deliberately matches any type, so a
     * wrong one fails on the captured argument rather than by returning a null number.
     */
    @Test
    void createAllocatesItsNumberUnderThePurchaseInvoiceDocumentType() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(40L);
        when(warehouseRepository.findByIdAndTenantId(40L, TENANT_ID))
            .thenReturn(Optional.of(warehouse));
        when(documentSequenceService.next(anyLong(), any(DocumentType.class)))
            .thenReturn("PI/26/000001");

        PurchaseInvoiceHeaderRequest request = new PurchaseInvoiceHeaderRequest();
        request.setWarehouseId(40L);
        request.setInvoiceDate(LocalDate.of(2026, 7, 1));
        request.setReceiptDate(LocalDate.of(2026, 7, 1));

        PurchaseInvoiceResponse response = service.create(request, TENANT_ID, USER_ID);

        ArgumentCaptor<DocumentType> allocatedType = ArgumentCaptor.forClass(DocumentType.class);
        verify(documentSequenceService).next(eq(TENANT_ID), allocatedType.capture());
        assertThat(allocatedType.getValue()).isEqualTo(DocumentType.PURCHASE_INVOICE);
        assertThat(response.getInvoiceNumber()).isEqualTo("PI/26/000001");
    }

    @Test
    void trackedMaterialMayBeSavedOnDraftLineWithoutExpiryDate() {
        PurchaseInvoice invoice = invoice(DocumentStatus.DRAFT);
        Material material = material(101L, "Yogurt");
        material.setExpiryTracked(true);
        Uom uom = uom(1L);
        PurchaseInvoiceLineRequest request = new PurchaseInvoiceLineRequest();
        request.setMaterialId(material.getId());
        request.setQuantity(new BigDecimal("2.000000"));
        request.setUomId(uom.getId());
        request.setUnitCost(new BigDecimal("5.000000"));

        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));
        when(materialRepository.findByIdAndTenantId(material.getId(), TENANT_ID))
            .thenReturn(Optional.of(material));
        when(uomRepository.findResolvableByIdForTenant(uom.getId(), TENANT_ID))
            .thenReturn(Optional.of(uom));

        PurchaseInvoiceResponse response = service.addLine(INVOICE_ID, request, TENANT_ID);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(response.getLines()).singleElement()
            .extracting(line -> line.getExpiryDate())
            .isNull();
        verify(invoiceRepository).save(invoice);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void postRejectsTrackedLineWithoutExpiryDateUsingStructuredLineParams() {
        PurchaseInvoice invoice = invoice(DocumentStatus.COMPLETE);
        Material material = material(101L, "Yogurt");
        material.setExpiryTracked(true);
        PurchaseInvoiceLine line = invoiceLine(42L, invoice, material, uom(1L), null);
        invoice.getLines().add(line);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.post(INVOICE_ID, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode())
                    .isEqualTo(InventoryErrorCode.PURCHASE_INVOICE_EXPIRY_DATE_REQUIRED);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseInvoiceLine");
                assertThat(ex.getParams()).containsEntry("invoiceId", INVOICE_ID);
                assertThat(ex.getParams()).containsEntry("lineId", 42L);
                assertThat(ex.getParams()).containsEntry("materialId", 101L);
                assertThat(ex.getParams()).containsEntry("materialName", "Yogurt");
                assertThat(ex.getParams()).containsEntry("field", "expiryDate");
            });

        verifyNoInteractions(ledgerService);
        verify(stockBalanceRepository, never()).saveAll(any());
        verify(invoiceRepository, never()).save(any(PurchaseInvoice.class));
    }

    @Test
    void postCopiesLineExpiryDateIntoLedgerCommand() {
        PurchaseInvoice invoice = invoice(DocumentStatus.COMPLETE);
        Material material = material(101L, "Yogurt");
        material.setExpiryTracked(true);
        LocalDate expiryDate = LocalDate.of(2027, 3, 31);
        invoice.getLines().add(invoiceLine(42L, invoice, material, uom(1L), expiryDate));
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));
        when(stockBalanceRepository.findByWarehouseAndMaterials(
            TENANT_ID, 40L, List.of(material.getId())))
            .thenReturn(List.of());

        PurchaseInvoiceResponse response = service.post(INVOICE_ID, TENANT_ID, USER_ID);

        ArgumentCaptor<LedgerCommand> command = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(ledgerService).record(command.capture());
        assertThat(command.getValue().getExpiryDate()).isEqualTo(expiryDate);
        assertThat(command.getValue().getSourceInvoiceLineId()).isEqualTo(42L);
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.POSTED);
        assertThat(response.getLines()).singleElement()
            .extracting(line -> line.getExpiryDate())
            .isEqualTo(expiryDate);
    }

    @Test
    void unpostSucceedsWhenPostedAndNoBatchConsumed() {
        PurchaseInvoice invoice = invoice(DocumentStatus.POSTED);
        InventoryTransaction tx1 = originalTransaction(501L);
        InventoryTransaction tx2 = originalTransaction(502L);
        UnpostRequest request = new UnpostRequest();
        request.setReason("ENTRY_ERROR");

        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(returnRepository.findReturnSummariesByOriginalInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(List.of());
        when(stockBatchRepository.findOpenedByPurchaseInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(List.of(batch(20L, "Tomato", "10.000000", "10.000000")));
        when(transactionRepository.findOriginalsByReference(TENANT_ID, "PURCHASE_INVOICE", INVOICE_ID))
            .thenReturn(List.of(tx1, tx2));

        PurchaseInvoiceResponse response = service.unpost(INVOICE_ID, request, TENANT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.COMPLETE);
        assertThat(response.getPostedToInventory()).isFalse();
        assertThat(response.getUnpostedAt()).isNotNull();
        assertThat(response.getUnpostedBy()).isEqualTo(USER_ID);
        assertThat(invoice.getStatus()).isEqualTo(DocumentStatus.COMPLETE);

        verify(ledgerService).reverse(501L, "ENTRY_ERROR", "UNPOST-10-501", USER_ID);
        verify(ledgerService).reverse(502L, "ENTRY_ERROR", "UNPOST-10-502", USER_ID);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"DRAFT", "POSTED", "CANCELLED"})
    void backdatedConsumptionCheckReturnsEmptyWithoutLedgerQueryForNonCompleteStatus(
            DocumentStatus status) {
        PurchaseInvoice invoice = invoice(status);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));

        assertThat(service.findBackdatedConsumptionConflicts(INVOICE_ID, TENANT_ID)).isEmpty();

        verify(transactionRepository, never())
            .findBackdatedConsumptionConflicts(any(), any(), any(), any());
    }

    @Test
    void backdatedConsumptionCheckReturnsEmptyWithoutLedgerQueryForLineLessCompleteInvoice() {
        PurchaseInvoice invoice = invoice(DocumentStatus.COMPLETE);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));

        assertThat(service.findBackdatedConsumptionConflicts(INVOICE_ID, TENANT_ID)).isEmpty();

        verify(transactionRepository, never())
            .findBackdatedConsumptionConflicts(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"DRAFT", "COMPLETE", "CANCELLED"})
    void unpostRejectsNonPostedStates(DocumentStatus status) {
        PurchaseInvoice invoice = invoice(status);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.unpost(INVOICE_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseInvoice");
                assertThat(ex.getParams()).containsEntry("currentStatus", status.name());
                assertThat(ex.getParams()).containsEntry("requiredStatus", "POSTED");
                assertThat(ex.getParams()).containsEntry("action", "unpost");
            });

        verifyNoInteractions(returnRepository, stockBatchRepository, transactionRepository, ledgerService);
    }

    @Test
    void unpostRejectsWhenPurchaseReturnReferencesInvoiceBeforeBatchCheck() {
        PurchaseInvoice invoice = invoice(DocumentStatus.POSTED);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(returnRepository.findReturnSummariesByOriginalInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(List.of(returnSummary(77L, "PRET-77")));

        assertThatThrownBy(() -> service.unpost(INVOICE_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.UNPOST_BLOCKED_HAS_RETURN);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseInvoice");
                assertThat(ex.getParams()).containsEntry("invoiceId", INVOICE_ID);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> returns =
                    (List<Map<String, Object>>) ex.getParams().get("returns");
                assertThat(returns).singleElement().satisfies(ret -> {
                    assertThat(ret).containsEntry("returnCode", "PRET-77");
                    assertThat(ret).containsEntry("returnId", 77L);
                });
            });

        verify(stockBatchRepository, never()).findOpenedByPurchaseInvoice(any(), any());
        verifyNoInteractions(transactionRepository, ledgerService);
    }

    @Test
    void unpostRejectsWhenAnyBatchWasConsumedBeforeReversing() {
        PurchaseInvoice invoice = invoice(DocumentStatus.POSTED);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(returnRepository.findReturnSummariesByOriginalInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(List.of());
        when(stockBatchRepository.findOpenedByPurchaseInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(List.of(batch(33L, "Flour", "10.000000", "9.500000")));

        assertThatThrownBy(() -> service.unpost(INVOICE_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.UNPOST_BLOCKED_BATCH_CONSUMED);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseInvoice");
                assertThat(ex.getParams()).containsEntry("invoiceId", INVOICE_ID);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> consumed =
                    (List<Map<String, Object>>) ex.getParams().get("consumedBatches");
                assertThat(consumed).singleElement().satisfies(batch -> {
                    assertThat(batch).containsEntry("materialName", "Flour");
                    assertThat(batch).containsEntry("batchId", 33L);
                    assertThat(batch).containsEntry("consumedQuantity", new BigDecimal("0.500000"));
                });
            });

        InOrder guardOrder = inOrder(returnRepository, stockBatchRepository);
        guardOrder.verify(returnRepository).findReturnSummariesByOriginalInvoice(TENANT_ID, INVOICE_ID);
        guardOrder.verify(stockBatchRepository).findOpenedByPurchaseInvoice(TENANT_ID, INVOICE_ID);
        verify(transactionRepository, never())
            .findOriginalsByReference(TENANT_ID, "PURCHASE_INVOICE", INVOICE_ID);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void secondUnpostCallDoesNotCreateMoreReversals() {
        PurchaseInvoice invoice = invoice(DocumentStatus.POSTED);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(returnRepository.findReturnSummariesByOriginalInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(List.of());
        when(stockBatchRepository.findOpenedByPurchaseInvoice(TENANT_ID, INVOICE_ID)).thenReturn(List.of());
        when(transactionRepository.findOriginalsByReference(TENANT_ID, "PURCHASE_INVOICE", INVOICE_ID))
            .thenReturn(List.of(originalTransaction(501L)));

        service.unpost(INVOICE_ID, null, TENANT_ID, USER_ID);

        assertThatThrownBy(() -> service.unpost(INVOICE_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex ->
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION));
        verify(ledgerService).reverse(501L, null, "UNPOST-10-501", USER_ID);
    }

    @Test
    void unpostHardDeletesBatchesOpenedByThisInvoice() {
        PurchaseInvoice invoice = invoice(DocumentStatus.POSTED);
        List<StockBatch> openedBatches = List.of(batch(20L, "Tomato", "10.000000", "10.000000"));
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(returnRepository.findReturnSummariesByOriginalInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(List.of());
        when(stockBatchRepository.findOpenedByPurchaseInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(openedBatches);
        when(transactionRepository.findOriginalsByReference(TENANT_ID, "PURCHASE_INVOICE", INVOICE_ID))
            .thenReturn(List.of(originalTransaction(501L)));

        service.unpost(INVOICE_ID, null, TENANT_ID, USER_ID);

        // Batches must be deleted before the ledger reversal so a later re-post never leaves
        // two batches on the same source invoice line.
        InOrder order = inOrder(stockBatchRepository, ledgerService);
        order.verify(stockBatchRepository).deleteAll(openedBatches);
        order.verify(ledgerService).reverse(501L, null, "UNPOST-10-501", USER_ID);
    }

    @Test
    void unpostAbortsWithBatchNotReversibleWhenBatchIsNotFullyUntouched() {
        PurchaseInvoice invoice = invoice(DocumentStatus.POSTED);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));
        when(returnRepository.findReturnSummariesByOriginalInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(List.of());
        // remaining (12) > original (10): passes the consumption guard (consumed = -2, not > 0)
        // yet fails the independent reversibility check — the second, independent safety net.
        when(stockBatchRepository.findOpenedByPurchaseInvoice(TENANT_ID, INVOICE_ID))
            .thenReturn(List.of(batch(44L, "Sugar", "10.000000", "12.000000")));

        assertThatThrownBy(() -> service.unpost(INVOICE_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.BATCH_NOT_REVERSIBLE);
                assertThat(ex.getParams()).containsEntry("entityType", "StockBatch");
                assertThat(ex.getParams()).containsEntry("batchId", 44L);
                assertThat(ex.getParams()).containsEntry("remainingQuantity", new BigDecimal("12.000000"));
                assertThat(ex.getParams()).containsEntry("originalQuantity", new BigDecimal("10.000000"));
            });

        verify(stockBatchRepository, never()).deleteAll(any());
        verifyNoInteractions(transactionRepository, ledgerService);
    }

    @Test
    void deleteDraftWithoutLedgerHistoryDeletesInvoice() {
        PurchaseInvoice invoice = invoice(DocumentStatus.DRAFT);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));
        when(transactionRepository.existsByReference(TENANT_ID, "PURCHASE_INVOICE", INVOICE_ID))
            .thenReturn(false);

        service.delete(INVOICE_ID, TENANT_ID);

        verify(transactionRepository).existsByReference(TENANT_ID, "PURCHASE_INVOICE", INVOICE_ID);
        verify(invoiceRepository).delete(invoice);
    }

    @Test
    void deleteDraftWithLedgerHistoryIsRejected() {
        PurchaseInvoice invoice = invoice(DocumentStatus.DRAFT);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));
        when(transactionRepository.existsByReference(TENANT_ID, "PURCHASE_INVOICE", INVOICE_ID))
            .thenReturn(true);

        assertThatThrownBy(() -> service.delete(INVOICE_ID, TENANT_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.ALREADY_PROCESSED);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseInvoice");
                assertThat(ex.getParams()).containsEntry("invoiceId", INVOICE_ID);
                assertThat(ex.getParams()).containsEntry("referenceType", "PURCHASE_INVOICE");
                assertThat(ex.getParams()).containsEntry("action", "delete");
            });

        verify(invoiceRepository, never()).delete(any(PurchaseInvoice.class));
    }

    @Test
    void uncompleteSucceedsFromCompletePreservesInvoiceNumberAndEnablesLineEditing() {
        PurchaseInvoice invoice = invoice(DocumentStatus.COMPLETE);
        String invoiceNumber = invoice.getInvoiceNumber();
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 2, 10, 15);
        invoice.setCompletedAt(completedAt);
        invoice.setCompletedBy(55L);
        UncompleteRequest request = new UncompleteRequest();
        request.setReason("NEEDS_EDIT");
        Material material = material(101L, "Flour");
        Uom uom = uom(1L);
        PurchaseInvoiceLineRequest lineRequest = new PurchaseInvoiceLineRequest();
        lineRequest.setMaterialId(material.getId());
        lineRequest.setQuantity(new BigDecimal("2.000000"));
        lineRequest.setUomId(uom.getId());
        lineRequest.setUnitCost(new BigDecimal("5.000000"));

        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));
        when(materialRepository.findByIdAndTenantId(material.getId(), TENANT_ID))
            .thenReturn(Optional.of(material));
        when(uomRepository.findResolvableByIdForTenant(uom.getId(), TENANT_ID))
            .thenReturn(Optional.of(uom));

        PurchaseInvoiceResponse response = service.uncomplete(INVOICE_ID, request, TENANT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(response.getInvoiceNumber()).isEqualTo(invoiceNumber);
        assertThat(response.getUnCompletedAt()).isNotNull();
        assertThat(response.getUnCompletedBy()).isEqualTo(USER_ID);
        assertThat(invoice.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(invoice.getInvoiceNumber()).isEqualTo(invoiceNumber);
        assertThat(invoice.getCompletedAt()).isEqualTo(completedAt);
        assertThat(invoice.getCompletedBy()).isEqualTo(55L);

        service.addLine(INVOICE_ID, lineRequest, TENANT_ID);

        assertThat(invoice.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getMaterial()).isSameAs(material);
            assertThat(line.getUom()).isSameAs(uom);
        });
        verifyNoInteractions(returnRepository, transactionRepository, ledgerService,
            stockBalanceRepository, stockBatchRepository);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"DRAFT", "POSTED", "CANCELLED"})
    void uncompleteRejectsNonCompleteStates(DocumentStatus status) {
        PurchaseInvoice invoice = invoice(status);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.uncomplete(INVOICE_ID, null, TENANT_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.INVALID_STATE_TRANSITION);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseInvoice");
                assertThat(ex.getParams()).containsEntry("currentStatus", status.name());
                assertThat(ex.getParams()).containsEntry("requiredStatus", "COMPLETE");
                assertThat(ex.getParams()).containsEntry("action", "uncomplete");
            });

        verify(invoiceRepository, never()).save(any(PurchaseInvoice.class));
        verifyNoInteractions(returnRepository, stockBatchRepository, transactionRepository, ledgerService);
    }

    @Test
    void deleteWorksAfterUncompleteWhenInvoiceHasNoLedgerHistory() {
        PurchaseInvoice invoice = invoice(DocumentStatus.COMPLETE);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));
        when(transactionRepository.existsByReference(TENANT_ID, "PURCHASE_INVOICE", INVOICE_ID))
            .thenReturn(false);

        service.uncomplete(INVOICE_ID, null, TENANT_ID, USER_ID);
        service.delete(INVOICE_ID, TENANT_ID);

        verify(invoiceRepository).delete(invoice);
    }

    @Test
    void deleteStillRejectsAfterUncompleteWhenInvoiceHasLedgerHistory() {
        PurchaseInvoice invoice = invoice(DocumentStatus.COMPLETE);
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
            .thenReturn(Optional.of(invoice));
        when(transactionRepository.existsByReference(TENANT_ID, "PURCHASE_INVOICE", INVOICE_ID))
            .thenReturn(true);

        service.uncomplete(INVOICE_ID, null, TENANT_ID, USER_ID);

        assertThatThrownBy(() -> service.delete(INVOICE_ID, TENANT_ID))
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(InventoryErrorCode.ALREADY_PROCESSED);
                assertThat(ex.getParams()).containsEntry("entityType", "PurchaseInvoice");
                assertThat(ex.getParams()).containsEntry("action", "delete");
            });

        verify(invoiceRepository, never()).delete(any(PurchaseInvoice.class));
    }

    private PurchaseInvoice invoice(DocumentStatus status) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(40L);
        warehouse.setName("Main Warehouse");

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setId(INVOICE_ID);
        invoice.setTenantId(TENANT_ID);
        invoice.setWarehouse(warehouse);
        invoice.setInvoiceNumber("PINV-10");
        invoice.setInvoiceDate(LocalDate.of(2026, 7, 1));
        invoice.setReceiptDate(LocalDate.of(2026, 7, 1));
        invoice.setStatus(status);
        invoice.setPostedToInventory(status == DocumentStatus.POSTED);
        return invoice;
    }

    private Material material(Long id, String name) {
        Material material = new Material();
        material.setId(id);
        material.setTenantId(TENANT_ID);
        material.setCode(name.toUpperCase());
        material.setName(name);
        return material;
    }

    private Uom uom(Long id) {
        Uom uom = new Uom();
        uom.setId(id);
        uom.setCode("KG");
        uom.setSymbol("kg");
        uom.setFactorToBase(BigDecimal.ONE);
        return uom;
    }

    private PurchaseInvoiceLine invoiceLine(Long id, PurchaseInvoice invoice, Material material,
                                            Uom uom, LocalDate expiryDate) {
        PurchaseInvoiceLine line = new PurchaseInvoiceLine();
        line.setId(id);
        line.setPurchaseInvoice(invoice);
        line.setMaterial(material);
        line.setQuantity(new BigDecimal("2.000000"));
        line.setUom(uom);
        line.setUnitCost(new BigDecimal("5.000000"));
        line.setLineTotal(new BigDecimal("10.000000"));
        line.setLineNetTotal(new BigDecimal("10.000000"));
        line.setDiscountPercent(BigDecimal.ZERO);
        line.setDiscountAmount(BigDecimal.ZERO);
        line.setExpiryDate(expiryDate);
        return line;
    }

    private InventoryTransaction originalTransaction(Long id) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setId(id);
        tx.setTenantId(TENANT_ID);
        return tx;
    }

    private StockBatch batch(Long id, String materialName, String original, String remaining) {
        Material material = new Material();
        material.setId(100L + id);
        material.setName(materialName);
        material.setCode(materialName.toUpperCase());

        StockBalance balance = new StockBalance();
        balance.setMaterial(material);

        StockBatch batch = new StockBatch();
        batch.setId(id);
        batch.setStockBalance(balance);
        batch.setOriginalQuantity(new BigDecimal(original));
        batch.setRemainingQuantity(new BigDecimal(remaining));
        return batch;
    }

    private PurchaseReturnRepository.ReturnReferenceSummary returnSummary(Long id, String code) {
        return new PurchaseReturnRepository.ReturnReferenceSummary() {
            @Override
            public Long getReturnId() {
                return id;
            }

            @Override
            public String getReturnCode() {
                return code;
            }
        };
    }
}
