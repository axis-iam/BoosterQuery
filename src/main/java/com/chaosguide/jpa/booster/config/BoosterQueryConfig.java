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

/**
 * Configuration for the SQL rewriter.
 * <p>
 * Controls the behavior of BoosterSqlRewriter and BoosterQueryExecutor.
 */
public class BoosterQueryConfig {

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

    /** Creates a config instance with all default values. */
    public BoosterQueryConfig() {
    }

    /**
     * Creates a config instance with the specified limit settings.
     *
     * @param defaultLimit    the default LIMIT value appended to queries without an explicit LIMIT
     * @param enableAutoLimit whether to automatically append LIMIT to queries
     */
    public BoosterQueryConfig(long defaultLimit, boolean enableAutoLimit) {
        this.defaultLimit = defaultLimit;
        this.enableAutoLimit = enableAutoLimit;
    }

    /** Returns the default LIMIT value. */
    public long getDefaultLimit() {
        return defaultLimit;
    }

    /**
     * Sets the default LIMIT value.
     *
     * @param defaultLimit the limit value; must be greater than 0
     * @throws IllegalArgumentException if {@code defaultLimit} is less than or equal to 0
     */
    public void setDefaultLimit(long defaultLimit) {
        if (defaultLimit <= 0) {
            throw new IllegalArgumentException("defaultLimit must be greater than 0");
        }
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

    /**
     * Creates a defensive copy to prevent shared instances from being accidentally modified.
     */
    public BoosterQueryConfig copy() {
        BoosterQueryConfig copy = new BoosterQueryConfig();
        copy.defaultLimit = this.defaultLimit;
        copy.enableAutoLimit = this.enableAutoLimit;
        copy.enableSqlRewrite = this.enableSqlRewrite;
        return copy;
    }

    @Override
    public String toString() {
        return "BoosterQueryConfig{" +
                "defaultLimit=" + defaultLimit +
                ", enableAutoLimit=" + enableAutoLimit +
                ", enableSqlRewrite=" + enableSqlRewrite +
                '}';
    }
}
