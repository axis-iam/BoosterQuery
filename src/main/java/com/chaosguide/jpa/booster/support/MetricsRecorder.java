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
package com.chaosguide.jpa.booster.support;

/**
 * Abstraction for recording observability metrics.
 * <p>
 * Decouples {@link com.chaosguide.jpa.booster.executor.BoosterQueryExecutor} from
 * Micrometer, allowing type-safe metric recording without a hard compile-time dependency.
 * When no metrics library is on the classpath, use {@link #noOp()} to silently skip all operations.
 */
public interface MetricsRecorder {

    /**
     * Increments a named counter with the given tags.
     *
     * @param name the metric name
     * @param tags alternating key-value pairs (e.g. "result", "success")
     */
    void incrementCounter(String name, String... tags);

    /**
     * Records a timer duration.
     *
     * @param name         the metric name
     * @param elapsedNanos elapsed time in nanoseconds
     */
    void recordTimer(String name, long elapsedNanos);

    /**
     * Returns a no-op recorder that silently discards all metric operations.
     */
    static MetricsRecorder noOp() {
        return NoOpMetricsRecorder.INSTANCE;
    }
}
