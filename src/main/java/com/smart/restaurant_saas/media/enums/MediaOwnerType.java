package com.smart.restaurant_saas.media.enums;

/**
 * The kind of record an attachment hangs off.
 *
 * <p>Adding a value here is two-thirds of making a new entity attachable; the rest is a
 * {@link MediaPurpose} value and a {@code MediaOwnerResolver} bean in the owning module — plus a
 * migration widening {@code chk_media_link_owner_type}, since the CHECK constraint mirrors this
 * enum.
 */
public enum MediaOwnerType {
    PRODUCT,
    EMPLOYEE
}
