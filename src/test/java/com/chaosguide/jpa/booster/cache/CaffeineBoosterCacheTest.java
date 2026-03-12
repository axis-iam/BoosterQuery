package com.chaosguide.jpa.booster.cache;

import com.chaosguide.jpa.booster.config.BoosterQueryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineBoosterCacheTest {

    private CaffeineBoosterCache cache;

    @BeforeEach
    void setUp() {
        BoosterQueryProperties.Cache props = new BoosterQueryProperties.Cache();
        props.setMaximumSize(100);
        props.setExpireAfterWrite(60000);
        cache = new CaffeineBoosterCache(props);
    }

    @Test
    void put_and_get_returnsValue() {
        cache.put("key1", "value1");
        assertThat(cache.get("key1")).isEqualTo("value1");
    }

    @Test
    void get_nonExistentKey_returnsNull() {
        assertThat(cache.get("nonexistent")).isNull();
    }

    @Test
    void put_nullKey_ignored() {
        cache.put(null, "value");
        // Should not throw
    }

    @Test
    void put_nullValue_ignored() {
        cache.put("key", null);
        assertThat(cache.get("key")).isNull();
    }

    @Test
    void clear_removesAllEntries() {
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.clear();
        assertThat(cache.get("k1")).isNull();
        assertThat(cache.get("k2")).isNull();
    }

    @Test
    void put_overwritesExistingValue() {
        cache.put("key", "old");
        cache.put("key", "new");
        assertThat(cache.get("key")).isEqualTo("new");
    }

    @Test
    void differentKeyTypes_workCorrectly() {
        // Simulate record type as key
        record TestKey(String sql, int hash) {}
        cache.put(new TestKey("SELECT 1", 42), "cached");
        assertThat(cache.get(new TestKey("SELECT 1", 42))).isEqualTo("cached");
        assertThat(cache.get(new TestKey("SELECT 2", 42))).isNull();
    }

    // ==================== Record key cache isolation tests ====================

    @Test
    void differentRecordTypes_isolatedCorrectly() {
        // Different record types with same field values should not conflict as cache keys
        record RewriteKey(String sql) {}
        record CountKey(String sql) {}

        cache.put(new RewriteKey("SELECT 1"), "rewritten");
        cache.put(new CountKey("SELECT 1"), "counted");

        assertThat(cache.get(new RewriteKey("SELECT 1"))).isEqualTo("rewritten");
        assertThat(cache.get(new CountKey("SELECT 1"))).isEqualTo("counted");
    }

    @Test
    void setBasedRecordKey_orderIndependent() {
        // Set as record field: different iteration order should not affect cache hit
        record CacheKey(String sql, Set<String> nullParams) {}

        Set<String> set1 = new LinkedHashSet<>(List.of("a", "b", "c"));
        Set<String> set2 = new LinkedHashSet<>(List.of("c", "b", "a"));

        cache.put(new CacheKey("SELECT 1", set1), "cached");
        // set1 and set2 have same elements but different insertion order, Set.equals() should consider them equal
        assertThat(cache.get(new CacheKey("SELECT 1", set2))).isEqualTo("cached");
    }

    @Test
    void put_sameKey_overwritesPrevious() {
        record Key(String sql) {}
        cache.put(new Key("SQL1"), "v1");
        cache.put(new Key("SQL1"), "v2");
        assertThat(cache.get(new Key("SQL1"))).isEqualTo("v2");
    }

    @Test
    void get_afterClear_returnsNull() {
        record Key(String sql) {}
        cache.put(new Key("SQL1"), "v1");
        cache.clear();
        assertThat(cache.get(new Key("SQL1"))).isNull();
    }

    @Test
    void put_nullKey_doesNotThrow() {
        // put(null, value) is silently ignored, should not throw
        cache.put(null, "value");
        // Do not call get(null) since Caffeine does not allow null key lookup
    }

    @Test
    void put_nullValue_doesNotStore() {
        record Key(String sql) {}
        cache.put(new Key("SQL1"), "existing");
        cache.put(new Key("SQL1"), null);
        // null value is ignored, should not overwrite existing value
        // Note: current implementation silently ignores put(key, null) without clearing existing value
        assertThat(cache.get(new Key("SQL1"))).isEqualTo("existing");
    }
}
