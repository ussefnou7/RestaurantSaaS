package com.smart.restaurant_saas.inventory.core.enums;

public enum DocumentType {
    PURCHASE_INVOICE("PI"),
    PURCHASE_RETURN("PR"),
    WASTE("WS"),
    PHYSICAL_COUNT("PC");

    private final String codePrefix;

    DocumentType(String codePrefix) {
        this.codePrefix = codePrefix;
    }

    public String codePrefix() {
        return codePrefix;
    }
}
