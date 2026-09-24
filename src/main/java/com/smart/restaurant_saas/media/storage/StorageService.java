package com.smart.restaurant_saas.media.storage;

import java.util.List;
import org.springframework.core.io.Resource;

/**
 * Everything that touches stored bytes goes through here.
 *
 * <p>V1 is a local filesystem. Cloudflare R2 is the intended destination — S3-compatible, so the
 * same key layout and no egress charge, which is the cost that dominates when a menu image is read
 * thousands of times and written once.
 *
 * <p>That swap is only cheap because of two rules that hold from day one and are not negotiable:
 * <strong>no URL is ever stored</strong> (a stored absolute URL turns a provider change into a data
 * migration across every row), and <strong>the key layout is fixed</strong>
 * ({@code t{tenantId}/{ownerType}/{uuid}/{variant}.{ext}}), so moving to an object store is a tree
 * copy.
 */
public interface StorageService {

    void put(String key, byte[] bytes, String contentType);

    /** @throws com.smart.restaurant_saas.common.ExternalServiceException if the key has no object */
    Resource get(String key);

    /** Absent is success. A delete that has already happened is not an error. */
    void delete(String key);

    boolean exists(String key);

    /** Every key under a prefix. Backs the orphan sweep; not used on a request path. */
    List<String> list(String prefix);
}
