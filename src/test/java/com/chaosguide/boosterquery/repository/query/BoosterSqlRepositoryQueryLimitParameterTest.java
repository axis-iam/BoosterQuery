package com.chaosguide.boosterquery.repository.query;

import com.chaosguide.boosterquery.annotation.BoosterQuery;
import com.chaosguide.boosterquery.config.BoosterQueryConfig;
import com.chaosguide.boosterquery.entity.TestUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.Param;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoosterSqlRepositoryQueryLimitParameterTest {

    @SuppressWarnings("unused")
    interface RecentActivityRepository extends Repository<TestUser, Long> {

        @BoosterQuery(
                """
                        SELECT a.action, gi.primary_email AS actor_email, a.resource_type,
                               a.resource_id, a.created_at
                        FROM audit_logs a
                        LEFT JOIN users u ON u.id = a.actor_id
                        LEFT JOIN global_identities gi ON gi.id = u.global_identity_id
                        ORDER BY a.created_at DESC
                        LIMIT :limit
                        """)
        List<RecentActivityRow> findRecentActivity(@Param("limit") int limit);
    }

    record RecentActivityRow(String action,
                             String actorEmail,
                             String resourceType,
                             Long resourceId,
                             LocalDateTime createdAt) {
    }

    @Test
    void should_bindLimitParameterAndNotApplyAutoLimit_when_boosterQueryListSqlAlreadyHasLimit() throws Exception {
        EntityManager entityManager = mock(EntityManager.class);
        Metamodel metamodel = mock(Metamodel.class);
        when(entityManager.getMetamodel()).thenReturn(metamodel);
        when(metamodel.managedType(RecentActivityRow.class)).thenThrow(new IllegalArgumentException());

        Query dataQuery = mock(Query.class);
        Parameter<Integer> limitParameter = mock(Parameter.class);
        when(limitParameter.getName()).thenReturn("limit");
        when(dataQuery.getParameters()).thenReturn(Set.of(limitParameter));
        when(dataQuery.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(dataQuery);

        Method method = RecentActivityRepository.class.getMethod("findRecentActivity", int.class);
        BoosterSqlRepositoryQuery repositoryQuery = new BoosterSqlRepositoryQuery(
                method,
                new DefaultRepositoryMetadata(RecentActivityRepository.class),
                new SpelAwareProxyProjectionFactory(),
                entityManager,
                new BoosterQueryConfig(),
                null);

        Object result = repositoryQuery.execute(new Object[]{2});

        assertThat(result).isEqualTo(List.of());
        verify(entityManager).createNativeQuery(argThat(sql -> sql.contains("LIMIT :limit")), eq(Tuple.class));
        verify(dataQuery).setParameter("limit", 2);
        verify(dataQuery, never()).setMaxResults(anyInt());
    }
}
