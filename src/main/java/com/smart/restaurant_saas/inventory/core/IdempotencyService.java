package com.smart.restaurant_saas.inventory.core;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.smart.restaurant_saas.inventory.core.enums.IdempotencyScope;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;

/**
 * Helper service to enforce idempotency across inventory operations.
 *
 * Two-strategy approach:
 *  - "checkExisting" is a fast read used by callers that want to short-circuit
 *    before doing any work. Returns the existing record ID if the key is already used.
 *  - The actual insert protection relies on the DB-level UNIQUE constraint
 *    (uk_inventory_transaction_tenant_idempotency).
 *    Callers must catch DataIntegrityViolationException at their level
 *    and re-resolve via checkExisting.
 *
 * This service does NOT mutate data. It only reads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final InventoryTransactionRepository inventoryTransactionRepository;

    /**
     * Checks whether an idempotency key has already been used for the given scope.
     * Returns the existing record's ID if found, else empty.
     *
     * @param tenantId tenant scope
     * @param scope which entity space to check
     * @param idempotencyKey the key (caller-generated, e.g. "ORDER_123" or a UUID)
     * @return Optional with the existing record ID, or empty if the key is unused
     */
    public Optional<Long> findExistingId(Long tenantId, IdempotencyScope scope, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        return switch (scope) {
            case INVENTORY_TRANSACTION -> inventoryTransactionRepository
                .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .map(tx -> tx.getId());
        };
    }

    /**
     * Convenience: returns true if the key has been used.
     */
    public boolean exists(Long tenantId, IdempotencyScope scope, String idempotencyKey) {
        return findExistingId(tenantId, scope, idempotencyKey).isPresent();
    }
}
