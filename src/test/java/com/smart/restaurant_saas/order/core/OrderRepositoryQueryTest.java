package com.smart.restaurant_saas.order.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class OrderRepositoryQueryTest {

    @Test
    void findByFiltersCastsNullableDateParametersForPostgresTypeInference() throws Exception {
        Query query = OrderRepository.class
            .getMethod("findByFilters",
                Long.class,
                OrderType.class,
                OrderSource.class,
                OrderStatus.class,
                Long.class,
                LocalDateTime.class,
                LocalDateTime.class,
                String.class,
                Long.class,
                Long.class,
                Pageable.class)
            .getAnnotation(Query.class);

        assertThat(query.value())
            .contains("CAST(:fromDate AS timestamp) IS NULL")
            .contains("CAST(:toDate AS timestamp) IS NULL");
    }

    @Test
    void findByFiltersContainsOptionalCustomerIdPredicate() throws Exception {
        Query query = OrderRepository.class
            .getMethod("findByFilters",
                Long.class,
                OrderType.class,
                OrderSource.class,
                OrderStatus.class,
                Long.class,
                LocalDateTime.class,
                LocalDateTime.class,
                String.class,
                Long.class,
                Long.class,
                Pageable.class)
            .getAnnotation(Query.class);

        assertThat(query.value()).contains("(:customerId IS NULL OR o.customerId = :customerId)");
    }
}
