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
package com.chaosguide.jpa.booster.repository.support;

import com.chaosguide.jpa.booster.cache.BoosterCache;
import com.chaosguide.jpa.booster.config.BoosterQueryConfig;
import com.chaosguide.jpa.booster.repository.BoosterNativeJpaRepository;
import com.chaosguide.jpa.booster.repository.BoosterQueryJpaRepository;
import com.chaosguide.jpa.booster.repository.BoosterQueryRepository;
import com.chaosguide.jpa.booster.repository.query.BoosterQueryLookupStrategy;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.ValueExpressionDelegate;

import java.io.Serializable;
import java.util.Optional;

/**
 * Custom JPA Repository Factory.
 * <p>
 * Creates {@link BoosterNativeJpaRepository} or {@link BoosterQueryJpaRepository} instances
 * and injects configuration from the Spring container into the repositories.
 */
public class BoosterQueryRepositoryFactory extends JpaRepositoryFactory {

    private final BoosterQueryConfig boosterQueryConfig;
    private final BoosterCache boosterCache;
    private final EntityManager entityManager;

    /**
     * Creates a new factory with the given entity manager and rewriter configuration,
     * without a cache.
     *
     * @param entityManager         the JPA entity manager
     * @param boosterQueryConfig configuration for SQL rewriting
     */
    public BoosterQueryRepositoryFactory(EntityManager entityManager, BoosterQueryConfig boosterQueryConfig) {
        this(entityManager, boosterQueryConfig, null);
    }

    /**
     * Creates a new factory with the given entity manager, rewriter configuration,
     * and optional cache.
     *
     * @param entityManager         the JPA entity manager
     * @param boosterQueryConfig configuration for SQL rewriting
     * @param boosterCache          optional SQL transformation cache; may be {@code null}
     */
    public BoosterQueryRepositoryFactory(EntityManager entityManager,
                                       BoosterQueryConfig boosterQueryConfig,
                                       BoosterCache boosterCache) {
        super(entityManager);
        this.entityManager = entityManager;
        this.boosterQueryConfig = boosterQueryConfig;
        this.boosterCache = boosterCache;
    }

    /**
     * Creates the target repository implementation for the given repository information.
     * <p>
     * Returns a {@link BoosterQueryJpaRepository} when the repository interface extends
     * {@link BoosterQueryRepository}; otherwise returns a {@link BoosterNativeJpaRepository}.
     *
     * @param information   metadata about the repository being created
     * @param entityManager the JPA entity manager
     * @return the concrete repository implementation instance
     */
    @Override
    @NonNull
    protected JpaRepositoryImplementation<?, ?> getTargetRepository(RepositoryInformation information,@NonNull EntityManager entityManager) {
        JpaEntityInformation<?, Serializable> entityInformation = getEntityInformation(information.getDomainType());
        if (BoosterQueryRepository.class.isAssignableFrom(information.getRepositoryInterface())) {
            return new BoosterQueryJpaRepository<>(entityInformation, entityManager, boosterQueryConfig, boosterCache);
        }
        return new BoosterNativeJpaRepository<>(entityInformation, entityManager);
    }

    /**
     * Determines the base class for repository proxy creation.
     * <p>
     * Returns {@link BoosterQueryJpaRepository} for enhanced repositories, or
     * {@link BoosterNativeJpaRepository} for basic ones.
     *
     * @param metadata repository metadata used to inspect the repository interface hierarchy
     * @return the repository base class
     */
    @Override
    @NonNull
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        if (BoosterQueryRepository.class.isAssignableFrom(metadata.getRepositoryInterface())) {
            return BoosterQueryJpaRepository.class;
        }
        return BoosterNativeJpaRepository.class;
    }

    /**
     * Returns a custom {@link QueryLookupStrategy} that wraps the default strategy with
     * {@link BoosterQueryLookupStrategy}, enabling {@link com.chaosguide.jpa.booster.annotation.BoosterQuery @BoosterQuery}
     * annotation support on repository methods.
     *
     * @param key                      the strategy key (CREATE, USE_DECLARED_QUERY, etc.)
     * @param valueExpressionDelegate  delegate for evaluating SpEL expressions in queries
     * @return an {@link Optional} containing the wrapped lookup strategy
     */
    @Override
    @NonNull
    protected Optional<QueryLookupStrategy> getQueryLookupStrategy(QueryLookupStrategy.Key key,
                                                                   @NonNull ValueExpressionDelegate valueExpressionDelegate) {
        Optional<QueryLookupStrategy> delegate = super.getQueryLookupStrategy(key, valueExpressionDelegate);
        return delegate.map(d -> new BoosterQueryLookupStrategy(d, entityManager, boosterQueryConfig, boosterCache));
    }
}
