package com.chaosguide.jpa.booster.support;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.beans.ConstructorProperties;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaResultMapperTest {

    enum Color { RED, GREEN, BLUE }

    // ==================== Tuple helper methods ====================

    @SuppressWarnings("unchecked")
    private static TupleElement<Object> mockElement(String alias) {
        TupleElement<Object> el = mock(TupleElement.class);
        when(el.getAlias()).thenReturn(alias);
        return el;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Tuple mockTuple(Map<String, Object> data) {
        Tuple tuple = mock(Tuple.class);
        List elements = new ArrayList<>();
        for (String key : data.keySet()) {
            elements.add(mockElement(key));
        }
        when(tuple.getElements()).thenReturn(elements);
        for (var entry : data.entrySet()) {
            when(tuple.get(entry.getKey())).thenReturn(entry.getValue());
        }
        return tuple;
    }

    // ---- Number -> various numeric types ----

    @Test
    void testConvert_numberToDouble() {
        Double result = JpaResultMapper.convertValue(42, Double.class);
        assertThat(result).isEqualTo(42.0);
    }

    @Test
    void testConvert_numberToFloat() {
        Float result = JpaResultMapper.convertValue(3.14, Float.class);
        assertThat(result).isEqualTo(3.14f);
    }

    @Test
    void testConvert_numberToShort() {
        Short result = JpaResultMapper.convertValue(100, Short.class);
        assertThat(result).isEqualTo((short) 100);
    }

    @Test
    void testConvert_numberToByte() {
        Byte result = JpaResultMapper.convertValue(7, Byte.class);
        assertThat(result).isEqualTo((byte) 7);
    }

    @Test
    void testConvert_numberToBigDecimal() {
        BigDecimal result = JpaResultMapper.convertValue(123.45, BigDecimal.class);
        assertThat(result).isEqualByComparingTo(new BigDecimal("123.45"));
    }

    @Test
    void testConvert_numberToBigInteger() {
        BigInteger result = JpaResultMapper.convertValue(999L, BigInteger.class);
        assertThat(result).isEqualTo(BigInteger.valueOf(999));
    }

    // ---- Number → Boolean ----

    @Test
    void testConvert_numberToBoolean_nonZeroIsTrue() {
        Boolean result = JpaResultMapper.convertValue(1, Boolean.class);
        assertThat(result).isTrue();
    }

    @Test
    void testConvert_numberToBoolean_zeroIsFalse() {
        Boolean result = JpaResultMapper.convertValue(0, Boolean.class);
        assertThat(result).isFalse();
    }

    // ---- String → Boolean ----

    @ParameterizedTest
    @CsvSource({"true,true", "TRUE,true", "1,true", "Y,true", "y,true", "false,false", "0,false", "N,false"})
    void testConvert_stringToBoolean(String input, boolean expected) {
        Boolean result = JpaResultMapper.convertValue(input, Boolean.class);
        assertThat(result).isEqualTo(expected);
    }

    // ---- String → Enum ----

    @Test
    void testConvert_stringToEnum() {
        Color result = JpaResultMapper.convertValue("GREEN", Color.class);
        assertThat(result).isEqualTo(Color.GREEN);
    }

    // ---- Number(ordinal) → Enum ----

    @Test
    void testConvert_ordinalToEnum() {
        Color result = JpaResultMapper.convertValue(2, Color.class);
        assertThat(result).isEqualTo(Color.BLUE);
    }

    // ---- Timestamp → LocalDateTime ----

    @Test
    void testConvert_timestampToLocalDateTime() {
        Timestamp ts = Timestamp.valueOf("2024-06-15 10:30:00");
        LocalDateTime result = JpaResultMapper.convertValue(ts, LocalDateTime.class);
        assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30, 0));
    }

    // ---- sql.Date → LocalDate ----

    @Test
    void testConvert_sqlDateToLocalDate() {
        java.sql.Date sqlDate = java.sql.Date.valueOf("2024-06-15");
        LocalDate result = JpaResultMapper.convertValue(sqlDate, LocalDate.class);
        assertThat(result).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    // ---- Timestamp → LocalDate ----

    @Test
    void testConvert_timestampToLocalDate() {
        Timestamp ts = Timestamp.valueOf("2024-06-15 10:30:00");
        LocalDate result = JpaResultMapper.convertValue(ts, LocalDate.class);
        assertThat(result).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    // ---- Timestamp → LocalTime ----

    @Test
    void testConvert_timestampToLocalTime() {
        Timestamp ts = Timestamp.valueOf("2024-06-15 10:30:45");
        LocalTime result = JpaResultMapper.convertValue(ts, LocalTime.class);
        assertThat(result).isEqualTo(LocalTime.of(10, 30, 45));
    }

    // ---- null safety ----

    @Test
    void testConvert_nullReturnsNull() {
        assertThat(JpaResultMapper.convertValue(null, String.class)).isNull();
    }

    // ---- Same type returns as-is ----

    @Test
    void testConvert_sameTypeReturnsAsIs() {
        String input = "hello";
        String result = JpaResultMapper.convertValue(input, String.class);
        assertThat(result).isSameAs(input);
    }

    // ==================== Tuple -> Map mapping ====================

    @Nested
    class MapMapping {

        @Test
        void tupleToMap_returnsLinkedHashMap() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "age", 25));
            @SuppressWarnings("unchecked")
            Map<String, Object> result = JpaResultMapper.map(tuple, Map.class);
            assertThat(result).containsEntry("name", "Alice");
            assertThat(result).containsEntry("age", 25);
        }

        @Test
        void emptyTuple_returnsEmptyMap() {
            Tuple tuple = mockTuple(Collections.emptyMap());
            @SuppressWarnings("unchecked")
            Map<String, Object> result = JpaResultMapper.map(tuple, Map.class);
            assertThat(result).isEmpty();
        }
    }

    // ==================== Tuple -> DTO mapping ====================

    @Nested
    class DtoMapping {

        // Simple DTO for testing
        public static class SimpleDto {
            private String name;
            private String email;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getEmail() { return email; }
            public void setEmail(String email) { this.email = email; }
        }

        public static class UnderscoreDto {
            private String userName;

            public String getUserName() { return userName; }
            public void setUserName(String userName) { this.userName = userName; }
        }

        @Test
        void tupleToDto_mapsFields() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "email", "alice@test.com"));
            SimpleDto result = JpaResultMapper.map(tuple, SimpleDto.class);
            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isEqualTo("alice@test.com");
        }

        @Test
        void tupleToDto_underscoreToCamelCase() {
            Tuple tuple = mockTuple(Map.of("user_name", "alice_user"));
            UnderscoreDto result = JpaResultMapper.map(tuple, UnderscoreDto.class);
            assertThat(result.getUserName()).isEqualTo("alice_user");
        }

        @Test
        void tupleToDto_unmatchedFieldIgnored() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "nonexistent_field", "ignored"));
            SimpleDto result = JpaResultMapper.map(tuple, SimpleDto.class);
            assertThat(result.getName()).isEqualTo("Alice");
            // nonexistent_field is ignored, no exception thrown
        }
    }

    // ==================== Tuple -> Record mapping ====================

    @Nested
    class RecordMapping {

        record UserRecord(String name, String email) {}
        record UnderscoreRecord(String userName) {}
        record TypeConvertRecord(Long id, String name) {}
        record PrimitiveRecord(long id, int count, boolean active, double score) {}

        @Test
        void tupleToRecord_mapsFields() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "email", "alice@test.com"));
            UserRecord result = JpaResultMapper.map(tuple, UserRecord.class);
            assertThat(result.name()).isEqualTo("Alice");
            assertThat(result.email()).isEqualTo("alice@test.com");
        }

        @Test
        void tupleToRecord_underscoreToCamelCase() {
            Tuple tuple = mockTuple(Map.of("user_name", "alice_user"));
            UnderscoreRecord result = JpaResultMapper.map(tuple, UnderscoreRecord.class);
            assertThat(result.userName()).isEqualTo("alice_user");
        }

        @Test
        void tupleToRecord_typeConversion() {
            Tuple tuple = mockTuple(Map.of("id", 42, "name", "Alice"));
            TypeConvertRecord result = JpaResultMapper.map(tuple, TypeConvertRecord.class);
            assertThat(result.id()).isEqualTo(42L);
            assertThat(result.name()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("Record with missing primitive params uses default zero values")
        void tupleToRecord_primitiveNullSafety() {
            Tuple tuple = mockTuple(Collections.emptyMap());
            PrimitiveRecord result = JpaResultMapper.map(tuple, PrimitiveRecord.class);
            assertThat(result.id()).isEqualTo(0L);
            assertThat(result.count()).isEqualTo(0);
            assertThat(result.active()).isFalse();
            assertThat(result.score()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Record with partial primitive values - mixed scenario")
        void tupleToRecord_primitivePartialValues() {
            Tuple tuple = mockTuple(Map.of("id", 99, "active", true));
            PrimitiveRecord result = JpaResultMapper.map(tuple, PrimitiveRecord.class);
            assertThat(result.id()).isEqualTo(99L);
            assertThat(result.count()).isEqualTo(0);
            assertThat(result.active()).isTrue();
            assertThat(result.score()).isEqualTo(0.0);
        }
    }

    // ==================== Tuple list mapping ====================

    @Nested
    class ListMapping {

        @Test
        void mapList_returnsCorrectSize() {
            Tuple t1 = mockTuple(Map.of("name", "Alice"));
            Tuple t2 = mockTuple(Map.of("name", "Bob"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) (List<?>) JpaResultMapper.map(List.of(t1, t2), Map.class);
            assertThat(result).hasSize(2);
        }

        @Test
        void mapList_nullReturnsEmpty() {
            List<Tuple> nullList = null;
            List<Map> result = JpaResultMapper.map(nullList, Map.class);
            assertThat(result).isEmpty();
        }

        @Test
        void mapList_emptyReturnsEmpty() {
            List<Map> result = JpaResultMapper.map(Collections.emptyList(), Map.class);
            assertThat(result).isEmpty();
        }
    }

    // ==================== Simple type direct extraction ====================

    @Nested
    class SimpleTypeMapping {

        @Test
        void tupleToString_extractsFirstColumn() {
            Tuple tuple = mock(Tuple.class);
            when(tuple.get(0)).thenReturn("hello");
            String result = JpaResultMapper.map(tuple, String.class);
            assertThat(result).isEqualTo("hello");
        }

        @Test
        void tupleToLong_extractsAndConverts() {
            Tuple tuple = mock(Tuple.class);
            when(tuple.get(0)).thenReturn(42);
            Long result = JpaResultMapper.map(tuple, Long.class);
            assertThat(result).isEqualTo(42L);
        }

        @Test
        void tupleToInteger_extractsDirectly() {
            Tuple tuple = mock(Tuple.class);
            when(tuple.get(0)).thenReturn(42);
            Integer result = JpaResultMapper.map(tuple, Integer.class);
            assertThat(result).isEqualTo(42);
        }
    }

    // ==================== Interface Projection mapping ====================

    @Nested
    class ProjectionMapping {

        // ---- Test interfaces ----

        interface SimpleProjection {
            String getName();
            String getEmail();
        }

        interface UnderscoreProjection {
            String getUserName();
        }

        interface BooleanProjection {
            boolean isActive();
        }

        interface NumericProjection {
            long getId();
            int getAge();
            double getScore();
        }

        interface DefaultMethodProjection {
            String getFirstName();
            String getLastName();
            default String getFullName() {
                return getFirstName() + " " + getLastName();
            }
        }

        interface ScalarProjection {
            BigDecimal getTotalAmount();
            LocalDateTime getCreatedAt();
        }

        // ---- Happy path ----

        @Test
        @DisplayName("Standard getter mapping - getName/getEmail")
        void standardGetter_mapsCorrectly() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "email", "alice@test.com"));
            SimpleProjection result = JpaResultMapper.map(tuple, SimpleProjection.class);
            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isEqualTo("alice@test.com");
        }

        @Test
        @DisplayName("is-prefix getter mapping - isActive")
        void isGetter_mapsCorrectly() {
            Tuple tuple = mockTuple(Map.of("active", true));
            BooleanProjection result = JpaResultMapper.map(tuple, BooleanProjection.class);
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("Underscore alias to camelCase getter - user_name -> getUserName")
        void underscoreAlias_convertedToCamelCase() {
            Tuple tuple = mockTuple(Map.of("user_name", "alice_user"));
            UnderscoreProjection result = JpaResultMapper.map(tuple, UnderscoreProjection.class);
            assertThat(result.getUserName()).isEqualTo("alice_user");
        }

        @Test
        @DisplayName("Numeric type conversion - BigInteger -> long/int")
        void numericTypeConversion() {
            Tuple tuple = mockTuple(Map.of("id", BigInteger.valueOf(42), "age", 25, "score", 3.14f));
            NumericProjection result = JpaResultMapper.map(tuple, NumericProjection.class);
            assertThat(result.getId()).isEqualTo(42L);
            assertThat(result.getAge()).isEqualTo(25);
            assertThat(result.getScore()).isCloseTo(3.14, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("default method - invokes interface default implementation")
        void defaultMethod_callsActualImplementation() {
            Tuple tuple = mockTuple(Map.of("firstName", "Alice", "lastName", "Smith"));
            DefaultMethodProjection result = JpaResultMapper.map(tuple, DefaultMethodProjection.class);
            assertThat(result.getFirstName()).isEqualTo("Alice");
            assertThat(result.getLastName()).isEqualTo("Smith");
            assertThat(result.getFullName()).isEqualTo("Alice Smith");
        }

        @Test
        @DisplayName("Complex type mapping - BigDecimal / LocalDateTime")
        void complexTypeMapping() {
            Timestamp ts = Timestamp.valueOf("2024-06-15 10:30:00");
            Tuple tuple = mockTuple(Map.of("totalAmount", new BigDecimal("99.99"), "createdAt", ts));
            ScalarProjection result = JpaResultMapper.map(tuple, ScalarProjection.class);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
            assertThat(result.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30, 0));
        }

        // ---- Null safety and primitive default values ----

        @Test
        @DisplayName("Primitive return type with null column - returns default value instead of NPE")
        void primitiveReturnNull_returnsDefaultValue() {
            Tuple tuple = mockTuple(Map.of("name", "placeholder"));
            // id/age/score columns don't exist, values are null
            when(tuple.get("id")).thenReturn(null);
            when(tuple.get("age")).thenReturn(null);
            when(tuple.get("score")).thenReturn(null);
            NumericProjection result = JpaResultMapper.map(tuple, NumericProjection.class);
            assertThat(result.getId()).isEqualTo(0L);
            assertThat(result.getAge()).isEqualTo(0);
            assertThat(result.getScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("boolean primitive with null - returns false")
        void booleanPrimitiveNull_returnsFalse() {
            Tuple tuple = mockTuple(Collections.emptyMap());
            BooleanProjection result = JpaResultMapper.map(tuple, BooleanProjection.class);
            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("Reference type with null column - returns null")
        void referenceTypeNull_returnsNull() {
            Tuple tuple = mockTuple(Collections.emptyMap());
            SimpleProjection result = JpaResultMapper.map(tuple, SimpleProjection.class);
            assertThat(result.getName()).isNull();
            assertThat(result.getEmail()).isNull();
        }

        // ---- Object methods ----

        @Test
        @DisplayName("equals - same proxy instance returns true")
        void equals_sameInstance_returnsTrue() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "email", "a@b.com"));
            SimpleProjection result = JpaResultMapper.map(tuple, SimpleProjection.class);
            assertThat(result.equals(result)).isTrue();
        }

        @Test
        @DisplayName("equals - different instances return false")
        void equals_differentInstance_returnsFalse() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "email", "a@b.com"));
            SimpleProjection r1 = JpaResultMapper.map(tuple, SimpleProjection.class);
            SimpleProjection r2 = JpaResultMapper.map(tuple, SimpleProjection.class);
            assertThat(r1.equals(r2)).isFalse();
        }

        @Test
        @DisplayName("hashCode - no NPE, returns stable value")
        void hashCode_doesNotThrowNPE() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "email", "a@b.com"));
            SimpleProjection result = JpaResultMapper.map(tuple, SimpleProjection.class);
            int h1 = result.hashCode();
            int h2 = result.hashCode();
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("toString - returns readable string")
        void toString_returnsReadableString() {
            Tuple tuple = mockTuple(Map.of("name", "Alice"));
            SimpleProjection result = JpaResultMapper.map(tuple, SimpleProjection.class);
            String str = result.toString();
            assertThat(str).contains("SimpleProjection");
            assertThat(str).contains("Alice");
        }

        // ---- Unmatched columns ----

        @Test
        @DisplayName("Getter with no matching column - reference type returns null")
        void unmatchedGetter_referenceType_returnsNull() {
            Tuple tuple = mockTuple(Map.of("name", "Alice"));
            SimpleProjection result = JpaResultMapper.map(tuple, SimpleProjection.class);
            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isNull();
        }

        // ---- Map interface priority ----

        @Test
        @DisplayName("Map.class uses mapToMap path, not Proxy")
        void mapInterface_goesToMapToMap_notProxy() {
            Tuple tuple = mockTuple(Map.of("name", "Alice"));
            @SuppressWarnings("unchecked")
            Map<String, Object> result = JpaResultMapper.map(tuple, Map.class);
            assertThat(result).containsEntry("name", "Alice");
            assertThat(result).isInstanceOf(Map.class);
            assertThat(java.lang.reflect.Proxy.isProxyClass(result.getClass())).isFalse();
        }

        // ---- List mapping ----

        @Test
        @DisplayName("List<Projection> - each row is an independent Proxy")
        void listMapping_eachRowIndependent() {
            Tuple t1 = mockTuple(Map.of("name", "Alice", "email", "a@b.com"));
            Tuple t2 = mockTuple(Map.of("name", "Bob", "email", "b@b.com"));
            List<SimpleProjection> result = JpaResultMapper.map(List.of(t1, t2), SimpleProjection.class);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Alice");
            assertThat(result.get(1).getName()).isEqualTo("Bob");
        }
    }

    // ==================== resolvePropertyName tests ====================

    @Nested
    class ResolvePropertyName {

        @Test
        @DisplayName("getXxx -> xxx (standard getter)")
        void getPrefix_decapitalized() {
            assertThat(JpaResultMapper.resolvePropertyName("getName")).isEqualTo("name");
            assertThat(JpaResultMapper.resolvePropertyName("getEmail")).isEqualTo("email");
        }

        @Test
        @DisplayName("isXxx -> xxx (boolean getter)")
        void isPrefix_decapitalized() {
            assertThat(JpaResultMapper.resolvePropertyName("isActive")).isEqualTo("active");
            assertThat(JpaResultMapper.resolvePropertyName("isEnabled")).isEqualTo("enabled");
        }

        @Test
        @DisplayName("getURL -> URL (consecutive uppercase preserved per JavaBean spec)")
        void consecutiveUpperCase_preserved() {
            assertThat(JpaResultMapper.resolvePropertyName("getURL")).isEqualTo("URL");
            assertThat(JpaResultMapper.resolvePropertyName("getHTTPStatus")).isEqualTo("HTTPStatus");
        }

        @Test
        @DisplayName("Bare get/is - no truncation")
        void bareGetOrIs_noTruncation() {
            assertThat(JpaResultMapper.resolvePropertyName("get")).isEqualTo("get");
            assertThat(JpaResultMapper.resolvePropertyName("is")).isEqualTo("is");
        }

        @Test
        @DisplayName("No prefix method name - returned as-is")
        void noPrefix_returnAsIs() {
            assertThat(JpaResultMapper.resolvePropertyName("name")).isEqualTo("name");
            assertThat(JpaResultMapper.resolvePropertyName("fetchData")).isEqualTo("fetchData");
        }
    }

    // ==================== Constructor metadata ClassValue cache tests ====================

    @Nested
    class ConstructorMetaCache {

        /** Parameterized constructor only, used to verify cache behavior */
        public static class CacheTestDto {
            private final String name;
            private final int age;

            @ConstructorProperties({"name", "age"})
            public CacheTestDto(String name, int age) {
                this.name = name;
                this.age = age;
            }

            public String getName() { return name; }
            public int getAge() { return age; }
        }

        @Test
        @DisplayName("Mapping same DTO type consecutively - ClassValue cache hit, returns same ConstructorMeta instance")
        void should_cacheConstructorMeta_when_mappingSameTypeTwice() {
            Tuple t1 = mockTuple(Map.of("name", "Alice", "age", 25));
            Tuple t2 = mockTuple(Map.of("name", "Bob", "age", 30));

            // First mapping: triggers ClassValue.computeValue
            CacheTestDto r1 = JpaResultMapper.map(t1, CacheTestDto.class);
            // Second mapping: should hit cache
            CacheTestDto r2 = JpaResultMapper.map(t2, CacheTestDto.class);

            // Verify mapping results are correct
            assertThat(r1.getName()).isEqualTo("Alice");
            assertThat(r1.getAge()).isEqualTo(25);
            assertThat(r2.getName()).isEqualTo("Bob");
            assertThat(r2.getAge()).isEqualTo(30);

            // Verify ClassValue returns same cached instance for the same Class (core ClassValue semantics)
            var meta1 = JpaResultMapper.constructorCache().get(CacheTestDto.class);
            var meta2 = JpaResultMapper.constructorCache().get(CacheTestDto.class);
            assertThat(meta1).isSameAs(meta2);

            // Verify cached metadata content is correct
            assertThat(meta1.ctor().getDeclaringClass()).isEqualTo(CacheTestDto.class);
            assertThat(meta1.paramNames()).containsExactly("name", "age");
            assertThat(meta1.paramTypes()).containsExactly(String.class, int.class);
        }

        /** Multi-param constructor DTO (different params than CacheTestDto) */
        public static class AnotherCacheDto {
            private final String email;

            @ConstructorProperties({"email"})
            public AnotherCacheDto(String email) {
                this.email = email;
            }

            public String getEmail() { return email; }
        }

        @Test
        @DisplayName("Different DTO types - ClassValue caches independently, no interference")
        void should_cacheIndependently_when_differentDtoTypes() {
            Tuple t1 = mockTuple(Map.of("name", "Alice", "age", 25));
            Tuple t2 = mockTuple(Map.of("email", "a@b.com"));

            CacheTestDto r1 = JpaResultMapper.map(t1, CacheTestDto.class);
            AnotherCacheDto r2 = JpaResultMapper.map(t2, AnotherCacheDto.class);

            assertThat(r1.getName()).isEqualTo("Alice");
            assertThat(r2.getEmail()).isEqualTo("a@b.com");

            var meta1 = JpaResultMapper.constructorCache().get(CacheTestDto.class);
            var meta2 = JpaResultMapper.constructorCache().get(AnotherCacheDto.class);
            assertThat(meta1).isNotSameAs(meta2);
            assertThat(meta1.paramNames()).containsExactly("name", "age");
            assertThat(meta2.paramNames()).containsExactly("email");
        }
    }

    // ==================== Additional convertValue edge case tests ====================

    @Nested
    class ConvertValueEdgeCases {

        @Test
        void numberToString() {
            String result = JpaResultMapper.convertValue(42, String.class);
            assertThat(result).isEqualTo("42");
        }

        @Test
        void sqlDateToLocalDateTime() {
            java.sql.Date sqlDate = java.sql.Date.valueOf("2024-06-15");
            LocalDateTime result = JpaResultMapper.convertValue(sqlDate, LocalDateTime.class);
            assertThat(result.toLocalDate()).isEqualTo(LocalDate.of(2024, 6, 15));
        }

        @Test
        void sqlTimeToLocalTime() {
            java.sql.Time sqlTime = java.sql.Time.valueOf("10:30:45");
            LocalTime result = JpaResultMapper.convertValue(sqlTime, LocalTime.class);
            assertThat(result).isEqualTo(LocalTime.of(10, 30, 45));
        }

        @Test
        void localDateTimeToLocalDate() {
            LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 10, 30);
            LocalDate result = JpaResultMapper.convertValue(ldt, LocalDate.class);
            assertThat(result).isEqualTo(LocalDate.of(2024, 6, 15));
        }

        @Test
        void ordinalOutOfRange_throwsException() {
            // Ordinal out of range throws IllegalArgumentException instead of silently returning wrong type
            assertThatThrownBy(() -> JpaResultMapper.convertValue(999, Color.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot convert");
        }

        @Test
        void incompatibleType_throwsException() {
            // Completely incompatible type combination should throw a clear exception
            assertThatThrownBy(() -> JpaResultMapper.convertValue("hello", java.util.List.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot convert")
                    .hasMessageContaining("String")
                    .hasMessageContaining("List");
        }

        @Test
        void primitiveBoolean_acceptsBoxedBoolean() {
            // boolean.class <- Boolean should work correctly
            Boolean boxed = Boolean.TRUE;
            Object result = JpaResultMapper.convertValue(boxed, boolean.class);
            assertThat(result).isEqualTo(true);
        }

        @Test
        void primitiveInt_acceptsBoxedInteger() {
            // int.class <- Integer should work correctly
            Integer boxed = 42;
            Object result = JpaResultMapper.convertValue(boxed, int.class);
            assertThat(result).isEqualTo(42);
        }

        @Test
        void primitiveLong_acceptsBoxedLong() {
            Long boxed = 100L;
            Object result = JpaResultMapper.convertValue(boxed, long.class);
            assertThat(result).isEqualTo(100L);
        }
    }

    // ==================== Constructor-based mapping ====================

    @Nested
    class ConstructorMapping {

        // ---- Test inner classes ----

        /** Parameterized constructor only, simulating Kotlin data class / immutable DTO */
        public static class ImmutableDto {
            private final String name;
            private final String email;

            @ConstructorProperties({"name", "email"})
            public ImmutableDto(String name, String email) {
                this.name = name;
                this.email = email;
            }

            public String getName() { return name; }
            public String getEmail() { return email; }
        }

        /** Underscore alias -> camelCase param name */
        public static class UnderscoreCtorDto {
            private final String userName;
            private final String emailAddress;

            @ConstructorProperties({"userName", "emailAddress"})
            public UnderscoreCtorDto(String userName, String emailAddress) {
                this.userName = userName;
                this.emailAddress = emailAddress;
            }

            public String getUserName() { return userName; }
            public String getEmailAddress() { return emailAddress; }
        }

        /** Constructor requiring type conversion */
        public static class TypeConvertCtorDto {
            private final Long id;
            private final String name;

            @ConstructorProperties({"id", "name"})
            public TypeConvertCtorDto(Long id, String name) {
                this.id = id;
                this.name = name;
            }

            public Long getId() { return id; }
            public String getName() { return name; }
        }

        /** With @ConstructorProperties annotation */
        public static class AnnotatedDto {
            private final String userName;
            private final int age;

            @ConstructorProperties({"userName", "age"})
            public AnnotatedDto(String userName, int age) {
                this.userName = userName;
                this.age = age;
            }

            public String getUserName() { return userName; }
            public int getAge() { return age; }
        }

        /** Constructor with all primitive type params */
        public static class PrimitiveDto {
            private final long id;
            private final int count;
            private final boolean active;
            private final double score;

            @ConstructorProperties({"id", "count", "active", "score"})
            public PrimitiveDto(long id, int count, boolean active, double score) {
                this.id = id;
                this.count = count;
                this.active = active;
                this.score = score;
            }

            public long getId() { return id; }
            public int getCount() { return count; }
            public boolean isActive() { return active; }
            public double getScore() { return score; }
        }

        /** Multiple constructors, verifies the one with most params is selected */
        public static class MultiCtorDto {
            private final String name;
            private final String email;
            private final int age;

            @ConstructorProperties({"name"})
            public MultiCtorDto(String name) {
                this(name, null, 0);
            }

            @ConstructorProperties({"name", "email", "age"})
            public MultiCtorDto(String name, String email, int age) {
                this.name = name;
                this.email = email;
                this.age = age;
            }

            public String getName() { return name; }
            public String getEmail() { return email; }
            public int getAge() { return age; }
        }

        /** Both no-arg and parameterized constructors, verifies backward-compatible BeanWrapper path */
        public static class BothCtorsDto {
            private String name;

            public BothCtorsDto() {}

            public BothCtorsDto(String name) {
                this.name = name;
            }

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
        }

        // ---- #1 Happy path: basic two-field mapping ----

        @Test
        @DisplayName("Constructor mapping - basic two-field mapping")
        void basicTwoFieldMapping() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "email", "a@b"));
            ImmutableDto result = JpaResultMapper.map(tuple, ImmutableDto.class);
            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isEqualTo("a@b");
        }

        // ---- #2 Happy path: underscore alias to camelCase ----

        @Test
        @DisplayName("Constructor mapping - underscore alias to camelCase")
        void underscoreAliasToCamelCase() {
            Tuple tuple = mockTuple(Map.of("user_name", "alice", "email_address", "a@b"));
            UnderscoreCtorDto result = JpaResultMapper.map(tuple, UnderscoreCtorDto.class);
            assertThat(result.getUserName()).isEqualTo("alice");
            assertThat(result.getEmailAddress()).isEqualTo("a@b");
        }

        // ---- #3 Happy path: type conversion Integer->Long ----

        @Test
        @DisplayName("Constructor mapping - type conversion Integer->Long")
        void typeConversion_integerToLong() {
            Tuple tuple = mockTuple(Map.of("id", 42, "name", "Alice"));
            TypeConvertCtorDto result = JpaResultMapper.map(tuple, TypeConvertCtorDto.class);
            assertThat(result.getId()).isEqualTo(42L);
            assertThat(result.getName()).isEqualTo("Alice");
        }

        // ---- #4 Happy path: @ConstructorProperties resolution ----

        @Test
        @DisplayName("Constructor mapping - @ConstructorProperties resolves param names")
        void constructorProperties_resolvesNames() {
            Tuple tuple = mockTuple(Map.of("userName", "Alice", "age", 25));
            AnnotatedDto result = JpaResultMapper.map(tuple, AnnotatedDto.class);
            assertThat(result.getUserName()).isEqualTo("Alice");
            assertThat(result.getAge()).isEqualTo(25);
        }

        // ---- #5 Happy path: List mapping constructs each row independently ----

        @Test
        @DisplayName("Constructor mapping - List mapping constructs each row independently")
        void listMapping_eachRowIndependent() {
            Tuple t1 = mockTuple(Map.of("name", "Alice", "email", "a@b"));
            Tuple t2 = mockTuple(Map.of("name", "Bob", "email", "b@b"));
            List<ImmutableDto> result = JpaResultMapper.map(List.of(t1, t2), ImmutableDto.class);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Alice");
            assertThat(result.get(1).getName()).isEqualTo("Bob");
        }

        // ---- #6 Boundary: missing reference type param ----

        @Test
        @DisplayName("Constructor mapping - missing reference type param passes null")
        void missingReferenceParam_passesNull() {
            Tuple tuple = mockTuple(Map.of("name", "Alice"));
            ImmutableDto result = JpaResultMapper.map(tuple, ImmutableDto.class);
            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isNull();
        }

        // ---- #7 Boundary: all primitive params missing ----

        @Test
        @DisplayName("Constructor mapping - all primitive params missing uses default values")
        void allPrimitivesMissing_usesDefaults() {
            Tuple tuple = mockTuple(Collections.emptyMap());
            PrimitiveDto result = JpaResultMapper.map(tuple, PrimitiveDto.class);
            assertThat(result.getId()).isEqualTo(0L);
            assertThat(result.getCount()).isEqualTo(0);
            assertThat(result.isActive()).isFalse();
            assertThat(result.getScore()).isEqualTo(0.0);
        }

        // ---- #8 Boundary: mixed null and non-null primitives ----

        @Test
        @DisplayName("Constructor mapping - partial primitive values with some missing")
        void mixedPrimitives_partialValues() {
            Tuple tuple = mockTuple(Map.of("id", 99));
            PrimitiveDto result = JpaResultMapper.map(tuple, PrimitiveDto.class);
            assertThat(result.getId()).isEqualTo(99L);
            assertThat(result.getCount()).isEqualTo(0);
            assertThat(result.isActive()).isFalse();
            assertThat(result.getScore()).isEqualTo(0.0);
        }

        // ---- #9 Boundary: extra Tuple columns silently ignored ----

        @Test
        @DisplayName("Constructor mapping - extra Tuple columns silently ignored")
        void extraTupleColumns_silentlyIgnored() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "email", "a@b", "extra", "ignored"));
            ImmutableDto result = JpaResultMapper.map(tuple, ImmutableDto.class);
            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isEqualTo("a@b");
        }

        // ---- #10 Boundary: all constructor params unmatched ----

        @Test
        @DisplayName("Constructor mapping - all params unmatched passes null")
        void allParamsUnmatched_allNull() {
            Tuple tuple = mockTuple(Map.of("unrelated", "x"));
            ImmutableDto result = JpaResultMapper.map(tuple, ImmutableDto.class);
            assertThat(result.getName()).isNull();
            assertThat(result.getEmail()).isNull();
        }

        // ---- #11 Selection: multiple constructors selects most params ----

        @Test
        @DisplayName("Constructor mapping - multiple constructors selects most params")
        void multipleConstructors_selectsMostParams() {
            Tuple tuple = mockTuple(Map.of("name", "Alice", "email", "a@b", "age", 30));
            MultiCtorDto result = JpaResultMapper.map(tuple, MultiCtorDto.class);
            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isEqualTo("a@b");
            assertThat(result.getAge()).isEqualTo(30);
        }

        // ---- #12 Compatibility: default constructor uses BeanWrapper ----

        @Test
        @DisplayName("Constructor mapping - default constructor uses BeanWrapper path")
        void hasDefaultConstructor_usesBeanWrapper() {
            Tuple tuple = mockTuple(Map.of("name", "Alice"));
            BothCtorsDto result = JpaResultMapper.map(tuple, BothCtorsDto.class);
            assertThat(result.getName()).isEqualTo("Alice");
        }

        // ---- resolveParameterNames unit tests ----

        /** No @ConstructorProperties, relies on -parameters compiler flag to resolve param names */
        public static class NoAnnotationDto {
            private final String name;
            private final int age;

            public NoAnnotationDto(String name, int age) {
                this.name = name;
                this.age = age;
            }

            public String getName() { return name; }
            public int getAge() { return age; }
        }

        @Test
        @DisplayName("resolveParameterNames - @ConstructorProperties takes priority")
        void resolveParameterNames_prefersAnnotation() throws Exception {
            Constructor<?> ctor = ImmutableDto.class.getDeclaredConstructor(String.class, String.class);
            String[] names = JpaResultMapper.resolveParameterNames(ctor);
            assertThat(names).containsExactly("name", "email");
        }

        @Test
        @DisplayName("Constructor mapping - fallback to -parameters flag when no @ConstructorProperties")
        void noAnnotation_fallbackToParametersFlag() {
            Tuple tuple = mockTuple(Map.of("name", "Bob", "age", 30));
            NoAnnotationDto result = JpaResultMapper.map(tuple, NoAnnotationDto.class);
            assertThat(result.getName()).isEqualTo("Bob");
            assertThat(result.getAge()).isEqualTo(30);
        }

        @Test
        @DisplayName("resolveParameterNames - fallback to -parameters path")
        void resolveParameterNames_fallbackToParameters() throws Exception {
            Constructor<?> ctor = NoAnnotationDto.class.getDeclaredConstructor(String.class, int.class);
            String[] names = JpaResultMapper.resolveParameterNames(ctor);
            assertThat(names).containsExactly("name", "age");
        }
    }

    // ==================== T-06 ConstructorMeta ClassValue cache boundary tests ====================

    @Nested
    class ConstructorMetaCacheEdgeCases {

        /** Multi-param constructor */
        public static class ManyParamsDto {
            private final String a, b, c, d, e, f, g, h;

            @ConstructorProperties({"a", "b", "c", "d", "e", "f", "g", "h"})
            public ManyParamsDto(String a, String b, String c, String d,
                                 String e, String f, String g, String h) {
                this.a = a; this.b = b; this.c = c; this.d = d;
                this.e = e; this.f = f; this.g = g; this.h = h;
            }

            public String getA() { return a; }
            public String getB() { return b; }
            public String getC() { return c; }
            public String getD() { return d; }
            public String getE() { return e; }
            public String getF() { return f; }
            public String getG() { return g; }
            public String getH() { return h; }
        }

        @Test
        @DisplayName("8-param constructor - cached metadata is correct")
        void should_cacheManyParamConstructor() {
            Tuple tuple = mockTuple(Map.of(
                    "a", "1", "b", "2", "c", "3", "d", "4",
                    "e", "5", "f", "6", "g", "7", "h", "8"
            ));

            ManyParamsDto result = JpaResultMapper.map(tuple, ManyParamsDto.class);
            assertThat(result.getA()).isEqualTo("1");
            assertThat(result.getH()).isEqualTo("8");

            var meta = JpaResultMapper.constructorCache().get(ManyParamsDto.class);
            assertThat(meta.paramNames()).hasSize(8);
            assertThat(meta.paramTypes()).hasSize(8);
        }

        @Test
        @DisplayName("Batch mapping - cache avoids repeated reflection")
        void should_useCacheForBatchMapping() {
            List<Tuple> tuples = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                tuples.add(mockTuple(Map.of("name", "User" + i, "age", i)));
            }

            List<ConstructorMapping.ImmutableDto> results = JpaResultMapper.map(tuples, ConstructorMapping.ImmutableDto.class);

            assertThat(results).hasSize(100);
            assertThat(results.get(0).getName()).isEqualTo("User0");
            assertThat(results.get(99).getName()).isEqualTo("User99");

            // Verify cache is effective (same Class only triggers computeValue once)
            var meta1 = JpaResultMapper.constructorCache().get(ConstructorMapping.ImmutableDto.class);
            var meta2 = JpaResultMapper.constructorCache().get(ConstructorMapping.ImmutableDto.class);
            assertThat(meta1).isSameAs(meta2);
        }

        @Test
        @DisplayName("Record type - ClassValue cache is independent from regular DTO")
        void should_cacheRecordSeparately() {
            Tuple dtoTuple = mockTuple(Map.of("name", "A", "email", "a@b"));
            Tuple recordTuple = mockTuple(Map.of("name", "B", "email", "b@c"));

            ConstructorMapping.ImmutableDto dto = JpaResultMapper.map(dtoTuple, ConstructorMapping.ImmutableDto.class);
            RecordMapping.UserRecord record = JpaResultMapper.map(recordTuple, RecordMapping.UserRecord.class);

            assertThat(dto.getName()).isEqualTo("A");
            assertThat(record.name()).isEqualTo("B");

            var dtoMeta = JpaResultMapper.constructorCache().get(ConstructorMapping.ImmutableDto.class);
            var recordMeta = JpaResultMapper.constructorCache().get(RecordMapping.UserRecord.class);
            assertThat(dtoMeta).isNotSameAs(recordMeta);
        }

        @Test
        @DisplayName("Cached metadata - constructor accessibility is correctly set")
        void should_setAccessible_onCachedConstructor() {
            var meta = JpaResultMapper.constructorCache().get(ConstructorMapping.ImmutableDto.class);
            assertThat(meta.ctor().canAccess(null) || meta.ctor().trySetAccessible()).isTrue();
        }
    }

    // ==================== convertValue additional boundary tests ====================

    @Nested
    class ConvertValueBoundary {

        @Test
        void bigDecimalToLong() {
            Long result = JpaResultMapper.convertValue(new BigDecimal("999"), Long.class);
            assertThat(result).isEqualTo(999L);
        }

        @Test
        void bigIntegerToInt() {
            Integer result = JpaResultMapper.convertValue(BigInteger.valueOf(42), Integer.class);
            assertThat(result).isEqualTo(42);
        }

        @Test
        void nullToPrimitiveLong() {
            // convertValue returns null for null, but the caller handles primitive defaults
            assertThat(JpaResultMapper.convertValue(null, Long.class)).isNull();
        }

        @Test
        void booleanToString() {
            String result = JpaResultMapper.convertValue(true, String.class);
            assertThat(result).isEqualTo("true");
        }

        @Test
        void timestampToLocalTime() {
            Timestamp ts = Timestamp.valueOf("2024-06-15 10:30:45");
            LocalTime result = JpaResultMapper.convertValue(ts, LocalTime.class);
            assertThat(result).isEqualTo(LocalTime.of(10, 30, 45));
        }

        @Test
        void stringFallbackToCast() {
            // convertValue for unsupported String->Number conversion uses fallback (direct cast)
            // Verify no unexpected behavior beyond exception
            Object result = JpaResultMapper.convertValue("hello", Object.class);
            assertThat(result).isEqualTo("hello");
        }

        @Test
        void numberToString_viaToString() {
            // Number -> String via value.toString()
            String result = JpaResultMapper.convertValue(3.14, String.class);
            assertThat(result).isEqualTo("3.14");
        }

        @Test
        void longToInteger() {
            Integer result = JpaResultMapper.convertValue(42L, Integer.class);
            assertThat(result).isEqualTo(42);
        }
    }
}
