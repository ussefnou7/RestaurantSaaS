package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrderConsumptionRepositoryIntegrationTest {

    @Autowired
    private OrderConsumptionRepository docRepository;

    @Autowired
    private OrderConsumptionLineRepository lineRepository;

    @Test
    void readQueriesAcceptEmptyResultsAndNullableFilters() {
        assertThatCode(() -> {
            docRepository.findByFilters(0L, null, null, null, null, PageRequest.of(0, 20));
            lineRepository.countLinesByDocIds(List.of(-1L));
            lineRepository.summarizeMaterialsByDocId(-1L, 0L);
            lineRepository.findLinesByDocId(-1L);
        }).doesNotThrowAnyException();
    }
}
