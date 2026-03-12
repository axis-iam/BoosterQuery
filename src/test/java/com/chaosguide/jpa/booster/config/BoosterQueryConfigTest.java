package com.chaosguide.jpa.booster.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoosterQueryConfigTest {

    @Test
    void testCopy_returnsIndependentInstance() {
        BoosterQueryConfig original = new BoosterQueryConfig();
        original.setDefaultLimit(500);
        original.setEnableAutoLimit(false);
        original.setEnableSqlRewrite(false);

        BoosterQueryConfig copy = original.copy();

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getDefaultLimit()).isEqualTo(500);
        assertThat(copy.isEnableAutoLimit()).isFalse();
        assertThat(copy.isEnableSqlRewrite()).isFalse();
    }

    @Test
    void testCopy_mutationIsolation() {
        BoosterQueryConfig original = new BoosterQueryConfig();
        original.setDefaultLimit(1000);
        original.setEnableAutoLimit(true);

        BoosterQueryConfig copy = original.copy();
        copy.setDefaultLimit(999);
        copy.setEnableAutoLimit(false);

        // Original object should not be affected
        assertThat(original.getDefaultLimit()).isEqualTo(1000);
        assertThat(original.isEnableAutoLimit()).isTrue();
    }

    @Test
    void testCopy_defaultValues() {
        BoosterQueryConfig original = new BoosterQueryConfig();
        BoosterQueryConfig copy = original.copy();

        assertThat(copy.getDefaultLimit()).isEqualTo(10000L);
        assertThat(copy.isEnableAutoLimit()).isTrue();
        assertThat(copy.isEnableSqlRewrite()).isTrue();
    }
}
