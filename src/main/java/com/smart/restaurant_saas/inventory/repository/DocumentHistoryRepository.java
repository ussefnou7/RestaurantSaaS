package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.DocumentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentHistoryRepository extends JpaRepository<DocumentHistory, Long> {
}
