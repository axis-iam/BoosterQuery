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
package com.chaosguide.jpa.booster.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.data.jpa.repository.Query;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares a native SQL query on a repository method with automatic rewriting support.
 * <p>
 * This annotation combines Spring Data JPA's {@link Query @Query(nativeQuery = true)} with
 * BoosterQuery's SQL rewriting, auto-limit, and result mapping capabilities.
 * <p>
 * Parameter binding supports three modes:
 * <ul>
 *   <li>{@link org.springframework.data.repository.query.Param @Param} annotation (highest priority)</li>
 *   <li>Java reflection parameter names (requires {@code -parameters} compiler flag)</li>
 *   <li>Single POJO/Map parameter (auto-extracted by field name)</li>
 * </ul>
 *
 * <pre>{@code
 * @BoosterQuery("SELECT * FROM t_user WHERE name = :name AND age > :age")
 * List<User> findByConditions(String name, Integer age);
 * }</pre>
 *
 * @see org.springframework.data.jpa.repository.Query
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
@Query(nativeQuery = true)
public @interface BoosterQuery {

    /**
     * Tri-state toggle: INHERIT uses global config, TRUE/FALSE explicitly overrides.
     */
    enum Toggle {
        TRUE, FALSE, INHERIT
    }

    /**
     * The native SQL query string. Supports named parameters in {@code :paramName} format.
     *
     * @return the SQL query
     */
    @AliasFor(annotation = Query.class, attribute = "value")
    String value();

    /**
     * Optional custom count query for pagination. If empty, a count query is auto-generated
     * from the main query by wrapping it with {@code SELECT COUNT(1) FROM (...)}.
     *
     * @return the count SQL query, or empty string to auto-generate
     */
    @AliasFor(annotation = Query.class, attribute = "countQuery")
    String countQuery() default "";

    /**
     * Whether to enable SQL rewriting (auto-removal of null/blank/empty parameter conditions).
     * Defaults to {@link Toggle#INHERIT}, which uses the global configuration
     * ({@code booster.query.enable-sql-rewrite}).
     *
     * @return the rewrite toggle
     */
    Toggle enableRewrite() default Toggle.INHERIT;

    /**
     * Whether to enable auto-limit protection (auto-appends LIMIT to prevent unbounded queries).
     * Defaults to {@link Toggle#INHERIT}, which uses the global configuration
     * ({@code booster.query.enable-auto-limit}).
     *
     * @return the auto-limit toggle
     */
    Toggle enableAutoLimit() default Toggle.INHERIT;

    /**
     * Custom limit value for this method. Overrides the global {@code booster.query.default-limit}
     * when set to a positive value. Use {@code -1} (default) to inherit the global setting.
     *
     * @return the limit value, or {@code -1} to inherit
     */
    long autoLimit() default -1L;

    /**
     * The result type for mapping query results. When set, overrides automatic type detection
     * from the method return type. Supports Entity classes, DTOs, Records, and Maps.
     * Defaults to {@code void.class}, which triggers automatic type detection.
     *
     * @return the result mapping class, or {@code void.class} for auto-detection
     */
    Class<?> resultType() default void.class;
}
