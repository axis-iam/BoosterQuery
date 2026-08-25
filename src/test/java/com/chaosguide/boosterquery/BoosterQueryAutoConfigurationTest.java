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
package com.chaosguide.boosterquery;

import com.chaosguide.boosterquery.support.MetricsRecorder;
import com.chaosguide.boosterquery.support.MicrometerMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BoosterQueryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BoosterQueryAutoConfiguration.class))
            .withBean(EntityManager.class, () -> mock(EntityManager.class));

    @Test
    void shouldNotRegisterMetricsRecorder_whenMeterRegistryIsMissing() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(MetricsRecorder.class));
    }

    @Test
    void shouldNotLoadMicrometerConfiguration_whenMicrometerIsMissingFromClasspath() {
        contextRunner.withClassLoader(new FilteredClassLoader(MeterRegistry.class))
                .run(context -> assertThat(context).doesNotHaveBean(MetricsRecorder.class));
    }

    @Test
    void shouldRegisterMicrometerMetricsRecorder_whenMeterRegistryIsAvailable() {
        contextRunner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(MetricsRecorder.class);
                    assertThat(context).hasSingleBean(MicrometerMetricsRecorder.class);
                });
    }

    @Test
    void shouldNotRegisterMetricsRecorder_whenMultipleMeterRegistriesHaveNoPrimaryCandidate() {
        contextRunner.withBean("firstMeterRegistry", MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean("secondMeterRegistry", MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> assertThat(context).doesNotHaveBean(MetricsRecorder.class));
    }

    @Test
    void shouldKeepCustomMetricsRecorder_whenUserProvidesOne() {
        MetricsRecorder customRecorder = mock(MetricsRecorder.class);

        contextRunner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(MetricsRecorder.class, () -> customRecorder)
                .run(context -> assertThat(context.getBean(MetricsRecorder.class)).isSameAs(customRecorder));
    }
}
