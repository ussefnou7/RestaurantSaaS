package com.smart.restaurant_saas.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a controller parameter to the effective tenant for the request, as resolved by
 * {@link CurrentTenantProvider#getCurrentTenantId()}.
 *
 * <p>This replaces {@code @RequestHeader("X-Tenant-Id")}. The difference is not cosmetic: the
 * header is caller-supplied input, while this value is derived from the signed JWT for every
 * principal except SYS_ADMIN. A controller can no longer receive a tenant id that the caller
 * chose, so a missing authorization annotation can no longer turn into a cross-tenant write.
 *
 * <p>Resolution happens during argument binding, before the handler body runs, and does not
 * depend on any other annotation being present on the method.
 *
 * @see CurrentTenantIdArgumentResolver
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentTenantId {
}
