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

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL helper utility for parsing and transforming SQL (e.g. count SQL generation).
 * <p>
 * Uses JSqlParser for robust SQL parsing, supporting complex structures (subqueries, UNION, etc.).
 */
public class SqlHelper {

    private static final Logger log = LoggerFactory.getLogger(SqlHelper.class);
    private static final Pattern SORT_PROPERTY_PATTERN = Pattern.compile("[A-Za-z0-9_.]+");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\s+LIMIT\\s+", Pattern.CASE_INSENSITIVE);

    private SqlHelper() {
        // utility class
    }

    /**
     * Builds a count SQL from the original query SQL.
     * <p>
     * First attempts to parse the SELECT statement with JSqlParser and replace the projection
     * columns with COUNT(1) at the AST level. Falls back to the safest subquery wrapper approach
     * when encountering DISTINCT, GROUP BY, UNION, or parse failures.
     *
     * @param sql original SQL
     * @return transformed SELECT COUNT(1) SQL
     */
    public static String buildCountSql(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)) {
                return buildSimpleCountSql(sql);
            }

            // If it's a simple SELECT statement (PlainSelect)
            if (select instanceof PlainSelect plainSelect) {

                // 1. Remove ORDER BY (optimization; count doesn't need ordering)
                plainSelect.setOrderByElements(null);

                // 2. Check for DISTINCT or GROUP BY
                // Directly replacing select items would produce incorrect results with these.
                // The safest approach is to wrap in a subquery: SELECT COUNT(1) FROM (original_sql) tmp
                if (plainSelect.getDistinct() != null ||
                    (plainSelect.getGroupBy() != null && plainSelect.getGroupBy().getGroupByExpressionList() != null)) {
                    return buildSimpleCountSql(sql);
                }

                // 3. Replace SELECT columns with COUNT(1)
                Function countFunc = new Function();
                countFunc.setName("COUNT");
                countFunc.setParameters(new ExpressionList<>(new LongValue(1)));

                SelectItem<?> countItem = new SelectItem<>(countFunc);
                List<SelectItem<?>> selectItems = new ArrayList<>();
                selectItems.add(countItem);

                plainSelect.setSelectItems(selectItems);

                return plainSelect.toString();
            } else if (select instanceof SetOperationList) {
                // For UNION / INTERSECT and other complex statements, wrapping in a subquery is safest
                return buildSimpleCountSql(sql);
            }

            // Fallback for all other cases
            return buildSimpleCountSql(sql);

        } catch (JSQLParserException e) {
            log.warn("JSqlParser failed to parse SQL, falling back to simple wrapper. SQL (sanitized): {}", SqlSanitizer.sanitize(sql), e);
            return buildSimpleCountSql(sql);
        } catch (RuntimeException e) {
            log.error("Unexpected runtime error in SqlHelper, falling back to simple wrapper.", e);
            return buildSimpleCountSql(sql);
        }
    }

    /**
     * Fallback: wraps the query in SELECT COUNT(1) FROM (...).
     * <p>
     * Used when reliable AST-based rewriting is not possible. Strips the outermost ORDER BY
     * for a slight performance gain, then wraps the entire query as a subquery to ensure
     * correct count results.
     */
    private static String buildSimpleCountSql(String sql) {
        // Try to strip the outermost ORDER BY for a slight performance improvement (simple string processing)
        String normalized = sql.trim();
        String lower = normalized.toLowerCase();
        int orderByIndex = lower.lastIndexOf(" order by ");
        
        // Only safe to strip ORDER BY when it's at the end and not inside a subquery (no closing paren after it)
        // Simple heuristic check
        if (orderByIndex > -1) {
            String suffix = normalized.substring(orderByIndex + " order by ".length());
            if (!suffix.contains(")")) {
                 normalized = normalized.substring(0, orderByIndex);
            }
        }
        
        return "select count(1) from (" + normalized + ") tmp_count";
    }

    /**
     * Appends or merges a {@link Sort} specification into the given SQL statement.
     * <p>
     * Uses JSqlParser to parse the SQL and inject {@code ORDER BY} elements at the AST level.
     * For {@link net.sf.jsqlparser.statement.select.PlainSelect PlainSelect} and
     * {@link net.sf.jsqlparser.statement.select.SetOperationList SetOperationList}, the
     * supplied sort orders take precedence; existing ORDER BY elements that do not conflict
     * are preserved. Falls back to string-level concatenation when parsing fails.
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

        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)) {
                return applySortFallback(sql, sort);
            }

            List<OrderByElement> orderByElements = buildOrderByElements(sort);

            if (select instanceof PlainSelect plainSelect) {
                List<OrderByElement> merged = mergeOrderByElements(orderByElements, plainSelect.getOrderByElements());
                plainSelect.setOrderByElements(merged);
                return select.toString();
            }
            if (select instanceof SetOperationList setOperationList) {
                List<OrderByElement> merged = mergeOrderByElements(orderByElements, setOperationList.getOrderByElements());
                setOperationList.setOrderByElements(merged);
                return select.toString();
            }

            return select.toString();
        } catch (JSQLParserException | RuntimeException e) {
            log.debug("JSqlParser sort injection failed, falling back to string concatenation. SQL (sanitized): {}", SqlSanitizer.sanitize(sql), e);
            return applySortFallback(sql, sort);
        }
    }

    private static List<OrderByElement> buildOrderByElements(Sort sort) {
        List<OrderByElement> elements = new ArrayList<>();
        for (Sort.Order order : sort) {
            OrderByElement element = new OrderByElement();
            element.setExpression(new Column(order.getProperty()));
            element.setAsc(order.isAscending());
            element.setAscDescPresent(true);
            elements.add(element);
        }
        return elements;
    }

    private static List<OrderByElement> mergeOrderByElements(List<OrderByElement> primary, List<OrderByElement> secondary) {
        if (secondary == null || secondary.isEmpty()) {
            return primary;
        }
        if (primary == null || primary.isEmpty()) {
            return secondary;
        }

        List<OrderByElement> merged = new ArrayList<>(primary.size() + secondary.size());
        merged.addAll(primary);

        for (OrderByElement existing : secondary) {
            if (existing == null) {
                continue;
            }
            String existingExpr = existing.getExpression() == null ? null : existing.getExpression().toString();
            boolean duplicated = false;
            for (OrderByElement added : primary) {
                String addedExpr = added.getExpression() == null ? null : added.getExpression().toString();
                if (existingExpr != null && existingExpr.equalsIgnoreCase(addedExpr)) {
                    duplicated = true;
                    break;
                }
            }
            if (!duplicated) {
                merged.add(existing);
            }
        }

        return merged;
    }

    private static String applySortFallback(String sql, Sort sort) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        StringBuilder orderByClauseBuilder = new StringBuilder();
        for (Sort.Order order : sort) {
            if (!orderByClauseBuilder.isEmpty()) {
                orderByClauseBuilder.append(", ");
            }
            orderByClauseBuilder.append(order.getProperty()).append(order.isAscending() ? " ASC" : " DESC");
        }
        String orderByClause = orderByClauseBuilder.toString();

        if (orderByClause.isBlank()) {
            return trimmed;
        }

        Matcher matcher = LIMIT_PATTERN.matcher(trimmed);
        int limitIndex = -1;
        while (matcher.find()) {
            limitIndex = matcher.start();
        }

        if (limitIndex > -1) {
            String beforeLimit = trimmed.substring(0, limitIndex).trim();
            String limitPart = trimmed.substring(limitIndex).trim();
            boolean hasOrderBy = beforeLimit.toLowerCase(Locale.ROOT).contains(" order by ");
            return beforeLimit + (hasOrderBy ? ", " : " ORDER BY ") + orderByClause + " " + limitPart;
        }

        boolean hasOrderBy = trimmed.toLowerCase(Locale.ROOT).contains(" order by ");
        return trimmed + (hasOrderBy ? ", " : " ORDER BY ") + orderByClause;
    }
}
