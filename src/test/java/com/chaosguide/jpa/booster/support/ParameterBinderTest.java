package com.chaosguide.jpa.booster.support;

import jakarta.persistence.Parameter;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ParameterBinderTest {

    static class Parent {
        private String parentField = "parentValue";
    }

    static class Child extends Parent {
        private String childField = "childValue";
        private int age = 25;
    }

    static class WithStaticAndSynthetic {
        private static final String CONSTANT = "const";
        private String normalField = "normal";
    }

    // ==================== toMap ====================

    @Nested
    class ToMap {

        @Test
        void basicPojo_extractsAllFields() {
            Child child = new Child();
            Map<String, Object> map = ParameterBinder.toMap(child);
            assertThat(map).containsEntry("childField", "childValue");
            assertThat(map).containsEntry("age", 25);
            assertThat(map).containsEntry("parentField", "parentValue");
        }

        @Test
        void inheritedFields_included() {
            Child child = new Child();
            Map<String, Object> map = ParameterBinder.toMap(child);
            assertThat(map).containsKey("parentField");
            assertThat(map.get("parentField")).isEqualTo("parentValue");
        }

        @Test
        void staticFields_excluded() {
            WithStaticAndSynthetic obj = new WithStaticAndSynthetic();
            Map<String, Object> map = ParameterBinder.toMap(obj);
            assertThat(map).doesNotContainKey("CONSTANT");
            assertThat(map).containsEntry("normalField", "normal");
        }

        @Test
        void nullInput_returnsEmptyMap() {
            Map<String, Object> map = ParameterBinder.toMap(null);
            assertThat(map).isEmpty();
        }

        @Test
        void cacheConsistency_differentInstances() {
            Child c1 = new Child();
            Child c2 = new Child();
            c2.age = 30;
            Map<String, Object> map1 = ParameterBinder.toMap(c1);
            Map<String, Object> map2 = ParameterBinder.toMap(c2);
            assertThat(map1.get("age")).isEqualTo(25);
            assertThat(map2.get("age")).isEqualTo(30);
        }

        @Test
        void nullFieldValue_includedInMap() {
            Child child = new Child();
            child.childField = null;
            Map<String, Object> map = ParameterBinder.toMap(child);
            assertThat(map).containsKey("childField");
            assertThat(map.get("childField")).isNull();
        }

        @Test
        void javaRecord_extractsComponents() {
            record UserQuery(String name, int age) {}
            UserQuery query = new UserQuery("Alice", 25);
            Map<String, Object> map = ParameterBinder.toMap(query);
            assertThat(map).containsEntry("name", "Alice");
            assertThat(map).containsEntry("age", 25);
        }

        @Test
        void javaRecord_withNullField() {
            record UserQuery(String name, Integer age) {}
            UserQuery query = new UserQuery(null, null);
            Map<String, Object> map = ParameterBinder.toMap(query);
            assertThat(map).containsEntry("name", null);
            assertThat(map).containsEntry("age", null);
        }

        @Test
        void emptyPojo_returnsEmptyMap() {
            class EmptyPojo {}
            Map<String, Object> map = ParameterBinder.toMap(new EmptyPojo());
            assertThat(map).isEmpty();
        }

        @Test
        void pojoWithAllNullFields_includedInMap() {
            class AllNulls {
                String name = null;
                Integer age = null;
            }
            Map<String, Object> map = ParameterBinder.toMap(new AllNulls());
            assertThat(map).hasSize(2);
            assertThat(map).containsEntry("name", null);
            assertThat(map).containsEntry("age", null);
        }

        @Test
        void shadowed_field_in_child_takesChildValue() {
            // When child and parent have a same-named field, child takes priority
            class ShadowParent {
                String name = "parent";
            }
            class ShadowChild extends ShadowParent {
                String name = "child";
            }
            Map<String, Object> map = ParameterBinder.toMap(new ShadowChild());
            assertThat(map.get("name")).isEqualTo("child");
        }
    }

    // ==================== bind(Query, Map) ====================

    @Nested
    class BindMap {

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Query mockQueryWithParams(String... paramNames) {
            Query query = mock(Query.class);
            Set params = new HashSet<>();
            for (String name : paramNames) {
                Parameter p = mock(Parameter.class);
                when(p.getName()).thenReturn(name);
                params.add(p);
            }
            when(query.getParameters()).thenReturn(params);
            return query;
        }

        @Test
        void matchingParams_bound() {
            Query query = mockQueryWithParams("name", "age");

            ParameterBinder.bind(query, Map.of("name", "Alice", "age", 25));

            verify(query).setParameter("name", "Alice");
            verify(query).setParameter("age", 25);
        }

        @Test
        void extraParams_ignored() {
            Query query = mockQueryWithParams("name");

            ParameterBinder.bind(query, Map.of("name", "Alice", "extra", "ignored"));

            verify(query).setParameter("name", "Alice");
            verify(query, never()).setParameter(eq("extra"), any());
        }

        @Test
        void nullParams_noOp() {
            Query query = mock(Query.class);
            ParameterBinder.bind(query, (Map<String, Object>) null);
            verify(query, never()).setParameter(anyString(), any());
        }

        @Test
        void emptyParams_noOp() {
            Query query = mock(Query.class);
            ParameterBinder.bind(query, Collections.emptyMap());
            verify(query, never()).setParameter(anyString(), any());
        }
    }

    // ==================== bind(Query, Object) ====================

    @Nested
    class BindObject {

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Query mockQueryWithParams(String... paramNames) {
            Query query = mock(Query.class);
            Set params = new HashSet<>();
            for (String name : paramNames) {
                Parameter p = mock(Parameter.class);
                when(p.getName()).thenReturn(name);
                params.add(p);
            }
            when(query.getParameters()).thenReturn(params);
            return query;
        }

        @Test
        void nullObject_noOp() {
            Query query = mock(Query.class);
            ParameterBinder.bind(query, (Object) null);
            verify(query, never()).setParameter(anyString(), any());
        }

        @Test
        void mapObject_delegatesToMapBind() {
            Query query = mockQueryWithParams("name");

            ParameterBinder.bind(query, (Object) Map.of("name", "Alice"));

            verify(query).setParameter("name", "Alice");
        }

        @Test
        void pojoObject_convertsToMapAndBinds() {
            Query query = mockQueryWithParams("childField", "age");

            Child child = new Child();
            ParameterBinder.bind(query, (Object) child);

            verify(query).setParameter("childField", "childValue");
            verify(query).setParameter("age", 25);
        }
    }
}
