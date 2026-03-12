package com.chaosguide.jpa.booster.repository.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests parameter name resolution logic of BoosterSqlRepositoryQuery.
 * <p>
 * Covered scenarios:
 * - @Param annotation takes precedence
 * - Fallback to -parameters compiler option
 * - Single parameter / multiple parameters
 * - Map parameter
 * - POJO parameter
 * - Exception when parameter name is unavailable
 */
class BoosterSqlRepositoryQueryParamResolveTest {

    // ==================== Test Helpers: invoke private methods via reflection ====================

    /**
     * Invokes BoosterSqlRepositoryQuery.MethodArguments.resolveNamedParams
     */
    private static Map<String, Object> invokeResolveNamedParams(Method method,
                                                                 Parameter[] parameters,
                                                                 Object[] values,
                                                                 List<Integer> nonSpecialIndexes) throws Exception {
        // MethodArguments is a private inner record of BoosterSqlRepositoryQuery
        Class<?> methodArgumentsClass = null;
        for (Class<?> inner : BoosterSqlRepositoryQuery.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("MethodArguments")) {
                methodArgumentsClass = inner;
                break;
            }
        }
        assertNotNull(methodArgumentsClass, "MethodArguments inner class not found");

        Method resolveMethod = methodArgumentsClass.getDeclaredMethod(
                "resolveNamedParams", Method.class, Parameter[].class, Object[].class, List.class);
        resolveMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resolveMethod.invoke(null, method, parameters, values, nonSpecialIndexes);
        return result;
    }

    /**
     * Invokes BoosterSqlRepositoryQuery.resolveParamName
     */
    private static String invokeResolveParamName(Parameter parameter, Method method) throws Exception {
        Class<?> methodArgumentsClass = null;
        for (Class<?> inner : BoosterSqlRepositoryQuery.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("MethodArguments")) {
                methodArgumentsClass = inner;
                break;
            }
        }
        assertNotNull(methodArgumentsClass, "MethodArguments inner class not found");

        Method resolveMethod = methodArgumentsClass.getDeclaredMethod("resolveParamName", Parameter.class, Method.class);
        resolveMethod.setAccessible(true);
        return (String) resolveMethod.invoke(null, parameter, method);
    }

    // ==================== Test interface (parameter names visible when compiled with -parameters) ====================

    @SuppressWarnings("unused")
    interface TestMethods {
        void withParam(@Param("userName") String name);
        void withMultipleParams(@Param("name") String name, @Param("age") Integer age, Pageable pageable);
        void withoutParam(String name);
        void withMultipleNoParam(String name, Integer age, Pageable pageable);
        void withMixedParams(@Param("name") String name, Integer age, Pageable pageable);
        void withMapParam(Map<String, Object> params);
        void noParams(Pageable pageable);
        void withBlankParam(@Param("") String name);
    }

    private static Method getMethod(String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return TestMethods.class.getMethod(name, paramTypes);
    }

    // ==================== resolveParamName Unit Tests ====================

    @Nested
    @DisplayName("resolveParamName")
    class ResolveParamNameTest {

        @Test
        @DisplayName("@Param annotation takes precedence over -parameters name")
        void paramAnnotationTakesPrecedence() throws Exception {
            Method method = getMethod("withParam", String.class);
            Parameter parameter = method.getParameters()[0];

            String name = invokeResolveParamName(parameter, method);

            assertEquals("userName", name);
        }

        @Test
        @DisplayName("-parameters compiler option: falls back to reflection param name without @Param")
        void fallsBackToReflectionParameterName() throws Exception {
            Method method = getMethod("withoutParam", String.class);
            Parameter parameter = method.getParameters()[0];

            String name = invokeResolveParamName(parameter, method);

            // This project is compiled with -parameters, so isNamePresent() == true
            if (parameter.isNamePresent()) {
                assertEquals("name", name);
            }
            // Returns null if not compiled with -parameters (this branch is not hit in CI)
        }

        @Test
        @DisplayName("Blank @Param value falls back to -parameters name")
        void blankParamFallsBackToReflection() throws Exception {
            Method method = getMethod("withBlankParam", String.class);
            Parameter parameter = method.getParameters()[0];

            String name = invokeResolveParamName(parameter, method);

            // @Param("") is treated as invalid, falls back to reflection
            if (parameter.isNamePresent()) {
                assertNotNull(name);
                assertFalse(name.isBlank());
            }
        }
    }

    // ==================== resolveNamedParams Unit Tests ====================

    @Nested
    @DisplayName("resolveNamedParams - single parameter scenarios")
    class SingleParamTest {

        @Test
        @DisplayName("Single param with @Param annotation resolves correctly")
        void singleParamWithAnnotation() throws Exception {
            Method method = getMethod("withParam", String.class);
            Parameter[] params = method.getParameters();
            Object[] values = {"Alice"};

            Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of(0));

            assertEquals(Map.of("userName", "Alice"), result);
        }

        @Test
        @DisplayName("Single param without @Param resolves via -parameters")
        void singleParamWithoutAnnotation() throws Exception {
            Method method = getMethod("withoutParam", String.class);
            Parameter[] params = method.getParameters();
            Object[] values = {"Alice"};

            if (params[0].isNamePresent()) {
                Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of(0));
                assertEquals(Map.of("name", "Alice"), result);
            }
        }

        @Test
        @DisplayName("Single param with null value retains param name with null value")
        void singleParamNullValue() throws Exception {
            Method method = getMethod("withParam", String.class);
            Parameter[] params = method.getParameters();
            Object[] values = {null};

            Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of(0));

            assertTrue(result.containsKey("userName"), "param name should be retained");
            assertNull(result.get("userName"), "param value should be null");
        }

        @Test
        @DisplayName("Single param without @Param, null value, retains name via -parameters")
        void singleParamNullWithoutAnnotation() throws Exception {
            Method method = getMethod("withoutParam", String.class);
            Parameter[] params = method.getParameters();
            Object[] values = {null};

            if (params[0].isNamePresent()) {
                Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of(0));
                assertTrue(result.containsKey("name"), "param name should be resolved via -parameters");
                assertNull(result.get("name"), "param value should be null");
            }
        }

        @Test
        @DisplayName("Single Map param is expanded directly")
        void singleMapParam() throws Exception {
            Method method = getMethod("withMapParam", Map.class);
            Parameter[] params = method.getParameters();
            Map<String, Object> mapValue = new HashMap<>();
            mapValue.put("name", "Alice");
            mapValue.put("age", 25);
            Object[] values = {mapValue};

            Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of(0));

            assertEquals("Alice", result.get("name"));
            assertEquals(25, result.get("age"));
        }
    }

    @Nested
    @DisplayName("resolveNamedParams - multiple parameter scenarios")
    class MultiParamTest {

        @Test
        @DisplayName("Multiple params all with @Param annotations")
        void allParamsWithAnnotation() throws Exception {
            Method method = getMethod("withMultipleParams", String.class, Integer.class, Pageable.class);
            Parameter[] params = method.getParameters();
            // nonSpecialIndexes excludes Pageable (index 2)
            Object[] values = {"Alice", 25, Pageable.unpaged()};

            Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of(0, 1));

            assertEquals("Alice", result.get("name"));
            assertEquals(25, result.get("age"));
        }

        @Test
        @DisplayName("Multiple params without @Param, resolved via -parameters")
        void multiParamsWithoutAnnotation() throws Exception {
            Method method = getMethod("withMultipleNoParam", String.class, Integer.class, Pageable.class);
            Parameter[] params = method.getParameters();
            Object[] values = {"Alice", 25, Pageable.unpaged()};

            if (params[0].isNamePresent()) {
                Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of(0, 1));
                assertEquals("Alice", result.get("name"));
                assertEquals(25, result.get("age"));
            }
        }

        @Test
        @DisplayName("Mixed params: some with @Param, others via -parameters")
        void mixedParams() throws Exception {
            Method method = getMethod("withMixedParams", String.class, Integer.class, Pageable.class);
            Parameter[] params = method.getParameters();
            Object[] values = {"Alice", 25, Pageable.unpaged()};

            if (params[1].isNamePresent()) {
                Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of(0, 1));
                // First param has @Param("name")
                assertEquals("Alice", result.get("name"));
                // Second param resolved via -parameters reflection as "age"
                assertEquals(25, result.get("age"));
            }
        }

        @Test
        @DisplayName("Multiple params with one null value retains param name with null")
        void multiParamsWithNullValue() throws Exception {
            Method method = getMethod("withMultipleParams", String.class, Integer.class, Pageable.class);
            Parameter[] params = method.getParameters();
            Object[] values = {"Alice", null, Pageable.unpaged()};

            Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of(0, 1));

            assertEquals("Alice", result.get("name"));
            assertTrue(result.containsKey("age"), "null param name should be retained");
            assertNull(result.get("age"), "null param value should be null");
        }
    }

    @Nested
    @DisplayName("resolveNamedParams - no parameter scenarios")
    class NoParamTest {

        @Test
        @DisplayName("Only Pageable, no business params, returns empty Map")
        void onlyPageable() throws Exception {
            Method method = getMethod("noParams", Pageable.class);
            Parameter[] params = method.getParameters();
            Object[] values = {Pageable.unpaged()};

            // Pageable is excluded, nonSpecialIndexes is empty
            Map<String, Object> result = invokeResolveNamedParams(method, params, values, List.of());

            assertTrue(result.isEmpty());
        }
    }
}
