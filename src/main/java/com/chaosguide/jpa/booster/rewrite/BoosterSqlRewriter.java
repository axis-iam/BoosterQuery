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
package com.chaosguide.jpa.booster.rewrite;

import com.chaosguide.jpa.booster.support.SqlSanitizer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.jsqlparser.JSQLParserException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL rewriter for dynamic condition removal.
 * <p>
 * Automatically removes WHERE / ON conditions that depend on null or empty parameter values,
 * eliminating the need for verbose if-branch logic to control SQL fragments in dynamic queries.
 * <p>
 * Rewriting flow:
 * - Collect all parameter names whose values are null or empty
 * - Invoke {@link JSqlParserRewriter} to remove the related conditions at the AST level
 * - Filter the parameter Map, removing entries no longer referenced in the SQL
 */
public class BoosterSqlRewriter {

    private static final Logger log = LoggerFactory.getLogger(BoosterSqlRewriter.class);

    private BoosterSqlRewriter() {
        // utility class
    }

    /**
     * Rewrites SQL: removes invalidated conditions based on parameter values and returns the new SQL with active parameters.
     *
     * @param sql    original SQL
     * @param params parameter Map
     * @return rewrite result containing the rewritten SQL and still-active parameters
     * @throws SqlRewriteException thrown on rewrite failure to prevent further execution
     */
    public static SqlRewriteResult rewrite(String sql, Map<String, Object> params) {
        // 1. Basic validation
        if (sql == null || sql.isBlank() || params == null || params.isEmpty()) {
            return new SqlRewriteResult(sql, params);
        }

        // 2. Collect parameter names to be removed
        Set<String> nullParams = collectNullParams(params);
        if (nullParams.isEmpty()) {
            return new SqlRewriteResult(sql, params);
        }

        try {
            // 3. Perform AST-based rewriting (using JSqlParser exclusively, no complex regex fallback)
            String newSql = JSqlParserRewriter.removeNullConditions(sql, nullParams);

            // 4. Filter parameter Map (only after successful SQL rewriting)
            Map<String, Object> activeParams = filterParams(params, nullParams, newSql);

            if (log.isDebugEnabled()) {
                log.debug("SQL Rewritten. Removed params: {}\n  [Before] {}\n  [After]  {}",
                        nullParams, SqlSanitizer.sanitize(sql), SqlSanitizer.sanitize(newSql));
            }

            return new SqlRewriteResult(newSql, activeParams);

        } catch (JSQLParserException | RuntimeException e) {
            // 4. Error handling: never fall back to "original SQL + filtered params", as that causes unbound parameter errors
            log.error("Failed to rewrite SQL (sanitized): {}. Params: {}", SqlSanitizer.sanitize(sql), nullParams, e);
            throw new SqlRewriteException("Dynamic SQL rewriting failed: " + e.getMessage(), e);
        }
    }

    /**
     * Collects all parameter names whose values are null or empty.
     * <p>
     * A parameter is considered "null or empty" if its value is:
     * <ul>
     *   <li>{@code null}</li>
     *   <li>a blank string (whitespace-only or empty)</li>
     *   <li>an empty collection</li>
     * </ul>
     *
     * @param params the parameter map to inspect
     * @return a set of parameter names with null/empty values; never {@code null}
     */
    public static Set<String> collectNullParams(Map<String, Object> params) {
        Set<String> nullParams = new HashSet<>();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (isNullOrEmpty(entry.getValue())) {
                nullParams.add(entry.getKey());
            }
        }

        return nullParams;
    }

    /**
     * Checks whether a parameter value is null or empty.
     */
    static boolean isNullOrEmpty(Object value) {
        return switch (value) {
            case null -> true;
            case String s -> s.isBlank();
            case Collection<?> c -> c.isEmpty();
            default -> false;
        };
    }

    /**
     * Filters the parameter Map, removing null parameter entries that are no longer referenced in the rewritten SQL.
     * <p>
     * Parameters that are null but still referenced by the rewritten SQL (e.g. in {@code :name IS NULL}
     * patterns or CASE WHEN expressions) are retained with their value normalized to {@code null} to prevent
     * JPA {@code QueryException: Named parameter [xxx] not set}.
     * <p>
     * <b>Null normalization:</b> since {@link #collectNullParams(Map)} treats blank strings ({@code ""},
     * {@code "  "}) and empty collections as "null", these values are normalized to actual {@code null}
     * when retained, ensuring {@code :p IS NULL} evaluates to {@code true} as expected.
     *
     * @param params       original parameter Map
     * @param nullParams   set of parameter names with null/empty values
     * @param rewrittenSql the SQL after rewriting; used to check which params are still referenced
     * @return filtered parameter Map
     */
    public static Map<String, Object> filterParams(Map<String, Object> params,
                                                    Set<String> nullParams,
                                                    String rewrittenSql) {
        Set<String> stillReferenced = extractParamNames(rewrittenSql);
        Map<String, Object> filtered = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!nullParams.contains(key)) {
                // Non-null parameter: keep original value
                filtered.put(key, entry.getValue());
            } else if (stillReferenced.contains(key)) {
                // Null/empty parameter still referenced in SQL: bind as null.
                // collectNullParams treats ""/"  "/emptyList as "null";
                // keeping the original value in :p IS NULL would evaluate to false, breaking semantics.
                filtered.put(key, null);
            }
        }

        return filtered;
    }

    /**
     * Extracts all named parameter names from a SQL string.
     * <p>
     * Uses the regex {@code (?<!:):([A-Za-z_][A-Za-z0-9_]*)} to match JPA named parameters
     * ({@code :paramName}), including underscore-prefixed names like {@code :_status}.
     * A negative lookbehind excludes PostgreSQL type-cast double-colons ({@code ::cast}).
     *
     * @param sql the SQL string to scan
     * @return a set of parameter names (without the colon prefix); never {@code null}
     */
    public static Set<String> extractParamNames(String sql) {
        if (sql == null || sql.isEmpty()) {
            return Set.of();
        }
        // Strip SQL comments first to prevent :paramName in comments from being misidentified
        String stripped = stripSqlComments(sql);
        Set<String> names = new HashSet<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(stripped);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * Strips comments from SQL while preserving string literal content.
     * <p>
     * Supports:
     * <ul>
     *   <li>{@code --} single-line comments (to end of line)</li>
     *   <li>{@code /* ... * /} block comments (multi-line, no nesting)</li>
     * </ul>
     * {@code --} and {@code /*} inside string literals ({@code '...'}) are not stripped.
     *
     * @param sql the original SQL string
     * @return SQL with comments stripped; returns as-is if null or empty
     */
    static String stripSqlComments(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        StringBuilder result = new StringBuilder(sql.length());
        int len = sql.length();
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            // Handle string literals: '...' (including escaped '')
            // Replace literal content with placeholders to prevent :paramName from being mis-extracted
            if (c == '\'') {
                result.append(c);
                i++;
                while (i < len) {
                    char sc = sql.charAt(i);
                    if (sc == '\'' && i + 1 < len && sql.charAt(i + 1) == '\'') {
                        // Escaped single quote '', replace with placeholder
                        result.append("''");
                        i += 2;
                    } else if (sc == '\'') {
                        result.append(sc);
                        i++;
                        break;
                    } else {
                        // Replace literal content with space to avoid :param matching
                        result.append(' ');
                        i++;
                    }
                }
                continue;
            }

            // Handle single-line comments: --
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                // Skip to end of line
                i += 2;
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            // Handle block comments: /* ... */
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < len) {
                    if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
                        i += 2;
                        break;
                    }
                    i++;
                }
                // Handle unclosed block comment: skip to end
                if (i >= len - 1 && !(i >= 2 && sql.charAt(i - 2) == '*' && sql.charAt(i - 1) == '/')) {
                    i = len;
                }
                continue;
            }

            result.append(c);
            i++;
        }

        return result.toString();
    }

    private static final Pattern NAMED_PARAM_PATTERN =
            Pattern.compile("(?<!:):([A-Za-z_][A-Za-z0-9_]*)");

    // ==================== Result Classes ====================

    /**
     * SQL rewrite result.
     */
    public record SqlRewriteResult(String sql, Map<String, Object> params) {
        @Override
        @NonNull
        public String toString() {
            return "SQL: " + SqlSanitizer.sanitize(sql) + "\nParam keys: " + (params == null ? "null" : params.keySet());
        }
    }

    /**
     * SQL rewrite exception.
     */
    public static class SqlRewriteException extends RuntimeException {
        public SqlRewriteException(String message) {
            super(message);
        }

        public SqlRewriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
