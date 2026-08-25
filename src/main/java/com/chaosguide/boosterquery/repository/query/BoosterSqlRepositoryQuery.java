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
package com.chaosguide.boosterquery.repository.query;

import com.chaosguide.boosterquery.annotation.BoosterQuery;
import com.chaosguide.boosterquery.cache.BoosterCache;
import com.chaosguide.boosterquery.config.BoosterQueryConfig;
import com.chaosguide.boosterquery.executor.BoosterQueryExecutor;
import com.chaosguide.boosterquery.support.ParameterBinder;
import com.chaosguide.boosterquery.support.SqlHelper;
import com.chaosguide.boosterquery.support.SqlParameterCastNormalizer;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Modifying;
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
 *   <li>Resolves the execution kind from the return type and {@link Modifying @Modifying}</li>
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
     * Categorizes how an annotated repository method is executed.
     */
    private enum ExecutionKind {
        PAGE,
        LIST,
        SINGLE,
        MODIFY
    }

    private final Method method;
    private final QueryMethod queryMethod;
    private final EntityManager entityManager;
    private final BoosterQuery boosterQuery;
    private final @Nullable Modifying modifyingAnnotation;
    private final BoosterQueryConfig effectiveConfig;
    private final BoosterQueryExecutor executor;
    private final ExecutionKind executionKind;
    private final Class<?> resultType;

    /**
     * Creates a new repository query for the given annotated method.
     * <p>
     * Reads the {@link BoosterQuery @BoosterQuery} annotation from the method, builds an
     * effective {@link BoosterQueryConfig} by merging annotation-level overrides with
     * the global configuration, and determines the execution kind and result type from the
     * method signature.
     *
     * @param method             the repository method annotated with {@code @BoosterQuery}
     * @param metadata           repository metadata (domain type, id type, etc.)
     * @param factory            projection factory for result type resolution
     * @param entityManager      the JPA {@link EntityManager} for query execution
     * @param boosterQueryConfig global SQL rewriting configuration
     * @param boosterCache       optional SQL transformation cache; may be {@code null}
     * @throws IllegalStateException if the repository method has an unsupported execution signature
     */
    public BoosterSqlRepositoryQuery(Method method,
                                     RepositoryMetadata metadata,
                                     ProjectionFactory factory,
                                     EntityManager entityManager,
                                     BoosterQueryConfig boosterQueryConfig,
                                     BoosterCache boosterCache) {
        this.method = method;
        this.entityManager = entityManager;
        this.boosterQuery = method.getAnnotation(BoosterQuery.class);
        this.modifyingAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, Modifying.class);
        this.executionKind = resolveExecutionKind(method, modifyingAnnotation != null);
        this.resultType = resolveResultType(method, boosterQuery);
        validateExecutionSignature(method, executionKind);
        this.queryMethod = new QueryMethod(method, metadata, factory, null);
        this.effectiveConfig = buildEffectiveConfig(boosterQuery, boosterQueryConfig);
        this.executor = new BoosterQueryExecutor(entityManager, effectiveConfig, boosterCache);
    }

    /**
     * Executes the annotated SQL query with the given method parameters.
     * <p>
     * Resolves named parameters, applies sort if present, and dispatches to the
     * appropriate executor method based on the execution kind (page, list, single, or DML).
     *
     * @param parameters the method arguments passed at invocation time
     * @return the query result: {@link Page}, {@link List}, single entity/DTO,
     *         {@link Optional}, count ({@code long}), or affected row count ({@code int})
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object @NonNull [] parameters) {
        MethodArguments methodArguments = MethodArguments.resolve(method, parameters);
        Map<String, Object> namedParameters = methodArguments.namedParameters();

        String sql = SqlParameterCastNormalizer.normalize(boosterQuery.value());
        String sqlWithSort = applySortIfPresent(sql, methodArguments.sort());

        return switch (executionKind) {
            case PAGE -> executePageQuery(sqlWithSort, namedParameters, methodArguments.pageable());
            case LIST -> executor.queryList(sqlWithSort, namedParameters, (Class<Object>) resultType);
            case SINGLE -> executeSingleResultQuery(sqlWithSort, namedParameters);
            case MODIFY -> executeModifyingQuery(sqlWithSort, namedParameters);
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
    private Object executePageQuery(String sql, Map<String, Object> parameters, Pageable pageable) {
        String countQuery = boosterQuery.countQuery();
        if (countQuery != null && !countQuery.isBlank()) {
            return executor.queryPage(pageable, sql, parameters, countQuery, (Class<Object>) resultType);
        }
        return executor.queryPage(pageable, sql, parameters, (Class<Object>) resultType);
    }

    /**
     * Executes a single-result query, wrapping in {@link Optional} if the method declares it.
     */
    @SuppressWarnings("unchecked")
    private Object executeSingleResultQuery(String sql, Map<String, Object> parameters) {
        Class<?> rawReturnType = method.getReturnType();
        boolean returnsOptional = Optional.class.isAssignableFrom(rawReturnType);
        Object result = executor.queryOne(sql, parameters, (Class<Object>) resultType);
        if (returnsOptional) {
            return Optional.ofNullable(result);
        }
        return result;
    }

    /**
     * Executes a modifying query while honoring Spring Data's flush and clear options.
     */
    private Object executeModifyingQuery(String sql, Map<String, Object> parameters) {
        Modifying modifyingMetadata = modifyingAnnotation;
        if (modifyingMetadata == null) {
            throw new IllegalStateException("Missing @Modifying metadata for modifying query: " + method);
        }
        if (modifyingMetadata.flushAutomatically()) {
            entityManager.flush();
        }
        int affectedRows = executor.execute(sql, parameters);
        if (modifyingMetadata.clearAutomatically()) {
            entityManager.clear();
        }
        return affectedRows;
    }

    /**
     * Builds an effective configuration by merging annotation-level overrides
     * ({@code enableRewrite}, {@code enableAutoLimit}, {@code autoLimit})
     * with the global {@link BoosterQueryConfig}.
     */
    private static BoosterQueryConfig buildEffectiveConfig(BoosterQuery boosterQuery,
                                                            BoosterQueryConfig base) {
        BoosterQueryConfig effectiveConfig = base != null ? base.copy() : new BoosterQueryConfig();
        if (boosterQuery.enableRewrite() == BoosterQuery.Toggle.TRUE) {
            effectiveConfig.setEnableSqlRewrite(true);
        } else if (boosterQuery.enableRewrite() == BoosterQuery.Toggle.FALSE) {
            effectiveConfig.setEnableSqlRewrite(false);
        }
        if (boosterQuery.enableAutoLimit() == BoosterQuery.Toggle.TRUE) {
            effectiveConfig.setEnableAutoLimit(true);
        } else if (boosterQuery.enableAutoLimit() == BoosterQuery.Toggle.FALSE) {
            effectiveConfig.setEnableAutoLimit(false);
        }
        if (boosterQuery.autoLimit() > 0) {
            effectiveConfig.setDefaultLimit(boosterQuery.autoLimit());
        }
        return effectiveConfig;
    }

    /**
     * Determines the {@link ExecutionKind} from the method contract.
     * {@link Modifying @Modifying} methods use MODIFY, {@code Page} uses PAGE,
     * {@code List} uses LIST, and all other query return types use SINGLE.
     */
    private static ExecutionKind resolveExecutionKind(Method method, boolean isModifying) {
        if (isModifying) {
            return ExecutionKind.MODIFY;
        }
        Class<?> returnType = method.getReturnType();
        if (Page.class.isAssignableFrom(returnType)) {
            return ExecutionKind.PAGE;
        }
        if (List.class.isAssignableFrom(returnType)) {
            return ExecutionKind.LIST;
        }
        return ExecutionKind.SINGLE;
    }

    /**
     * Validates special method contracts required by the resolved execution kind.
     *
     * @throws IllegalStateException if a page or modifying method has an invalid signature
     */
    private static void validateExecutionSignature(Method method, ExecutionKind executionKind) {
        if (executionKind == ExecutionKind.PAGE && !hasParameterOfType(method, Pageable.class)) {
            throw new IllegalStateException("BoosterQuery method returning Page must declare a Pageable parameter: " + method);
        }

        Class<?> returnType = method.getReturnType();
        if (executionKind == ExecutionKind.MODIFY) {
            boolean hasSupportedReturnType = returnType == int.class
                    || returnType == Integer.class
                    || returnType == void.class
                    || returnType == Void.class;
            if (!hasSupportedReturnType) {
                throw new IllegalStateException(
                        "BoosterQuery @Modifying method must return int, Integer, void, or Void: " + method);
            }
            if (hasParameterOfType(method, Pageable.class) || hasParameterOfType(method, Sort.class)) {
                throw new IllegalStateException(
                        "BoosterQuery @Modifying method must not declare Pageable or Sort parameters: " + method);
            }
        } else if (returnType == void.class || returnType == Void.class) {
            throw new IllegalStateException(
                    "BoosterQuery method returning void or Void must be annotated with @Modifying: " + method);
        }
    }

    private static boolean hasParameterOfType(Method method, Class<?> parameterType) {
        for (Parameter parameter : method.getParameters()) {
            if (parameterType.isAssignableFrom(parameter.getType())) {
                return true;
            }
        }
        return false;
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
     * Resolves the result type for query result mapping from the method signature.
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
     * @return the resolved result type for mapping
     * @throws IllegalStateException if a container return type lacks a generic type parameter
     */
    private static Class<?> resolveResultType(Method method, BoosterQuery boosterQuery) {
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
            Class<?> extracted = resolveSingleTypeArgument(genericReturnType);
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
        //    For DML methods (void/Void), resultType is unused (MODIFY path), so returning it has no side effect.
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
    private static Class<?> resolveSingleTypeArgument(Type genericType) {
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
     * @param namedParameters named parameters extracted from {@code @Param}, Map, or POJO arguments
     */
    private record MethodArguments(@Nullable Pageable pageable,
                                   @Nullable Sort sort,
                                   Map<String, Object> namedParameters) {

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
            List<Integer> queryParameterIndexes = new ArrayList<>();

            for (int i = 0; i < parameters.length; i++) {
                Class<?> type = parameters[i].getType();
                if (Pageable.class.isAssignableFrom(type)) {
                    pageable = values[i];
                } else if (Sort.class.isAssignableFrom(type)) {
                    sort = values[i];
                } else {
                    queryParameterIndexes.add(i);
                }
            }

            Map<String, Object> namedParameters = resolveNamedParameters(method, parameters, values, queryParameterIndexes);
            Pageable pageableValue = pageable instanceof Pageable p ? p : Pageable.unpaged();
            Sort sortValue = sort instanceof Sort s ? s : Sort.unsorted();
            return new MethodArguments(pageableValue, sortValue, namedParameters);
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
         * @param method                the repository method, used for diagnostic context
         * @param parameters            the method's parameter descriptors
         * @param values                the actual argument values
         * @param queryParameterIndexes indexes of parameters that are not Pageable/Sort
         * @return a mutable map of named parameter bindings
         * @throws IllegalStateException if parameter names cannot be resolved
         */
        private static Map<String, Object> resolveNamedParameters(Method method,
                                                                  Parameter[] parameters,
                                                                  Object[] values,
                                                                  List<Integer> queryParameterIndexes) {
            Map<String, Object> namedParameters = new HashMap<>();
            if (queryParameterIndexes.isEmpty()) {
                return namedParameters;
            }

            if (queryParameterIndexes.size() == 1) {
                int idx = queryParameterIndexes.getFirst();
                Object value = values[idx];
                if (value == null) {
                    String parameterName = resolveParameterName(parameters[idx]);
                    if (parameterName != null) {
                        namedParameters.put(parameterName, null);
                    }
                    return namedParameters;
                }
                if (value instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) {
                            namedParameters.put(e.getKey().toString(), e.getValue());
                        }
                    }
                    return namedParameters;
                }
                String parameterName = resolveParameterName(parameters[idx]);
                if (parameterName != null) {
                    namedParameters.put(parameterName, value);
                    return namedParameters;
                }
                if (ClassUtils.isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
                    throw new IllegalStateException(
                            "BoosterQuery method with a single simple parameter must declare @Param or compile with -parameters: " + method);
                }
                return ParameterBinder.toMap(value);
            }

            for (int idx : queryParameterIndexes) {
                Parameter parameter = parameters[idx];
                Object value = values[idx];
                if (value instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) {
                            namedParameters.put(e.getKey().toString(), e.getValue());
                        }
                    }
                    continue;
                }

                String parameterName = resolveParameterName(parameter);
                if (parameterName == null) {
                    throw new IllegalStateException(
                            "BoosterQuery method with multiple parameters must declare @Param or compile with -parameters: " + method);
                }
                namedParameters.put(parameterName, value);
            }

            return namedParameters;
        }

        /**
         * Resolves the parameter name using {@code @Param} annotation first,
         * then falling back to the reflection-based name (requires {@code -parameters} compiler flag).
         * Returns {@code null} if neither source is available.
         *
         * @param parameter the method parameter to inspect
         * @return the resolved parameter name, or {@code null}
         */
        @Nullable
        private static String resolveParameterName(Parameter parameter) {
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
