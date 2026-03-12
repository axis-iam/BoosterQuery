package com.chaosguide.jpa.booster.repository.query;

import com.chaosguide.jpa.booster.annotation.BoosterQuery;
import com.chaosguide.jpa.booster.dto.UserDTO;
import com.chaosguide.jpa.booster.entity.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests return type inference logic of BoosterSqlRepositoryQuery.resolveElementType.
 * <p>
 * Covered scenarios:
 * - Direct DTO / Record / interface projection return should not fall back to entity class
 * - Simple types (String, BigDecimal, LocalDateTime) return the declared type directly
 * - Raw type containers (List, Page without generics) should throw exception
 * - Regression protection for generic type extraction from container types
 */
class BoosterSqlRepositoryQueryElementTypeTest {

    // ==================== Test Helpers ====================

    /**
     * Invokes BoosterSqlRepositoryQuery.resolveElementType(Method, BoosterQuery) via reflection
     */
    private static Class<?> invokeResolveElementType(Method method,
                                                      BoosterQuery boosterQuery) throws Exception {
        Method resolveMethod = BoosterSqlRepositoryQuery.class.getDeclaredMethod(
                "resolveElementType", Method.class, BoosterQuery.class);
        resolveMethod.setAccessible(true);
        return (Class<?>) resolveMethod.invoke(null, method, boosterQuery);
    }

    // ==================== Test Repository Interface ====================

    @SuppressWarnings({"unused", "rawtypes"})
    interface TestUserRepository extends Repository<TestUser, Long> {
        // Direct DTO return (original bug scenario)
        @BoosterQuery("SELECT name, email FROM t_test_user LIMIT 1")
        UserDTO queryOneDto();

        // Direct Record return
        @BoosterQuery("SELECT SUM(1) AS totalRevenue, COUNT(*) AS orderCount FROM t_test_user")
        SummaryRecord queryOneRecord();

        // Direct interface projection return
        @BoosterQuery("SELECT SUM(1) AS totalRevenue, COUNT(*) AS orderCount FROM t_test_user")
        SummaryProjection queryOneProjection();

        // Direct entity class return
        @BoosterQuery("SELECT * FROM t_test_user LIMIT 1")
        TestUser queryOneEntity();

        // Container types - should extract via generics
        @BoosterQuery("SELECT name, email FROM t_test_user")
        Page<UserDTO> queryPageDto(Pageable pageable);

        @BoosterQuery("SELECT name, email FROM t_test_user")
        List<UserDTO> queryListDto();

        @BoosterQuery("SELECT name, email FROM t_test_user LIMIT 1")
        Optional<UserDTO> queryOptionalDto();

        // Primitive types
        @BoosterQuery("SELECT COUNT(*) FROM t_test_user")
        long countQuery();

        @BoosterQuery("UPDATE t_test_user SET name = 'x'")
        int modifyQuery();

        // Map return
        @BoosterQuery("SELECT name, email FROM t_test_user LIMIT 1")
        Map<String, Object> queryOneMap();

        // Explicit resultType specified
        @BoosterQuery(value = "SELECT * FROM t_test_user LIMIT 1", resultType = UserDTO.class)
        TestUser queryWithExplicitResultType();

        // Simple types - String / BigDecimal / LocalDateTime
        @BoosterQuery("SELECT name FROM t_test_user LIMIT 1")
        String queryOneString();

        @BoosterQuery("SELECT SUM(amount) FROM t_order")
        BigDecimal queryTotalAmount();

        @BoosterQuery("SELECT MAX(created_at) FROM t_test_user")
        LocalDateTime queryLatestTime();

        // Raw type containers - should throw exception
        @BoosterQuery("SELECT * FROM t_test_user")
        List queryRawList();

        @BoosterQuery("SELECT * FROM t_test_user")
        Page queryRawPage(Pageable pageable);

        @BoosterQuery("SELECT * FROM t_test_user LIMIT 1")
        Optional queryRawOptional();
    }

    // ==================== Test DTO / Record / Interface ====================

    public record SummaryRecord(long totalRevenue, int orderCount) {}

    public interface SummaryProjection {
        long getTotalRevenue();
        int getOrderCount();
    }

    // ==================== Helper Methods ====================

    private static Method getMethod(String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return TestUserRepository.class.getMethod(name, paramTypes);
    }

    private static BoosterQuery getAnnotation(String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return getMethod(name, paramTypes).getAnnotation(BoosterQuery.class);
    }

    // ==================== Direct Return Type Tests (core fix scenarios) ====================

    @Test
    @DisplayName("Direct DTO return: elementType should be DTO class, not entity class")
    void directDto_shouldReturnDtoClass() throws Exception {
        Method method = getMethod("queryOneDto");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryOneDto"));
        assertEquals(UserDTO.class, result, "should return declared DTO type, not entity class");
    }

    @Test
    @DisplayName("Direct Record return: elementType should be Record class")
    void directRecord_shouldReturnRecordClass() throws Exception {
        Method method = getMethod("queryOneRecord");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryOneRecord"));
        assertEquals(SummaryRecord.class, result);
    }

    @Test
    @DisplayName("Direct interface projection return: elementType should be interface type")
    void directProjection_shouldReturnInterfaceClass() throws Exception {
        Method method = getMethod("queryOneProjection");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryOneProjection"));
        assertEquals(SummaryProjection.class, result);
    }

    @Test
    @DisplayName("Direct entity return: elementType should be entity class")
    void directEntity_shouldReturnEntityClass() throws Exception {
        Method method = getMethod("queryOneEntity");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryOneEntity"));
        assertEquals(TestUser.class, result);
    }

    @Test
    @DisplayName("Direct Map return: elementType should be Map type")
    void directMap_shouldReturnMapClass() throws Exception {
        Method method = getMethod("queryOneMap");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryOneMap"));
        assertEquals(Map.class, result);
    }

    // ==================== Simple Type Tests (boundary scenarios) ====================

    @Test
    @DisplayName("String return: elementType should be String.class")
    void directString_shouldReturnStringClass() throws Exception {
        Method method = getMethod("queryOneString");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryOneString"));
        assertEquals(String.class, result);
    }

    @Test
    @DisplayName("BigDecimal return: elementType should be BigDecimal.class")
    void directBigDecimal_shouldReturnBigDecimalClass() throws Exception {
        Method method = getMethod("queryTotalAmount");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryTotalAmount"));
        assertEquals(BigDecimal.class, result);
    }

    @Test
    @DisplayName("LocalDateTime return: elementType should be LocalDateTime.class")
    void directLocalDateTime_shouldReturnLocalDateTimeClass() throws Exception {
        Method method = getMethod("queryLatestTime");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryLatestTime"));
        assertEquals(LocalDateTime.class, result);
    }

    // ==================== Container Type Tests (regression protection) ====================

    @Test
    @DisplayName("Page<DTO> should extract DTO type from generics")
    void pageDto_shouldExtractGenericType() throws Exception {
        Method method = getMethod("queryPageDto", Pageable.class);
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryPageDto", Pageable.class));
        assertEquals(UserDTO.class, result);
    }

    @Test
    @DisplayName("List<DTO> should extract DTO type from generics")
    void listDto_shouldExtractGenericType() throws Exception {
        Method method = getMethod("queryListDto");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryListDto"));
        assertEquals(UserDTO.class, result);
    }

    @Test
    @DisplayName("Optional<DTO> should extract DTO type from generics")
    void optionalDto_shouldExtractGenericType() throws Exception {
        Method method = getMethod("queryOptionalDto");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryOptionalDto"));
        assertEquals(UserDTO.class, result);
    }

    // ==================== Raw Type Container Tests (defensive interception) ====================

    @Test
    @DisplayName("Raw type List should throw IllegalStateException")
    void rawList_shouldThrowException() throws Exception {
        Method method = getMethod("queryRawList");
        BoosterQuery bq = getAnnotation("queryRawList");

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> invokeResolveElementType(method, bq));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("List"),
                "exception message should contain container type name");
    }

    @Test
    @DisplayName("Raw type Page should throw IllegalStateException")
    void rawPage_shouldThrowException() throws Exception {
        Method method = getMethod("queryRawPage", Pageable.class);
        BoosterQuery bq = getAnnotation("queryRawPage", Pageable.class);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> invokeResolveElementType(method, bq));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Page"),
                "exception message should contain container type name");
    }

    @Test
    @DisplayName("Raw type Optional should throw IllegalStateException")
    void rawOptional_shouldThrowException() throws Exception {
        Method method = getMethod("queryRawOptional");
        BoosterQuery bq = getAnnotation("queryRawOptional");

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> invokeResolveElementType(method, bq));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Optional"),
                "exception message should contain container type name");
    }

    // ==================== Primitive Type Tests (regression protection) ====================

    @Test
    @DisplayName("long return: elementType should be Long.class")
    void longReturn_shouldReturnLongClass() throws Exception {
        Method method = getMethod("countQuery");
        Class<?> result = invokeResolveElementType(method, getAnnotation("countQuery"));
        assertEquals(Long.class, result);
    }

    @Test
    @DisplayName("int return: elementType should be Integer.class")
    void intReturn_shouldReturnIntegerClass() throws Exception {
        Method method = getMethod("modifyQuery");
        Class<?> result = invokeResolveElementType(method, getAnnotation("modifyQuery"));
        assertEquals(Integer.class, result);
    }

    // ==================== @BoosterQuery.resultType Precedence Tests ====================

    @Test
    @DisplayName("Explicit @BoosterQuery(resultType=...) overrides method return type")
    void explicitResultType_shouldOverrideReturnType() throws Exception {
        Method method = getMethod("queryWithExplicitResultType");
        Class<?> result = invokeResolveElementType(method, getAnnotation("queryWithExplicitResultType"));
        assertEquals(UserDTO.class, result, "annotation resultType should take precedence over method return type");
    }
}
