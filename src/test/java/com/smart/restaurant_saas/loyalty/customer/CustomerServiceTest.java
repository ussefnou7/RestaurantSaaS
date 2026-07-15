package com.smart.restaurant_saas.loyalty.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.loyalty.LoyaltyErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final String PHONE = "0555000111";

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void findOrCreateReturnsExistingUnchangedIgnoringIncomingName() {
        Customer existing = customer(50L, "Original Name");
        when(customerRepository.findByTenantIdAndPhone(TENANT_ID, PHONE)).thenReturn(Optional.of(existing));

        Customer result = customerService.findOrCreate(TENANT_ID, PHONE, "Different Name");

        assertThat(result).isSameAs(existing);
        assertThat(result.getName()).isEqualTo("Original Name");
        // First-write-wins: never persist / mutate an existing customer.
        verify(customerRepository, never()).save(any());
    }

    @Test
    void findOrCreateInsertsNewCustomerWhenAbsent() {
        when(customerRepository.findByTenantIdAndPhone(TENANT_ID, PHONE)).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            c.setId(99L);
            return c;
        });

        Customer result = customerService.findOrCreate(TENANT_ID, PHONE, "Sara");

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getPhone()).isEqualTo(PHONE);
        assertThat(result.getName()).isEqualTo("Sara");
    }

    @Test
    void findOrCreateReSelectsWinnerOnConcurrentDuplicateInsert() {
        Customer winner = customer(77L, "Sara");
        when(customerRepository.findByTenantIdAndPhone(TENANT_ID, PHONE))
            .thenReturn(Optional.empty())   // initial lookup: not there yet
            .thenReturn(Optional.of(winner)); // re-select after the constraint violation
        when(customerRepository.save(any(Customer.class)))
            .thenThrow(new DataIntegrityViolationException("uk_customer_tenant_phone"));

        Customer result = customerService.findOrCreate(TENANT_ID, PHONE, "Sara");

        assertThat(result).isSameAs(winner);
        verify(customerRepository, times(2)).findByTenantIdAndPhone(TENANT_ID, PHONE);
    }

    @Test
    void findOrCreateRejectsBlankPhone() {
        assertThatThrownBy(() -> customerService.findOrCreate(TENANT_ID, "  ", "Sara"))
            .isInstanceOfSatisfying(ValidationException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(LoyaltyErrorCode.CUSTOMER_PHONE_REQUIRED);
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
        verify(customerRepository, never()).findByTenantIdAndPhone(any(), any());
    }

    private Customer customer(Long id, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setTenantId(TENANT_ID);
        customer.setPhone(PHONE);
        customer.setName(name);
        return customer;
    }
}
