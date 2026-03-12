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

import com.chaosguide.jpa.booster.cache.BoosterCache;
import com.chaosguide.jpa.booster.config.BoosterQueryConfig;
import com.chaosguide.jpa.booster.executor.BoosterQueryExecutor;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * EnhancedJPA repository implementation.
 * <p>
 * Extends {@link BoosterNativeJpaRepository} to reuse basic enhanced query capabilities (native...),
 * while implementing {@link BoosterQueryRepository} to provide enhanced query features:
 * - SQL rewriting (automatically removes conditions for null parameters)
 * - Auto-appends LIMIT to prevent oversized queries
 * <p>
 * Uses {@link BoosterQueryExecutor} for enhanced query processing.
 */
public class BoosterQueryJpaRepository<T, ID extends Serializable> extends BoosterNativeJpaRepository<T, ID>
        implements BoosterQueryRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;
    private final BoosterQueryExecutor boosterQueryExecutor;

    /**
     * Creates a repository instance without caching support.
     *
     * @param entityInformation JPA entity metadata
     * @param entityManager     JPA EntityManager
     * @param config            SQL rewriting configuration
     */
    public BoosterQueryJpaRepository(JpaEntityInformation<T, ?> entityInformation,
                                    EntityManager entityManager,
                                    BoosterQueryConfig config) {
        this(entityInformation, entityManager, config, null);
    }

    /**
     * Creates a repository instance with optional caching support.
     *
     * @param entityInformation JPA entity metadata
     * @param entityManager     JPA EntityManager
     * @param config            SQL rewriting configuration; defaults applied when {@code null}
     * @param cache             optional SQL cache for rewritten/count/sort queries; {@code null} to disable
     */
    public BoosterQueryJpaRepository(JpaEntityInformation<T, ?> entityInformation,
                                     EntityManager entityManager,
                                     BoosterQueryConfig config,
                                     BoosterCache cache) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        BoosterQueryConfig effectiveConfig = config != null ? config : new BoosterQueryConfig();
        this.boosterQueryExecutor = new BoosterQueryExecutor(entityManager, effectiveConfig, cache);
    }

    // ==================== EnhancedPaginated Queries ====================
    /** {@inheritDoc} */
    @Override
    public Page<T> boosterQuery(String sql, Map<String, Object> params, Pageable pageable) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterQueryExecutor.queryPage(pageable, sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public Page<T> boosterQuery(String sql, Object paramObj, Pageable pageable) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterQueryExecutor.queryPage(pageable, sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public Page<T> boosterQuery(String sql, Pageable pageable) {
        return boosterQuery(sql, null, pageable);
    }

    // ==================== EnhancedCustom Return Type Queries (DTO) ====================

    /** {@inheritDoc} */
    @Override
    public <R> List<R> boosterQueryList(String sql, Map<String, Object> params, Class<R> resultType) {
        return boosterQueryExecutor.queryList(sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> List<R> boosterQueryList(String sql, Object paramObj, Class<R> resultType) {
        return boosterQueryExecutor.queryList(sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> Page<R> boosterQuery(String sql, Map<String, Object> params, Pageable pageable, Class<R> resultType) {
        return boosterQueryExecutor.queryPage(pageable, sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> Page<R> boosterQuery(String sql, Object paramObj, Pageable pageable, Class<R> resultType) {
        return boosterQueryExecutor.queryPage(pageable, sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> R boosterQueryOne(String sql, Map<String, Object> params, Class<R> resultType) {
        return boosterQueryExecutor.queryOne(sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> R boosterQueryOne(String sql, Object paramObj, Class<R> resultType) {
        return boosterQueryExecutor.queryOne(sql, paramObj, resultType);
    }

    // ==================== EnhancedList Queries ====================

    /** {@inheritDoc} */
    @Override
    public List<T> boosterQueryList(String sql, Map<String, Object> params) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterQueryExecutor.queryList(sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public List<T> boosterQueryList(String sql, Object paramObj) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterQueryExecutor.queryList(sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public List<T> boosterQueryList(String sql) {
        return boosterQueryList(sql, null);
    }

    // ==================== EnhancedSingle Object Queries ====================

    /** {@inheritDoc} */
    @Override
    public T boosterQueryOne(String sql, Map<String, Object> params) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterQueryExecutor.queryOne(sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public T boosterQueryOne(String sql, Object paramObj) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterQueryExecutor.queryOne(sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public T boosterQueryOne(String sql) {
        return boosterQueryOne(sql, null);
    }

    // ==================== EnhancedCount Queries ====================

    /** {@inheritDoc} */
    @Override
    public long boosterCount(String sql, Map<String, Object> params) {
        return boosterQueryExecutor.count(sql, params);
    }

    /** {@inheritDoc} */
    @Override
    public long boosterCount(String sql, Object paramObj) {
        return boosterQueryExecutor.count(sql, paramObj);
    }

    /** {@inheritDoc} */
    @Override
    public long boosterCount(String sql) {
        return boosterCount(sql, null);
    }

    // ==================== EnhancedModification Operations ====================

    /** {@inheritDoc} */
    @Override
    public int boosterExecute(String sql, Map<String, Object> params) {
        return boosterQueryExecutor.execute(sql, params);
    }

    /** {@inheritDoc} */
    @Override
    public int boosterExecute(String sql, Object paramObj) {
        return boosterQueryExecutor.execute(sql, paramObj);
    }

    /** {@inheritDoc} */
    @Override
    public int boosterExecute(String sql) {
        return boosterQueryExecutor.execute(sql, null);
    }

}
