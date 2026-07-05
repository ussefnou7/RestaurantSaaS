package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorCode;
import com.smart.restaurant_saas.common.ErrorParams;
import java.util.Map;

/**
 * Ledger-domain failures. Now a structured {@link BusinessException}: each factory carries an
 * {@link InventoryErrorCode} + params so the frontend can translate; the string argument is
 * English debug text for logs only.
 */
public class InventoryLedgerException extends BusinessException {

    private InventoryLedgerException(ErrorCode errorCode, String debugMessage, Map<String, Object> params) {
        super(errorCode, debugMessage, params);
    }

    /** Field-level validation of a {@code LedgerCommand}. {@code field} is the offending field name. */
    public static InventoryLedgerException validation(String field, String debugMessage) {
        return new InventoryLedgerException(InventoryErrorCode.VALIDATION_FAILED, debugMessage,
            ErrorParams.of("field", field));
    }

    public static InventoryLedgerException notFound(String entity, Long id) {
        return new InventoryLedgerException(InventoryErrorCode.RESOURCE_NOT_FOUND,
            entity + " not found: id=" + id,
            ErrorParams.of("entityType", entity, "entityId", id));
    }

    public static InventoryLedgerException alreadyReversed(Long txId) {
        return new InventoryLedgerException(InventoryErrorCode.ALREADY_PROCESSED,
            "Transaction id=" + txId + " has already been reversed",
            ErrorParams.of("entityType", "InventoryTransaction", "entityId", txId, "action", "reverse"));
    }

    /**
     * A referenced entity exists but belongs to another tenant. No dedicated error code exists
     * for this; mapped to VALIDATION_FAILED (400, preserving prior status) with the mismatch in
     * params. Flagged for review — see the migration summary.
     */
    public static InventoryLedgerException tenantMismatch(String entity, Long entityId, Long expectedTenant) {
        return new InventoryLedgerException(InventoryErrorCode.VALIDATION_FAILED,
            entity + " id=" + entityId + " does not belong to tenant=" + expectedTenant,
            ErrorParams.of("field", "tenantId", "entityType", entity,
                "entityId", entityId, "expectedTenant", expectedTenant));
    }
}
