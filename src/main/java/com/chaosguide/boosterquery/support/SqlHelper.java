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

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SQL helper utility for parsing and transforming SQL (e.g. count SQL generation).
 * <p>
 * Uses lightweight string-level transformations for count SQL and sorting so common
 * pagination paths do not need to load JSqlParser. Complex count queries are wrapped
 * as subqueries instead of being parsed.
 */
public class SqlHelper {

    private static final Pattern SORT_PROPERTY_PATTERN = Pattern.compile("[A-Za-z0-9_.]+");
    private static final Pattern ORDER_DIRECTION_SUFFIX = Pattern.compile(
            "(?i)\\s+(ASC|DESC)(\\s+NULLS\\s+(FIRST|LAST))?\\s*$");

    private SqlHelper() {
        // utility class
    }

    /**
     * Builds a count SQL from the original query SQL.
     * <p>
     * For simple {@code SELECT ... FROM ...} statements, replaces the projection with
     * {@code COUNT(1)} and strips top-level {@code ORDER BY}/{@code LIMIT}/{@code OFFSET}.
     * Queries with {@code DISTINCT}, {@code GROUP BY}, set operations, CTEs, or invalid shapes
     * use the safest subquery wrapper form.
     *
     * @param sql original SQL
     * @return transformed SELECT COUNT(1) SQL
     */
    public static String buildCountSql(String sql) {
        String normalized = normalizeSql(sql);
        if (normalized.isBlank()) {
            return buildSimpleCountSql(normalized);
        }

        String countSql = tryBuildProjectedCountSql(normalized);
        return countSql != null ? countSql : buildSimpleCountSql(normalized);
    }

    /**
     * Fallback: wraps the query in SELECT COUNT(1) FROM (...).
     * <p>
     * Used when direct projection replacement is not safe. Strips top-level trailing
     * ordering/pagination clauses before wrapping.
     */
    private static String buildSimpleCountSql(String sql) {
        String normalized = SqlScanner.stripTopLevelTailClauses(SqlScanner.stripTrailingSemicolon(sql.trim()));
        return "select count(1) from (" + normalized + ") tmp_count";
    }

    /**
     * Appends or merges a {@link Sort} specification into the given SQL statement.
     * <p>
     * Injects {@code ORDER BY} at the top level. Supplied sort orders take precedence;
     * existing order elements that target the same expression are skipped.
     * <p>
     * Sort property names are validated against an alphanumeric pattern (with underscores and dots)
     * to prevent SQL injection.
     *
     * @param sql  the original SQL query string; returned as-is when {@code null}, blank, or unsorted
     * @param sort the Spring Data {@link Sort} specification to apply
     * @return the SQL string with ORDER BY clause applied, or the original SQL if sort is unsorted
     * @throws IllegalArgumentException if any sort property contains disallowed characters
     */
    public static String applySort(String sql, Sort sort) {
        if (sql == null || sql.isBlank() || sort == null || sort.isUnsorted()) {
            return sql;
        }

        for (Sort.Order order : sort) {
            String property = order.getProperty();
            if (property.isBlank() || !SORT_PROPERTY_PATTERN.matcher(property).matches()) {
                throw new IllegalArgumentException("Invalid sort property format (expected alphanumeric with underscores/dots)");
            }
        }

        return applySortFallback(normalizeSql(sql), sort);
    }

    private static String applySortFallback(String sql, Sort sort) {
        String trimmed = SqlScanner.stripTrailingSemicolon(sql.trim());

        List<String> primaryOrders = buildSortOrderParts(sort);

        if (primaryOrders.isEmpty()) {
            return trimmed;
        }

        int tailIndex = SqlScanner.findFirstTopLevelPaginationClause(trimmed);
        String head = tailIndex >= 0 ? trimmed.substring(0, tailIndex).trim() : trimmed;
        String tail = tailIndex >= 0 ? " " + trimmed.substring(tailIndex).trim() : "";

        int orderByIndex = SqlScanner.findLastTopLevelKeyword(head, "order by");
        if (orderByIndex >= 0) {
            int orderByEnd = SqlScanner.endOfKeywordAt(head, orderByIndex, "order by");
            String beforeOrderBy = head.substring(0, orderByIndex).trim();
            String existingClause = head.substring(orderByEnd).trim();
            List<String> merged = mergeOrderParts(primaryOrders, SqlScanner.splitTopLevelComma(existingClause));
            return beforeOrderBy + " ORDER BY " + String.join(", ", merged) + tail;
        }

        return head + " ORDER BY " + String.join(", ", primaryOrders) + tail;
    }

    private static String tryBuildProjectedCountSql(String sql) {
        if (!SqlScanner.startsWithKeyword(sql, "select")) {
            return null;
        }

        if (SqlScanner.findTopLevelKeyword(sql, "union") >= 0
                || SqlScanner.findTopLevelKeyword(sql, "intersect") >= 0
                || SqlScanner.findTopLevelKeyword(sql, "except") >= 0) {
            return null;
        }

        String withoutTail = SqlScanner.stripTopLevelTailClauses(sql);
        if (SqlScanner.findTopLevelKeyword(withoutTail, "group by") >= 0
                || SqlScanner.findTopLevelKeyword(withoutTail, "having") >= 0) {
            return null;
        }

        int fromIndex = SqlScanner.findTopLevelKeyword(withoutTail, "from");
        if (fromIndex < 0) {
            return null;
        }

        String projection = withoutTail.substring("select".length(), fromIndex).trim();
        if (SqlScanner.startsWithKeyword(projection, "distinct")) {
            return null;
        }

        return "SELECT COUNT(1) " + withoutTail.substring(fromIndex).trim();
    }

    private static List<String> buildSortOrderParts(Sort sort) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order order : sort) {
            parts.add(order.getProperty() + (order.isAscending() ? " ASC" : " DESC"));
        }
        return parts;
    }

    private static List<String> mergeOrderParts(List<String> primary, List<String> existing) {
        List<String> merged = new ArrayList<>(primary.size() + existing.size());
        HashSet<String> seen = new HashSet<>();
        for (String part : primary) {
            merged.add(part);
            seen.add(orderExpressionKey(part));
        }
        for (String part : existing) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && seen.add(orderExpressionKey(trimmed))) {
                merged.add(trimmed);
            }
        }
        return merged;
    }

    private static String orderExpressionKey(String orderPart) {
        return ORDER_DIRECTION_SUFFIX.matcher(orderPart.trim())
                .replaceFirst("")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeSql(String sql) {
        String normalized = SqlParameterCastNormalizer.normalize(sql);
        return normalized == null ? "" : normalized.trim();
    }
}
