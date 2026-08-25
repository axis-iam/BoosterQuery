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
import com.chaosguide.boosterquery.config.BoosterQueryConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoosterSqlRepositoryQueryExecutionTest {

    @Target(METHOD)
    @Retention(RUNTIME)
    @Modifying(flushAutomatically = true)
    @interface FlushingUpdate {
    }

    @SuppressWarnings("unused")
    interface ScalarRepository extends Repository<Object, Long> {

        @BoosterQuery("SELECT age FROM t_user WHERE id = 1")
        Integer findAge();

        @BoosterQuery("SELECT balance FROM t_account WHERE id = 1")
        Long findBalance();

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @BoosterQuery("UPDATE t_user SET active = false")
        int deactivateUsers();

        @Modifying
        @BoosterQuery("UPDATE t_user SET active = false")
        String invalidModifyingReturnType();

        @Modifying
        @BoosterQuery("UPDATE t_user SET active = false")
        int invalidModifyingSpecialParameter(Pageable pageable);

        @FlushingUpdate
        @BoosterQuery("UPDATE t_user SET active = false")
        int deactivateUsersWithComposedAnnotation();

        @BoosterQuery("SELECT 1")
        void invalidVoidQuery();
    }

    @Test
    void shouldExecuteSingleResultQuery_whenReturnTypeIsInteger() throws Exception {
        Method method = ScalarRepository.class.getMethod("findAge");
        EntityManager entityManager = entityManagerForScalar(42);

        Object result = newRepositoryQuery(method, entityManager).execute(new Object[0]);

        assertThat(result).isEqualTo(42);
        verify(entityManager).createNativeQuery(anyString(), eq(Tuple.class));
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void shouldExecuteSingleResultQuery_whenReturnTypeIsLong() throws Exception {
        Method method = ScalarRepository.class.getMethod("findBalance");
        EntityManager entityManager = entityManagerForScalar(99L);

        Object result = newRepositoryQuery(method, entityManager).execute(new Object[0]);

        assertThat(result).isEqualTo(99L);
        verify(entityManager).createNativeQuery(anyString(), eq(Tuple.class));
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void shouldHonorFlushAndClear_whenMethodIsModifying() throws Exception {
        Method method = ScalarRepository.class.getMethod("deactivateUsers");
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(3);

        Object result = newRepositoryQuery(method, entityManager).execute(new Object[0]);

        assertThat(result).isEqualTo(3);
        InOrder executionOrder = inOrder(entityManager, query);
        executionOrder.verify(entityManager).flush();
        executionOrder.verify(query).executeUpdate();
        executionOrder.verify(entityManager).clear();
    }

    @Test
    void shouldRejectMethod_whenModifyingReturnTypeIsUnsupported() throws Exception {
        Method method = ScalarRepository.class.getMethod("invalidModifyingReturnType");

        assertThatIllegalStateException()
                .isThrownBy(() -> newRepositoryQuery(method, mock(EntityManager.class)))
                .withMessageContaining("@Modifying")
                .withMessageContaining("int, Integer, void, or Void");
    }

    @Test
    void shouldRejectMethod_whenModifyingMethodDeclaresSpecialParameter() throws Exception {
        Method method = ScalarRepository.class.getMethod("invalidModifyingSpecialParameter", Pageable.class);

        assertThatIllegalStateException()
                .isThrownBy(() -> newRepositoryQuery(method, mock(EntityManager.class)))
                .withMessageContaining("@Modifying")
                .withMessageContaining("Pageable or Sort");
    }

    @Test
    void shouldRecognizeComposedModifyingAnnotation() throws Exception {
        Method method = ScalarRepository.class.getMethod("deactivateUsersWithComposedAnnotation");
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(2);

        Object result = newRepositoryQuery(method, entityManager).execute(new Object[0]);

        assertThat(result).isEqualTo(2);
        verify(entityManager).flush();
        verify(query).executeUpdate();
    }

    @Test
    void shouldRejectMethod_whenVoidReturnTypeIsNotModifying() throws Exception {
        Method method = ScalarRepository.class.getMethod("invalidVoidQuery");

        assertThatIllegalStateException()
                .isThrownBy(() -> newRepositoryQuery(method, mock(EntityManager.class)))
                .withMessageContaining("void")
                .withMessageContaining("@Modifying");
    }

    private static BoosterSqlRepositoryQuery newRepositoryQuery(Method method, EntityManager entityManager) {
        return new BoosterSqlRepositoryQuery(
                method,
                new DefaultRepositoryMetadata(ScalarRepository.class),
                new SpelAwareProxyProjectionFactory(),
                entityManager,
                new BoosterQueryConfig(),
                null);
    }

    private static EntityManager entityManagerForScalar(Object value) {
        EntityManager entityManager = mock(EntityManager.class);
        Metamodel metamodel = mock(Metamodel.class);
        doThrow(new IllegalArgumentException()).when(metamodel).managedType(any());
        when(entityManager.getMetamodel()).thenReturn(metamodel);

        Tuple tuple = mock(Tuple.class);
        when(tuple.get(0)).thenReturn(value);

        Query query = mock(Query.class);
        when(query.getResultList()).thenReturn(List.of(tuple));
        when(entityManager.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        return entityManager;
    }
}
