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
package com.chaosguide.jpa.booster.cache;

import org.jspecify.annotations.Nullable;

/**
 * SQL transformation cache interface.
 */
public interface BoosterCache {

    /**
     * Retrieves a cached value.
     *
     * @param key cache key
     * @return cached value, or null if not present
     */
    @Nullable
    String get(Object key);

    /**
     * Stores a value in the cache.
     *
     * @param key   cache key
     * @param value cache value
     */
    void put(Object key, String value);

    /**
     * Clears all cache entries.
     */
    void clear();
}
