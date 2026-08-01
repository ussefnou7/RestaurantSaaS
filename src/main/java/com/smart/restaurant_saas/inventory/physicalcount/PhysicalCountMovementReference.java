package com.smart.restaurant_saas.inventory.physicalcount;

/** Human-readable source-document code resolved for one ledger transaction. */
public interface PhysicalCountMovementReference {

    Long getTransactionId();

    String getReferenceCode();
}
