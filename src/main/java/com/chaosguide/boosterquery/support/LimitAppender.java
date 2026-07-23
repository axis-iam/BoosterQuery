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
package com.chaosguide.boosterquery.support;

import java.util.regex.Pattern;

/**
 * SQL LIMIT detection utility.
 * <p>
 * Only responsible for detecting whether SQL already contains a LIMIT clause.
 * Actual row limiting is done via {@code query.setMaxResults()}, not string concatenation.
 */
public class LimitAppender {

    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\b", Pattern.CASE_INSENSITIVE);

    private LimitAppender() {
        // utility class
    }

    /**
     * Checks whether the SQL already contains a LIMIT clause.
     *
     * @param sql original SQL
     * @return true if LIMIT is present, false otherwise
     */
    public static boolean hasLimit(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }

        // Strip content after ORDER BY (since LIMIT typically follows ORDER BY)
        String upper = sql.toUpperCase().trim();
        int orderByIndex = upper.lastIndexOf(" ORDER BY ");
        if (orderByIndex >= 0) {
            upper = upper.substring(orderByIndex);
        }

        return LIMIT_PATTERN.matcher(upper).find();
    }

}
