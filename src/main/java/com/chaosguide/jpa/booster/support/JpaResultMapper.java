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
package com.chaosguide.jpa.booster.support;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.beans.ConstructorProperties;
import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JPA query result mapping utility.
 * <p>
 * Converts {@link Tuple} result sets to DTOs, Records, interface projections, Maps, or scalar types.
 */
public class JpaResultMapper {

    /**
     * Cached constructor metadata containing the constructor reference, parameter names, and parameter types.
     * <p>
     * Avoids repeated reflective lookups ({@link #selectConstructor}, {@link #resolveParameterNames})
     * on every {@link #mapByConstructor} invocation.
     */
    record ConstructorMeta(Constructor<?> ctor, String[] paramNames, Class<?>[] paramTypes) {}

    /**
     * {@link ClassValue}-based constructor metadata cache.
     * <p>
     * For a given {@link Class}, {@code computeValue} executes only once;
     * subsequent {@code get()} calls return the cached result. Ideal for batch mapping (e.g. 1000-row result sets).
     */
    private static final ClassValue<ConstructorMeta> CONSTRUCTOR_CACHE = new ClassValue<>() {
        @Override
        protected ConstructorMeta computeValue(Class<?> type) {
            Constructor<?> ctor = selectConstructor(type);
            String[] paramNames = resolveParameterNames(ctor);
            Class<?>[] paramTypes = ctor.getParameterTypes();
            return new ConstructorMeta(ctor, paramNames, paramTypes);
        }
    };

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = Map.of(
            boolean.class, Boolean.class,
            int.class, Integer.class,
            long.class, Long.class,
            double.class, Double.class,
            float.class, Float.class,
            short.class, Short.class,
            byte.class, Byte.class,
            char.class, Character.class
    );

    private static final Set<Class<?>> SIMPLE_TYPES = Set.of(
            String.class, Long.class, Integer.class, Boolean.class,
            Double.class, Float.class, Short.class, Byte.class,
            BigDecimal.class, BigInteger.class,
            Date.class, java.sql.Date.class, Timestamp.class,
            LocalDateTime.class, LocalDate.class, LocalTime.class
    );

    /**
     * Converts a list of Tuples to a list of the specified type.
     */
    public static <T> List<T> map(List<Tuple> tuples, Class<T> resultType) {
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            result.add(map(tuple, resultType));
        }
        return result;
    }

    /**
     * Converts a single Tuple to the specified type.
     */
    @SuppressWarnings("unchecked")
    public static <T> T map(Tuple tuple, Class<T> resultType) {
        if (Map.class.isAssignableFrom(resultType)) {
            return (T) mapToMap(tuple);
        }
        if (resultType.isRecord()) {
            return mapToRecord(tuple, resultType);
        }
        if (isSimpleType(resultType)) {
            Object value = tuple.get(0);
            return convertValue(value, resultType);
        }
        if (resultType.isInterface()) {
            return mapToProjection(tuple, resultType);
        }
        return mapToDto(tuple, resultType);
    }

    private record AliasLookup(Map<String, Object> byAlias, Map<String, Object> byCamelAlias) {

        Object resolve(String name) {
            return byAlias.containsKey(name) ? byAlias.get(name) : byCamelAlias.get(name);
        }
    }

    private static AliasLookup buildAliasLookup(Tuple tuple) {
        Map<String, Object> byAlias = new HashMap<>();
        Map<String, Object> byCamelAlias = new HashMap<>();
        for (TupleElement<?> element : tuple.getElements()) {
            String alias = element.getAlias();
            if (alias == null) continue;
            Object value = tuple.get(alias);
            byAlias.put(alias, value);
            byCamelAlias.put(toCamelCase(alias), value);
        }
        return new AliasLookup(byAlias, byCamelAlias);
    }

    private static Object resolveValue(AliasLookup lookup, String name, Class<?> type) {
        Object raw = lookup.resolve(name);
        Object converted = convertValue(raw, type);
        if (converted == null && type.isPrimitive()) {
            return defaultPrimitiveValue(type);
        }
        return converted;
    }

    private static <T> T mapToRecord(Tuple tuple, Class<T> recordClass) {
        try {
            RecordComponent[] components = recordClass.getRecordComponents();
            if (components == null) {
                throw new IllegalArgumentException("Not a record: " + recordClass.getName());
            }
            AliasLookup lookup = buildAliasLookup(tuple);
            Class<?>[] paramTypes = new Class<?>[components.length];
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                RecordComponent c = components[i];
                paramTypes[i] = c.getType();
                args[i] = resolveValue(lookup, c.getName(), c.getType());
            }
            return recordClass.getDeclaredConstructor(paramTypes).newInstance(args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new RuntimeException("Failed to map Tuple to record: " + recordClass.getName(), e);
        }
    }

    private static Map<String, Object> mapToMap(Tuple tuple) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (TupleElement<?> element : tuple.getElements()) {
            String alias = element.getAlias();
            if (alias != null) {
                map.put(alias, tuple.get(alias));
            }
        }
        return map;
    }

    /**
     * Maps a Tuple to an interface projection via {@link Proxy} dynamic proxy.
     * <p>
     * Getter methods ({@code getXxx()}, {@code isXxx()}) are resolved to property names
     * following the JavaBean specification ({@link Introspector#decapitalize}), then matched
     * against Tuple column aliases (with underscore-to-camelCase conversion).
     * <p>
     * Handles: Object methods (equals/hashCode/toString), default methods,
     * type conversion via {@link #convertValue}, and null safety for primitive return types.
     *
     * @param tuple               the JPA Tuple to map
     * @param projectionInterface the target interface type
     * @return a Proxy instance implementing the projection interface
     */
    @SuppressWarnings("unchecked")
    private static <T> T mapToProjection(Tuple tuple, Class<T> projectionInterface) {
        Map<String, Object> valueByName = new HashMap<>();
        for (TupleElement<?> element : tuple.getElements()) {
            String alias = element.getAlias();
            if (alias == null) continue;
            Object value = tuple.get(alias);
            valueByName.put(alias, value);
            String camel = toCamelCase(alias);
            if (!camel.equals(alias)) {
                valueByName.put(camel, value);
            }
        }

        return (T) Proxy.newProxyInstance(
                projectionInterface.getClassLoader(),
                new Class<?>[]{projectionInterface},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> projectionInterface.getSimpleName() + valueByName;
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    }
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    String propName = resolvePropertyName(method.getName());
                    Object rawValue = valueByName.get(propName);
                    Class<?> returnType = method.getReturnType();
                    if (rawValue == null && returnType.isPrimitive()) {
                        return defaultPrimitiveValue(returnType);
                    }
                    return convertValue(rawValue, returnType);
                }
        );
    }

    /**
     * Resolves a getter method name to a JavaBean property name.
     * <p>
     * {@code getName} → {@code name}, {@code isActive} → {@code active},
     * {@code getURL} → {@code URL} (follows {@link Introspector#decapitalize} rules).
     */
    static String resolvePropertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Introspector.decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Introspector.decapitalize(methodName.substring(2));
        }
        return methodName;
    }

    /**
     * Returns the default zero value for a primitive type, preventing NPE during unboxing.
     */
    private static Object defaultPrimitiveValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }

    private static <T> T mapToDto(Tuple tuple, Class<T> dtoClass) {
        if (hasDefaultConstructor(dtoClass)) {
            return mapByBeanWrapper(tuple, dtoClass);
        }
        return mapByConstructor(tuple, dtoClass);
    }

    private static boolean hasDefaultConstructor(Class<?> clazz) {
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private static <T> T mapByBeanWrapper(Tuple tuple, Class<T> dtoClass) {
        try {
            T dto = BeanUtils.instantiateClass(dtoClass);
            BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(dto);
            beanWrapper.setAutoGrowNestedPaths(true);
            for (TupleElement<?> element : tuple.getElements()) {
                String alias = element.getAlias();
                if (alias != null) {
                    Object value = tuple.get(alias);
                    if (beanWrapper.isWritableProperty(alias)) {
                        beanWrapper.setPropertyValue(alias, value);
                    } else {
                        String camelCase = toCamelCase(alias);
                        if (beanWrapper.isWritableProperty(camelCase)) {
                            beanWrapper.setPropertyValue(camelCase, value);
                        }
                    }
                }
            }
            return dto;
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to map Tuple to DTO: " + dtoClass.getName(), e);
        }
    }

    private static <T> T mapByConstructor(Tuple tuple, Class<T> dtoClass) {
        try {
            AliasLookup lookup = buildAliasLookup(tuple);

            ConstructorMeta meta = CONSTRUCTOR_CACHE.get(dtoClass);
            @SuppressWarnings("unchecked")
            Constructor<T> ctor = (Constructor<T>) meta.ctor();
            String[] paramNames = meta.paramNames();
            Class<?>[] paramTypes = meta.paramTypes();
            Object[] args = new Object[paramNames.length];

            for (int i = 0; i < paramNames.length; i++) {
                args[i] = resolveValue(lookup, paramNames[i], paramTypes[i]);
            }

            if (!ctor.canAccess(null)) {
                ctor.setAccessible(true);
            }
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new RuntimeException(
                    "Failed to map Tuple to DTO via constructor: " + dtoClass.getName(), e);
        }
    }

    /**
     * Returns the constructor metadata cache (for testing only).
     */
    static ClassValue<ConstructorMeta> constructorCache() {
        return CONSTRUCTOR_CACHE;
    }

    private static Constructor<?> selectConstructor(Class<?> clazz) {
        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        if (ctors.length == 1) {
            return ctors[0];
        }
        // Prefer constructors annotated with @ConstructorProperties (pick the one with the most parameters)
        Constructor<?> annotated = null;
        for (Constructor<?> ctor : ctors) {
            if (ctor.isAnnotationPresent(ConstructorProperties.class)) {
                if (annotated == null || ctor.getParameterCount() > annotated.getParameterCount()) {
                    annotated = ctor;
                }
            }
        }
        if (annotated != null) {
            return annotated;
        }
        // Fallback: filter out synthetic constructors, pick the one with the most parameters
        Constructor<?> best = null;
        for (Constructor<?> ctor : ctors) {
            if (ctor.isSynthetic()) continue;
            if (best == null || ctor.getParameterCount() > best.getParameterCount()) {
                best = ctor;
            }
        }
        return best != null ? best : ctors[0];
    }

    /**
     * Resolves constructor parameter names.
     * <p>
     * Priority: {@link ConstructorProperties} annotation, then {@code -parameters} compiler flag reflection, then throws.
     */
    static String[] resolveParameterNames(Constructor<?> ctor) {
        ConstructorProperties annotation = ctor.getAnnotation(ConstructorProperties.class);
        if (annotation != null) {
            String[] names = annotation.value();
            if (names.length != ctor.getParameterCount()) {
                throw new IllegalStateException(
                        "@ConstructorProperties value count (" + names.length
                                + ") does not match constructor parameter count (" + ctor.getParameterCount()
                                + ") for " + ctor.getDeclaringClass().getName()
                );
            }
            return names;
        }

        Parameter[] params = ctor.getParameters();
        if (params.length > 0 && params[0].isNamePresent()) {
            String[] names = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                names[i] = params[i].getName();
            }
            return names;
        }

        throw new IllegalStateException(
                "Cannot resolve parameter names for constructor of " + ctor.getDeclaringClass().getName()
                        + ". Either add @ConstructorProperties annotation or compile with -parameters flag."
        );
    }

    private static boolean isSimpleType(Class<?> type) {
        return BeanUtils.isSimpleProperty(type) || type.equals(Object.class) || SIMPLE_TYPES.contains(type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> T convertValue(Object value, Class<T> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return (T) value;

        // ---- Number conversions ----
        if (value instanceof Number num) {
            if (targetType == Long.class || targetType == long.class) return (T) Long.valueOf(num.longValue());
            if (targetType == Integer.class || targetType == int.class) return (T) Integer.valueOf(num.intValue());
            if (targetType == Double.class || targetType == double.class) return (T) Double.valueOf(num.doubleValue());
            if (targetType == Float.class || targetType == float.class) return (T) Float.valueOf(num.floatValue());
            if (targetType == Short.class || targetType == short.class) return (T) Short.valueOf(num.shortValue());
            if (targetType == Byte.class || targetType == byte.class) return (T) Byte.valueOf(num.byteValue());
            if (targetType == BigDecimal.class) return (T) (num instanceof BigDecimal ? num : new BigDecimal(num.toString()));
            if (targetType == BigInteger.class) return (T) (num instanceof BigInteger ? num : BigInteger.valueOf(num.longValue()));
            if (targetType == Boolean.class || targetType == boolean.class) return (T) Boolean.valueOf(num.intValue() != 0);
            if (targetType.isEnum()) {
                Object[] constants = targetType.getEnumConstants();
                int ordinal = num.intValue();
                if (ordinal >= 0 && ordinal < constants.length) return (T) constants[ordinal];
            }
        }

        // ---- String ----
        if (targetType == String.class) return (T) value.toString();

        // ---- Boolean ← String / Number ----
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof String s) {
                return (T) Boolean.valueOf("true".equalsIgnoreCase(s) || "1".equals(s) || "Y".equalsIgnoreCase(s));
            }
        }

        // ---- Enum ← String / Number ----
        if (targetType.isEnum()) {
            if (value instanceof String s) {
                return (T) Enum.valueOf((Class<? extends Enum>) targetType, s);
            }
        }

        // ---- Temporal type conversions ----
        if (targetType == LocalDateTime.class) {
            if (value instanceof Timestamp ts) return (T) ts.toLocalDateTime();
            if (value instanceof java.sql.Date d) return (T) d.toLocalDate().atStartOfDay();
            if (value instanceof Date d) return (T) new Timestamp(d.getTime()).toLocalDateTime();
        }
        if (targetType == LocalDate.class) {
            if (value instanceof java.sql.Date d) return (T) d.toLocalDate();
            if (value instanceof Timestamp ts) return (T) ts.toLocalDateTime().toLocalDate();
            if (value instanceof LocalDateTime ldt) return (T) ldt.toLocalDate();
            if (value instanceof Date d) return (T) new java.sql.Date(d.getTime()).toLocalDate();
        }
        if (targetType == LocalTime.class) {
            if (value instanceof Timestamp ts) return (T) ts.toLocalDateTime().toLocalTime();
            if (value instanceof java.sql.Time t) return (T) t.toLocalTime();
        }

        // Handle primitive-to-wrapper type correspondence (boolean.class <- Boolean, int.class <- Integer, etc.)
        if (targetType.isPrimitive()) {
            Class<?> wrapper = PRIMITIVE_TO_WRAPPER.get(targetType);
            if (wrapper != null && wrapper.isInstance(value)) {
                return (T) value;
            }
        }
        if (targetType.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        throw new IllegalArgumentException(
                "Cannot convert value of type " + value.getClass().getName()
                        + " to target type " + targetType.getName());
    }

    private static String toCamelCase(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean nextUpperCase = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    sb.append(Character.toUpperCase(c));
                    nextUpperCase = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
