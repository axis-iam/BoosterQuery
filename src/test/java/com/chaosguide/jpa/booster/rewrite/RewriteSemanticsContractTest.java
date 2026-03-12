package com.chaosguide.jpa.booster.rewrite;

import net.sf.jsqlparser.JSQLParserException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class RewriteSemanticsContractTest {

    private static String normalize(String sql) {
        return sql == null ? null : sql.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    @Test
    void deletesOnlyValueFilterExpressionsInWhere() {
        String sql = "select * from users u where u.name = :name and u.age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("age", 18);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String rewritten = normalize(result.sql());
        assertFalse(rewritten.contains("u.name=:name"));
        assertTrue(rewritten.contains("u.age>:age"));
        assertFalse(result.params().containsKey("name"));
        assertEquals(18, result.params().get("age"));
    }

    @Test
    void preservesStructuralIsNullExpressionEvenWhenParamIsNull() {
        String sql = "select * from users u where (:name is null or u.name = :name)";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String rewritten = normalize(result.sql());
        assertTrue(rewritten.contains(":nameisnull"));
        assertFalse(rewritten.contains("u.name=:name"));
        assertTrue(result.params().containsKey("name"), "param still referenced in SQL (:name IS NULL), should be retained");
        assertNull(result.params().get("name"), "retained param value should be null");
    }

    @Test
    void doesNotRewriteParametersOutsideWhereOnHavingAndSubqueries() {
        String sql = "select case when u.age > :minAge then 'adult' else 'minor' end as grp from users u";
        Map<String, Object> params = new HashMap<>();
        params.put("minAge", null);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String rewritten = normalize(result.sql());
        assertTrue(rewritten.contains(":minage"));
        assertTrue(result.params().containsKey("minAge"), "param still referenced in CASE WHEN should be retained");
        assertNull(result.params().get("minAge"), "retained param value should be null");
    }

    @Test
    void rewritesUnionEachSelectBodyIndependently() throws JSQLParserException {
        String sql = "select * from users where age > :age1 union select * from users where age < :age2";
        String rewritten = JSqlParserRewriter.removeNullConditions(sql, Set.of("age1"));

        String normalized = normalize(rewritten);
        assertFalse(normalized.contains(":age1"));
        assertTrue(normalized.contains(":age2"));
    }

    @Test
    void rewritesCteSubqueryBody() throws JSQLParserException {
        String sql = "with x as (select * from users where name = :name) select * from x where age > :age";
        String rewritten = JSqlParserRewriter.removeNullConditions(sql, Set.of("name"));

        String normalized = normalize(rewritten);
        assertTrue(normalized.contains("with"));
        assertFalse(normalized.contains(":name"), ":name condition inside CTE should be removed");
        assertTrue(normalized.contains(":age"));
    }

    @Test
    void cteSubqueryParamRemovedAfterFullRewrite() {
        String sql = "with x as (select * from users where name = :name) select * from x where age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        params.put("age", 25);

        BoosterSqlRewriter.SqlRewriteResult result = BoosterSqlRewriter.rewrite(sql, params);

        String normalized = normalize(result.sql());
        assertFalse(normalized.contains(":name"), ":name was rewritten out of CTE, should not appear in SQL");
        assertTrue(normalized.contains(":age"));
        assertFalse(result.params().containsKey("name"), "param removed from CTE should not remain in params");
        assertEquals(25, result.params().get("age"));
    }
}

