package com.smart.restaurant_saas.inventory.physicalcount;

/** Projection returned by the freeze-conflict query in PhysicalCountRepository. */
public interface MaterialConflictProjection {
    Long getMaterialId();
    String getMaterialName();
    String getCountCode();
}
