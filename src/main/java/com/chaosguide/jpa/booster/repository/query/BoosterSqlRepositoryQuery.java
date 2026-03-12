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
package com.chaosguide.jpa.booster.repository.query;

import com.chaosguide.jpa.booster.annotation.BoosterQuery;
import com.chaosguide.jpa.booster.cache.BoosterCache;
import com.chaosguide.jpa.booster.config.BoosterQueryConfig;
import com.chaosguide.jpa.booster.executor.BoosterQueryExecutor;
import com.chaosguide.jpa.booster.support.ParameterBinder;
import com.chaosguide.jpa.booster.support.SqlHelper;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link RepositoryQuery} implementation that executes SQL declared via
 * {@link BoosterQuery @BoosterQuery} annotations.
 * <p>
 * Handles the full lifecycle of a repository query method:
 * <ul>
 *   <li>Resolves the query kind (Page / List / single / count / DML) from the method return type</li>
 *   <li>Extracts and binds named parameters from method arguments ({@code @Param}, Map, POJO)</li>
 *   <li>Delegates execution to {@link BoosterQueryExecutor} which applies SQL rewriting,
 *       auto-limit, and caching as configured</li>
 * </ul>
 *
 * @see BoosterQuery
 * @see BoosterQueryExecutor
 * @see BoosterQueryLookupStrategy
 */
public class BoosterSqlRepositoryQuery implements RepositoryQuery {

    /**
     * Categorizes the query type based on the repository method's return type.
     */
    private enum QueryKind {
        PAGE,
        LIST,
        ONE,
        COUNT,
        MODIFY
    }

    private final Method method;
    private final QueryMethod queryMethod;
    private final EntityManager entityManager;
    private final BoosterQuery boosterQuery;
    private final BoosterQueryConfig effectiveConfig;
    private final BoosterQueryExecutor executor;
    private final QueryKind queryKind;
    private final Class<?> elementType;

    /**
     * Creates a new repository query for the given annotated method.
     * <p>
     * Reads the {@link BoosterQuery @BoosterQuery} annotation from the method, builds an
     * effective {@link BoosterQueryConfig} by merging annotation-level overrides with
     * the global configuration, and determines the query kind and element type from the
     * method signature.
     *
     * @param method                the repository method annotated with {@code @BoosterQuery}
     * @param metadata              repository metadata (domain type, id type, etc.)
     * @param factory               projection factory for result type resolution
     * @param entityManager         the JPA {@link EntityManager} for query execution
     * @param boosterQueryConfig global SQL rewriting configuration
     * @param boosterCache          optional SQL transformation cache; may be {@code null}
     * @throws IllegalStateException if a Page-returning method lacks a {@code Pageable} parameter
     */
    public BoosterSqlRepositoryQuery(Method method,
                                     RepositoryMetadata metadata,
                                     ProjectionFactory factory,
                                     EntityManager entityManager,
                                     BoosterQueryConfig boosterQueryConfig,
                                     BoosterCache boosterCache) {
        this.method = method;
        this.queryMethod = new QueryMethod(method, metadata, factory, null);
        this.entityManager = entityManager;
        this.boosterQuery = method.getAnnotation(BoosterQuery.class);
        this.effectiveConfig = buildEffectiveConfig(boosterQuery, boosterQueryConfig);
        this.executor = new BoosterQueryExecutor(entityManager, effectiveConfig, boosterCache);
        this.queryKind = resolveQueryKind(method);
        this.elementType = resolveElementType(method, boosterQuery);
        validateMethodSignature(method, queryKind);
    }

    /**
     * Executes the annotated SQL query with the given method parameters.
     * <p>
     * Resolves named parameters, applies sort if present, and dispatches to the
     * appropriate executor method based on the query kind (page, list, single, count, or DML).
     *
     * @param parameters the method arguments passed at invocation time
     * @return the query result: {@link Page}, {@link List}, single entity/DTO,
     *         {@link Optional}, count ({@code long}), or affected row count ({@code int})
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object @NonNull [] parameters) {
        MethodArguments args = MethodArguments.resolve(method, parameters);
        Map<String, Object> namedParams = args.namedParams();

        String sql = boosterQuery.value();
        String sqlWithSort = applySortIfPresent(sql, args.sort());

        return switch (queryKind) {
            case PAGE -> executePage(executor, sqlWithSort, namedParams, args.pageable());
            case LIST -> executor.queryList(sqlWithSort, namedParams, (Class<Object>) elementType);
            case ONE -> executeOne(executor, sqlWithSort, namedParams);
            case COUNT -> executor.count(sqlWithSort, namedParams);
            case MODIFY -> executor.execute(sqlWithSort, namedParams);
        };
    }

    /**
     * Returns the {@link QueryMethod} metadata for this repository query.
     *
     * @return the query method descriptor
     */
    @Override
    @NonNull
    public QueryMethod getQueryMethod() {
        return queryMethod;
    }

    /**
     * Executes a paginated query, using the custom count query from the annotation if provided.
     */
    @SuppressWarnings("unchecked")
    private Object executePage(BoosterQueryExecutor executor,
                              String sql,
                              Map<String, Object> params,
                              Pageable pageable) {
        String countQuery = boosterQuery.countQuery();
        if (countQuery != null && !countQuery.isBlank()) {
            return executor.queryPage(pageable, sql, params, countQuery, (Class<Object>) elementType);
        }
        return executor.queryPage(pageable, sql, params, (Class<Object>) elementType);
    }

    /**
     * Executes a single-result query, wrapping in {@link Optional} if the method declares it.
     */
    @SuppressWarnings("unchecked")
    private Object executeOne(BoosterQueryExecutor executor, String sql, Map<String, Object> params) {
        Class<?> rawReturnType = method.getReturnType();
        boolean returnsOptional = Optional.class.isAssignableFrom(rawReturnType);
        Object result = executor.queryOne(sql, params, (Class<Object>) elementType);
        if (returnsOptional) {
            return Optional.ofNullable(result);
        }
        return result;
    }

    /**
     * Builds an effective configuration by merging annotation-level overrides
     * ({@code enableRewrite}, {@code enableAutoLimit}, {@code autoLimit})
     * with the global {@link BoosterQueryConfig}.
     */
    private static BoosterQueryConfig buildEffectiveConfig(BoosterQuery boosterQuery,
                                                              BoosterQueryConfig base) {
        BoosterQueryConfig cfg = base != null ? base.copy() : new BoosterQueryConfig();
        if (boosterQuery.enableRewrite() == BoosterQuery.Toggle.TRUE) {
            cfg.setEnableSqlRewrite(true);
        } else if (boosterQuery.enableRewrite() == BoosterQuery.Toggle.FALSE) {
            cfg.setEnableSqlRewrite(false);
        }
        if (boosterQuery.enableAutoLimit() == BoosterQuery.Toggle.TRUE) {
            cfg.setEnableAutoLimit(true);
        } else if (boosterQuery.enableAutoLimit() == BoosterQuery.Toggle.FALSE) {
            cfg.setEnableAutoLimit(false);
        }
        if (boosterQuery.autoLimit() > 0) {
            cfg.setDefaultLimit(boosterQuery.autoLimit());
        }
        return cfg;
    }

    /**
     * Determines the {@link QueryKind} from the method return type.
     * {@code Page} → PAGE, {@code List} → LIST, {@code Optional} or concrete type → ONE,
     * {@code long/Long} → COUNT, {@code int/Integer/void} → MODIFY.
     */
    private static QueryKind resolveQueryKind(Method method) {
        Class<?> returnType = method.getReturnType();
        if (Page.class.isAssignableFrom(returnType)) {
            return QueryKind.PAGE;
        }
        if (List.class.isAssignableFrom(returnType)) {
            return QueryKind.LIST;
        }
        if (Optional.class.isAssignableFrom(returnType)) {
            return QueryKind.ONE;
        }
        if (returnType == long.class || returnType == Long.class) {
            return QueryKind.COUNT;
        }
        if (returnType == int.class || returnType == Integer.class || returnType == void.class || returnType == Void.class) {
            return QueryKind.MODIFY;
        }
        return QueryKind.ONE;
    }

    /**
     * Validates that a PAGE query method declares a {@link Pageable} parameter.
     *
     * @throws IllegalStateException if a Page-returning method has no Pageable parameter
     */
    private static void validateMethodSignature(Method method, QueryKind kind) {
        if (kind == QueryKind.PAGE) {
            boolean hasPageable = false;
            for (Parameter p : method.getParameters()) {
                if (Pageable.class.isAssignableFrom(p.getType())) {
                    hasPageable = true;
                    break;
                }
            }
            if (!hasPageable) {
                throw new IllegalStateException("BoosterQuery method returning Page must declare a Pageable parameter: " + method);
            }
        }
    }

    /**
     * Appends an ORDER BY clause to the SQL if a non-empty {@link Sort} is provided.
     */
    private static String applySortIfPresent(String sql, @Nullable Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return sql;
        }
        return SqlHelper.applySort(sql, sort);
    }

    /**
     * Resolves the element type for query result mapping from the method signature.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>{@code @BoosterQuery(resultType=...)} — explicit annotation override, highest priority</li>
     *   <li>Container types ({@code Page}, {@code List}, {@code Optional}) — extracts from generic parameter;
     *       raw types are rejected with {@link IllegalStateException}</li>
     *   <li>Primitive wrappers ({@code long/Long} → {@code Long.class}, {@code int/Integer} → {@code Integer.class})</li>
     *   <li>All other concrete types (DTO, Record, interface projection, entity, String, BigDecimal, etc.)
     *       — returned as declared; {@code JpaResultMapper} dispatches mapping strategy by type</li>
     * </ol>
     *
     * @param method       the repository method to inspect
     * @param boosterQuery the {@code @BoosterQuery} annotation on the method
     * @return the resolved element type for result mapping
     * @throws IllegalStateException if a container return type lacks a generic type parameter
     */
    private static Class<?> resolveElementType(Method method, BoosterQuery boosterQuery) {
        // 1. Annotation-specified resultType takes highest priority
        if (boosterQuery.resultType() != void.class) {
            return boosterQuery.resultType();
        }

        Type genericReturnType = method.getGenericReturnType();
        Class<?> returnType = method.getReturnType();

        // 2. Container types: extract element type from generic parameter; raw types are rejected
        if (Page.class.isAssignableFrom(returnType)
                || List.class.isAssignableFrom(returnType)
                || Optional.class.isAssignableFrom(returnType)) {
            Class<?> extracted = extractSingleGenericType(genericReturnType);
            if (extracted != null) {
                return extracted;
            }
            throw new IllegalStateException(
                    "BoosterQuery method returning " + returnType.getSimpleName()
                            + " must declare a generic type parameter (e.g., List<UserDTO>): " + method);
        }

        // 3. Primitive types → corresponding wrapper types
        if (returnType == long.class || returnType == Long.class) {
            return Long.class;
        }
        if (returnType == int.class || returnType == Integer.class) {
            return Integer.class;
        }

        // 4. All other concrete types: DTO / Record / interface projection / entity / simple types (String, BigDecimal, etc.)
        //    Returned as declared; JpaResultMapper dispatches mapping strategy by type.
        //    For DML methods (void/Void), elementType is unused (MODIFY path), so returning it has no side effect.
        return returnType;
    }

    /**
     * Extracts the single type argument from a parameterized type.
     * <p>
     * For example, given {@code List<UserDTO>}, returns {@code UserDTO.class}.
     * Handles nested parameterized types (e.g., {@code Optional<Map<String, Object>>} returns {@code Map.class}).
     * Returns {@code null} for raw types or types with zero or multiple type arguments.
     *
     * @param genericType the generic return type to inspect
     * @return the extracted element class, or {@code null} if extraction fails
     */
    @Nullable
    private static Class<?> extractSingleGenericType(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1) {
                Type arg = args[0];
                if (arg instanceof Class<?> c) {
                    return c;
                }
                if (arg instanceof ParameterizedType inner && inner.getRawType() instanceof Class<?> raw) {
                    return raw;
                }
            }
        }
        return null;
    }

    /**
     * Holds the resolved method arguments, separating special parameters ({@link Pageable}, {@link Sort})
     * from named query parameters.
     *
     * @param pageable the pageable parameter, or {@link Pageable#unpaged()} if absent
     * @param sort     the sort parameter, or {@link Sort#unsorted()} if absent
     * @param namedParams named parameters extracted from {@code @Param}, Map, or POJO arguments
     */
    private record MethodArguments(@Nullable Pageable pageable,
                                   @Nullable Sort sort,
                                   Map<String, Object> namedParams) {

        /**
         * Resolves method arguments by classifying each parameter as special ({@link Pageable}/{@link Sort})
         * or query-bound, then extracting named parameters from the non-special ones.
         *
         * @param method the repository method being invoked
         * @param values the actual argument values passed at invocation time
         * @return the resolved method arguments
         */
        static MethodArguments resolve(Method method, Object[] values) {
            Parameter[] parameters = method.getParameters();
            Object pageable = null;
            Object sort = null;
            List<Integer> nonSpecialIndexes = new ArrayList<>();

            for (int i = 0; i < parameters.length; i++) {
                Class<?> type = parameters[i].getType();
                if (Pageable.class.isAssignableFrom(type)) {
                    pageable = values[i];
                } else if (Sort.class.isAssignableFrom(type)) {
                    sort = values[i];
                } else {
                    nonSpecialIndexes.add(i);
                }
            }

            Map<String, Object> namedParams = resolveNamedParams(method, parameters, values, nonSpecialIndexes);
            Pageable pageableValue = pageable instanceof Pageable p ? p : Pageable.unpaged();
            Sort sortValue = sort instanceof Sort s ? s : Sort.unsorted();
            return new MethodArguments(pageableValue, sortValue, namedParams);
        }

        /**
         * Resolves named query parameters from non-special method arguments.
         * <p>
         * Supports three binding modes:
         * <ul>
         *   <li>Single {@link Map} argument — entries are used directly as named parameters</li>
         *   <li>Single POJO argument (without {@code @Param}) — fields extracted via {@link ParameterBinder#toMap}</li>
         *   <li>Multiple arguments — each must have {@code @Param} or be compiled with {@code -parameters}</li>
         * </ul>
         *
         * @param method            the repository method (for error messages)
         * @param parameters        the method's parameter descriptors
         * @param values            the actual argument values
         * @param nonSpecialIndexes indexes of parameters that are not Pageable/Sort
         * @return a mutable map of named parameter bindings
         * @throws IllegalStateException if parameter names cannot be resolved
         */
        private static Map<String, Object> resolveNamedParams(Method method,
                                                             Parameter[] parameters,
                                                             Object[] values,
                                                             List<Integer> nonSpecialIndexes) {
            Map<String, Object> namedParams = new HashMap<>();
            if (nonSpecialIndexes.isEmpty()) {
                return namedParams;
            }

            if (nonSpecialIndexes.size() == 1) {
                int idx = nonSpecialIndexes.getFirst();
                Object value = values[idx];
                if (value == null) {
                    String paramName = resolveParamName(parameters[idx], method);
                    if (paramName != null) {
                        namedParams.put(paramName, null);
                    }
                    return namedParams;
                }
                if (value instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) {
                            namedParams.put(e.getKey().toString(), e.getValue());
                        }
                    }
                    return namedParams;
                }
                String paramName = resolveParamName(parameters[idx], method);
                if (paramName != null) {
                    namedParams.put(paramName, value);
                    return namedParams;
                }
                if (ClassUtils.isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
                    throw new IllegalStateException(
                            "BoosterQuery method with a single simple parameter must declare @Param or compile with -parameters: " + method);
                }
                return ParameterBinder.toMap(value);
            }

            for (int idx : nonSpecialIndexes) {
                Parameter parameter = parameters[idx];
                Object value = values[idx];
                if (value instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) {
                            namedParams.put(e.getKey().toString(), e.getValue());
                        }
                    }
                    continue;
                }

                String paramName = resolveParamName(parameter, method);
                if (paramName == null) {
                    throw new IllegalStateException(
                            "BoosterQuery method with multiple parameters must declare @Param or compile with -parameters: " + method);
                }
                namedParams.put(paramName, value);
            }

            return namedParams;
        }

        /**
         * Resolves the parameter name using {@code @Param} annotation first,
         * then falling back to the reflection-based name (requires {@code -parameters} compiler flag).
         * Returns {@code null} if neither source is available.
         *
         * @param parameter the method parameter to inspect
         * @param method    the declaring method (unused, reserved for future diagnostics)
         * @return the resolved parameter name, or {@code null}
         */
        @Nullable
        private static String resolveParamName(Parameter parameter, Method method) {
            Param param = parameter.getAnnotation(Param.class);
            if (param != null && !param.value().isBlank()) {
                return param.value();
            }
            if (parameter.isNamePresent()) {
                return parameter.getName();
            }
            return null;
        }
    }
}

