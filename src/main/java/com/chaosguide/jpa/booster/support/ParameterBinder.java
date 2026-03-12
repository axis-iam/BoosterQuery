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

import jakarta.persistence.Parameter;
import jakarta.persistence.Query;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles JPA query parameter processing and binding.
 * <p>
 * Supports both Map parameters and POJO object parameters.
 */
public class ParameterBinder {

    private static final Logger log = LoggerFactory.getLogger(ParameterBinder.class);

    private ParameterBinder() {
        // utility class
    }

    /**
     * Caches field metadata per class to avoid reflective traversal on every toMap() call.
     */
    private record FieldMeta(String name, Field field) {}

    private static final ClassValue<FieldMeta[]> FIELD_CACHE = new ClassValue<>() {
        @Override
        protected FieldMeta @NonNull [] computeValue(@NonNull Class<?> type) {
            List<FieldMeta> fields = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (seen.contains(field.getName())) {
                        continue;
                    }
                    seen.add(field.getName());
                    field.setAccessible(true);
                    fields.add(new FieldMeta(field.getName(), field));
                }
            }
            return fields.toArray(new FieldMeta[0]);
        }
    };

    /**
     * Binds parameters to a Query object.
     * <p>
     * Automatically detects named parameters defined in the Query and only binds matching ones
     * to avoid IllegalArgumentException.
     *
     * @param query  JPA Query
     * @param params parameter Map
     */
    public static void bind(Query query, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }

        Set<String> queryParamNames = query.getParameters().stream()
                .map(Parameter::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (queryParamNames.contains(key)) {
                query.setParameter(key, entry.getValue());
            } else {
                log.debug("Ignored parameter '{}' as it is not present in the query.", key);
            }
        }
    }

    /**
     * Binds parameters to a Query object (POJO version).
     *
     * @param query    JPA Query
     * @param paramObj parameter object (POJO)
     */
    public static void bind(Query query, Object paramObj) {
        if (paramObj == null) {
            return;
        }
        if (paramObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) paramObj;
            bind(query, map);
            return;
        }

        Map<String, Object> params = toMap(paramObj);
        bind(query, params);
    }

    /**
     * Converts an object to {@code Map<String, Object>} keyed by field name.
     * Uses ClassValue to cache field metadata and avoid repeated reflection.
     */
    public static Map<String, Object> toMap(Object obj) {
        Map<String, Object> map = new HashMap<>();
        if (obj == null) {
            return map;
        }

        FieldMeta[] fields = FIELD_CACHE.get(obj.getClass());
        for (FieldMeta meta : fields) {
            try {
                Object value = meta.field().get(obj);
                map.put(meta.name(), value);
            } catch (IllegalAccessException e) {
                log.warn("Failed to access field '{}' of class '{}'", meta.name(), obj.getClass().getName(), e);
            }
        }
        return map;
    }
}
