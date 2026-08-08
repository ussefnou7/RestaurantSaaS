package com.smart.restaurant_saas.order.core;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void findByFiltersAllowsNullDateFiltersOnPostgres() {
        assertThatCode(() -> orderRepository.findByFilters(
            0L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(0, 20)))
            .doesNotThrowAnyException();
    }
}
