package com.smart.restaurant_saas.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository method that queries tenant-owned data without a tenant predicate.
 *
 * <p>These methods are not wrong, but they are only correct because of who calls them: every
 * current caller passes an id it already resolved through a tenant-scoped parent, so the rows the
 * query can reach are already confined to one tenant. The method itself enforces nothing. Hand it
 * an id taken straight from a request body and it will happily cross a tenant boundary.
 *
 * <p>That is not hypothetical. {@code UomRepository.findById} had exactly this shape in
 * {@code WasteService.resolveUom} and {@code InventoryLedgerService.record}: the surrounding
 * services were expected to check visibility, five did, two did not, and tenant A could persist
 * tenant B's private UOM on its own waste lines and inventory transactions. The rule was invisible
 * at the declaration, so it was invisible to the authors who skipped it.
 *
 * <p>The annotation exists to make that requirement visible where the method is declared rather
 * than in an audit document. Each annotated method carries a javadoc line stating what the caller
 * must guarantee.
 *
 * <p><strong>Before calling one of these, satisfy its stated precondition.</strong> If you cannot —
 * if the id you hold came from request input rather than a tenant-scoped lookup — do not call it.
 * Use the tenant-scoped alternative, or add one, as
 * {@link com.smart.restaurant_saas.inventory.repository.UomRepository#findResolvableByIdForTenant}
 * was used to close the case above.
 *
 * <p>This marker is documentation, not enforcement: nothing reads it at runtime. Adding tenant
 * predicates to these methods is separate work, because several are legitimately parent-scoped and
 * changing their signatures would ripple through their callers.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface TenantUnscoped {

    /** What the caller must guarantee about the ids it passes for this method to stay correct. */
    String value();
}
