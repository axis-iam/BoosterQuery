package com.chaosguide.jpa.booster.rewrite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class BoosterSqlRewriterTest {

    @Test
    void should_removeNullCondition_when_orWithNullParam() {
        String sql = "select * from t_test_user u where (u.name = :name or u.email like :email)";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("email", "%@example.com");

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains("u.name = :name"));
        assertTrue(newSql.contains("u.email like :email"));
        assertFalse(result.params().containsKey("name"));
        assertEquals("%@example.com", result.params().get("email"));
    }

    @Test
    void should_removeJoinCondition_when_joinOnParamIsNull() {
        String sql = "select u.* from t_test_user u join t_test_user x on u.id = x.id and x.name = :name where u.age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("age", 20);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertTrue(newSql.contains("u.id = x.id"));
        assertFalse(newSql.contains("x.name = :name"));
        assertFalse(result.params().containsKey("name"));
        assertEquals(20, result.params().get("age"));
    }

    @Test
    void should_removeSubSelectCondition_when_paramIsNull() {
        String sql = "select * from t_test_user u where exists (" +
                "select 1 from t_test_user x where x.age > :age and x.name = :name)";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 25);
        params.put("name", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertTrue(newSql.contains("exists"));
        assertTrue(newSql.contains("x.age > :age"));
        assertFalse(newSql.contains("x.name = :name"));
        assertEquals(25, result.params().get("age"));
        assertFalse(result.params().containsKey("name"));
    }

    @Test
    void should_preserveIsNullPattern_when_paramIsNull() {
        String sql = "select * from t_test_user u where (:name is null or u.name = :name)";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertTrue(newSql.contains(":name is null"));
        assertFalse(newSql.contains("u.name = :name"));
        assertTrue(result.params().containsKey("name"), "param still referenced in SQL (:name IS NULL), should be retained");
        assertNull(result.params().get("name"), "retained param value should be null");
    }

    @Test
    void should_removeInCondition_when_collectionIsEmpty() {
        String sql = "select * from t_test_user u where u.id in (:ids)";
        Map<String, Object> params = new HashMap<>();
        params.put("ids", Collections.emptyList());

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains("u.id in"));
        assertFalse(result.params().containsKey("ids"));
    }

    @Test
    void should_removeBetweenCondition_when_oneBoundIsNull() {
        String sql = "select * from t_test_user u where u.age between :minAge and :maxAge";
        Map<String, Object> params = new HashMap<>();
        params.put("minAge", null);
        params.put("maxAge", 40);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains("between :minage and :maxage"));
        assertFalse(result.params().containsKey("minAge"));
        assertTrue(result.params().containsKey("maxAge"));
    }

    @Test
    void should_removeNestedConditions_when_allLeftBranchParamsNull() {
        String sql = "select * from t_test_user u where (u.name = :name and u.age > :age) or u.email like :email";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("age", null);
        params.put("email", "%@example.com");

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains("u.name = :name"));
        assertFalse(newSql.contains("u.age > :age"));
        assertTrue(newSql.contains("u.email like :email"));
        assertFalse(result.params().containsKey("name"));
        assertFalse(result.params().containsKey("age"));
        assertEquals("%@example.com", result.params().get("email"));
    }

    @Test
    void should_removeUpdateCondition_when_paramIsNull() {
        String sql = "update t_test_user set age = age + 1 where name = :name and age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Alice");
        params.put("age", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertTrue(newSql.contains("update t_test_user set age = age + 1"));
        assertTrue(newSql.contains("where name = :name"));
        assertFalse(newSql.contains("age > :age"));
        assertEquals("Alice", result.params().get("name"));
        assertFalse(result.params().containsKey("age"));
    }

    @Test
    void should_removeDeleteCondition_when_paramIsNull() {
        String sql = "delete from t_test_user where email like :email or age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("email", null);
        params.put("age", 30);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains("email like :email"));
        assertTrue(newSql.contains("age > :age"));
        assertFalse(result.params().containsKey("email"));
        assertEquals(30, result.params().get("age"));
    }

    // --- New Complex Test Cases ---

    @Test
    void testComplexNestedLogic() {
        // ((a and b) or (c and d)) and e
        // a=null, b=1, c=null, d=2, e=3 -> (b) or (d) and e ? No.
        // a=null -> (b)
        // c=null -> (d)
        // result: (b or d) and e
        String sql = "select * from t where ((col_a=:a and col_b=:b) or (col_c=:c and col_d=:d)) and col_e=:e";
        Map<String, Object> params = new HashMap<>();
        params.put("a", null);
        params.put("b", 1);
        params.put("c", null);
        params.put("d", 2);
        params.put("e", 3);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);
        String newSql = result.sql().toLowerCase(Locale.ROOT).replace(" ", ""); // Remove spaces for comparison
        
        // Expect: col_a removed, col_c removed.
        assertFalse(newSql.contains("col_a=:a"));
        assertTrue(newSql.contains("col_b=:b"));
        assertFalse(newSql.contains("col_c=:c"));
        assertTrue(newSql.contains("col_d=:d"));
        assertTrue(newSql.contains("col_e=:e"));
    }

    @Test
    void testExistsWithNullParam() {
        // EXISTS should be kept, but inner condition removed
        String sql = "select * from t_user u where exists (select 1 from t_order o where o.user_id = u.id and o.status = :status)";
        Map<String, Object> params = new HashMap<>();
        params.put("status", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);
        String newSql = result.sql().toLowerCase(Locale.ROOT).replace(" ", "");

        assertTrue(newSql.contains("exists"));
        assertTrue(newSql.contains("o.user_id=u.id"));
        assertFalse(newSql.contains("o.status=:status"));
        assertFalse(result.params().containsKey("status"));
    }

    @Test
    void testDeeplyNestedAndOr() {
        // (A or B) and (C or D)
        // A=null, C=null -> B and D
        String sql = "select * from t where (a=:a or b=:b) and (c=:c or d=:d)";
        Map<String, Object> params = new HashMap<>();
        params.put("a", null);
        params.put("b", 1);
        params.put("c", null);
        params.put("d", 2);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);
        String newSql = result.sql().toLowerCase(Locale.ROOT).replace(" ", "");

        assertFalse(newSql.contains("a=:a"));
        assertTrue(newSql.contains("b=:b"));
        assertFalse(newSql.contains("c=:c"));
        assertTrue(newSql.contains("d=:d"));
    }

    // ==================== extractParamNames unit tests ====================

    static Stream<Arguments> extractParamNamesProvider() {
        return Stream.of(
                Arguments.of("simple param", "SELECT * FROM t WHERE id = :id", Set.of("id")),
                Arguments.of("multiple params", "WHERE name = :name AND age > :age", Set.of("name", "age")),
                Arguments.of("IS NULL pattern", "WHERE (:name IS NULL)", Set.of("name")),
                Arguments.of("CASE WHEN", "SELECT CASE WHEN a > :minAge THEN 'x' END", Set.of("minAge")),
                Arguments.of("no params", "SELECT 1", Set.of()),
                Arguments.of("duplicate param", "WHERE :p IS NULL OR c = :p", Set.of("p")),
                Arguments.of("PostgreSQL double colon (not a param)", "SELECT '::cast'", Set.of()),
                Arguments.of("underscore-prefixed param", "WHERE :_name IS NULL OR name = :_name", Set.of("_name"))
        );
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("extractParamNamesProvider")
    void testExtractParamNames(String scenario, String sql, Set<String> expected) {
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(expected, result, scenario);
    }

    @Test
    void testExtractParamNames_nullAndEmptyInput() {
        assertEquals(Set.of(), BoosterSqlRewriter.extractParamNames(null));
        assertEquals(Set.of(), BoosterSqlRewriter.extractParamNames(""));
    }

    // ==================== stripSqlComments + extractParamNames comment stripping tests ====================

    @Test
    void should_ignoreParamInLineComment() {
        String sql = "SELECT * FROM t WHERE id = :id -- :oldParam";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_ignoreParamInBlockComment() {
        String sql = "SELECT * FROM t /* :debug */ WHERE id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_preserveParamInString() {
        String sql = "SELECT * FROM t WHERE name = ':notParam' AND id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleMultilineBlockComment() {
        String sql = """
                SELECT * FROM t
                /* This is a multiline comment
                   :hiddenParam
                   :anotherHidden */
                WHERE id = :id""";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_stripLineComment_preservingNewline() {
        // stripSqlComments is package-visible, tested directly
        String sql = "SELECT * FROM t -- comment\nWHERE id = :id";
        String stripped = BoosterSqlRewriter.stripSqlComments(sql);
        // Line comment stripped, newline preserved (skipped to newline but not consumed), subsequent content unaffected
        assertTrue(stripped.contains("WHERE id = :id"));
        assertFalse(stripped.contains("comment"));
    }

    @Test
    void should_stripBlockComment_acrossLines() {
        String sql = "SELECT /* block comment\nmultiline */ * FROM t";
        String stripped = BoosterSqlRewriter.stripSqlComments(sql);
        assertEquals("SELECT  * FROM t", stripped);
    }

    @Test
    void should_preserveStringLiteralStructure() {
        // -- and /* inside string literals should not be treated as comments
        String sql = "SELECT * FROM t WHERE name = '--not comment' AND id = :id";
        String stripped = BoosterSqlRewriter.stripSqlComments(sql);
        // Literal content replaced with spaces, but quote structure preserved, subsequent SQL unaffected
        assertTrue(stripped.contains("AND id = :id"), "SQL structure outside literals should be preserved");
        assertEquals(Set.of("id"), BoosterSqlRewriter.extractParamNames(sql));
    }

    @Test
    void should_handleEscapedQuoteInStringLiteral() {
        // Escaped single quotes '' should not prematurely end the string literal
        String sql = "SELECT * FROM t WHERE name = 'it''s -- :fake' AND id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleUnclosedBlockComment() {
        // Unclosed block comment: entire remaining content treated as comment
        String sql = "SELECT * FROM t WHERE id = :id /* unclosed comment :hidden";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    // ==================== stripSqlComments boundary tests ====================

    @Test
    void should_handleConsecutiveLineComments() {
        String sql = "SELECT * FROM t -- :fake1\n-- :fake2\nWHERE id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleBlockFollowedByLineComment() {
        String sql = "SELECT /* :a */ * FROM t -- :b\nWHERE id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleLineCommentAtEofWithoutNewline() {
        String sql = "SELECT * FROM t WHERE id = :id -- trailing comment :fake";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleEmptyBlockComment() {
        String sql = "SELECT /**/ * FROM t WHERE id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_preserveBlockCommentSyntaxInStringLiteral() {
        String sql = "SELECT * FROM t WHERE name = '/* :fake */' AND id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleEmptyStringLiteral() {
        String sql = "SELECT * FROM t WHERE name = '' AND id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleMultipleStringLiteralsWithParamsBetween() {
        String sql = "SELECT * FROM t WHERE a = ':fake1' AND b = :real AND c = ':fake2'";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("real"), result);
    }

    @Test
    void should_returnEmptyWhenAllParamsInComments() {
        String sql = "SELECT 1 -- :hidden\n/* :another */";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of(), result);
    }

    // ==================== filterParams integration tests ====================

    @Test
    void testFilterParams_conditionFullyRemoved_paramExcluded() {
        // Condition fully removed, rewritten SQL no longer references the param
        Map<String, Object> params = new HashMap<>();
        params.put("age", null);
        params.put("name", "Alice");
        String rewrittenSql = "SELECT * FROM t";

        Map<String, Object> result = BoosterSqlRewriter.filterParams(params, Set.of("age"), rewrittenSql);

        assertFalse(result.containsKey("age"), "condition removed, param should not be retained");
        assertEquals("Alice", result.get("name"));
    }

    @Test
    void testFilterParams_isNullPatternRetained_paramKept() {
        // IS NULL pattern preserved, rewritten SQL still references the param
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        String rewrittenSql = "SELECT * FROM t WHERE (:name IS NULL)";

        Map<String, Object> result = BoosterSqlRewriter.filterParams(params, Set.of("name"), rewrittenSql);

        assertTrue(result.containsKey("name"), "SQL still references :name, param should be retained");
        assertNull(result.get("name"), "retained param value should be null");
    }

    @Test
    void testFilterParams_mixedRetainAndRemove() {
        // Mixed scenario: :name still in SQL, :age already removed
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("age", null);
        params.put("status", "active");
        String rewrittenSql = "SELECT * FROM t WHERE (:name IS NULL OR name = :name)";

        Map<String, Object> result = BoosterSqlRewriter.filterParams(params, Set.of("name", "age"), rewrittenSql);

        assertTrue(result.containsKey("name"), "SQL still references :name, should be retained");
        assertNull(result.get("name"));
        assertFalse(result.containsKey("age"), "SQL no longer references :age, should be removed");
        assertEquals("active", result.get("status"));
    }

    @Test
    void testFilterParams_blankStringRetained_normalizedToNull() {
        // Blank strings are treated as "null params" by collectNullParams.
        // If still referenced in SQL, should be normalized to null instead of retaining the blank value
        Map<String, Object> params = new HashMap<>();
        params.put("name", "   ");
        String rewrittenSql = "SELECT * FROM t WHERE (:name IS NULL OR name = :name)";

        Map<String, Object> result = BoosterSqlRewriter.filterParams(params, Set.of("name"), rewrittenSql);

        assertTrue(result.containsKey("name"), "SQL still references :name, should be retained");
        assertNull(result.get("name"), "blank string should be normalized to null");
    }

    @Test
    void testFilterParams_emptyCollectionRetained_normalizedToNull() {
        // Empty collections are treated as "null params" by collectNullParams.
        // If still referenced in SQL, should be normalized to null
        Map<String, Object> params = new HashMap<>();
        params.put("ids", Collections.emptyList());
        String rewrittenSql = "SELECT * FROM t WHERE (:ids IS NULL OR id IN (:ids))";

        Map<String, Object> result = BoosterSqlRewriter.filterParams(params, Set.of("ids"), rewrittenSql);

        assertTrue(result.containsKey("ids"), "SQL still references :ids, should be retained");
        assertNull(result.get("ids"), "empty collection should be normalized to null");
    }

    @Test
    void testFilterParams_underscorePrefixedParam_retained() {
        // Underscore-prefixed param names should be retained when still referenced in SQL
        Map<String, Object> params = new HashMap<>();
        params.put("_name", null);
        String rewrittenSql = "SELECT * FROM t WHERE (:_name IS NULL OR name = :_name)";

        Map<String, Object> result = BoosterSqlRewriter.filterParams(params, Set.of("_name"), rewrittenSql);

        assertTrue(result.containsKey("_name"), "SQL still references :_name, should be retained");
        assertNull(result.get("_name"));
    }

    // ==================== rewrite() boundary tests ====================

    @Test
    void should_rewriteSuccessfully_when_allSelectParamsNull() {
        String sql = "SELECT * FROM t_user WHERE name = :name AND age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("age", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains(":name"));
        assertFalse(newSql.contains(":age"));
        assertFalse(newSql.contains("where"));
        assertTrue(result.params().isEmpty());
    }

    @Test
    void should_throwException_when_allUpdateParamsNull() {
        String sql = "UPDATE t_user SET status = 'inactive' WHERE name = :name AND age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("age", null);

        assertThrows(BoosterSqlRewriter.SqlRewriteException.class,
                () -> BoosterSqlRewriter.rewrite(sql, params));
    }

    @Test
    void should_throwException_when_allDeleteParamsNull() {
        String sql = "DELETE FROM t_user WHERE name = :name AND age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("age", null);

        assertThrows(BoosterSqlRewriter.SqlRewriteException.class,
                () -> BoosterSqlRewriter.rewrite(sql, params));
    }

    @Test
    void should_keepCondition_when_paramIsZero() {
        String sql = "SELECT * FROM t_user WHERE age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 0);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        assertTrue(result.sql().toLowerCase(Locale.ROOT).contains(":age"));
        assertEquals(0, result.params().get("age"));
    }

    @Test
    void should_keepCondition_when_paramIsFalse() {
        String sql = "SELECT * FROM t_user WHERE active = :active";
        Map<String, Object> params = new HashMap<>();
        params.put("active", false);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        assertTrue(result.sql().toLowerCase(Locale.ROOT).contains(":active"));
        assertEquals(false, result.params().get("active"));
    }

    @Test
    void should_returnOriginal_when_sqlIsNull() {
        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(null, Map.of("a", "b"));
        assertNull(result.sql());
    }

    @Test
    void should_returnOriginal_when_sqlIsBlank() {
        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite("   ", Map.of("a", "b"));
        assertEquals("   ", result.sql());
    }

    @Test
    void should_returnOriginal_when_paramsEmpty() {
        String sql = "SELECT * FROM t WHERE id = :id";
        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, Collections.emptyMap());
        assertEquals(sql, result.sql());
    }

    @Test
    void should_returnOriginal_when_paramsNull() {
        String sql = "SELECT * FROM t WHERE id = :id";
        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, null);
        assertEquals(sql, result.sql());
    }

    // ==================== T-10 stripSqlComments additional boundary tests ====================

    @Test
    void should_handleChineseInLineComment() {
        String sql = "SELECT * FROM t -- 这是中文注释 :hidden\nWHERE id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleChineseInBlockComment() {
        String sql = "SELECT * FROM t /* 中文块注释 :hidden 你好 */ WHERE id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleUnicodeInStringLiteral() {
        String sql = "SELECT * FROM t WHERE name = '你好 :fake' AND id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleLineCommentInsideBlockComment() {
        // -- inside a block comment is not a line comment; block comment takes precedence
        String sql = "SELECT * FROM t /* -- :fake */ WHERE id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleUnclosedBlockCommentSyntaxInStringLiteral() {
        // Unclosed block comment syntax inside a string should not affect subsequent parsing
        String sql = "SELECT * FROM t WHERE name = '/* unclosed' AND id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleMultipleEscapedQuotesInString() {
        // Multiple consecutive escaped quotes ''
        String sql = "SELECT * FROM t WHERE name = 'it''''s -- :fake' AND id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    @Test
    void should_handleOnlyComments_noSqlContent() {
        String sql = "-- entire line is comment :hidden";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of(), result);
    }

    @Test
    void should_handleParamNameWithDigits() {
        String sql = "SELECT * FROM t WHERE a = :param1 AND b = :p2_abc AND c = :_x3";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("param1", "p2_abc", "_x3"), result);
    }

    @Test
    void should_handleParamNameSingleUnderscore() {
        String sql = "SELECT * FROM t WHERE x = :_";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("_"), result);
    }

    @Test
    void should_handleDoubleUnderscorePrefix() {
        String sql = "SELECT * FROM t WHERE x = :__param";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("__param"), result);
    }

    @Test
    void should_notMatchColonFollowedByDigit() {
        // :123 is not a valid param name
        String sql = "SELECT * FROM t WHERE x = :123";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of(), result);
    }

    @Test
    void should_handleAdjacentCommentAndString() {
        // String literal immediately followed by block comment
        String sql = "SELECT * FROM t WHERE a = ':fake'/* :hidden */ AND b = :real";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("real"), result);
    }

    @Test
    void should_handleCrlfLineEnding() {
        // Windows-style CRLF line ending
        String sql = "SELECT * FROM t -- :fake\r\nWHERE id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
    }

    // ==================== collectNullParams boundary tests ====================

    @Test
    void should_treatBlankStringAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "   ");
        Set<String> result = BoosterSqlRewriter.collectNullParams(params);
        assertTrue(result.contains("name"));
    }

    @Test
    void should_treatEmptyStringAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "");
        Set<String> result = BoosterSqlRewriter.collectNullParams(params);
        assertTrue(result.contains("name"));
    }

    @Test
    void should_notTreatZeroAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("count", 0);
        Set<String> result = BoosterSqlRewriter.collectNullParams(params);
        assertTrue(result.isEmpty());
    }

    @Test
    void should_notTreatFalseAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("active", false);
        Set<String> result = BoosterSqlRewriter.collectNullParams(params);
        assertTrue(result.isEmpty());
    }

    @Test
    void should_treatEmptyArrayListAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("ids", new ArrayList<>());
        Set<String> result = BoosterSqlRewriter.collectNullParams(params);
        assertTrue(result.contains("ids"));
    }

    @Test
    void should_notTreatNonEmptyCollectionAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("ids", List.of(1, 2, 3));
        Set<String> result = BoosterSqlRewriter.collectNullParams(params);
        assertTrue(result.isEmpty());
    }

    // ==================== rewrite() DML safety boundary tests ====================

    @Test
    void should_allowUpdate_when_atLeastOneConditionRemains() {
        String sql = "UPDATE t_user SET status = 'x' WHERE name = :name AND age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Alice");
        params.put("age", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertTrue(newSql.contains("name = :name"));
        assertFalse(newSql.contains("age > :age"));
    }

    @Test
    void should_allowDelete_when_atLeastOneConditionRemains() {
        String sql = "DELETE FROM t_user WHERE name = :name AND age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Alice");
        params.put("age", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertTrue(newSql.contains("name = :name"));
        assertFalse(newSql.contains("age > :age"));
    }

    @Test
    void should_throwException_when_singleDeleteConditionIsNull() {
        String sql = "DELETE FROM t_user WHERE name = :name";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);

        assertThrows(BoosterSqlRewriter.SqlRewriteException.class,
                () -> BoosterSqlRewriter.rewrite(sql, params));
    }

    @Test
    void should_throwException_when_singleUpdateConditionIsNull() {
        String sql = "UPDATE t_user SET status = 'x' WHERE name = :name";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);

        assertThrows(BoosterSqlRewriter.SqlRewriteException.class,
                () -> BoosterSqlRewriter.rewrite(sql, params));
    }

    // ==================== rewrite() end-to-end comment stripping tests ====================

    @Test
    void should_rewriteCorrectly_when_sqlContainsCommentWithParam() {
        // End-to-end verification: :param inside comments should not affect rewrite logic
        String sql = "SELECT * FROM t_user WHERE name = :name -- :age removed\n AND status = :status";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("status", "active");

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains("name = :name"));
        assertTrue(newSql.contains("status = :status"));
        assertFalse(result.params().containsKey("name"));
        assertEquals("active", result.params().get("status"));
    }

    @Test
    void should_rewriteCorrectly_when_sqlContainsBlockCommentWithParam() {
        String sql = "SELECT * FROM t_user /* debug :debug */ WHERE age > :age AND name = :name";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 18);
        params.put("name", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertTrue(newSql.contains("age > :age"));
        assertFalse(newSql.contains("name = :name"));
        assertEquals(18, result.params().get("age"));
    }

    // ==================== rewrite() HAVING clause tests ====================

    @Test
    void should_removeHavingCondition_when_paramIsNull() {
        String sql = "SELECT department, COUNT(*) FROM t_user GROUP BY department HAVING COUNT(*) > :minCount";
        Map<String, Object> params = new HashMap<>();
        params.put("minCount", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains(":mincount"));
        assertFalse(result.params().containsKey("minCount"));
    }

    @Test
    void should_keepHavingCondition_when_paramHasValue() {
        String sql = "SELECT department, COUNT(*) FROM t_user GROUP BY department HAVING COUNT(*) > :minCount";
        Map<String, Object> params = new HashMap<>();
        params.put("minCount", 5);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertTrue(newSql.contains(":mincount"));
        assertEquals(5, result.params().get("minCount"));
    }

    // ==================== collectNullParams additional boundary scenarios ====================

    @Test
    void should_treatEmptySetAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("ids", Set.of());
        // Set implements Collection, empty Set should be treated as null
        Set<String> result = BoosterSqlRewriter.collectNullParams(params);
        assertTrue(result.contains("ids"));
    }

    @Test
    void should_notTreatSingleElementCollectionAsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("ids", List.of(1));
        Set<String> result = BoosterSqlRewriter.collectNullParams(params);
        assertFalse(result.contains("ids"));
    }

    @Test
    void should_notTreatEmptyArrayAsNull() {
        // Arrays are not Collection, should not be treated as null
        Map<String, Object> params = new HashMap<>();
        params.put("ids", new int[0]);
        Set<String> result = BoosterSqlRewriter.collectNullParams(params);
        assertFalse(result.contains("ids"));
    }

    @Test
    void should_handleMixedNullAndNonNullParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("age", 25);
        params.put("email", "");
        params.put("status", "active");
        params.put("tags", Collections.emptyList());
        params.put("score", 0);

        Set<String> result = BoosterSqlRewriter.collectNullParams(params);

        assertEquals(Set.of("name", "email", "tags"), result);
    }

    // ==================== rewrite() multiple JOIN condition tests ====================

    @Test
    void should_removeMultipleJoinConditions_when_paramsNull() {
        String sql = "SELECT u.* FROM t_user u " +
                "JOIN t_role r ON u.role_id = r.id AND r.name = :roleName " +
                "JOIN t_dept d ON u.dept_id = d.id AND d.name = :deptName " +
                "WHERE u.status = :status";
        Map<String, Object> params = new HashMap<>();
        params.put("roleName", null);
        params.put("deptName", null);
        params.put("status", "active");

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains("r.name = :rolename"));
        assertFalse(newSql.contains("d.name = :deptname"));
        assertTrue(newSql.contains("u.status = :status"));
        assertTrue(newSql.contains("u.role_id = r.id"));
        assertTrue(newSql.contains("u.dept_id = d.id"));
    }

    // ==================== rewrite() blank string end-to-end tests ====================

    @Test
    void should_removeCondition_when_paramIsBlankString() {
        String sql = "SELECT * FROM t_user WHERE name = :name AND age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "   ");
        params.put("age", 18);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String newSql = result.sql().toLowerCase(Locale.ROOT);
        assertFalse(newSql.contains("name = :name"));
        assertTrue(newSql.contains("age > :age"));
        assertFalse(result.params().containsKey("name"));
    }

    // ==================== extractParamNames PostgreSQL double-colon type cast ====================

    @Test
    void should_notMatchPostgresDoubleCast() {
        String sql = "SELECT created_at::date FROM t_user WHERE id = :id";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("id"), result);
        assertFalse(result.contains("date"));
    }

    @Test
    void should_handlePostgresDoubleCastWithParam() {
        String sql = "SELECT * FROM t_user WHERE created_at::date > :startDate";
        Set<String> result = BoosterSqlRewriter.extractParamNames(sql);
        assertEquals(Set.of("startDate"), result);
    }
}
