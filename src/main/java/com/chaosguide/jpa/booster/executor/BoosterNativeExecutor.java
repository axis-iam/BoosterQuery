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

import com.chaosguide.jpa.booster.support.JpaResultMapper;
import com.chaosguide.jpa.booster.support.ParameterBinder;
import com.chaosguide.jpa.booster.support.SqlHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Unified JPA query executor.
 * <p>
 * Encapsulates native SQL query execution, pagination, count, and parameter binding
 * logic based on EntityManager. Can be used by repositories or directly in services.
 * <p>
 * Highlights:
 * 1. Supports direct mapping to DTO, Map, and primitive types (via JpaResultMapper)
 */
@SuppressWarnings("SqlSourceToSinkFlow")
public class BoosterNativeExecutor {

    private final EntityManager entityManager;

    public BoosterNativeExecutor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // ==================== Paginated Queries ====================

    /**
     * Execute a paginated query with auto-generated count SQL.
     */
    public <T> Page<T> queryPage(Pageable pageable, String sql, Map<String, Object> params, Class<T> resultType) {
        Pageable effectivePageable = pageable == null ? Pageable.unpaged() : pageable;

        // 1. Count query (paged mode only); return empty page early when count=0 to avoid unnecessary data query
        long total = 0;
        if (effectivePageable.isPaged()) {
            String countSql = SqlHelper.buildCountSql(sql);
            Query countQuery = entityManager.createNativeQuery(countSql);
            ParameterBinder.bind(countQuery, params);
            Object countResult = countQuery.getSingleResult();
            total = (countResult instanceof Number) ? ((Number) countResult).longValue() : 0L;

            if (total == 0) {
                return new PageImpl<>(Collections.emptyList(), effectivePageable, 0);
            }
        }

        // 2. Data query
        String dataSql = SqlHelper.applySort(sql, effectivePageable.getSort());
        List<T> content = executeQueryList(dataSql, params, resultType, effectivePageable);

        if (!effectivePageable.isPaged()) {
            total = content.size();
        }

        return new PageImpl<>(content, effectivePageable, total);
    }

    /**
     * Execute a paginated query with auto-generated count SQL (Object paramObj variant).
     */
    public <T> Page<T> queryPage(Pageable pageable, String sql, Object paramObj, Class<T> resultType) {
        return queryPage(pageable, sql, ParameterBinder.toMap(paramObj), resultType);
    }

    // ==================== List Queries ====================

    /**
     * Execute a native SQL query and return the results as a list.
     * <p>
     * If {@code resultType} is a JPA entity, the EntityManager handles mapping directly.
     * Otherwise, results are fetched as {@link Tuple} and mapped via {@link JpaResultMapper}
     * to DTO, Record, Map, or primitive types.
     *
     * @param sql        the native SQL query string with named parameters (e.g. {@code :paramName})
     * @param params     a map of parameter names to their values; {@code null} values are bound as-is
     * @param resultType the target class to map each row to (entity, DTO, Record, Map, or primitive)
     * @param <T>        the result element type
     * @return a list of mapped results, never {@code null}
     */
    public <T> List<T> queryList(String sql, Map<String, Object> params, Class<T> resultType) {
        return executeQueryList(sql, params, resultType, null);
    }

    /**
     * Execute a native SQL query and return the results as a list (Object parameter variant).
     * <p>
     * The parameter object is converted to a {@code Map<String, Object>} via {@link ParameterBinder#toMap(Object)},
     * supporting POJOs (fields extracted by reflection) and Map instances.
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

    // ==================== Single Object Queries ====================

    /**
     * Execute a native SQL query and return a single result.
     * <p>
     * Internally delegates to {@link #queryList(String, Map, Class)} and extracts a single element.
     * Returns {@code null} if the result set is empty.
     *
     * @param sql        the native SQL query string with named parameters (e.g. {@code :paramName})
     * @param params     a map of parameter names to their values
     * @param resultType the target class to map the result row to
     * @param <T>        the result type
     * @return the single result, or {@code null} if no rows are found
     * @throws IncorrectResultSizeDataAccessException if more than one row is returned
     */
    public <T> T queryOne(String sql, Map<String, Object> params, Class<T> resultType) {
        List<T> list = queryList(sql, params, resultType);
        return getSingleResult(list);
    }

    /**
     * Execute a native SQL query and return a single result (Object parameter variant).
     * <p>
     * The parameter object is converted to a {@code Map<String, Object>} via {@link ParameterBinder#toMap(Object)}.
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
     * Execute a count query and return the total number of matching rows.
     * <p>
     * If the given SQL is not already a {@code SELECT COUNT ...} statement, it is automatically
     * transformed into one via {@link SqlHelper#buildCountSql(String)}.
     *
     * @param sql    the native SQL query string (either a SELECT or a pre-built COUNT query)
     * @param params a map of parameter names to their values
     * @return the count result; returns {@code 0} if the result is not a number
     */
    public long count(String sql, Map<String, Object> params) {
        return executeCount(sql, params);
    }

    /**
     * Execute a count query and return the total number of matching rows (Object parameter variant).
     * <p>
     * The parameter object is converted to a {@code Map<String, Object>} via {@link ParameterBinder#toMap(Object)}.
     *
     * @param sql      the native SQL query string (either a SELECT or a pre-built COUNT query)
     * @param paramObj a POJO or Map whose properties/entries serve as query parameters
     * @return the count result; returns {@code 0} if the result is not a number
     */
    public long count(String sql, Object paramObj) {
        return executeCount(sql, ParameterBinder.toMap(paramObj));
    }

    private long executeCount(String sql, Map<String, Object> params) {
        Objects.requireNonNull(sql, "SQL must not be null");
        // If the input is not already a count statement, try to convert it automatically
        String countSql = sql.trim().toLowerCase().startsWith("select count")
                ? sql
                : SqlHelper.buildCountSql(sql);

        Query query = entityManager.createNativeQuery(countSql);
        ParameterBinder.bind(query, params);

        Object result = query.getSingleResult();
        return result instanceof Number ? ((Number) result).longValue() : 0L;
    }

    // ==================== Update Execution ====================

    /**
     * Execute a native SQL DML statement (INSERT, UPDATE, or DELETE) and return the number of affected rows.
     *
     * @param sql    the native SQL DML statement with named parameters (e.g. {@code :paramName})
     * @param params a map of parameter names to their values
     * @return the number of rows affected by the statement
     */
    public int execute(String sql, Map<String, Object> params) {
        return executeUpdate(sql, params);
    }

    /**
     * Execute a native SQL DML statement (INSERT, UPDATE, or DELETE) and return the number of affected rows
     * (Object parameter variant).
     * <p>
     * The parameter object is converted to a {@code Map<String, Object>} via {@link ParameterBinder#toMap(Object)}.
     *
     * @param sql      the native SQL DML statement with named parameters (e.g. {@code :paramName})
     * @param paramObj a POJO or Map whose properties/entries serve as query parameters
     * @return the number of rows affected by the statement
     */
    public int execute(String sql, Object paramObj) {
        return executeUpdate(sql, ParameterBinder.toMap(paramObj));
    }

    private int executeUpdate(String sql, Map<String, Object> params) {
        Query query = entityManager.createNativeQuery(sql);
        ParameterBinder.bind(query, params);
        return query.executeUpdate();
    }

    // ==================== Helper Methods ====================

    /**
     * Execute a query and handle result mapping (Entity vs DTO).
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> executeQueryList(String sql, Map<String, Object> params, Class<T> resultType, Pageable pageable) {
        boolean isEntity = isEntity(resultType);
        Query query;

        if (isEntity) {
            // For entities, let JPA handle the mapping
            query = entityManager.createNativeQuery(sql, resultType);
        } else {
            // For DTOs or primitive types, use Tuple
            query = entityManager.createNativeQuery(sql, Tuple.class);
        }

        ParameterBinder.bind(query, params);

        if (pageable != null && pageable.isPaged()) {
            query.setFirstResult(Math.toIntExact(pageable.getOffset()));
            query.setMaxResults(pageable.getPageSize());
        }

        if (isEntity) {
            // For entity queries, getResultList returns List<T>
            return (List<T>) query.getResultList();
        } else {
            // For Tuple queries, getResultList returns List<Tuple>
            List<Tuple> tupleList = (List<Tuple>) query.getResultList();
            return JpaResultMapper.map(tupleList, resultType);
        }
    }

    /**
     * Caches whether a resultType is a JPA entity to avoid repeated Metamodel lookups per query.
     */
    private final ClassValue<Boolean> isEntityCache = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                entityManager.getMetamodel().managedType(type);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    };

    private boolean isEntity(Class<?> clazz) {
        if (clazz == null) return false;
        return isEntityCache.get(clazz);
    }
}
