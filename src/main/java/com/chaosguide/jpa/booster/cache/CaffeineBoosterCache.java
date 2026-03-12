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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.chaosguide.jpa.booster.config.BoosterQueryProperties;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-based implementation of {@link BoosterCache}.
 * <p>
 * Uses a local in-process {@link Cache} backed by Caffeine. Maximum size and TTL
 * are configurable via {@link BoosterQueryProperties.Cache} (bound from
 * {@code booster.query.cache.*} properties).
 *
 * @see BoosterCache
 * @see BoosterQueryProperties.Cache
 */
public class CaffeineBoosterCache implements BoosterCache {

    private final Cache<Object, String> cache;

    /**
     * Creates a new Caffeine cache instance with the given configuration.
     * <p>
     * Applies {@code maximumSize} and {@code expireAfterWrite} settings from the
     * supplied properties when they are positive values; otherwise uses Caffeine defaults.
     *
     * @param properties cache configuration properties (maximum size, TTL, etc.)
     */
    public CaffeineBoosterCache(BoosterQueryProperties.Cache properties) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();

        if (properties.getMaximumSize() > 0) {
            builder.maximumSize(properties.getMaximumSize());
        }

        if (properties.getExpireAfterWrite() > 0) {
            builder.expireAfterWrite(properties.getExpireAfterWrite(), TimeUnit.MILLISECONDS);
        }

        this.cache = builder.build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the cached value associated with the given key, or {@code null}
     * if the key is not present or has been evicted.
     */
    @Override
    public @Nullable String get(Object key) {
        return cache.getIfPresent(key);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Stores the value only when both key and value are non-null;
     * null arguments are silently ignored.
     */
    @Override
    public void put(Object key, String value) {
        if (key != null && value != null) {
            cache.put(key, value);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Invalidates all entries in the underlying Caffeine cache.
     */
    @Override
    public void clear() {
        cache.invalidateAll();
    }
}
