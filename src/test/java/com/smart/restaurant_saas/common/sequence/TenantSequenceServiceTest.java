package com.smart.restaurant_saas.common.sequence;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantCodeService;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import com.smart.restaurant_saas.tenant.TenantRepository;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Proxy;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class TenantSequenceServiceTest {

    private final InMemorySequenceRepository sequenceRepository = new InMemorySequenceRepository();
    private final TenantSequenceService service = new TenantSequenceService(
            sequenceRepository.repository(),
            tenantRepository(),
            new TenantCodeService(null, null)
    );

    @Test
    void entityCodesIncrementPerTenantAndEntityType() {
        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.MAT)).isEqualTo("KFC-MAT-0001");
        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.MAT)).isEqualTo("KFC-MAT-0002");
        assertThat(service.generateEntityCode(9L, TenantEntityPrefix.MAT)).isEqualTo("EDR-MAT-0001");
    }

    @Test
    void entityCodesZeroPadAndGrowPastFourDigits() {
        sequenceRepository.seed(5L, (short) 0, TenantEntityPrefix.MAT.name(), 9_998);

        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.MAT)).isEqualTo("KFC-MAT-9999");
        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.MAT)).isEqualTo("KFC-MAT-10000");
    }

    @Test
    void entityTypesHaveIndependentCountersForSameTenant() {
        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.MAT)).isEqualTo("KFC-MAT-0001");
        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.SUP)).isEqualTo("KFC-SUP-0001");
        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.MAT)).isEqualTo("KFC-MAT-0002");
        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.SUP)).isEqualTo("KFC-SUP-0002");
    }

    @Test
    void entityCodeBucketIsIsolatedFromDocumentYearBucket() {
        short currentYear = (short) Year.now().getValue();

        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.MAT)).isEqualTo("KFC-MAT-0001");
        assertThat(service.generateDocumentNumber(5L, TenantEntityPrefix.MAT))
                .isEqualTo("KFC-MAT-" + currentYear + "-0001");
        assertThat(service.generateEntityCode(5L, TenantEntityPrefix.MAT)).isEqualTo("KFC-MAT-0002");
        assertThat(service.generateDocumentNumber(5L, TenantEntityPrefix.MAT))
                .isEqualTo("KFC-MAT-" + currentYear + "-0002");
    }

    @Test
    void repositoryIncrementMethodUsesPessimisticWriteLockForConcurrentSafety() throws NoSuchMethodException {
        Lock lock = TenantSequenceCounterRepository.class
                .getMethod("findForUpdate", Long.class, short.class, String.class)
                .getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private TenantRepository tenantRepository() {
        return (TenantRepository) Proxy.newProxyInstance(
                TenantRepository.class.getClassLoader(),
                new Class<?>[]{TenantRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(tenant((Long) args[0]));
                    case "toString" -> "TenantRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private Tenant tenant(Long id) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setCode(id.equals(5L) ? "kfc" : "edr");
        tenant.setName("Tenant " + id);
        return tenant;
    }

    private static final class InMemorySequenceRepository {

        private final Map<Key, TenantSequenceCounter> counters = new HashMap<>();
        private long nextId = 1L;

        private TenantSequenceCounterRepository repository() {
            return (TenantSequenceCounterRepository) Proxy.newProxyInstance(
                    TenantSequenceCounterRepository.class.getClassLoader(),
                    new Class<?>[]{TenantSequenceCounterRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findForUpdate" -> Optional.ofNullable(counters.get(new Key(
                                (Long) args[0],
                                (Short) args[1],
                                (String) args[2]
                        )));
                        case "save" -> save((TenantSequenceCounter) args[0]);
                        case "toString" -> "TenantSequenceCounterRepositoryStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private void seed(Long tenantId, short year, String sequenceKey, int lastSeq) {
            TenantSequenceCounter counter = new TenantSequenceCounter();
            counter.setId(nextId++);
            counter.setTenantId(tenantId);
            counter.setYear(year);
            counter.setSequenceKey(sequenceKey);
            counter.setLastSeq(lastSeq);
            counters.put(new Key(tenantId, year, sequenceKey), counter);
        }

        private TenantSequenceCounter save(TenantSequenceCounter counter) {
            if (counter.getId() == null) {
                counter.setId(nextId++);
            }
            counters.put(new Key(counter.getTenantId(), counter.getYear(), counter.getSequenceKey()), counter);
            return counter;
        }

        private record Key(Long tenantId, short year, String sequenceKey) {
        }
    }
}
