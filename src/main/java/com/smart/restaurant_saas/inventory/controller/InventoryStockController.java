package com.smart.restaurant_saas.inventory.controller;

import com.smart.restaurant_saas.inventory.dto.request.CreateManualInventoryTransactionRequest;
import com.smart.restaurant_saas.inventory.dto.response.InventoryTransactionResponse;
import com.smart.restaurant_saas.inventory.dto.response.StockBalanceResponse;
import com.smart.restaurant_saas.inventory.service.InventoryTransactionService;
import com.smart.restaurant_saas.inventory.service.StockBalanceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory")
public class InventoryStockController {

    private final StockBalanceService stockBalanceService;
    private final InventoryTransactionService transactionService;

    @GetMapping("/stock-balances")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_STOCK_VIEW')")
    public List<StockBalanceResponse> listStockBalances(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) String search
    ) {
        return stockBalanceService.listStockBalances(warehouseId, materialId, categoryId, lowStock, search);
    }

    @GetMapping("/transactions")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_STOCK_VIEW')")
    public List<InventoryTransactionResponse> listTransactions(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String referenceType,
            @RequestParam(required = false) String search
    ) {
        return transactionService.listTransactions(
                warehouseId,
                materialId,
                categoryId,
                transactionType,
                direction,
                dateFrom,
                dateTo,
                referenceType,
                search
        );
    }

    @PostMapping("/transactions/manual")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.hasPermission('INVENTORY_STOCK_MANAGE')")
    public InventoryTransactionResponse createManualTransaction(
            @Valid @RequestBody CreateManualInventoryTransactionRequest request
    ) {
        return transactionService.createManualTransaction(request);
    }
}
