package com.chaosguide.jpa.booster.executor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chaosguide.jpa.booster.cache.BoosterCache;
import com.chaosguide.jpa.booster.config.BoosterQueryConfig;
import com.chaosguide.jpa.booster.entity.TestUser;
import com.chaosguide.jpa.booster.rewrite.BoosterSqlRewriter;
import com.chaosguide.jpa.booster.support.MicrometerMetricsRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BoosterQueryExecutorTest {

    private EntityManager entityManager;
    private BoosterQueryConfig config;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        Metamodel metamodel = mock(Metamodel.class);
        when(entityManager.getMetamodel()).thenReturn(metamodel);
        ManagedType<?> managedType = mock(ManagedType.class);
        doReturn(managedType).when(metamodel).managedType(TestUser.class);

        config = new BoosterQueryConfig();
        config.setEnableSqlRewrite(true);
        config.setEnableAutoLimit(true);
        config.setDefaultLimit(1000);
    }

    // ==================== Defensive copy ====================

    @Nested
    class DefensiveCopy {

        @Test
        void configMutationAfterConstruction_doesNotAffectExecutor() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);

            // Mutate original config after construction
            config.setEnableAutoLimit(false);

            // Executor should still use enableAutoLimit=true (defensive copy)
            executor.queryList("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            // If autoLimit is in effect, setMaxResults should be called
            verify(q).setMaxResults(1000);
        }
    }

    // ==================== AutoLimit via setMaxResults ====================

    @Nested
    class AutoLimit {

        @Test
        void queryList_autoLimitEnabled_setsMaxResults() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);
            executor.queryList("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            verify(q).setMaxResults(1000);
        }

        @Test
        void queryList_sqlAlreadyHasLimit_doesNotSetMaxResults() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);
            executor.queryList("SELECT * FROM t_test_user LIMIT 50", Collections.emptyMap(), TestUser.class);

            verify(q, never()).setMaxResults(anyInt());
        }

        @Test
        void queryList_autoLimitDisabled_doesNotSetMaxResults() {
            config.setEnableAutoLimit(false);
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);
            executor.queryList("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            verify(q, never()).setMaxResults(anyInt());
        }
    }

    // ==================== long-to-int overflow protection ====================

    @Nested
    class SafeAutoLimit {

        @Test
        void defaultLimit_exceedsIntMax_clampedToIntMax() {
            config.setDefaultLimit(Long.MAX_VALUE);
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);
            executor.queryList("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            verify(q).setMaxResults(Integer.MAX_VALUE);
        }
    }

    // ==================== SQL rewriting ====================

    @Nested
    class SqlRewrite {

        @Test
        void nullParam_conditionRemoved() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            config.setEnableAutoLimit(false);
            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);

            Map<String, Object> params = new HashMap<>();
            params.put("name", "Alice");
            params.put("age", null);

            executor.queryList("SELECT * FROM t_test_user WHERE name = :name AND age = :age", params, TestUser.class);

            // Verify the executed SQL does not contain :age
            verify(entityManager).createNativeQuery(argThat(sql -> !sql.contains(":age") && sql.contains(":name")), eq(TestUser.class));
        }

        @Test
        void rewriteDisabled_keepsOriginalSql() {
            config.setEnableSqlRewrite(false);
            config.setEnableAutoLimit(false);
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);

            Map<String, Object> params = new HashMap<>();
            params.put("name", null);

            String sql = "SELECT * FROM t_test_user WHERE name = :name";
            executor.queryList(sql, params, TestUser.class);

            verify(entityManager).createNativeQuery(sql, TestUser.class);
        }

        @Test
        void allParamsNonNull_noRewrite() {
            config.setEnableAutoLimit(false);
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);

            String sql = "SELECT * FROM t_test_user WHERE name = :name";
            executor.queryList(sql, Map.of("name", "Alice"), TestUser.class);

            verify(entityManager).createNativeQuery(sql, TestUser.class);
        }
    }

    // ==================== Exception paths ====================

    @Nested
    class ExceptionPaths {

        @Test
        void queryOne_multipleResults_throwsException() {
            config.setEnableAutoLimit(false);
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            TestUser u1 = new TestUser("A", 1, "a@t.com");
            TestUser u2 = new TestUser("B", 2, "b@t.com");
            when(q.getResultList()).thenReturn(List.of(u1, u2));

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);

            assertThatThrownBy(() -> executor.queryOne("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class))
                    .isInstanceOf(IncorrectResultSizeDataAccessException.class);
        }

        @Test
        void queryOne_emptyResult_returnsNull() {
            config.setEnableAutoLimit(false);
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);

            TestUser result = executor.queryOne("SELECT * FROM t_test_user WHERE id = 999", Collections.emptyMap(), TestUser.class);
            assertThat(result).isNull();
        }
    }

    // ==================== Rewrite logging ====================

    @Nested
    class RewriteLogging {

        private Logger executorLogger;
        private ListAppender<ILoggingEvent> listAppender;

        @BeforeEach
        void setUpLogger() {
            executorLogger = (Logger) LoggerFactory.getLogger(BoosterQueryExecutor.class);
            listAppender = new ListAppender<>();
            listAppender.start();
            executorLogger.addAppender(listAppender);
            executorLogger.setLevel(Level.DEBUG);
        }

        @AfterEach
        void tearDownLogger() {
            executorLogger.detachAppender(listAppender);
        }

        private String capturedMessages() {
            return listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
        }

        private BoosterQueryExecutor buildExecutor() {
            config.setEnableAutoLimit(false);
            return new BoosterQueryExecutor(entityManager, config, null);
        }

        @Test
        void should_logNoRewriteNeeded_when_noNullParams() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            buildExecutor().queryList(
                    "SELECT * FROM t_test_user WHERE name = :name",
                    Map.of("name", "Alice"), TestUser.class);

            assertThat(capturedMessages()).contains("rewrite skipped");
        }

        @Test
        void should_logMiss_when_nullParamAndCacheMiss() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            Map<String, Object> params = new HashMap<>();
            params.put("name", "Alice");
            params.put("age", null);

            buildExecutor().queryList(
                    "SELECT * FROM t_test_user WHERE name = :name AND age = :age",
                    params, TestUser.class);

            String msgs = capturedMessages();
            assertThat(msgs).contains("[MISS]");
            assertThat(msgs).contains("age");   // Removed param name appears in log
        }

        @Test
        void should_logHit_when_nullParamAndCacheHit() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterCache cache = mock(BoosterCache.class);
            when(cache.get(any())).thenReturn("SELECT * FROM t_test_user WHERE name = :name");

            config.setEnableAutoLimit(false);
            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, cache);

            Map<String, Object> params = new HashMap<>();
            params.put("name", "Alice");
            params.put("age", null);

            executor.queryList(
                    "SELECT * FROM t_test_user WHERE name = :name AND age = :age",
                    params, TestUser.class);

            assertThat(capturedMessages()).contains("[HIT]");
        }

        @Test
        void should_notContainParamValues_when_logging() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            Map<String, Object> params = new HashMap<>();
            params.put("name", "SENSITIVE_VALUE_ALICE");
            params.put("age", null);

            buildExecutor().queryList(
                    "SELECT * FROM t_test_user WHERE name = :name AND age = :age",
                    params, TestUser.class);

            assertThat(capturedMessages()).doesNotContain("SENSITIVE_VALUE_ALICE");
        }
    }

    // ==================== Offset overflow protection ====================

    @Nested
    class OffsetOverflow {

        @Test
        void should_throwArithmeticException_when_offsetOverflowsInt() {
            config.setEnableAutoLimit(false);
            config.setEnableSqlRewrite(false);

            Query countQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(Long.MAX_VALUE);

            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);
            // page=Integer.MAX_VALUE, size=2 -> offset exceeds int range
            var hugePageable = PageRequest.of(Integer.MAX_VALUE, 2);

            assertThatThrownBy(() -> executor.queryPage(hugePageable,
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    // ==================== Edge cases ====================

    @Nested
    class EdgeCases {

        @Test
        void nullSql_returnsWithoutRewrite() {
            config.setEnableAutoLimit(false);
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(any(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);
            // null SQL should not throw (prepareQuery returns immediately)
            executor.queryList(null, Collections.emptyMap(), TestUser.class);
        }

        @Test
        void emptyParams_noRewrite() {
            config.setEnableAutoLimit(false);
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null);
            String sql = "SELECT * FROM t_test_user";
            executor.queryList(sql, Collections.emptyMap(), TestUser.class);

            verify(entityManager).createNativeQuery(sql, TestUser.class);
        }

        @Test
        void nullConfig_usesDefaults() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            // null config should use defaults
            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, null, null);
            executor.queryList("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            // Default: autoLimit=true, defaultLimit=10000
            verify(q).setMaxResults(10000);
        }
    }

    // ==================== Micrometer observability metrics ====================

    @Nested
    class Metrics {

        private SimpleMeterRegistry meterRegistry;

        @BeforeEach
        void setUpRegistry() {
            meterRegistry = new SimpleMeterRegistry();
        }

        private BoosterQueryExecutor buildExecutorWithMetrics() {
            config.setEnableAutoLimit(false);
            return new BoosterQueryExecutor(entityManager, config, null, new MicrometerMetricsRecorder(meterRegistry));
        }

        private BoosterQueryExecutor buildExecutorWithMetricsAndCache(BoosterCache cache) {
            config.setEnableAutoLimit(false);
            return new BoosterQueryExecutor(entityManager, config, cache, new MicrometerMetricsRecorder(meterRegistry));
        }

        // ---------- rewrite.total result=skip ----------

        @Test
        void should_incrementSkipCounter_when_noNullParams() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            buildExecutorWithMetrics().queryList(
                    "SELECT * FROM t_test_user WHERE name = :name",
                    Map.of("name", "Alice"), TestUser.class);

            Counter counter = meterRegistry.find("booster.query.rewrite.total")
                    .tag("result", "skip").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        // ---------- rewrite.total result=success + rewrite.duration ----------

        @Test
        void should_incrementSuccessCounterAndRecordTimer_when_cacheMissRewrite() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            Map<String, Object> params = new HashMap<>();
            params.put("name", "Alice");
            params.put("age", null);

            buildExecutorWithMetrics().queryList(
                    "SELECT * FROM t_test_user WHERE name = :name AND age = :age",
                    params, TestUser.class);

            // Verify success count
            Counter successCounter = meterRegistry.find("booster.query.rewrite.total")
                    .tag("result", "success").counter();
            assertThat(successCounter).isNotNull();
            assertThat(successCounter.count()).isEqualTo(1.0);

            // Verify timer has recorded
            Timer timer = meterRegistry.find("booster.query.rewrite.duration").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
        }

        // ---------- rewrite.total result=hit ----------

        @Test
        void should_incrementHitCounter_when_cacheHit() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterCache cache = mock(BoosterCache.class);
            when(cache.get(any())).thenReturn("SELECT * FROM t_test_user WHERE name = :name");

            Map<String, Object> params = new HashMap<>();
            params.put("name", "Alice");
            params.put("age", null);

            buildExecutorWithMetricsAndCache(cache).queryList(
                    "SELECT * FROM t_test_user WHERE name = :name AND age = :age",
                    params, TestUser.class);

            Counter hitCounter = meterRegistry.find("booster.query.rewrite.total")
                    .tag("result", "hit").counter();
            assertThat(hitCounter).isNotNull();
            assertThat(hitCounter.count()).isEqualTo(1.0);
        }

        // ---------- execute.total type=list/one/count/execute ----------

        @Test
        void should_incrementExecuteCounter_when_queryList() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            buildExecutorWithMetrics().queryList(
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            Counter counter = meterRegistry.find("booster.query.execute.total")
                    .tag("type", "list").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void should_incrementExecuteCounter_when_queryOne() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            buildExecutorWithMetrics().queryOne(
                    "SELECT * FROM t_test_user WHERE id = 1", Collections.emptyMap(), TestUser.class);

            // queryOne calls queryList, so both one and list are counted once
            Counter oneCounter = meterRegistry.find("booster.query.execute.total")
                    .tag("type", "one").counter();
            assertThat(oneCounter).isNotNull();
            assertThat(oneCounter.count()).isEqualTo(1.0);

            Counter listCounter = meterRegistry.find("booster.query.execute.total")
                    .tag("type", "list").counter();
            assertThat(listCounter).isNotNull();
            assertThat(listCounter.count()).isEqualTo(1.0);
        }

        @Test
        void should_incrementExecuteCounter_when_count() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(q);
            when(q.getSingleResult()).thenReturn(42L);

            buildExecutorWithMetrics().count(
                    "SELECT COUNT(*) FROM t_test_user", Collections.emptyMap());

            Counter counter = meterRegistry.find("booster.query.execute.total")
                    .tag("type", "count").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void should_incrementExecuteCounter_when_execute() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(q);
            when(q.executeUpdate()).thenReturn(1);

            buildExecutorWithMetrics().execute(
                    "UPDATE t_test_user SET name = :name WHERE id = :id",
                    Map.of("name", "Bob", "id", 1));

            Counter counter = meterRegistry.find("booster.query.execute.total")
                    .tag("type", "execute").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        // ---------- No exception when meterRegistry is null ----------

        @Test
        void should_notThrow_when_meterRegistryIsNull() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            config.setEnableAutoLimit(false);
            // Pass null for meterRegistry
            BoosterQueryExecutor executor = new BoosterQueryExecutor(entityManager, config, null, null);

            Map<String, Object> params = new HashMap<>();
            params.put("name", "Alice");
            params.put("age", null);

            // Should execute normally without throwing
            executor.queryList(
                    "SELECT * FROM t_test_user WHERE name = :name AND age = :age",
                    params, TestUser.class);
        }

        // ---------- T-03 Micrometer boundary tests ----------

        @Test
        void should_accumulateCounters_when_multipleQueryListCalls() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = buildExecutorWithMetrics();

            // 3 queryList calls
            for (int i = 0; i < 3; i++) {
                executor.queryList("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);
            }

            Counter counter = meterRegistry.find("booster.query.execute.total")
                    .tag("type", "list").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        }

        @Test
        void should_incrementPageCounter_when_queryPage() {
            Query dataQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(Collections.emptyList());

            Query countQuery = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(0L);

            buildExecutorWithMetrics().queryPage(
                    PageRequest.of(0, 10),
                    "SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            Counter counter = meterRegistry.find("booster.query.execute.total")
                    .tag("type", "page").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        void should_recordMultipleRewriteTimers() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            BoosterQueryExecutor executor = buildExecutorWithMetrics();

            for (int i = 0; i < 5; i++) {
                Map<String, Object> params = new HashMap<>();
                params.put("name", "Alice");
                params.put("p" + i, null); // Different null param each time

                executor.queryList(
                        "SELECT * FROM t_test_user WHERE name = :name AND p" + i + " = :p" + i,
                        params, TestUser.class);
            }

            Timer timer = meterRegistry.find("booster.query.rewrite.duration").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(5);
        }

        @Test
        void should_notCreateSkipCounter_when_onlyRewriteHappens() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(Collections.emptyList());

            Map<String, Object> params = new HashMap<>();
            params.put("name", null);

            buildExecutorWithMetrics().queryList(
                    "SELECT * FROM t_test_user WHERE name = :name",
                    params, TestUser.class);

            // success should have a value
            Counter successCounter = meterRegistry.find("booster.query.rewrite.total")
                    .tag("result", "success").counter();
            assertThat(successCounter).isNotNull();
            assertThat(successCounter.count()).isEqualTo(1.0);

            // skip should not be created (or be 0)
            Counter skipCounter = meterRegistry.find("booster.query.rewrite.total")
                    .tag("result", "skip").counter();
            if (skipCounter != null) {
                assertThat(skipCounter.count()).isEqualTo(0.0);
            }
        }

        @Test
        void should_incrementBothOneAndListCounters_when_queryOne() {
            Query q = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(q);
            when(q.getResultList()).thenReturn(List.of(new TestUser("A", 1, "a@t.com")));

            buildExecutorWithMetrics().queryOne(
                    "SELECT * FROM t_test_user WHERE id = 1", Collections.emptyMap(), TestUser.class);

            // queryOne calls queryList, both counted once
            assertThat(meterRegistry.find("booster.query.execute.total")
                    .tag("type", "one").counter().count()).isEqualTo(1.0);
            assertThat(meterRegistry.find("booster.query.execute.total")
                    .tag("type", "list").counter().count()).isEqualTo(1.0);
        }

        @Test
        void should_countAllMetricTypes_independently() {
            // Verify counters for different operation types are independent
            BoosterQueryExecutor executor = buildExecutorWithMetrics();

            // list
            Query listQ = mock(Query.class);
            when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(listQ);
            when(listQ.getResultList()).thenReturn(Collections.emptyList());
            executor.queryList("SELECT * FROM t_test_user", Collections.emptyMap(), TestUser.class);

            // count
            Query countQ = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(countQ);
            when(countQ.getSingleResult()).thenReturn(0L);
            executor.count("SELECT COUNT(*) FROM t_test_user", Collections.emptyMap());

            // execute
            Query execQ = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(execQ);
            when(execQ.executeUpdate()).thenReturn(0);
            executor.execute("UPDATE t_test_user SET name = 'x' WHERE id = 1", Collections.emptyMap());

            assertThat(meterRegistry.find("booster.query.execute.total")
                    .tag("type", "list").counter().count()).isEqualTo(1.0);
            assertThat(meterRegistry.find("booster.query.execute.total")
                    .tag("type", "count").counter().count()).isEqualTo(1.0);
            assertThat(meterRegistry.find("booster.query.execute.total")
                    .tag("type", "execute").counter().count()).isEqualTo(1.0);
        }
    }
}
