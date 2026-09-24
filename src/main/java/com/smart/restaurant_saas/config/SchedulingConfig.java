package com.smart.restaurant_saas.config;

import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionBatchingProperties;
import com.smart.restaurant_saas.media.MediaProperties;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduling and ShedLock so the D58 order-consumption batching poll and the D128 media
 * deletion drain each run on at most one application instance at a time. The lock is DB-backed
 * (the {@code shedlock} table) and uses DB time to avoid clock drift between instances.
 *
 * <p>Both schedulers' {@code @ConfigurationProperties} are registered here rather than in a config
 * class of their own — {@code MediaProperties} also carries the storage root, which is not a
 * scheduling concern, but one more eight-line {@code @Configuration} to say so is not worth it.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@EnableConfigurationProperties({OrderConsumptionBatchingProperties.class, MediaProperties.class})
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Bean
    public LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
        return new DefaultLockingTaskExecutor(lockProvider);
    }
}
