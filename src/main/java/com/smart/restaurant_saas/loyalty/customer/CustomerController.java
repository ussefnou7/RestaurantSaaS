package com.smart.restaurant_saas.loyalty.customer;

import com.smart.restaurant_saas.loyalty.customer.dto.CustomerRequest;
import com.smart.restaurant_saas.loyalty.customer.dto.CustomerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loyalty/customers")
@RequiredArgsConstructor
@Tag(name = "Loyalty - Customers", description = "Tenant loyalty customer capture for the POS")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('LOYALTY_VIEW')")
    @Operation(
        summary = "List customers",
        description = "Returns the tenant's full customer list for POS cache sync when no query "
                    + "parameters are supplied. With search/page/size parameters, returns a paginated "
                    + "admin-web list filtered by name or phone."
    )
    public Object list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        if (search == null && page == null && size == null) {
            return customerService.findAll(tenantId);
        }
        return customerService.findPage(tenantId, search, pageable);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('LOYALTY_MANAGE')")
    @Operation(
        summary = "Find or create a customer",
        description = "Resolves the customer for the given phone within the tenant, creating one only "
                    + "if absent. An existing customer is returned unchanged (first-write-wins). Backs "
                    + "the POS \"new customer\" popup."
    )
    public ResponseEntity<CustomerResponse> findOrCreate(
            @Valid @RequestBody CustomerRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Customer customer = customerService.findOrCreate(tenantId, request.getPhone(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.toResponse(customer));
    }
}
