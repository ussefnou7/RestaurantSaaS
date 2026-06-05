package com.smart.restaurant_saas.inventory.controller;

import com.smart.restaurant_saas.inventory.dto.request.CancelPurchaseInvoiceRequest;
import com.smart.restaurant_saas.inventory.dto.request.CreatePurchaseInvoiceRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdatePurchaseInvoiceRequest;
import com.smart.restaurant_saas.inventory.dto.response.PurchaseInvoiceResponse;
import com.smart.restaurant_saas.inventory.service.PurchaseInvoiceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory/purchase-invoices")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService purchaseInvoiceService;

    @GetMapping
    @PreAuthorize("@securityService.hasPermission('INVENTORY_PURCHASE_VIEW')")
    public List<PurchaseInvoiceResponse> listInvoices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        return purchaseInvoiceService.listInvoices(
                search,
                supplierId,
                warehouseId,
                status,
                paymentStatus,
                dateFrom,
                dateTo
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_PURCHASE_VIEW')")
    public PurchaseInvoiceResponse getInvoice(@PathVariable Long id) {
        return purchaseInvoiceService.getInvoice(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    public PurchaseInvoiceResponse createInvoice(@Valid @RequestBody CreatePurchaseInvoiceRequest request) {
        return purchaseInvoiceService.createInvoice(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    public PurchaseInvoiceResponse updateInvoice(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePurchaseInvoiceRequest request
    ) {
        return purchaseInvoiceService.updateInvoice(id, request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    public PurchaseInvoiceResponse completeInvoice(@PathVariable Long id) {
        return purchaseInvoiceService.completeInvoice(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")
    public PurchaseInvoiceResponse cancelInvoice(
            @PathVariable Long id,
            @RequestBody(required = false) CancelPurchaseInvoiceRequest request
    ) {
        return purchaseInvoiceService.cancelInvoice(id, request);
    }
}
