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

import java.util.regex.Pattern;

/**
 * SQL sanitizer for logging purposes.
 * Replaces sensitive literals (strings, numbers) with placeholders to prevent information leakage.
 */
public final class SqlSanitizer {

    private static final Pattern LITERAL_PATTERN = Pattern.compile("'[^']*'|\\b\\d+\\b");

    private SqlSanitizer() {
        // Utility class
    }

    /**
     * Sanitizes SQL by replacing string literals and numeric literals with '?'.
     *
     * @param sql the original SQL
     * @return sanitized SQL with literals replaced by '?', or null if input is null
     */
    public static String sanitize(String sql) {
        if (sql == null) {
            return null;
        }
        return LITERAL_PATTERN.matcher(sql).replaceAll("?");
    }
}
