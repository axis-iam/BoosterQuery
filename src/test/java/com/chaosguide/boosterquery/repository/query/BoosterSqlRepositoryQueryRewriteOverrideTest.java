package com.chaosguide.boosterquery.repository.query;

import com.chaosguide.boosterquery.annotation.BoosterQuery;
import com.chaosguide.boosterquery.config.BoosterQueryConfig;
import com.chaosguide.boosterquery.entity.TestUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.Query;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.Param;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoosterSqlRepositoryQueryRewriteOverrideTest {

    @SuppressWarnings("unused")
    interface UserSearchRepository extends Repository<TestUser, Long> {

        @BoosterQuery(
                value = "SELECT * FROM t_test_user WHERE name = :name",
                enableRewrite = BoosterQuery.Toggle.FALSE)
        List<TestUser> findWithRewriteDisabled(@Param("name") String name);

        @BoosterQuery(
                value = "SELECT * FROM t_test_user WHERE name = :name AND age >= :age",
                enableRewrite = BoosterQuery.Toggle.TRUE)
        List<TestUser> findWithRewriteEnabled(@Param("name") String name, @Param("age") Integer age);

        @BoosterQuery("SELECT * FROM global_identities WHERE status = :status::global_identity_status")
        List<TestUser> findByPostgresShorthandCast(@Param("status") String status);
    }

    @Test
    void annotationRewriteFalse_keepsOriginalSqlEvenWithNullParam() throws Exception {
        EntityManager entityManager = entityManagerWithTestUserEntity();
        Query dataQuery = mock(Query.class);
        Parameter<String> nameParameter = mock(Parameter.class);
        when(nameParameter.getName()).thenReturn("name");
        when(dataQuery.getParameters()).thenReturn(Set.of(nameParameter));
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);

        Method method = UserSearchRepository.class.getMethod("findWithRewriteDisabled", String.class);
        BoosterSqlRepositoryQuery repositoryQuery = newRepositoryQuery(method, entityManager, new BoosterQueryConfig());

        repositoryQuery.execute(new Object[]{null});

        verify(entityManager).createNativeQuery("SELECT * FROM t_test_user WHERE name = :name", TestUser.class);
        verify(dataQuery).setParameter("name", null);
    }

    @Test
    void annotationRewriteTrue_overridesGlobalRewriteDisabled() throws Exception {
        EntityManager entityManager = entityManagerWithTestUserEntity();
        Query dataQuery = mock(Query.class);
        Parameter<Integer> ageParameter = mock(Parameter.class);
        when(ageParameter.getName()).thenReturn("age");
        when(dataQuery.getParameters()).thenReturn(Set.of(ageParameter));
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);

        BoosterQueryConfig config = new BoosterQueryConfig();
        config.setEnableSqlRewrite(false);

        Method method = UserSearchRepository.class.getMethod("findWithRewriteEnabled", String.class, Integer.class);
        BoosterSqlRepositoryQuery repositoryQuery = newRepositoryQuery(method, entityManager, config);

        repositoryQuery.execute(new Object[]{null, 18});

        verify(entityManager).createNativeQuery(
                argThat(sql -> !sql.contains(":name") && sql.contains(":age")),
                eq(TestUser.class));
        verify(dataQuery).setParameter("age", 18);
    }

    @Test
    void postgresShorthandParamCast_isNormalizedBeforeCreatingJpaQuery() throws Exception {
        EntityManager entityManager = entityManagerWithTestUserEntity();
        Query dataQuery = mock(Query.class);
        Parameter<String> statusParameter = mock(Parameter.class);
        when(statusParameter.getName()).thenReturn("status");
        when(dataQuery.getParameters()).thenReturn(Set.of(statusParameter));
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);

        Method method = UserSearchRepository.class.getMethod("findByPostgresShorthandCast", String.class);
        BoosterSqlRepositoryQuery repositoryQuery = newRepositoryQuery(method, entityManager, new BoosterQueryConfig());

        repositoryQuery.execute(new Object[]{"ACTIVE"});

        verify(entityManager).createNativeQuery(
                "SELECT * FROM global_identities WHERE status = CAST(:status AS global_identity_status)",
                TestUser.class);
        verify(dataQuery).setParameter("status", "ACTIVE");
    }

    private static BoosterSqlRepositoryQuery newRepositoryQuery(Method method,
                                                               EntityManager entityManager,
                                                               BoosterQueryConfig config) {
        return new BoosterSqlRepositoryQuery(
                method,
                new DefaultRepositoryMetadata(UserSearchRepository.class),
                new SpelAwareProxyProjectionFactory(),
                entityManager,
                config,
                null);
    }

    private static EntityManager entityManagerWithTestUserEntity() {
        EntityManager entityManager = mock(EntityManager.class);
        Metamodel metamodel = mock(Metamodel.class);
        @SuppressWarnings("rawtypes")
        ManagedType managedType = mock(ManagedType.class);
        when(entityManager.getMetamodel()).thenReturn(metamodel);
        doReturn(managedType).when(metamodel).managedType(TestUser.class);
        return entityManager;
    }
}
