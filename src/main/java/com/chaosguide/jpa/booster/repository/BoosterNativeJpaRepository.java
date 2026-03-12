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

import com.chaosguide.jpa.booster.executor.BoosterNativeExecutor;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Custom base JPA repository implementation.
 * <p>
 * Extends {@link SimpleJpaRepository} to retain standard capabilities while implementing
 * {@link BoosterNativeRepository} to provide enhanced query features. Delegates execution
 * to {@link BoosterNativeExecutor} internally.
 */
public class BoosterNativeJpaRepository<T, ID extends Serializable> extends SimpleJpaRepository<T, ID>
        implements BoosterNativeRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;
    private final BoosterNativeExecutor boosterNativeExecutor;

    /**
     * Creates a new {@link BoosterNativeJpaRepository} for the given {@link JpaEntityInformation}
     * and {@link EntityManager}.
     *
     * @param entityInformation JPA entity metadata, must not be {@literal null}
     * @param entityManager     the JPA {@link EntityManager}, must not be {@literal null}
     */
    public BoosterNativeJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.boosterNativeExecutor = new BoosterNativeExecutor(entityManager);
    }

    // ==================== Paginated Queries ====================

    /** {@inheritDoc} */
    @Override
    public Page<T> nativeQuery(String sql, Map<String, Object> params, Pageable pageable) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterNativeExecutor.queryPage(pageable, sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public Page<T> nativeQuery(String sql, Object paramObj, Pageable pageable) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterNativeExecutor.queryPage(pageable, sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public Page<T> nativeQuery(String sql, Pageable pageable) {
        return nativeQuery(sql, null, pageable);
    }

    // ==================== List Queries ====================

    /** {@inheritDoc} */
    @Override
    public List<T> nativeQueryList(String sql, Map<String, Object> params) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterNativeExecutor.queryList(sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public List<T> nativeQueryList(String sql, Object paramObj) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterNativeExecutor.queryList(sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public List<T> nativeQueryList(String sql) {
        return nativeQueryList(sql, null);
    }

    // ==================== Single Object Queries ====================

    /** {@inheritDoc} */
    @Override
    public T nativeQueryOne(String sql, Map<String, Object> params) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterNativeExecutor.queryOne(sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public T nativeQueryOne(String sql, Object paramObj) {
        Class<T> resultType = entityInformation.getJavaType();
        return boosterNativeExecutor.queryOne(sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public T nativeQueryOne(String sql) {
        return nativeQueryOne(sql, null);
    }

    // ==================== Count Queries ====================

    /** {@inheritDoc} */
    @Override
    public long nativeCount(String sql, Map<String, Object> params) {
        return boosterNativeExecutor.count(sql, params);
    }

    /** {@inheritDoc} */
    @Override
    public long nativeCount(String sql, Object paramObj) {
        return boosterNativeExecutor.count(sql, paramObj);
    }

    /** {@inheritDoc} */
    @Override
    public long nativeCount(String sql) {
        return nativeCount(sql, null);
    }

    // ==================== Modification Operations ====================

    /** {@inheritDoc} */
    @Override
    public int nativeExecute(String sql, Map<String, Object> params) {
        return boosterNativeExecutor.execute(sql, params);
    }

    /** {@inheritDoc} */
    @Override
    public int nativeExecute(String sql, Object paramObj) {
        return boosterNativeExecutor.execute(sql, paramObj);
    }

    /** {@inheritDoc} */
    @Override
    public int nativeExecute(String sql) {
        return boosterNativeExecutor.execute(sql, null);
    }

    // ==================== Custom Return Type Queries (DTO) ====================

    /** {@inheritDoc} */
    @Override
    public <R> List<R> nativeQueryList(String sql, Map<String, Object> params, Class<R> resultType) {
        return boosterNativeExecutor.queryList(sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> List<R> nativeQueryList(String sql, Object paramObj, Class<R> resultType) {
        return boosterNativeExecutor.queryList(sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> Page<R> nativeQuery(String sql, Map<String, Object> params, Pageable pageable, Class<R> resultType) {
        return boosterNativeExecutor.queryPage(pageable, sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> Page<R> nativeQuery(String sql, Object paramObj, Pageable pageable, Class<R> resultType) {
        return boosterNativeExecutor.queryPage(pageable, sql, paramObj, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> R nativeQueryOne(String sql, Map<String, Object> params, Class<R> resultType) {
        return boosterNativeExecutor.queryOne(sql, params, resultType);
    }

    /** {@inheritDoc} */
    @Override
    public <R> R nativeQueryOne(String sql, Object paramObj, Class<R> resultType) {
        return boosterNativeExecutor.queryOne(sql, paramObj, resultType);
    }


}
