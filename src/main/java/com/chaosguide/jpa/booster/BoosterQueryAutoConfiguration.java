/*
 * Copyright 2025 ChaosGuide
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chaosguide.jpa.booster;

import com.chaosguide.jpa.booster.cache.BoosterCache;
import com.chaosguide.jpa.booster.cache.CaffeineBoosterCache;
import com.chaosguide.jpa.booster.config.BoosterQueryConfig;
import com.chaosguide.jpa.booster.executor.BoosterNativeExecutor;
import com.chaosguide.jpa.booster.executor.BoosterQueryExecutor;
import com.chaosguide.jpa.booster.support.MetricsRecorder;
import com.chaosguide.jpa.booster.support.MicrometerMetricsRecorder;
import jakarta.persistence.EntityManager;
import com.chaosguide.jpa.booster.config.BoosterQueryProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration entry point for BoosterQuery.
 * <p>
 * When an {@link EntityManager} is present in the container, automatically registers:
 * - {@link BoosterNativeExecutor} - basic query executor
 * - {@link BoosterQueryConfig} - query rewriting configuration
 * - {@link BoosterQueryExecutor} - enhanced query executor
 * - {@link BoosterCache} - SQL transformation cache (if enabled)
 * <p>
 * Allows business applications or custom repositories to inject and use these beans
 * without additional configuration.
 */
@AutoConfiguration
@ConditionalOnBean(EntityManager.class)
@EnableConfigurationProperties(BoosterQueryProperties.class)
public class BoosterQueryAutoConfiguration {

    /**
     * Registers a basic {@link BoosterNativeExecutor} bean backed by the given {@link EntityManager}.
     * <p>
     * Only created when no other {@code BoosterNativeExecutor} bean exists in the context.
     *
     * @param entityManager the JPA entity manager
     * @return a new basic query executor instance
     */
    @Bean
    @ConditionalOnMissingBean
    public BoosterNativeExecutor boosterNativeExecutor(EntityManager entityManager) {
        return new BoosterNativeExecutor(entityManager);
    }

    /**
     * Creates a {@link BoosterQueryConfig} from externalized {@link BoosterQueryProperties}.
     * <p>
     * Transfers {@code defaultLimit}, {@code enableAutoLimit}, and {@code enableSqlRewrite}
     * settings from the bound properties into a mutable config object.
     *
     * @param properties the externalized configuration properties (prefix {@code booster.query})
     * @return a new rewriter configuration instance
     */
    @Bean
    @ConditionalOnMissingBean
    public BoosterQueryConfig boosterQueryConfig(BoosterQueryProperties properties) {
        BoosterQueryConfig config = new BoosterQueryConfig();
        config.setDefaultLimit(properties.getDefaultLimit());
        config.setEnableAutoLimit(properties.isEnableAutoLimit());
        config.setEnableSqlRewrite(properties.isEnableSqlRewrite());
        return config;
    }

    /**
     * Registers a Caffeine-based {@link BoosterCache} when caching is explicitly enabled
     * via {@code booster.query.cache.enabled=true}.
     * <p>
     * Cache size and TTL are controlled by {@link BoosterQueryProperties.Cache}.
     *
     * @param properties the externalized configuration properties containing cache settings
     * @return a new {@link CaffeineBoosterCache} instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "booster.query.cache", name = "enabled", havingValue = "true")
    public BoosterCache boosterCache(BoosterQueryProperties properties) {
        return new CaffeineBoosterCache(properties.getCache());
    }

    /**
     * Registers a {@link BoosterQueryExecutor} that wraps the basic executor with SQL
     * rewriting, auto-limit, and optional caching.
     * <p>
     * The {@link BoosterCache} dependency is resolved lazily via {@link ObjectProvider} and
     * may be {@code null} when caching is not enabled.
     * <p>
     * {@link MetricsRecorder} is optionally resolved from the container. When Micrometer is not
     * on the classpath, the inner {@link MicrometerConfiguration} is not loaded, no
     * {@code MetricsRecorder} bean exists, and the executor defaults to no-op metrics.
     *
     * @param entityManager   the JPA entity manager
     * @param config          the SQL rewriter configuration
     * @param cacheProvider   lazy provider for the optional cache bean
     * @param metricsProvider lazy provider for the optional metrics recorder bean
     * @return a new enhanced query executor instance
     */
    @Bean
    @ConditionalOnMissingBean
    public BoosterQueryExecutor boosterQueryExecutor(EntityManager entityManager,
                                                       BoosterQueryConfig config,
                                                       ObjectProvider<BoosterCache> cacheProvider,
                                                       ObjectProvider<MetricsRecorder> metricsProvider) {
        return new BoosterQueryExecutor(entityManager, config, cacheProvider.getIfAvailable(), metricsProvider.getIfAvailable());
    }

    /**
     * Inner conditional configuration class: only loaded when Micrometer is on the classpath.
     * <p>
     * Registers a {@link MicrometerMetricsRecorder} bean backed by the application's
     * {@link io.micrometer.core.instrument.MeterRegistry}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MicrometerConfiguration {

        @Bean
        @ConditionalOnMissingBean(MetricsRecorder.class)
        MetricsRecorder boosterMetricsRecorder(ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registryProvider) {
            io.micrometer.core.instrument.MeterRegistry registry = registryProvider.getIfAvailable();
            return registry != null ? new MicrometerMetricsRecorder(registry) : MetricsRecorder.noOp();
        }
    }
}
