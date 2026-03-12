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
package com.chaosguide.jpa.booster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BoosterQuery configuration properties.
 * <p>
 * Bound to the {@code booster.query} configuration prefix.
 */
@ConfigurationProperties(prefix = "booster.query")
public class BoosterQueryProperties {

    /**
     * Default LIMIT value.
     * Automatically appended when the SQL has no LIMIT clause.
     * <p>
     * Default: 10000
     */
    private long defaultLimit = 10000L;

    /**
     * Whether to enable auto-appending LIMIT.
     * Default: true
     */
    private boolean enableAutoLimit = true;

    /**
     * Whether to enable SQL rewriting (removes conditions for null parameters).
     * Default: true
     */
    private boolean enableSqlRewrite = true;

    /**
     * Cache configuration.
     */
    private Cache cache = new Cache();

    /** Returns the default LIMIT value. */
    public long getDefaultLimit() {
        return defaultLimit;
    }

    /** Sets the default LIMIT value. */
    public void setDefaultLimit(long defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    /** Returns whether auto-appending LIMIT is enabled. */
    public boolean isEnableAutoLimit() {
        return enableAutoLimit;
    }

    /** Sets whether to automatically append LIMIT to queries. */
    public void setEnableAutoLimit(boolean enableAutoLimit) {
        this.enableAutoLimit = enableAutoLimit;
    }

    /** Returns whether SQL rewriting (null-parameter condition removal) is enabled. */
    public boolean isEnableSqlRewrite() {
        return enableSqlRewrite;
    }

    /** Sets whether to enable SQL rewriting. */
    public void setEnableSqlRewrite(boolean enableSqlRewrite) {
        this.enableSqlRewrite = enableSqlRewrite;
    }

    /** Returns the cache configuration. */
    public Cache getCache() {
        return cache;
    }

    /** Sets the cache configuration. */
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /**
     * Cache configuration class.
     */
    public static class Cache {
        /**
         * Whether to enable local caching (Caffeine-based).
         * <p>
         * Cached content includes:
         * 1. SQL rewrite results (Original SQL + Null Params -> Rewritten SQL)
         * 2. Count SQL (SQL -> Count SQL)
         * 3. Sort SQL (SQL + Sort -> Sorted SQL)
         * <p>
         * Default: false (must be explicitly enabled)
         */
        private boolean enabled = false;

        /**
         * Maximum number of cache entries.
         * <p>
         * Default: 1000
         */
        private long maximumSize = 1000;

        /**
         * Expiration time after write (milliseconds).
         * <p>
         * Default: 60 minutes (3600000ms)
         */
        private long expireAfterWrite = 3600000;

        /** Returns whether local caching is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether to enable local caching. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the maximum number of cache entries. */
        public long getMaximumSize() {
            return maximumSize;
        }

        /** Sets the maximum number of cache entries. */
        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        /** Returns the expiration time after write, in milliseconds. */
        public long getExpireAfterWrite() {
            return expireAfterWrite;
        }

        /** Sets the expiration time after write, in milliseconds. */
        public void setExpireAfterWrite(long expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }
    }
}
