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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Enhancedenhanced repository interface.
 * <p>
 * Provides enhanced query methods (booster...) with automatic SQL rewriting
 * (e.g., removing conditions for null parameters, auto-appending LIMIT).
 * <p>
 * Extends {@link BoosterNativeRepository} to also provide basic enhanced query capabilities (native...).
 */
public interface BoosterQueryRepository<T, ID extends Serializable> extends BoosterNativeRepository<T, ID> {

    // ==================== EnhancedPaginated Queries (with SQL Rewriting + Auto LIMIT) ====================

    /**
     * Enhancedpaginated query.
     * <p>
     * Enhanced features:
     * - Automatically removes WHERE conditions for null/empty parameters
     * - Auto-appends LIMIT to prevent oversized queries (if SQL has no LIMIT)
     *
     * @param sql      native query SQL
     * @param params   named parameter Map
     * @param pageable pagination information
     * @return paginated result
     */
    Page<T> boosterQuery(String sql, Map<String, Object> params, Pageable pageable);

    /**
     * Enhancedpaginated query (parameter object version).
     *
     * @param sql      native query SQL
     * @param paramObj parameter object
     * @param pageable pagination information
     * @return paginated result
     */
    Page<T> boosterQuery(String sql, Object paramObj, Pageable pageable);

    /**
     * Enhancedpaginated query without parameters.
     *
     * @param sql      native query SQL
     * @param pageable pagination information
     * @return paginated result
     */
    Page<T> boosterQuery(String sql, Pageable pageable);

    // ==================== EnhancedList Queries ====================

    /**
     * Enhancedlist query (non-paginated).
     *
     * @param sql    native query SQL
     * @param params named parameter Map
     * @return result list
     */
    List<T> boosterQueryList(String sql, Map<String, Object> params);

    /**
     * Enhancedlist query (parameter object version).
     *
     * @param sql      native query SQL
     * @param paramObj parameter object
     * @return result list
     */
    List<T> boosterQueryList(String sql, Object paramObj);

    /**
     * Enhancedlist query without parameters.
     *
     * @param sql native query SQL
     * @return result list
     */
    List<T> boosterQueryList(String sql);

    // ==================== EnhancedSingle Object Queries ====================

    /**
     * Enhancedsingle object query.
     *
     * @param sql    native query SQL
     * @param params named parameter Map
     * @return single object, or null
     */
    T boosterQueryOne(String sql, Map<String, Object> params);

    /**
     * Enhancedsingle object query (parameter object version).
     *
     * @param sql      native query SQL
     * @param paramObj parameter object
     * @return single object, or null
     */
    T boosterQueryOne(String sql, Object paramObj);

    /**
     * Enhancedsingle object query without parameters.
     *
     * @param sql native query SQL
     * @return single object, or null
     */
    T boosterQueryOne(String sql);

    // ==================== EnhancedCustom Type Queries (DTO) ====================

    /**
     * Enhancedlist query (custom return type).
     *
     * @param sql        native query SQL
     * @param params     named parameter Map
     * @param resultType result type class
     * @param <R>        return type generic
     * @return result list
     */
    <R> List<R> boosterQueryList(String sql, Map<String, Object> params, Class<R> resultType);

    /**
     * Enhancedlist query (custom return type, parameter object version).
     *
     * @param sql        native query SQL
     * @param paramObj   parameter object
     * @param resultType result type class
     * @param <R>        return type generic
     * @return result list
     */
    <R> List<R> boosterQueryList(String sql, Object paramObj, Class<R> resultType);

    /**
     * Enhancedpaginated query (custom return type).
     *
     * @param sql        native query SQL
     * @param params     named parameter Map
     * @param pageable   pagination information
     * @param resultType result type class
     * @param <R>        return type generic
     * @return paginated result
     */
    <R> Page<R> boosterQuery(String sql, Map<String, Object> params, Pageable pageable, Class<R> resultType);

    /**
     * Enhancedpaginated query (custom return type, parameter object version).
     *
     * @param sql        native query SQL
     * @param paramObj   parameter object
     * @param pageable   pagination information
     * @param resultType result type class
     * @param <R>        return type generic
     * @return paginated result
     */
    <R> Page<R> boosterQuery(String sql, Object paramObj, Pageable pageable, Class<R> resultType);

    /**
     * Enhancedsingle object query (custom return type).
     *
     * @param sql        native query SQL
     * @param params     named parameter Map
     * @param resultType result type class
     * @param <R>        return type generic
     * @return single object, or null
     */
    <R> R boosterQueryOne(String sql, Map<String, Object> params, Class<R> resultType);

    /**
     * Enhancedsingle object query (custom return type, parameter object version).
     *
     * @param sql        native query SQL
     * @param paramObj   parameter object
     * @param resultType result type class
     * @param <R>        return type generic
     * @return single object, or null
     */
    <R> R boosterQueryOne(String sql, Object paramObj, Class<R> resultType);

    // ==================== EnhancedCount Queries ====================

    /**
     * EnhancedCOUNT query.
     *
     * @param sql    native query SQL
     * @param params named parameter Map
     * @return total record count
     */
    long boosterCount(String sql, Map<String, Object> params);

    /**
     * EnhancedCOUNT query (parameter object version).
     *
     * @param sql      native query SQL
     * @param paramObj parameter object
     * @return total record count
     */
    long boosterCount(String sql, Object paramObj);

    /**
     * EnhancedCOUNT query without parameters.
     *
     * @param sql native query SQL
     * @return total record count
     */
    long boosterCount(String sql);

    // ==================== EnhancedModification Operations ====================

    /**
     * Enhancedmodification execution (supports INSERT/UPDATE/DELETE).
     *
     * @param sql    native SQL
     * @param params named parameter Map
     * @return number of affected rows
     */
    @Modifying
    @Transactional
    int boosterExecute(String sql, Map<String, Object> params);

    /**
     * Enhancedmodification execution (parameter object version).
     *
     * @param sql      native SQL
     * @param paramObj parameter object
     * @return number of affected rows
     */
    @Modifying
    @Transactional
    int boosterExecute(String sql, Object paramObj);

    /**
     * Enhancedmodification execution without parameters.
     *
     * @param sql native SQL
     * @return number of affected rows
     */
    @Modifying
    @Transactional
    int boosterExecute(String sql);
}
