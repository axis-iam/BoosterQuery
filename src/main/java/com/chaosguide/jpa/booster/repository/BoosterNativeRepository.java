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
package com.chaosguide.jpa.booster.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Base enhanced repository interface.
 * <p>
 * Extends the default {@link JpaRepository} with enhanced query methods using native SQL.
 * Implemented by {@link BoosterNativeJpaRepository}.
 */
public interface BoosterNativeRepository<T, ID extends Serializable> extends JpaRepository<T, ID> {

    // ==================== Paginated Queries ====================

    /**
     * Paginated query using native SQL with automatic count SQL generation and parameter binding.
     *
     * @param sql      native query SQL, typically containing WHERE / ORDER BY clauses
     * @param params   named parameter Map, corresponding to :name placeholders in the SQL
     * @param pageable Spring Data pagination and sorting information
     * @return paginated result
     */
    Page<T> nativeQuery(String sql, Map<String, Object> params, Pageable pageable);

    /**
     * Paginated query using native SQL (parameter object version).
     * <p>
     * Automatically extracts fields from the parameter object as named parameters via reflection.
     *
     * @param sql       native query SQL
     * @param paramObj  parameter object whose field names correspond to :fieldName in the SQL
     * @param pageable  pagination information
     * @return paginated result
     */
    Page<T> nativeQuery(String sql, Object paramObj, Pageable pageable);

    /**
     * Paginated query without parameters.
     *
     * @param sql      native query SQL
     * @param pageable pagination information
     * @return paginated result
     */
    Page<T> nativeQuery(String sql, Pageable pageable);

    // ==================== List Queries (Non-paginated) ====================

    /**
     * List query (non-paginated), returns all matching results.
     *
     * @param sql    native query SQL
     * @param params named parameter Map
     * @return result list
     */
    List<T> nativeQueryList(String sql, Map<String, Object> params);

    /**
     * List query (parameter object version).
     *
     * @param sql      native query SQL
     * @param paramObj parameter object
     * @return result list
     */
    List<T> nativeQueryList(String sql, Object paramObj);

    /**
     * List query without parameters.
     *
     * @param sql native query SQL
     * @return result list
     */
    List<T> nativeQueryList(String sql);

    // ==================== Single Object Queries ====================

    /**
     * Query a single object (expects a unique result).
     * <p>
     * Returns null if no result is found; throws an exception if more than one result exists.
     *
     * @param sql    native query SQL
     * @param params named parameter Map
     * @return single object, or null
     * @throws org.springframework.dao.IncorrectResultSizeDataAccessException if the result is not unique
     */
    T nativeQueryOne(String sql, Map<String, Object> params);

    /**
     * Single object query (parameter object version).
     *
     * @param sql      native query SQL
     * @param paramObj parameter object
     * @return single object, or null
     */
    T nativeQueryOne(String sql, Object paramObj);

    /**
     * Single object query without parameters.
     *
     * @param sql native query SQL
     * @return single object, or null
     */
    T nativeQueryOne(String sql);

    // ==================== Custom Return Type Queries (DTO) ====================

    /**
     * List query (custom return type).
     * <p>
     * Supports mapping results to any DTO, Map, or primitive type.
     *
     * @param sql        native query SQL
     * @param params     named parameter Map
     * @param resultType result type (DTO.class, Map.class, Integer.class, etc.)
     * @param <R>        return type generic
     * @return result list
     */
    <R> List<R> nativeQueryList(String sql, Map<String, Object> params, Class<R> resultType);

    /**
     * List query (custom return type, parameter object version).
     */
    <R> List<R> nativeQueryList(String sql, Object paramObj, Class<R> resultType);

    /**
     * Paginated query (custom return type).
     */
    <R> Page<R> nativeQuery(String sql, Map<String, Object> params, Pageable pageable, Class<R> resultType);

    /**
     * Paginated query (custom return type, parameter object version).
     */
    <R> Page<R> nativeQuery(String sql, Object paramObj, Pageable pageable, Class<R> resultType);

    /**
     * Single object query (custom return type).
     */
    <R> R nativeQueryOne(String sql, Map<String, Object> params, Class<R> resultType);

    /**
     * Single object query (custom return type, parameter object version).
     */
    <R> R nativeQueryOne(String sql, Object paramObj, Class<R> resultType);

    // ==================== Count Queries ====================

    /**
     * Execute a COUNT query, returning the number of matching records.
     *
     * @param sql    native query SQL (automatically converted to COUNT SQL)
     * @param params named parameter Map
     * @return total record count
     */
    long nativeCount(String sql, Map<String, Object> params);

    /**
     * COUNT query (parameter object version).
     *
     * @param sql      native query SQL
     * @param paramObj parameter object
     * @return total record count
     */
    long nativeCount(String sql, Object paramObj);

    /**
     * COUNT query without parameters.
     *
     * @param sql native query SQL
     * @return total record count
     */
    long nativeCount(String sql);

    // ==================== Modification Operations ====================

    /**
     * Execute a DML statement (INSERT/UPDATE/DELETE) with named parameters.
     *
     * @param sql    native DML SQL
     * @param params named parameter Map
     * @return number of affected rows
     */
    @Modifying
    @Transactional
    int nativeExecute(String sql, Map<String, Object> params);

    /**
     * Execute a DML statement (parameter object version).
     *
     * @param sql      native DML SQL
     * @param paramObj parameter object
     * @return number of affected rows
     */
    @Modifying
    @Transactional
    int nativeExecute(String sql, Object paramObj);

    /**
     * Execute a DML statement without parameters.
     *
     * @param sql native DML SQL
     * @return number of affected rows
     */
    @Modifying
    @Transactional
    int nativeExecute(String sql);


}
