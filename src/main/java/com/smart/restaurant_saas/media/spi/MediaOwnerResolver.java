package com.smart.restaurant_saas.media.spi;

import com.smart.restaurant_saas.media.enums.MediaOwnerType;

/**
 * How an owning module answers the two questions the media module is not allowed to know.
 *
 * <p>The media module has no compile-time dependency on Menu, HR or Inventory, and no switch on
 * {@code ownerType}. Each owning module contributes one bean instead; the dependency points from
 * the owner to media, never the other way.
 *
 * <p>Three things fall out of this one interface: {@link #exists} is the tenant check on upload, it
 * is also the reconciliation predicate for links whose owner was hard-deleted, and
 * {@link #isMutable} is the deletion guard. None of them needs code per owner type.
 */
public interface MediaOwnerResolver {

    MediaOwnerType ownerType();

    /**
     * Whether the owner row exists <em>within this tenant</em>.
     *
     * <p>Always called with the request's tenant, which is what closes the cross-tenant hole: an
     * {@code ownerId} belonging to another tenant must answer {@code false} here, not throw and not
     * answer {@code true}. Implementations query by {@code (id, tenantId)}, never by id alone.
     */
    boolean exists(Long tenantId, Long ownerId);

    /**
     * Whether attachments on this owner may still be <em>removed</em>. Adding is always permitted,
     * in every state.
     *
     * <p>An attachment is evidence for the record it hangs off, and once that record is final the
     * correction path is to add the right file beside the wrong one, not to erase it — the same
     * reasoning that makes {@code inventory_transaction} append-only. Owners with no final state
     * return {@code true} unconditionally.
     */
    boolean isMutable(Long tenantId, Long ownerId);
}
