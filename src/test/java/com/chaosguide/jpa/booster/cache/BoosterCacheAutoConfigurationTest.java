package com.chaosguide.jpa.booster.cache;

import com.chaosguide.jpa.booster.BoosterQueryAutoConfiguration;
import com.chaosguide.jpa.booster.config.BoosterQueryConfig;
import com.chaosguide.jpa.booster.executor.BoosterNativeExecutor;
import com.chaosguide.jpa.booster.executor.BoosterQueryExecutor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Full auto-configuration tests
 */
class BoosterCacheAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BoosterQueryAutoConfiguration.class))
            .withBean(EntityManager.class, () -> mock(EntityManager.class));

    // ==================== Cache ====================

    @Test
    void cacheShouldNotBeEnabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(BoosterCache.class);
            assertThat(context).doesNotHaveBean(CaffeineBoosterCache.class);
        });
    }

    @Test
    void cacheShouldBeEnabledWhenPropertyIsSet() {
        contextRunner.withPropertyValues("booster.query.cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(BoosterCache.class);
                    assertThat(context).hasSingleBean(CaffeineBoosterCache.class);
                });
    }

    // ==================== Bean registration ====================

    @Test
    void allBeansRegisteredByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(BoosterNativeExecutor.class);
            assertThat(context).hasSingleBean(BoosterQueryConfig.class);
            assertThat(context).hasSingleBean(BoosterQueryExecutor.class);
        });
    }

    // ==================== Property binding ====================

    @Test
    void customProperties_appliedToConfig() {
        contextRunner.withPropertyValues(
                "booster.query.default-limit=500",
                "booster.query.enable-auto-limit=false",
                "booster.query.enable-sql-rewrite=false"
        ).run(context -> {
            BoosterQueryConfig config = context.getBean(BoosterQueryConfig.class);
            assertThat(config.getDefaultLimit()).isEqualTo(500);
            assertThat(config.isEnableAutoLimit()).isFalse();
            assertThat(config.isEnableSqlRewrite()).isFalse();
        });
    }

    @Test
    void defaultProperties_appliedToConfig() {
        contextRunner.run(context -> {
            BoosterQueryConfig config = context.getBean(BoosterQueryConfig.class);
            assertThat(config.getDefaultLimit()).isEqualTo(10000L);
            assertThat(config.isEnableAutoLimit()).isTrue();
            assertThat(config.isEnableSqlRewrite()).isTrue();
        });
    }

    // ==================== ConditionalOnMissingBean ====================

    @Test
    void customConfig_overridesAutoConfigured() {
        BoosterQueryConfig custom = new BoosterQueryConfig();
        custom.setDefaultLimit(999);

        contextRunner.withBean(BoosterQueryConfig.class, () -> custom)
                .run(context -> {
                    BoosterQueryConfig config = context.getBean(BoosterQueryConfig.class);
                    assertThat(config.getDefaultLimit()).isEqualTo(999);
                });
    }

    @Test
    void customCache_overridesAutoConfigured() {
        BoosterCache customCache = mock(BoosterCache.class);

        contextRunner.withPropertyValues("booster.query.cache.enabled=true")
                .withBean(BoosterCache.class, () -> customCache)
                .run(context -> {
                    BoosterCache cache = context.getBean(BoosterCache.class);
                    assertThat(cache).isSameAs(customCache);
                });
    }
}