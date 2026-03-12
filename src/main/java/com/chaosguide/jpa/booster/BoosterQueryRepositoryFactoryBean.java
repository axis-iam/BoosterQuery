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
package com.chaosguide.jpa.booster;

import com.chaosguide.jpa.booster.cache.BoosterCache;
import com.chaosguide.jpa.booster.config.BoosterQueryConfig;
import com.chaosguide.jpa.booster.repository.support.BoosterQueryRepositoryFactory;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import java.io.Serializable;

/**
 * Custom JPA Repository FactoryBean.
 * <p>
 * Replaces the default {@link JpaRepositoryFactoryBean} with {@link BoosterQueryRepositoryFactory},
 * enabling injection of {@link BoosterQueryConfig} and {@link BoosterCache} from the Spring container
 * into repository instances.
 */
public class BoosterQueryRepositoryFactoryBean<T extends Repository<S, ID>, S, ID extends Serializable>
        extends JpaRepositoryFactoryBean<T, S, ID> {

    private BoosterQueryConfig boosterQueryConfig;
    private BoosterCache boosterCache;

    /**
     * Creates a new factory bean for the given repository interface.
     *
     * @param repositoryInterface the repository interface class to create a proxy for
     */
    public BoosterQueryRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
    }

    /**
     * Injects the {@link BoosterQueryConfig} from the Spring container.
     * <p>
     * Falls back to a default configuration when no bean is present in the context.
     *
     * @param configProvider lazy provider for the rewriter configuration bean
     */
    @Autowired
    public void setBoosterQueryConfig(ObjectProvider<BoosterQueryConfig> configProvider) {
        // Fall back to default config if none is configured in the container
        this.boosterQueryConfig = configProvider.getIfAvailable(BoosterQueryConfig::new);
    }

    /**
     * Injects the optional {@link BoosterCache} from the Spring container.
     * <p>
     * The cache may be {@code null} when caching is not enabled.
     *
     * @param cacheProvider lazy provider for the optional cache bean
     */
    @Autowired
    public void setBoosterCache(ObjectProvider<BoosterCache> cacheProvider) {
        this.boosterCache = cacheProvider.getIfAvailable();
    }

    /**
     * Creates a {@link BoosterQueryRepositoryFactory} that produces enhanced repository
     * implementations with SQL rewriting and caching support.
     *
     * @param entityManager the JPA entity manager for the repository factory
     * @return a new {@link BoosterQueryRepositoryFactory} instance
     */
    @Override
    @NonNull
    protected RepositoryFactorySupport createRepositoryFactory(@NonNull EntityManager entityManager) {
        return new BoosterQueryRepositoryFactory(entityManager, boosterQueryConfig, boosterCache);
    }
}
