package com.chaosguide.jpa.booster.executor;

import com.chaosguide.jpa.booster.entity.TestUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BoosterNativeExecutorTest {

    private EntityManager entityManager;
    private BoosterNativeExecutor executor;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        Metamodel metamodel = mock(Metamodel.class);
        when(entityManager.getMetamodel()).thenReturn(metamodel);
        ManagedType<?> managedType = mock(ManagedType.class);
        doReturn(managedType).when(metamodel).managedType(TestUser.class);
        when(metamodel.managedType(String.class)).thenThrow(new IllegalArgumentException());

        executor = new BoosterNativeExecutor(entityManager);
    }

    private Query mockEntityQuery(List<?> results) {
        Query q = mock(Query.class);
        when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
        when(q.getResultList()).thenReturn(results);
        return q;
    }

    private Query mockCountQuery(long count) {
        Query q = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(count);
        return q;
    }

    // ==================== queryList ====================

    @Nested
    class QueryList {

        @Test
        void entityType_returnsEntityList() {
            TestUser user = new TestUser("Alice", 25, "alice@test.com");
            mockEntityQuery(List.of(user));

            List<TestUser> result = executor.queryList("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getName()).isEqualTo("Alice");
        }

        @Test
        void emptyResult_returnsEmptyList() {
            mockEntityQuery(Collections.emptyList());

            List<TestUser> result = executor.queryList("SELECT * FROM t_test_user WHERE 1=0", Collections.emptyMap(), TestUser.class);
            assertThat(result).isEmpty();
        }

        @Test
        void withParams_bindsParameters() {
            Query q = mockEntityQuery(Collections.emptyList());
            when(q.getParameters()).thenReturn(Collections.emptySet());

            executor.queryList("SELECT * FROM t_test_user WHERE name = :name",
                    Map.of("name", "Alice"), TestUser.class);

            verify(entityManager).createNativeQuery(anyString(), eq(TestUser.class));
        }
    }

    // ==================== queryOne ====================

    @Nested
    class QueryOne {

        @Test
        void singleResult_returnsEntity() {
            TestUser user = new TestUser("Alice", 25, "alice@test.com");
            mockEntityQuery(List.of(user));

            TestUser result = executor.queryOne("SELECT * FROM t_test_user WHERE id = 1", Collections.emptyMap(), TestUser.class);
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Alice");
        }

        @Test
        void emptyResult_returnsNull() {
            mockEntityQuery(Collections.emptyList());

            TestUser result = executor.queryOne("SELECT * FROM t_test_user WHERE id = 999", Collections.emptyMap(), TestUser.class);
            assertThat(result).isNull();
        }

        @Test
        void multipleResults_throwsException() {
            TestUser u1 = new TestUser("Alice", 25, "a@test.com");
            TestUser u2 = new TestUser("Bob", 30, "b@test.com");
            mockEntityQuery(List.of(u1, u2));

            assertThatThrownBy(() -> executor.queryOne("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class))
                    .isInstanceOf(IncorrectResultSizeDataAccessException.class);
        }
    }

    // ==================== count ====================

    @Nested
    class Count {

        @Test
        void countSql_returnsCount() {
            mockCountQuery(42L);

            long result = executor.count("SELECT count(*) FROM t_test_user", Collections.emptyMap());
            assertThat(result).isEqualTo(42L);
        }

        @Test
        void nonCountSql_autoConvertsToCount() {
            mockCountQuery(10L);

            long result = executor.count("SELECT * FROM t_test_user WHERE age > 18", Collections.emptyMap());
            assertThat(result).isEqualTo(10L);
        }

        @Test
        void zeroCount_returnsZero() {
            mockCountQuery(0L);

            long result = executor.count("SELECT * FROM t_test_user", Collections.emptyMap());
            assertThat(result).isEqualTo(0L);
        }
    }

    // ==================== execute (DML) ====================

    @Nested
    class Execute {

        @Test
        void updateSql_returnsAffectedRows() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(q);
            when(q.getParameters()).thenReturn(Collections.emptySet());
            when(q.executeUpdate()).thenReturn(3);

            int result = executor.execute("UPDATE t_test_user SET age = age + 1 WHERE age > :age", Map.of("age", 18));
            assertThat(result).isEqualTo(3);
        }

        @Test
        void deleteSql_returnsAffectedRows() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(q);
            when(q.getParameters()).thenReturn(Collections.emptySet());
            when(q.executeUpdate()).thenReturn(0);

            int result = executor.execute("DELETE FROM t_test_user WHERE id = 999", Collections.emptyMap());
            assertThat(result).isEqualTo(0);
        }
    }

    // ==================== queryPage ====================

    @Nested
    class QueryPage {

        @Test
        void pagedQuery_setsOffsetAndMaxResults() {
            TestUser user = new TestUser("Alice", 25, "a@test.com");
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(List.of(user));

            Query countQuery = mockCountQuery(1L);

            Page<TestUser> page = executor.queryPage(PageRequest.of(0, 10), "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(1L);
            verify(dataQuery).setFirstResult(0);
            verify(dataQuery).setMaxResults(10);
        }

        @Test
        void unpagedQuery_noOffsetOrMaxResults() {
            TestUser user = new TestUser("Alice", 25, "a@test.com");
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(List.of(user));

            Page<TestUser> page = executor.queryPage(Pageable.unpaged(), "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(1L);
            verify(dataQuery, never()).setFirstResult(anyInt());
            verify(dataQuery, never()).setMaxResults(anyInt());
        }

        @Test
        void nullPageable_treatedAsUnpaged() {
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

            Page<TestUser> page = executor.queryPage(null, "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).isEmpty();
            verify(dataQuery, never()).setFirstResult(anyInt());
        }

        @Test
        void should_returnEmptyPage_when_countIsZero() {
            // Count query returns 0
            Query countQuery = mockCountQuery(0L);

            Page<TestUser> page = executor.queryPage(PageRequest.of(0, 10),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            // Verify empty page returned
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(0L);
            assertThat(page.getNumber()).isEqualTo(0);
            assertThat(page.getSize()).isEqualTo(10);

            // Verify no data query executed (should not create NativeQuery with resultType)
            verify(entityManager, never()).createNativeQuery(anyString(), eq(TestUser.class));
        }

        @Test
        void should_executeCountBeforeData_when_paged() {
            // Use ArrayList to record call order
            List<String> callOrder = new ArrayList<>();

            // Set up count query (total 15, spans multiple pages to avoid PageImpl overriding total with content.size())
            Query countQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenAnswer(invocation -> {
                callOrder.add("count");
                return 15L;
            });

            // Set up data query (returns a full page of 10 rows)
            List<TestUser> users = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                users.add(new TestUser("User" + i, 20 + i, "u" + i + "@test.com"));
            }
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenAnswer(invocation -> {
                callOrder.add("data");
                return users;
            });

            Page<TestUser> page = executor.queryPage(PageRequest.of(0, 10),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).hasSize(10);
            assertThat(page.getTotalElements()).isEqualTo(15L);

            // Verify execution order: count query precedes data query
            assertThat(callOrder).containsExactly("count", "data");
        }

        @Test
        void should_setCorrectOffset_when_secondPage() {
            Query countQuery = mockCountQuery(25L);
            TestUser user = new TestUser("Alice", 25, "a@test.com");
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(List.of(user));

            Page<TestUser> page = executor.queryPage(PageRequest.of(1, 10),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getNumber()).isEqualTo(1);
            assertThat(page.getSize()).isEqualTo(10);
            assertThat(page.getTotalElements()).isEqualTo(25L);
            verify(dataQuery).setFirstResult(10);
            verify(dataQuery).setMaxResults(10);
        }

        @Test
        void should_returnEmptyContent_when_pageBeyondTotal() {
            Query countQuery = mockCountQuery(3L);
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

            Page<TestUser> page = executor.queryPage(PageRequest.of(5, 10),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(3L);
            verify(dataQuery).setFirstResult(50);
            verify(dataQuery).setMaxResults(10);
        }

        @Test
        void should_returnPartialContent_when_lastPage() {
            Query countQuery = mockCountQuery(15L);
            List<TestUser> partialUsers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                partialUsers.add(new TestUser("User" + i, 20 + i, "u" + i + "@test.com"));
            }
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(partialUsers);

            Page<TestUser> page = executor.queryPage(PageRequest.of(1, 10),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).hasSize(5);
            assertThat(page.getTotalElements()).isEqualTo(15L);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }

        @Test
        void should_handleSingleElementTotal() {
            Query countQuery = mockCountQuery(1L);
            TestUser user = new TestUser("Alice", 25, "a@test.com");
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(List.of(user));

            Page<TestUser> page = executor.queryPage(PageRequest.of(0, 10),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(1L);
            assertThat(page.getTotalPages()).isEqualTo(1);
            assertThat(page.isFirst()).isTrue();
            assertThat(page.isLast()).isTrue();
        }

        @Test
        void should_returnEmptyUnpagedPage_when_noResults() {
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

            Page<TestUser> page = executor.queryPage(Pageable.unpaged(),
                    "SELECT * FROM t_test_user WHERE 1=0", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }

        @Test
        void should_handlePageSizeOne() {
            Query countQuery = mockCountQuery(5L);
            TestUser user = new TestUser("Alice", 25, "a@test.com");
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(List.of(user));

            Page<TestUser> page = executor.queryPage(PageRequest.of(2, 1),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(5L);
            assertThat(page.getTotalPages()).isEqualTo(5);
            verify(dataQuery).setFirstResult(2);
            verify(dataQuery).setMaxResults(1);
        }

        // ==================== T-11 queryPage boundary tests ====================

        @Test
        void should_handleLargePageSize() {
            // pageSize near Integer.MAX_VALUE
            Query countQuery = mockCountQuery(100L);
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

            Page<TestUser> page = executor.queryPage(PageRequest.of(0, Integer.MAX_VALUE),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).isEmpty();
            verify(dataQuery).setMaxResults(Integer.MAX_VALUE);
        }

        @Test
        void should_countReturnBigDecimalNumber_convertsCorrectly() {
            // Count query returns BigDecimal instead of Long (some DB driver behavior)
            // Test count method directly to avoid mock conflicts
            Query countQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(new BigDecimal("7"));

            long result = executor.count("SELECT COUNT(*) FROM t_test_user", Collections.emptyMap());

            assertThat(result).isEqualTo(7L);
        }

        @Test
        void should_returnCorrectTotalPages_when_totalExactlyMultipleOfPageSize() {
            Query countQuery = mockCountQuery(30L);
            List<TestUser> users = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                users.add(new TestUser("U" + i, i, "u@t.com"));
            }
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(users);

            Page<TestUser> page = executor.queryPage(PageRequest.of(0, 10),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getTotalPages()).isEqualTo(3);
            assertThat(page.getTotalElements()).isEqualTo(30L);
        }

        @Test
        void should_returnEmptyPage_when_countIsZero_withSort() {
            // count=0 with sort should still return empty page directly
            Query countQuery = mockCountQuery(0L);

            Page<TestUser> page = executor.queryPage(
                    PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("name")),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(0L);
            verify(entityManager, never()).createNativeQuery(anyString(), eq(TestUser.class));
        }

        @Test
        void should_returnEmptyPage_when_pageBeyondTotal_withPageSizeOne() {
            // Edge case: pageSize=1, page 100, only 3 total records
            Query countQuery = mockCountQuery(3L);
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

            Page<TestUser> page = executor.queryPage(PageRequest.of(100, 1),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(3L);
            verify(dataQuery).setFirstResult(100);
            verify(dataQuery).setMaxResults(1);
        }

        @Test
        void should_handleMultiplePagedCallsIndependently() {
            // Multiple paged calls should not interfere with each other
            Query countQuery = mockCountQuery(20L);
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(List.of(new TestUser("A", 1, "a@t.com")));

            Page<TestUser> page1 = executor.queryPage(PageRequest.of(0, 5),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);
            Page<TestUser> page2 = executor.queryPage(PageRequest.of(1, 5),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page1.getNumber()).isEqualTo(0);
            assertThat(page2.getNumber()).isEqualTo(1);
        }

        @Test
        void should_handleUnpaged_when_multipleResults() {
            List<TestUser> users = List.of(
                    new TestUser("A", 1, "a@t.com"),
                    new TestUser("B", 2, "b@t.com"),
                    new TestUser("C", 3, "c@t.com")
            );
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(users);

            Page<TestUser> page = executor.queryPage(Pageable.unpaged(),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getTotalElements()).isEqualTo(3L);
            // unpaged should not execute count query
            verify(entityManager, never()).createNativeQuery(argThat(sql -> sql.toLowerCase().contains("count")));
        }
    }

    // ==================== count null SQL defense ====================

    @Nested
    class CountNullSql {

        @Test
        void should_throwNPE_when_sqlIsNull() {
            assertThatThrownBy(() -> executor.count(null, Collections.emptyMap()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("SQL");
        }
    }

    // ==================== Offset overflow protection ====================

    @Nested
    class OffsetOverflow {

        @Test
        void should_throwArithmeticException_when_offsetOverflowsInt() {
            // page=Integer.MAX_VALUE, size=2 -> offset exceeds int range
            Query countQuery = mockCountQuery(Long.MAX_VALUE);
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

            Pageable hugePageable = PageRequest.of(Integer.MAX_VALUE, 2);

            assertThatThrownBy(() -> executor.queryPage(hugePageable,
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    // ==================== queryList boundary tests ====================

    @Nested
    class QueryListEdgeCases {

        @Test
        void emptyParams_executesSuccessfully() {
            mockEntityQuery(Collections.emptyList());

            List<TestUser> result = executor.queryList(
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            assertThat(result).isEmpty();
        }

        @Test
        void nullParams_executesSuccessfully() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            List<TestUser> result = executor.queryList(
                    "SELECT * FROM t_test_user", (Map<String, Object>) null, TestUser.class);

            assertThat(result).isEmpty();
        }
    }

    // ==================== count boundary tests ====================

    @Nested
    class CountEdgeCases {

        @Test
        void countSql_withWhitespace_detectsCountPrefix() {
            mockCountQuery(5L);

            long result = executor.count("  SELECT COUNT(*) FROM t_test_user  ", Collections.emptyMap());
            assertThat(result).isEqualTo(5L);
        }

        @Test
        void countSql_largeLongValue() {
            mockCountQuery(Long.MAX_VALUE);

            long result = executor.count("SELECT * FROM t_test_user", Collections.emptyMap());
            assertThat(result).isEqualTo(Long.MAX_VALUE);
        }
    }
}

