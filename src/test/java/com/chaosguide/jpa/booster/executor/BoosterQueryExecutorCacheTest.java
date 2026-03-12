package com.chaosguide.jpa.booster.executor;

import com.chaosguide.jpa.booster.cache.BoosterCache;
import com.chaosguide.jpa.booster.config.BoosterQueryConfig;
import com.chaosguide.jpa.booster.entity.TestUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BoosterQueryExecutorCacheTest {

    private EntityManager entityManager;
    private BoosterCache boosterCache;
    private BoosterQueryExecutor executor;
    private BoosterQueryConfig config;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        boosterCache = mock(BoosterCache.class);
        config = new BoosterQueryConfig();
        config.setEnableSqlRewrite(true);
        config.setEnableAutoLimit(false);

        // Mock Metamodel for isEntity check
        Metamodel metamodel = mock(Metamodel.class);
        when(entityManager.getMetamodel()).thenReturn(metamodel);
        // TestUser is managed
        // Use doReturn/when to avoid type safe issues if necessary, but this should work
        ManagedType managedType = mock(ManagedType.class);
        doReturn(managedType).when(metamodel).managedType(TestUser.class);

        executor = new BoosterQueryExecutor(entityManager, config, boosterCache);
    }

    @Test
    void testRewriteCacheHit() {
        String originalSql = "SELECT * FROM t_test_user WHERE name = :name AND age = :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "John");
        params.put("age", null);

        // Mock Query creation
        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(Collections.emptyList());

        // First execution: Cache Miss
        when(boosterCache.get(any())).thenReturn(null);
        
        executor.queryList(originalSql, params, TestUser.class);

        // Verify put was called with rewritten SQL
        ArgumentCaptor<Object> keyCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(boosterCache, atLeastOnce()).put(keyCaptor.capture(), valueCaptor.capture());

        // Find the RewriteCacheKey put (there might be others like Count or Sort if we called other methods, but queryList only does Rewrite + AutoLimit (disabled))
        // Actually queryList calls prepareQuery which puts RewriteCacheKey.
        // It doesn't call count or sort.
        String cachedSql = valueCaptor.getValue();
        assertThat(cachedSql).doesNotContain(":age");

        // Second execution: Cache Hit
        String hitSql = "SELECT * FROM CACHED_HIT";
        
        // Reset invocations to clear verify history, but stubs remain.
        clearInvocations(entityManager, boosterCache);
        
        // We need to match the specific key type for the rewrite cache.
        // Since we can't access the private record class, we use argThat with string check.
        when(boosterCache.get(argThat(k -> k != null && k.toString().contains("RewriteCacheKey"))))
                .thenReturn(hitSql);

        // We also need to mock the query creation for the HIT sql
        Query hitQuery = mock(Query.class);
        when(entityManager.createNativeQuery(hitSql, TestUser.class)).thenReturn(hitQuery);
        when(hitQuery.getResultList()).thenReturn(Collections.emptyList());

        executor.queryList(originalSql, params, TestUser.class);

        verify(entityManager).createNativeQuery(hitSql, TestUser.class);
    }

    @Test
    void testCountCacheHit() {
        String sql = "SELECT * FROM t_test_user WHERE name = :name";
        Map<String, Object> params = Collections.singletonMap("name", "John");

        // Mock count query result
        Query countQuery = mock(Query.class);
        when(countQuery.getSingleResult()).thenReturn(10L);
        when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);

        // First call: Cache Miss
        when(boosterCache.get(any())).thenReturn(null);
        executor.count(sql, params);

        // Verify put
        verify(boosterCache, atLeastOnce()).put(argThat(k -> k.toString().contains("CountCacheKey")), anyString());

        // Second call: Cache Hit
        String cachedCountSql = "SELECT COUNT(*) FROM CACHED";
        clearInvocations(entityManager, boosterCache);
        
        when(boosterCache.get(argThat(k -> k != null && k.toString().contains("CountCacheKey"))))
                .thenReturn(cachedCountSql);
        
        // Mock query for cached SQL
        when(entityManager.createNativeQuery(cachedCountSql)).thenReturn(countQuery);

        executor.count(sql, params);

        verify(entityManager).createNativeQuery(cachedCountSql);
    }
    
    @Test
    void testSortCacheHit() {
        String sql = "SELECT * FROM t_test_user";
        Map<String, Object> params = Collections.emptyMap();
        Sort sort = Sort.by("name");
        PageRequest pageRequest = PageRequest.of(0, 10, sort);

        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString(), eq(TestUser.class))).thenReturn(query);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query); // For count
        when(query.getSingleResult()).thenReturn(10L);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        // First call: Cache Miss
        when(boosterCache.get(any())).thenReturn(null);
        executor.queryPage(pageRequest, sql, params, TestUser.class);

        // Verify sort cache put
        verify(boosterCache, atLeastOnce()).put(argThat(k -> k.toString().contains("SortCacheKey")), anyString());
        
        // Second call: Cache Hit
        String cachedSortedSql = "SELECT * FROM t_test_user ORDER BY CACHED";
        clearInvocations(entityManager, boosterCache);
        
        when(boosterCache.get(argThat(k -> k != null && k.toString().contains("SortCacheKey"))))
                .thenReturn(cachedSortedSql);
        
        // Mock query for cached SQL
        when(entityManager.createNativeQuery(cachedSortedSql, TestUser.class)).thenReturn(query);

        executor.queryPage(pageRequest, sql, params, TestUser.class);
        
        verify(entityManager).createNativeQuery(cachedSortedSql, TestUser.class);
    }

    @Test
    void testIsEntityOptimization() {
        // Mock query creation to avoid NPE during execution
        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString(), any(Class.class))).thenReturn(mockQuery);
        when(entityManager.createNativeQuery(anyString(), eq(jakarta.persistence.Tuple.class))).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(Collections.emptyList());

        // Verify that Metamodel.managedType is called only once for the same class
        Metamodel metamodel = entityManager.getMetamodel();
        
        // Call twice
        executor.queryList("SELECT 1", Collections.emptyMap(), TestUser.class);
        executor.queryList("SELECT 1", Collections.emptyMap(), TestUser.class);
        
        // Verify 1 call
        verify(metamodel, times(1)).managedType(TestUser.class);
        
        // Call with another class (e.g. String.class which is not managed)
        // Mock behavior for String.class to throw exception as per real JPA
        when(metamodel.managedType(String.class)).thenThrow(new IllegalArgumentException());
        
        executor.queryList("SELECT 1", Collections.emptyMap(), String.class);
        executor.queryList("SELECT 1", Collections.emptyMap(), String.class);
        
        // Verify 1 call for String.class
        verify(metamodel, times(1)).managedType(String.class);
    }
}
