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
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.RepositoryQuery;

import java.lang.reflect.Method;

/**
 * Custom {@link QueryLookupStrategy} that intercepts repository methods annotated
 * with {@link BoosterQuery @BoosterQuery}.
 * <p>
 * For annotated methods, a {@link BoosterSqlRepositoryQuery} is created to handle
 * enhanced SQL execution (rewriting, auto-limit, caching). All other methods are
 * delegated to the default Spring Data JPA query lookup strategy.
 *
 * @see BoosterQuery
 * @see BoosterSqlRepositoryQuery
 */
public class BoosterQueryLookupStrategy implements QueryLookupStrategy {

    private final QueryLookupStrategy delegate;
    private final EntityManager entityManager;
    private final BoosterQueryConfig boosterQueryConfig;
    private final BoosterCache boosterCache;

    /**
     * Creates a new lookup strategy that decorates the given delegate.
     *
     * @param delegate              the default Spring Data JPA query lookup strategy to fall back to
     * @param entityManager         the JPA {@link EntityManager} used for query creation
     * @param boosterQueryConfig configuration for SQL rewriting behavior
     * @param boosterCache          optional SQL transformation cache; may be {@code null}
     */
    public BoosterQueryLookupStrategy(QueryLookupStrategy delegate,
                                     EntityManager entityManager,
                                     BoosterQueryConfig boosterQueryConfig,
                                     @Nullable BoosterCache boosterCache) {
        this.delegate = delegate;
        this.entityManager = entityManager;
        this.boosterQueryConfig = boosterQueryConfig;
        this.boosterCache = boosterCache;
    }

    /**
     * Resolves a {@link RepositoryQuery} for the given repository method.
     * <p>
     * If the method is annotated with {@link BoosterQuery @BoosterQuery}, returns a
     * {@link BoosterSqlRepositoryQuery} that handles enhanced SQL execution. Otherwise,
     * delegates to the default Spring Data JPA lookup strategy.
     *
     * @param method       the repository method to resolve
     * @param metadata     repository metadata (domain type, id type, etc.)
     * @param factory      projection factory for result type resolution
     * @param namedQueries named queries registered in the application
     * @return a {@link BoosterSqlRepositoryQuery} for annotated methods, or the delegate result
     */
    @Override
    @NonNull
    public RepositoryQuery resolveQuery(Method method,
                                        @NonNull RepositoryMetadata metadata,
                                        @NonNull ProjectionFactory factory,
                                        @NonNull NamedQueries namedQueries) {
        BoosterQuery boosterQuery = method.getAnnotation(BoosterQuery.class);
        if (boosterQuery == null) {
            return delegate.resolveQuery(method, metadata, factory, namedQueries);
        }
        return new BoosterSqlRepositoryQuery(method, metadata, factory, entityManager, boosterQueryConfig, boosterCache);
    }
}
