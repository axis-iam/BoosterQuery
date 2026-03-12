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
package com.chaosguide.jpa.booster.executor;

import com.chaosguide.jpa.booster.cache.BoosterCache;
import com.chaosguide.jpa.booster.config.BoosterQueryConfig;
import com.chaosguide.jpa.booster.rewrite.BoosterSqlRewriter;
import com.chaosguide.jpa.booster.support.JpaResultMapper;
import com.chaosguide.jpa.booster.support.LimitAppender;
import com.chaosguide.jpa.booster.support.MetricsRecorder;
import com.chaosguide.jpa.booster.support.ParameterBinder;
import com.chaosguide.jpa.booster.support.SqlHelper;
import com.chaosguide.jpa.booster.support.SqlSanitizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enhanced SQL query executor with automatic rewriting, auto-limit, and caching.
 * <p>
 * Key design choices:
 * 1. No ThreadLocal; state is passed via local variables, ensuring thread safety by construction.
 * 2. Clear separation of concerns: SQL rewriting -> count query (no LIMIT) -> data query (with LIMIT).
 * 3. Rich result mapping: supports Entity, DTO, Map, and primitive types via JpaResultMapper.
 * 4. Built-in caching for rewritten SQL, count SQL, and sorted SQL.
 */
@SuppressWarnings("SqlSourceToSinkFlow")
public class BoosterQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(BoosterQueryExecutor.class);

    // Micrometer metric name constants
    private static final String METRIC_REWRITE_TOTAL = "booster.query.rewrite.total";
    private static final String METRIC_REWRITE_DURATION = "booster.query.rewrite.duration";
    private static final String METRIC_EXECUTE_TOTAL = "booster.query.execute.total";
    private static final String TAG_RESULT = "result";
    private static final String TAG_TYPE = "type";

    private final EntityManager entityManager;
    private final BoosterQueryConfig config;
    private final BoosterCache cache;

    private final MetricsRecorder metrics;

    /** Creates an executor with default configuration, no cache, and no metrics. */
    public BoosterQueryExecutor(EntityManager entityManager) {
        this(entityManager, new BoosterQueryConfig(), null, null);
    }

    /** Creates an executor with the given configuration, no cache, and no metrics. */
    public BoosterQueryExecutor(EntityManager entityManager, BoosterQueryConfig config) {
        this(entityManager, config, null, null);
    }

    /** Creates an executor with the given configuration and cache, no metrics. */
    public BoosterQueryExecutor(EntityManager entityManager, BoosterQueryConfig config, BoosterCache cache) {
        this(entityManager, config, cache, null);
    }

    /**
     * Full constructor with optional metrics support.
     *
     * @param entityManager JPA EntityManager
     * @param config        SQL rewriting configuration
     * @param cache         optional SQL cache
     * @param metrics       optional metrics recorder; {@code null} defaults to no-op
     */
    public BoosterQueryExecutor(EntityManager entityManager,
                                 BoosterQueryConfig config,
                                 BoosterCache cache,
                                 @Nullable MetricsRecorder metrics) {
        this.entityManager = entityManager;
        this.config = config != null ? config.copy() : new BoosterQueryConfig();
        this.cache = cache;
        this.metrics = metrics != null ? metrics : MetricsRecorder.noOp();
    }

    // ==================== Paginated Queries ====================

    /**
     * Execute a paginated query with automatic SQL rewriting (Object parameter variant).
     * <p>
     * The parameter object is converted to a {@code Map<String, Object>} via {@link ParameterBinder#toMap(Object)},
     * then delegates to {@link #queryPage(Pageable, String, Map, Class)}.
     *
     * @param pageable   pagination information (page number, size, sort); {@code null} is treated as unpaged
     * @param sql        the native SQL query string with named parameters (e.g. {@code :paramName})
     * @param paramObj   a POJO or Map whose properties/entries serve as query parameters
     * @param resultType the target class to map each row to (entity, DTO, Record, Map, or primitive)
     * @param <T>        the result element type
     * @return a {@link Page} containing the mapped results and total count
     */
    public <T> Page<T> queryPage(Pageable pageable, String sql, Object paramObj, Class<T> resultType) {
        return queryPage(pageable, sql, ParameterBinder.toMap(paramObj), resultType);
    }

    /**
     * Execute a paginated query with automatic SQL rewriting.
     * <p>
     * Processing pipeline:
     * <ol>
     *   <li>Rewrite SQL via {@link BoosterSqlRewriter}: conditions depending on null/blank/empty parameters
     *       are removed from WHERE, HAVING, and JOIN ON clauses.</li>
     *   <li>Execute a count query (auto-generated from the rewritten SQL) to determine total rows.
     *       Returns an empty page immediately if total is zero.</li>
     *   <li>Apply sorting and execute the data query with pagination offset/limit.</li>
     *   <li>For unpaged requests, auto-LIMIT is applied when enabled in configuration.</li>
     * </ol>
     * Rewritten SQL, count SQL, and sorted SQL are cached via {@link BoosterCache} when available.
     *
     * @param pageable   pagination information (page number, size, sort); {@code null} is treated as unpaged
     * @param sql        the native SQL query string with named parameters (e.g. {@code :paramName})
     * @param params     a map of parameter names to their values; null-valued entries trigger condition removal
     * @param resultType the target class to map each row to (entity, DTO, Record, Map, or primitive)
     * @param <T>        the result element type
     * @return a {@link Page} containing the mapped results and total count
     */
    public <T> Page<T> queryPage(Pageable pageable, String sql, Map<String, Object> params, Class<T> resultType) {
        incrementCounter(METRIC_EXECUTE_TOTAL, TAG_TYPE, "page");
        Pageable effectivePageable = pageable == null ? Pageable.unpaged() : pageable;
        // 1. Preprocessing
        PreparedQuery prepared = prepareQuery(sql, params);
        String activeSql = prepared.sql();
        Map<String, Object> activeParams = prepared.params();

        // 2. Count query
        long total = 0;
        if (effectivePageable.isPaged()) {
            String countSql = buildCachedCountSql(activeSql);
            Query countQuery = entityManager.createNativeQuery(countSql); // count doesn't need mapping
            ParameterBinder.bind(countQuery, activeParams);
            Object countResult = countQuery.getSingleResult();
            total = (countResult instanceof Number) ? ((Number) countResult).longValue() : 0L;

            if (total == 0) {
                return new PageImpl<>(Collections.emptyList(), effectivePageable, 0);
            }
        }

        // 3. Data query
        String dataSql = applyCachedSort(activeSql, effectivePageable.getSort());
        // Only apply auto-LIMIT for unpaged queries;
        // for paged queries, Hibernate handles limit/offset automatically
        int autoLimitValue = -1;
        if (config.isEnableAutoLimit() && !effectivePageable.isPaged() && !LimitAppender.hasLimit(dataSql)) {
            autoLimitValue = safeAutoLimit();
        }

        List<T> content = executeQueryList(dataSql, activeParams, resultType, effectivePageable, autoLimitValue);

        // Calculate total for unpaged queries
        if (!effectivePageable.isPaged()) {
            total = content.size();
        }

        return new PageImpl<>(content, effectivePageable, total);
    }

    /**
     * Execute a paginated query with automatic SQL rewriting and a custom count SQL.
     * <p>
     * When {@code countSql} is provided (non-null and non-blank), it is rewritten independently
     * from the data SQL using its own {@link BoosterSqlRewriter} pass. When {@code null} or blank,
     * the count SQL is auto-generated from the rewritten data SQL.
     *
     * @param pageable   pagination information; {@code null} is treated as unpaged
     * @param sql        the native SQL query string with named parameters
     * @param params     a map of parameter names to their values; null-valued entries trigger condition removal
     * @param countSql   custom count SQL for pagination; {@code null} or blank to auto-generate
     * @param resultType the target class to map each row to
     * @param <T>        the result element type
     * @return a {@link Page} containing the mapped results and total count
     */
    public <T> Page<T> queryPage(Pageable pageable,
                                 String sql,
                                 Map<String, Object> params,
                                 String countSql,
                                 Class<T> resultType) {
        incrementCounter(METRIC_EXECUTE_TOTAL, TAG_TYPE, "page");
        Pageable effectivePageable = pageable == null ? Pageable.unpaged() : pageable;

        PreparedQuery prepared = prepareQuery(sql, params);
        String activeSql = prepared.sql();
        Map<String, Object> activeParams = prepared.params();

        long total = 0;
        if (effectivePageable.isPaged()) {
            String effectiveCountSql;
            Map<String, Object> effectiveCountParams;
            if (countSql == null || countSql.isBlank()) {
                effectiveCountSql = buildCachedCountSql(activeSql);
                effectiveCountParams = activeParams;
            } else {
                PreparedQuery preparedCount = prepareQuery(countSql, params);
                effectiveCountSql = preparedCount.sql();
                effectiveCountParams = preparedCount.params();
            }

            Query countQueryObj = entityManager.createNativeQuery(effectiveCountSql);
            ParameterBinder.bind(countQueryObj, effectiveCountParams);
            Object countResult = countQueryObj.getSingleResult();
            total = (countResult instanceof Number) ? ((Number) countResult).longValue() : 0L;

            if (total == 0) {
                return new PageImpl<>(Collections.emptyList(), effectivePageable, 0);
            }
        }

        String dataSql = applyCachedSort(activeSql, effectivePageable.getSort());
        int autoLimitValue = -1;
        if (config.isEnableAutoLimit() && !effectivePageable.isPaged() && !LimitAppender.hasLimit(dataSql)) {
            autoLimitValue = safeAutoLimit();
        }

        List<T> content = executeQueryList(dataSql, activeParams, resultType, effectivePageable, autoLimitValue);

        if (!effectivePageable.isPaged()) {
            total = content.size();
        }

        return new PageImpl<>(content, effectivePageable, total);
    }

    // ==================== List Queries ====================

    /**
     * Execute a list query with automatic SQL rewriting (Object parameter variant).
     * <p>
     * The parameter object is converted to a {@code Map<String, Object>} via {@link ParameterBinder#toMap(Object)},
     * then delegates to {@link #queryList(String, Map, Class)}.
     *
     * @param sql        the native SQL query string with named parameters (e.g. {@code :paramName})
     * @param paramObj   a POJO or Map whose properties/entries serve as query parameters
     * @param resultType the target class to map each row to (entity, DTO, Record, Map, or primitive)
     * @param <T>        the result element type
     * @return a list of mapped results, never {@code null}
     */
    public <T> List<T> queryList(String sql, Object paramObj, Class<T> resultType) {
        return queryList(sql, ParameterBinder.toMap(paramObj), resultType);
    }

    /**
     * Execute a list query with automatic SQL rewriting.
     * <p>
     * The SQL is first rewritten via {@link BoosterSqlRewriter} to remove conditions that depend on
     * null/blank/empty parameter values. When auto-LIMIT is enabled and the SQL does not already
     * contain a LIMIT clause, a default limit is applied to prevent unbounded result sets.
     *
     * @param sql        the native SQL query string with named parameters (e.g. {@code :paramName})
     * @param params     a map of parameter names to their values; null-valued entries trigger condition removal
     * @param resultType the target class to map each row to (entity, DTO, Record, Map, or primitive)
     * @param <T>        the result element type
     * @return a list of mapped results, never {@code null}
     */
    public <T> List<T> queryList(String sql, Map<String, Object> params, Class<T> resultType) {
        incrementCounter(METRIC_EXECUTE_TOTAL, TAG_TYPE, "list");
        PreparedQuery prepared = prepareQuery(sql, params);
        String finalSql = prepared.sql();

        boolean applyAutoLimit = config.isEnableAutoLimit() && !LimitAppender.hasLimit(finalSql);
        int autoLimitValue = applyAutoLimit ? safeAutoLimit() : -1;

        return executeQueryList(finalSql, prepared.params(), resultType, null, autoLimitValue);
    }

    // ==================== Single Object Queries ====================

    /**
     * Execute a single-result query with automatic SQL rewriting (Object parameter variant).
     * <p>
     * The parameter object is converted to a {@code Map<String, Object>} via {@link ParameterBinder#toMap(Object)},
     * then delegates to {@link #queryOne(String, Map, Class)}.
     *
     * @param sql        the native SQL query string with named parameters (e.g. {@code :paramName})
     * @param paramObj   a POJO or Map whose properties/entries serve as query parameters
     * @param resultType the target class to map the result row to
     * @param <T>        the result type
     * @return the single result, or {@code null} if no rows are found
     * @throws IncorrectResultSizeDataAccessException if more than one row is returned
     */
    public <T> T queryOne(String sql, Object paramObj, Class<T> resultType) {
        return queryOne(sql, ParameterBinder.toMap(paramObj), resultType);
    }

    /**
     * Execute a single-result query with automatic SQL rewriting.
     * <p>
     * Internally delegates to {@link #queryList(String, Map, Class)} and extracts a single element.
     * Returns {@code null} if the result set is empty. The SQL is rewritten before execution
     * to remove conditions depending on null/blank/empty parameters.
     *
     * @param sql        the native SQL query string with named parameters (e.g. {@code :paramName})
     * @param params     a map of parameter names to their values; null-valued entries trigger condition removal
     * @param resultType the target class to map the result row to
     * @param <T>        the result type
     * @return the single result, or {@code null} if no rows are found
     * @throws IncorrectResultSizeDataAccessException if more than one row is returned
     */
    public <T> T queryOne(String sql, Map<String, Object> params, Class<T> resultType) {
        incrementCounter(METRIC_EXECUTE_TOTAL, TAG_TYPE, "one");
        List<T> list = queryList(sql, params, resultType);
        return getSingleResult(list);
    }

    private <T> T getSingleResult(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        if (list.size() > 1) {
            throw new IncorrectResultSizeDataAccessException("Expected 1 result but found " + list.size(), 1);
        }
        return list.getFirst();
    }

    // ==================== Count Queries ====================

    /**
     * Execute a count query with automatic SQL rewriting (Object parameter variant).
     * <p>
     * The parameter object is converted to a {@code Map<String, Object>} via {@link ParameterBinder#toMap(Object)},
     * then delegates to {@link #count(String, Map)}.
     *
     * @param sql      the native SQL query string (either a SELECT or a pre-built COUNT query)
     * @param paramObj a POJO or Map whose properties/entries serve as query parameters
     * @return the count result; returns {@code 0} if the result is not a number
     */
    public long count(String sql, Object paramObj) {
        return count(sql, ParameterBinder.toMap(paramObj));
    }

    /**
     * Execute a count query with automatic SQL rewriting.
     * <p>
     * The SQL is first rewritten via {@link BoosterSqlRewriter} to remove conditions that depend on
     * null/blank/empty parameter values. If the rewritten SQL is not already a {@code SELECT COUNT ...}
     * statement, it is automatically transformed into one via {@link SqlHelper#buildCountSql(String)}.
     * The generated count SQL is cached via {@link BoosterCache} when available.
     *
     * @param sql    the native SQL query string (either a SELECT or a pre-built COUNT query)
     * @param params a map of parameter names to their values; null-valued entries trigger condition removal
     * @return the count result; returns {@code 0} if the result is not a number
     */
    public long count(String sql, Map<String, Object> params) {
        incrementCounter(METRIC_EXECUTE_TOTAL, TAG_TYPE, "count");
        PreparedQuery prepared = prepareQuery(sql, params);
        String activeSql = prepared.sql();
        String normalized = activeSql == null ? "" : activeSql.trim().toLowerCase();
        
        String countSql;
        if (normalized.startsWith("select count")) {
            countSql = activeSql;
        } else {
            countSql = buildCachedCountSql(activeSql);
        }
        
        Query query = entityManager.createNativeQuery(countSql);
        ParameterBinder.bind(query, prepared.params());
        Object result = query.getSingleResult();
        return result instanceof Number ? ((Number) result).longValue() : 0L;
    }

    // ==================== Update/Delete Execution ====================

    /**
     * Execute a DML statement (INSERT, UPDATE, or DELETE) with automatic SQL rewriting
     * (Object parameter variant).
     * <p>
     * The parameter object is converted to a {@code Map<String, Object>} via {@link ParameterBinder#toMap(Object)},
     * then delegates to {@link #execute(String, Map)}.
     *
     * @param sql      the native SQL DML statement with named parameters (e.g. {@code :paramName})
     * @param paramObj a POJO or Map whose properties/entries serve as query parameters
     * @return the number of rows affected by the statement
     * @throws com.chaosguide.jpa.booster.rewrite.BoosterSqlRewriter.SqlRewriteException
     *         if all WHERE conditions are removed from an UPDATE or DELETE (safety guard)
     */
    public int execute(String sql, Object paramObj) {
        return execute(sql, ParameterBinder.toMap(paramObj));
    }

    /**
     * Execute a DML statement (INSERT, UPDATE, or DELETE) with automatic SQL rewriting.
     * <p>
     * The SQL is first rewritten via {@link BoosterSqlRewriter} to remove conditions that depend on
     * null/blank/empty parameter values. A safety guard prevents execution if all WHERE conditions
     * are removed from an UPDATE or DELETE statement (to avoid full-table modifications).
     *
     * @param sql    the native SQL DML statement with named parameters (e.g. {@code :paramName})
     * @param params a map of parameter names to their values; null-valued entries trigger condition removal
     * @return the number of rows affected by the statement
     * @throws com.chaosguide.jpa.booster.rewrite.BoosterSqlRewriter.SqlRewriteException
     *         if all WHERE conditions are removed from an UPDATE or DELETE (safety guard)
     */
    public int execute(String sql, Map<String, Object> params) {
        incrementCounter(METRIC_EXECUTE_TOTAL, TAG_TYPE, "execute");
        PreparedQuery prepared = prepareQuery(sql, params);
        Query query = entityManager.createNativeQuery(prepared.sql());
        ParameterBinder.bind(query, prepared.params());
        return query.executeUpdate();
    }

    // ==================== Core Processing Logic ====================

    /**
     * Holds the rewritten SQL text and its corresponding active parameters.
     */
    private record PreparedQuery(String sql, Map<String, Object> params) {}

    private record RewriteCacheKey(String sql, Set<String> nullParams) {}
    private record CountCacheKey(String sql) {}
    private record SortCacheKey(String sql, Sort sort) {}

    /**
     * Preprocesses the raw SQL and parameters before execution:
     * <p>
     * - When rewriting is enabled, invokes BoosterSqlRewriter to remove conditions that depend on null parameters.
     * - Returns the rewritten SQL along with the remaining active parameters.
     * - On cache hit, parameters are re-filtered via {@link BoosterSqlRewriter#filterParams(Map, Set, String)}
     *   against the cached SQL, preserving null params that are still referenced (e.g. in {@code :p IS NULL} patterns).
     * - If rewriting fails, logs the error and aborts execution to prevent parameter mismatch issues.
     */
    private PreparedQuery prepareQuery(String sql, Map<String, Object> params) {
        if (sql == null || sql.isBlank()) {
            return new PreparedQuery(sql, params);
        }
        if (!config.isEnableSqlRewrite() || params == null || params.isEmpty()) {
            return new PreparedQuery(sql, params);
        }

        try {
            // 1. Collect null parameters
            Set<String> nullParams = BoosterSqlRewriter.collectNullParams(params);

            // 2. No null parameters found; return as-is
            if (nullParams.isEmpty()) {
                log.debug("SQL rewrite skipped (no null params): {}", SqlSanitizer.sanitize(sql));
                incrementCounter(METRIC_REWRITE_TOTAL, TAG_RESULT, "skip");
                return new PreparedQuery(sql, params);
            }

            // 3. Try to retrieve rewritten SQL from cache
            String rewrittenSql = null;
            if (cache != null) {
                rewrittenSql = cache.get(new RewriteCacheKey(sql, nullParams));
            }

            // 4. Cache miss; perform rewriting
            if (rewrittenSql == null) {
                long startNanos = System.nanoTime();

                // Note: this partially duplicates BoosterSqlRewriter.rewrite logic to allow cache control,
                // but we still delegate the actual rewriting to BoosterSqlRewriter for reusability.
                BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);
                rewrittenSql = result.sql();

                long elapsedNanos = System.nanoTime() - startNanos;
                recordTimer(METRIC_REWRITE_DURATION, elapsedNanos);
                incrementCounter(METRIC_REWRITE_TOTAL, TAG_RESULT, "success");

                log.debug("SQL rewrite complete [MISS]:\n  Original SQL : {}\n  Rewritten SQL: {}\n  Removed params: {}",
                        SqlSanitizer.sanitize(sql), SqlSanitizer.sanitize(rewrittenSql), nullParams);

                if (cache != null) {
                    cache.put(new RewriteCacheKey(sql, nullParams), rewrittenSql);
                }

                return new PreparedQuery(rewrittenSql, result.params());
            }

            // 5. Cache hit; just filter out null parameters (preserving those still referenced in SQL)
            Map<String, Object> activeParams = BoosterSqlRewriter.filterParams(params, nullParams, rewrittenSql);
            incrementCounter(METRIC_REWRITE_TOTAL, TAG_RESULT, "hit");

            log.debug("SQL rewrite complete [HIT]:\n  Original SQL: {}\n  Cached SQL  : {}\n  Removed params: {}",
                    SqlSanitizer.sanitize(sql), SqlSanitizer.sanitize(rewrittenSql), nullParams);

            return new PreparedQuery(rewrittenSql, activeParams);

        } catch (BoosterSqlRewriter.SqlRewriteException e) {
            log.error("SQL rewrite failed, aborting query to prevent errors. SQL (sanitized): {}", SqlSanitizer.sanitize(sql), e);
            throw e;
        }
    }

    private String buildCachedCountSql(String sql) {
        if (cache != null) {
            String cached = cache.get(new CountCacheKey(sql));
            if (cached != null) {
                return cached;
            }
        }

        String countSql = SqlHelper.buildCountSql(sql);

        if (cache != null) {
            cache.put(new CountCacheKey(sql), countSql);
        }

        return countSql;
    }

    private String applyCachedSort(String sql, Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return sql;
        }

        if (cache != null) {
            String cached = cache.get(new SortCacheKey(sql, sort));
            if (cached != null) {
                return cached;
            }
        }

        String sortedSql = SqlHelper.applySort(sql, sort);

        if (cache != null) {
            cache.put(new SortCacheKey(sql, sort), sortedSql);
        }

        return sortedSql;
    }

    /**
     * Safely converts the long-typed defaultLimit to int, clamping to Integer.MAX_VALUE on overflow.
     */
    private int safeAutoLimit() {
        long limit = config.getDefaultLimit();
        return (int) Math.min(limit, Integer.MAX_VALUE);
    }

    // ==================== Helper Methods ====================

    /**
     * Caches whether a result type is a JPA entity, avoiding repeated Metamodel lookups per query.
     */
    private final ClassValue<Boolean> isEntityCache = new ClassValue<>() {
        @Override
        protected Boolean computeValue(@NonNull Class<?> type) {
            try {
                entityManager.getMetamodel().managedType(type);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    };

    /**
     * Executes a query and maps results accordingly (Entity vs DTO/Map/primitive).
     *
     * @param autoLimit when > 0, sets query.setMaxResults() for a database-agnostic auto-LIMIT
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> executeQueryList(String sql, Map<String, Object> params, Class<T> resultType, Pageable pageable, int autoLimit) {
        boolean isEntity = isEntity(resultType);
        Query query;

        if (isEntity) {
            query = entityManager.createNativeQuery(sql, resultType);
        } else {
            query = entityManager.createNativeQuery(sql, Tuple.class);
        }

        ParameterBinder.bind(query, params);

        if (pageable != null && pageable.isPaged()) {
            query.setFirstResult(Math.toIntExact(pageable.getOffset()));
            query.setMaxResults(pageable.getPageSize());
        } else if (autoLimit > 0) {
            query.setMaxResults(autoLimit);
        }

        if (isEntity) {
            return (List<T>) query.getResultList();
        } else {
            List<Tuple> tupleList = (List<Tuple>) query.getResultList();
            return JpaResultMapper.map(tupleList, resultType);
        }
    }

    private boolean isEntity(Class<?> clazz) {
        if (clazz == null) return false;
        return isEntityCache.get(clazz);
    }

    // ==================== Metric Helpers ====================

    private void incrementCounter(String name, String... tags) {
        metrics.incrementCounter(name, tags);
    }

    private void recordTimer(String name, long elapsedNanos) {
        metrics.recordTimer(name, elapsedNanos);
    }
}
