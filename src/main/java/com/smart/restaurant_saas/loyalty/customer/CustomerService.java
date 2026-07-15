package com.smart.restaurant_saas.loyalty.customer;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.loyalty.LoyaltyErrorCode;
import com.smart.restaurant_saas.loyalty.customer.dto.CustomerResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll(Long tenantId) {
        return customerRepository.findByTenantIdOrderByIdDesc(tenantId).stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Resolves the customer for {@code (tenantId, phone)}, creating one only if absent.
     *
     * <p>First-write-wins: an existing customer is returned <em>unchanged</em>, even if the
     * incoming {@code name} differs — mutating a customer is deferred to the future Change Request
     * workflow. Concurrent inserts are guarded the same way {@code IdempotencyService} callers guard
     * theirs: attempt the save, catch the unique-constraint {@link DataIntegrityViolationException},
     * and re-select the row the other transaction just committed.
     *
     * <p>Runs in its own transaction ({@code REQUIRES_NEW}) so a loyalty-side failure — including a
     * poisoned transaction from the duplicate-insert path — can never roll back or fail an enclosing
     * order-creation transaction. Callers must still wrap the call defensively (see OrderService).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer findOrCreate(Long tenantId, String phone, String name) {
        if (phone == null || phone.isBlank()) {
            throw new ValidationException(LoyaltyErrorCode.CUSTOMER_PHONE_REQUIRED,
                "Customer phone is required",
                ErrorParams.of("field", "phone"));
        }

        return customerRepository.findByTenantIdAndPhone(tenantId, phone)
            .orElseGet(() -> insertGuarded(tenantId, phone, name));
    }

    public CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
            .id(customer.getId())
            .name(customer.getName())
            .phone(customer.getPhone())
            .build();
    }

    private Customer insertGuarded(Long tenantId, String phone, String name) {
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setPhone(phone);
        customer.setName(name);
        try {
            return customerRepository.save(customer);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent transaction inserted the same (tenant_id, phone) first — return its row.
            return customerRepository.findByTenantIdAndPhone(tenantId, phone)
                .orElseThrow(() -> ex);
        }
    }
}
