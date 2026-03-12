package com.chaosguide.jpa.booster.rewrite;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JSqlParserRewriter test cases (P0/P1 full coverage)
 */
public class JSqlParserRewriterTest {

    // ============ Basic functionality tests ============

    @Test
    public void testSimpleWhereCondition() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE name = :name AND age > :age";
        Set<String> nullParams = Set.of("name");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertEquals("SELECT * FROM users WHERE age > :age", result);
    }

    @Test
    public void testAllConditionsRemoved() {
        String sql = "DELETE FROM users WHERE name = :name AND age = :age";
        Set<String> nullParams = Set.of("name", "age");

        assertThrows(IllegalStateException.class, () -> JSqlParserRewriter.removeNullConditions(sql, nullParams));
    }

    @Test
    public void testOrExpression() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE (name = :name OR email = :email) AND status = 'active'";
        Set<String> nullParams = Set.of("name", "email");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertEquals("SELECT * FROM users WHERE status = 'active'", result);
    }

    // ============ P0 tests: subqueries inside CASE WHEN ============

    @Test
    public void testCaseWhenWithSubQuery() throws JSQLParserException {
        String sql = "SELECT " +
                "  CASE " +
                "    WHEN EXISTS(SELECT 1 FROM orders WHERE user_id = :userId AND status = 'paid') " +
                "    THEN 'premium' " +
                "    ELSE 'regular' " +
                "  END as user_type " +
                "FROM users";
        Set<String> nullParams = Set.of("userId");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected: userId condition in subquery removed, status condition preserved
        assertFalse(result.contains(":userId"));
        assertTrue(result.contains("status = 'paid'"));
    }

    @Test
    public void testCaseWhenInSelectList() throws JSQLParserException {
        String sql = "SELECT id, " +
                "  CASE " +
                "    WHEN age > :minAge THEN 'adult' " +
                "    ELSE 'minor' " +
                "  END as age_group " +
                "FROM users";
        Set<String> nullParams = Set.of("minAge");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Simple conditions in CASE WHEN are not removed (they are not WHERE conditions).
        // However, if CASE WHEN contains subqueries, their conditions will be cleaned up.
        assertTrue(result.contains("CASE"));
    }

    // ============ P0 tests: subqueries in comparison expressions ============

    @Test
    public void testSubQueryInComparison() throws JSQLParserException {
        String sql = "SELECT * FROM products " +
                "WHERE price > (SELECT AVG(price) FROM products WHERE category = :category)";
        Set<String> nullParams = Set.of("category");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected: category condition in subquery removed
        assertFalse(result.contains(":category"));
        assertTrue(result.contains("AVG(price)"));
    }

    @Test
    public void testSubQueryOnBothSides() throws JSQLParserException {
        String sql = "SELECT * FROM users " +
                "WHERE (SELECT COUNT(*) FROM orders WHERE user_id = :userId1) > " +
                "      (SELECT AVG(order_count) FROM stats WHERE dept = :dept)";
        Set<String> nullParams = Set.of("userId1");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected: left subquery condition removed, right side preserved
        assertFalse(result.contains(":userId1"));
        assertTrue(result.contains(":dept"));
    }

    // ============ P0 tests: ANY/ALL subqueries ============

    @Test
    public void testAnySubQuery() throws JSQLParserException {
        String sql = "SELECT * FROM products " +
                "WHERE price > ANY(SELECT price FROM products WHERE category = :category)";
        Set<String> nullParams = Set.of("category");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected: condition in ANY subquery removed
        assertFalse(result.contains(":category"));
    }

    @Test
    public void testAllSubQuery() throws JSQLParserException {
        String sql = "SELECT * FROM products " +
                "WHERE price > ALL(SELECT price FROM products WHERE status = :status)";
        Set<String> nullParams = Set.of("status");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":status"));
    }

    // ============ P0 tests: subqueries in UPDATE SET clause ============

    @Test
    public void testUpdateSetWithSubQuery() throws JSQLParserException {
        String sql = "UPDATE products " +
                "SET avg_price = (SELECT AVG(price) FROM products WHERE category = :category) " +
                "WHERE id = 1";
        Set<String> nullParams = Set.of("category");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected: subquery condition in SET clause removed
        assertFalse(result.contains(":category"));
        assertTrue(result.contains("AVG(price)"));
    }

    @Test
    public void testUpdateMultipleSetWithSubQuery() throws JSQLParserException {
        String sql = "UPDATE users " +
                "SET order_count = (SELECT COUNT(*) FROM orders WHERE user_id = users.id AND status = :status), " +
                "    last_order_date = (SELECT MAX(created_at) FROM orders WHERE user_id = users.id) " +
                "WHERE id = :userId";
        Set<String> nullParams = Set.of("status");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected: status condition in first subquery removed, userId preserved
        assertFalse(result.contains(":status"));
        assertTrue(result.contains(":userId"));
    }

    // ============ P1 tests: NOT expressions ============

    @Test
    public void testNotExpression() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE NOT (name = :name)";
        Set<String> nullParams = Set.of("name");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected: condition inside NOT removed, entire NOT also removed
        assertFalse(result.contains("NOT"));
        assertFalse(result.contains(":name"));
    }

    @Test
    public void testNotWithMultipleConditions() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE NOT (name = :name OR age > 18)";
        Set<String> nullParams = Set.of("name");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected: name condition removed, NOT (age > 18) preserved
        assertFalse(result.contains(":name"));
        assertTrue(result.contains("NOT"));
        assertTrue(result.contains("age > 18"));
    }

    @Test
    public void testNotIsNull() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE name = :name AND NOT (email IS NULL)";
        Set<String> nullParams = Set.of("name");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected: name condition removed, IS NULL preserved (structural condition)
        assertFalse(result.contains(":name"));
        assertTrue(result.contains("NOT"));
        assertTrue(result.contains("IS NULL"));
    }

    // ============ Comprehensive tests ============

    @Test
    public void testComplexQueryWithAllFeatures() throws JSQLParserException {
        String sql = "SELECT " +
                "  u.id, " +
                "  (SELECT COUNT(*) FROM orders WHERE user_id = u.id AND status = :orderStatus) as order_count, " +
                "  CASE " +
                "    WHEN u.age > (SELECT AVG(age) FROM users WHERE dept = :dept) THEN 'senior' " +
                "    ELSE 'junior' " +
                "  END as seniority " +
                "FROM users u " +
                "WHERE u.name = :userName " +
                "  AND NOT (u.email IS NULL) " +
                "  AND u.salary > ANY(SELECT salary FROM employees WHERE dept = :empDept)";

        Set<String> nullParams = Set.of("orderStatus", "dept");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        // Expected:
        // - Subquery conditions in SELECT list removed
        // - Subquery conditions in CASE WHEN removed
        // - userName preserved
        // - empDept preserved
        assertFalse(result.contains(":orderStatus"));
        assertFalse(result.contains(":dept"));
        assertTrue(result.contains(":userName"));
        assertTrue(result.contains(":empDept"));
        assertTrue(result.contains("NOT"));
        assertTrue(result.contains("IS NULL"));
    }

    // ============ Original tests retained ============

    @Test
    public void testJoinOnCondition() throws JSQLParserException {
        String sql = "SELECT * FROM users u JOIN orders o ON u.id = o.user_id AND o.status = :status";
        Set<String> nullParams = Set.of("status");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertTrue(result.contains("u.id = o.user_id"));
        assertFalse(result.contains(":status"));
    }

    @Test
    public void testSubQuery() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE status = :status)";
        Set<String> nullParams = Set.of("status");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":status"));
    }

    @Test
    public void testHavingClause() throws JSQLParserException {
        String sql = "SELECT dept, COUNT(*) FROM users GROUP BY dept HAVING COUNT(*) > :threshold";
        Set<String> nullParams = Set.of("threshold");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains("HAVING"));
        assertFalse(result.contains(":threshold"));
    }

    @Test
    public void testBetweenExpression() throws JSQLParserException {
        String sql = "SELECT * FROM orders WHERE price BETWEEN :min AND :max";
        Set<String> nullParams = Set.of("min");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains("BETWEEN"));
    }

    @Test
    public void testIsNullPreserved() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE name = :name AND email IS NULL";
        Set<String> nullParams = Set.of("name");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertTrue(result.contains("IS NULL"));
        assertFalse(result.contains(":name"));
    }

    @Test
    public void testComplexNestedQuery() throws JSQLParserException {
        String sql = "SELECT * FROM users u " +
                "WHERE u.dept_id IN (" +
                "  SELECT d.id FROM departments d " +
                "  WHERE d.name = :deptName AND d.active = true" +
                ") AND u.age > :age";
        Set<String> nullParams = Set.of("deptName");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":deptName"));
        assertTrue(result.contains("d.active = true"));
        assertTrue(result.contains("u.age > :age"));
    }

    @Test
    public void testUnionQuery() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE age > :age1 " +
                "UNION " +
                "SELECT * FROM users WHERE age < :age2";
        Set<String> nullParams = Set.of("age1");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":age1"));
        assertTrue(result.contains(":age2"));
    }

    @Test
    public void testEmptyNullParams() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE name = :name";
        Set<String> nullParams = Set.of();

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertEquals(sql, result);
    }

    @Test
    public void testUpdateStatement() throws JSQLParserException {
        String sql = "UPDATE users SET status = 'inactive' WHERE name = :name AND age > 18";
        Set<String> nullParams = Set.of("name");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":name"));
        assertTrue(result.contains("age > 18"));
    }

    @Test
    public void testArithmeticExpression() throws JSQLParserException {
        Expression expr = CCJSqlParserUtil.parseExpression("price * :discount + :fee");
        boolean result = SqlParamUsageVisitor.containsAnyParam(expr, Set.of("discount"));
        assertTrue(result);
    }

    @Test
    public void testConcatExpression() throws JSQLParserException {
        Expression expr = CCJSqlParserUtil.parseExpression("CONCAT(name, :suffix)");
        boolean result = SqlParamUsageVisitor.containsAnyParam(expr, Set.of("suffix"));
        assertTrue(result);
    }

    @Test
    public void testCastExpression() throws JSQLParserException {
        Expression expr = CCJSqlParserUtil.parseExpression("CAST(:value AS INTEGER)");
        boolean result = SqlParamUsageVisitor.containsAnyParam(expr, Set.of("value"));
        assertTrue(result);
    }

    // ============ JSqlParser 5.3 compatibility tests ============

    @Test
    public void testParenthesizedExpressionWithNull() throws JSQLParserException {
        String sql = "SELECT * FROM t WHERE (a = :a OR b = :b)";
        Set<String> nullParams = Set.of("a");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertTrue(result.contains("b = :b"), "Non-null condition should remain");
        assertFalse(result.contains("a = :a"), "Null condition should be removed");
    }

    @Test
    public void testParenthesizedExpressionAllNull() throws JSQLParserException {
        String sql = "SELECT * FROM t WHERE (a = :a OR b = :b) AND c = :c";
        Set<String> nullParams = Set.of("a", "b");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertTrue(result.contains("c = :c"), "Non-null condition should remain");
        assertFalse(result.contains("a = :a"), "Null condition should be removed");
        assertFalse(result.contains("b = :b"), "Null condition should be removed");
    }

    @Test
    public void testNestedParenthesizedExpression() throws JSQLParserException {
        String sql = "SELECT * FROM t WHERE ((a = :a AND b = :b) OR c = :c)";
        Set<String> nullParams = Set.of("a");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains("a = :a"), "Null condition should be removed");
        assertTrue(result.contains("b = :b"), "Non-null condition should remain");
        assertTrue(result.contains("c = :c"), "Non-null condition should remain");
    }

    // ============ CTE (WITH / RECURSIVE) rewrite tests ============

    // Scenario 1: single CTE with internal null param - positive
    @Test
    public void should_removeCteInternalCondition_when_paramIsNull() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t WHERE name = :name AND age > :age) SELECT * FROM x";
        Set<String> nullParams = Set.of("name");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":name"), "name condition inside CTE should be removed");
        assertTrue(result.contains(":age"), "age condition inside CTE should be preserved");
    }

    // Scenario 1: single CTE with internal null param - negative
    @Test
    public void should_keepCteInternalCondition_when_paramHasValue() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t WHERE name = :name AND age > :age) SELECT * FROM x";
        Set<String> nullParams = Set.of();

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertTrue(result.contains(":name"), "condition should be preserved when name has value");
        assertTrue(result.contains(":age"), "condition should be preserved when age has value");
    }

    // Scenario 2: single CTE with outer null param - positive
    @Test
    public void should_removeOuterCondition_when_cteOuterParamIsNull() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t) SELECT * FROM x WHERE age > :age";
        Set<String> nullParams = Set.of("age");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":age"), "outer age condition should be removed");
        assertFalse(result.contains("WHERE"), "WHERE clause should be completely removed");
    }

    // Scenario 2: single CTE with outer null param - negative
    @Test
    public void should_keepOuterCondition_when_cteOuterParamHasValue() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t) SELECT * FROM x WHERE age > :age";
        Set<String> nullParams = Set.of();

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertTrue(result.contains(":age"), "outer condition should be preserved when age has value");
    }

    // Scenario 3: multiple CTEs with independent params - positive
    @Test
    public void should_removeConditionInFirstCte_when_itsParamIsNull() throws JSQLParserException {
        String sql = "WITH a AS (SELECT * FROM t1 WHERE c1 = :p1), " +
                "b AS (SELECT * FROM t2 WHERE c2 = :p2) " +
                "SELECT * FROM a JOIN b ON a.id = b.id";
        Set<String> nullParams = Set.of("p1");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":p1"), "p1 condition in first CTE should be removed");
        assertTrue(result.contains(":p2"), "p2 condition in second CTE should be preserved");
    }

    // Scenario 3: multiple CTEs with independent params - negative
    @Test
    public void should_keepAllCteConditions_when_allParamsHaveValue() throws JSQLParserException {
        String sql = "WITH a AS (SELECT * FROM t1 WHERE c1 = :p1), " +
                "b AS (SELECT * FROM t2 WHERE c2 = :p2) " +
                "SELECT * FROM a JOIN b ON a.id = b.id";
        Set<String> nullParams = Set.of();

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertTrue(result.contains(":p1"), "condition should be preserved when p1 has value");
        assertTrue(result.contains(":p2"), "condition should be preserved when p2 has value");
    }

    // Scenario 4: RECURSIVE CTE - positive
    @Test
    public void should_removeRecursiveCteBaseCondition_when_paramIsNull() throws JSQLParserException {
        String sql = "WITH RECURSIVE tree AS (" +
                "SELECT * FROM nodes WHERE parent_id = :pid " +
                "UNION ALL " +
                "SELECT n.* FROM nodes n JOIN tree t ON n.parent_id = t.id" +
                ") SELECT * FROM tree WHERE level < :maxLevel";
        Set<String> nullParams = Set.of("pid");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":pid"), "parent_id condition in RECURSIVE CTE base query should be removed");
        assertTrue(result.contains(":maxLevel"), "outer maxLevel condition should be preserved");
    }

    // Scenario 4: RECURSIVE CTE - negative
    @Test
    public void should_keepRecursiveCteBaseCondition_when_paramHasValue() throws JSQLParserException {
        String sql = "WITH RECURSIVE tree AS (" +
                "SELECT * FROM nodes WHERE parent_id = :pid " +
                "UNION ALL " +
                "SELECT n.* FROM nodes n JOIN tree t ON n.parent_id = t.id" +
                ") SELECT * FROM tree WHERE level < :maxLevel";
        Set<String> nullParams = Set.of();

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertTrue(result.contains(":pid"), "condition should be preserved when pid has value");
        assertTrue(result.contains(":maxLevel"), "condition should be preserved when maxLevel has value");
    }

    // Scenario 5: CTE + outer JOIN ON condition - positive
    @Test
    public void should_removeJoinOnCondition_when_cteWithJoinParamIsNull() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t1 WHERE status = :status) " +
                "SELECT * FROM x JOIN t2 ON x.id = t2.xid AND t2.type = :type";
        Set<String> nullParams = Set.of("type");

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertFalse(result.contains(":type"), "type condition in JOIN ON should be removed");
        assertTrue(result.contains(":status"), "status condition inside CTE should be preserved");
        assertTrue(result.contains("x.id = t2.xid"), "structural JOIN ON condition should be preserved");
    }

    // Scenario 5: CTE + outer JOIN ON condition - negative
    @Test
    public void should_keepJoinOnCondition_when_cteWithJoinParamHasValue() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t1 WHERE status = :status) " +
                "SELECT * FROM x JOIN t2 ON x.id = t2.xid AND t2.type = :type";
        Set<String> nullParams = Set.of();

        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);

        assertTrue(result.contains(":type"), "JOIN ON condition should be preserved when type has value");
        assertTrue(result.contains(":status"), "CTE internal condition should be preserved when status has value");
    }

    // ============ CTE boundary tests ============

    @Test
    public void should_removeAllCteConditions_when_allCteParamsNull() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t WHERE a = :a AND b = :b) SELECT * FROM x WHERE c = :c";
        Set<String> nullParams = Set.of("a", "b");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":a"));
        assertFalse(result.contains(":b"));
        assertTrue(result.contains(":c"));
    }

    @Test
    public void should_removeAllConditions_when_selectCteAndOuterAllNull() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t WHERE a = :a) SELECT * FROM x WHERE b = :b";
        Set<String> nullParams = Set.of("a", "b");
        // Removing all SELECT conditions is valid (unlike DELETE/UPDATE)
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":a"));
        assertFalse(result.contains(":b"));
    }

    @Test
    public void should_throwException_when_deleteCteAllConditionsRemoved() {
        // DELETE WHERE has only param-dependent conditions; all removed → safety check throws
        String sql = "WITH x AS (SELECT id FROM t WHERE a = :a) DELETE FROM t2 WHERE b = :b";
        Set<String> nullParams = Set.of("a", "b");
        assertThrows(IllegalStateException.class, () -> JSqlParserRewriter.removeNullConditions(sql, nullParams));
    }

    @Test
    public void should_rewriteCteBody_when_deleteWithCteParamIsNull() throws JSQLParserException {
        // CTE body condition removed, DELETE WHERE partially preserved
        String sql = "WITH x AS (SELECT id FROM t WHERE a = :a AND b = :b) DELETE FROM t2 WHERE id IN (SELECT id FROM x) AND c = :c";
        Set<String> nullParams = Set.of("a");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":a"));
        assertTrue(result.contains(":b"));
        assertTrue(result.contains(":c"));
    }

    @Test
    public void should_removeOnlyNullConditionInCte_when_multipleConditions() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t WHERE a = :a AND b = :b AND c = :c) SELECT * FROM x";
        Set<String> nullParams = Set.of("b");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertTrue(result.contains(":a"));
        assertFalse(result.contains(":b"));
        assertTrue(result.contains(":c"));
    }

    @Test
    public void should_rewriteSubqueryInsideCte_when_paramIsNull() throws JSQLParserException {
        String sql = "WITH x AS (SELECT * FROM t WHERE id IN (SELECT id FROM t2 WHERE status = :status)) SELECT * FROM x WHERE age > :age";
        Set<String> nullParams = Set.of("status");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":status"));
        assertTrue(result.contains(":age"));
    }

    @Test
    public void should_rewriteChainedCtes_when_paramIsNull() throws JSQLParserException {
        String sql = "WITH a AS (SELECT * FROM t1 WHERE x = :x), " +
                "b AS (SELECT * FROM a WHERE y = :y) " +
                "SELECT * FROM b WHERE z = :z";
        Set<String> nullParams = Set.of("x", "y");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":x"));
        assertFalse(result.contains(":y"));
        assertTrue(result.contains(":z"));
    }

    @Test
    public void should_removeHavingInCte_when_paramIsNull() throws JSQLParserException {
        String sql = "WITH x AS (SELECT dept, COUNT(*) as cnt FROM t GROUP BY dept HAVING COUNT(*) > :threshold) SELECT * FROM x";
        Set<String> nullParams = Set.of("threshold");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":threshold"));
        assertFalse(result.contains("HAVING"));
    }

    // ============ T-05 CTE additional boundary tests ============

    @Test
    public void should_rewriteCteWithUnion_when_paramIsNull() throws JSQLParserException {
        // CTE + outer UNION combination
        String sql = "WITH x AS (SELECT * FROM t WHERE a = :a) " +
                "SELECT * FROM x WHERE b = :b UNION SELECT * FROM x WHERE c = :c";
        Set<String> nullParams = Set.of("b");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":b"), "b condition in first UNION segment should be removed");
        assertTrue(result.contains(":a"), "a condition inside CTE should be preserved");
        assertTrue(result.contains(":c"), "c condition in second UNION segment should be preserved");
    }

    @Test
    public void should_rewriteMultipleCtes_when_allParamsNull() throws JSQLParserException {
        // Multiple CTEs with all params null (valid for SELECT)
        String sql = "WITH a AS (SELECT * FROM t1 WHERE x = :x), " +
                "b AS (SELECT * FROM t2 WHERE y = :y) " +
                "SELECT * FROM a JOIN b ON a.id = b.id";
        Set<String> nullParams = Set.of("x", "y");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":x"));
        assertFalse(result.contains(":y"));
    }

    @Test
    public void should_handleCteWithNoWhereClause() throws JSQLParserException {
        // CTE without WHERE clause is unaffected
        String sql = "WITH x AS (SELECT * FROM t) SELECT * FROM x WHERE a = :a";
        Set<String> nullParams = Set.of("a");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":a"));
        assertTrue(result.contains("WITH"));
    }

    @Test
    public void should_removeRecursiveCteOuterCondition_when_paramIsNull() throws JSQLParserException {
        // RECURSIVE CTE + outer condition is null
        String sql = "WITH RECURSIVE tree AS (" +
                "SELECT * FROM nodes WHERE parent_id IS NULL " +
                "UNION ALL " +
                "SELECT n.* FROM nodes n JOIN tree t ON n.parent_id = t.id" +
                ") SELECT * FROM tree WHERE level < :maxLevel AND name = :name";
        Set<String> nullParams = Set.of("maxLevel");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":maxLevel"));
        assertTrue(result.contains(":name"));
    }

    @Test
    public void should_handleCteWithMultipleConditions_partialNull() throws JSQLParserException {
        // Multiple conditions in CTE, only some are null
        String sql = "WITH x AS (SELECT * FROM t WHERE a = :a AND b > :b AND c LIKE :c) SELECT * FROM x";
        Set<String> nullParams = Set.of("a", "c");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":a"));
        assertTrue(result.contains(":b"));
        assertFalse(result.contains(":c"));
    }

    @Test
    public void should_rewriteCteAndOuterConditions_independently() throws JSQLParserException {
        // CTE internal and outer each have null params, processed independently
        String sql = "WITH x AS (SELECT * FROM t WHERE inner_p = :inner_p) " +
                "SELECT * FROM x WHERE outer_p = :outer_p AND other = :other";
        Set<String> nullParams = Set.of("inner_p", "outer_p");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":inner_p"));
        assertFalse(result.contains(":outer_p"));
        assertTrue(result.contains(":other"));
    }

    // ============ JSqlParserRewriter general boundary tests ============

    @Test
    public void testDeleteAllConditionsRemoved_throwsException() {
        String sql = "DELETE FROM users WHERE name = :name";
        Set<String> nullParams = Set.of("name");
        assertThrows(IllegalStateException.class,
                () -> JSqlParserRewriter.removeNullConditions(sql, nullParams));
    }

    @Test
    public void testUpdateAllConditionsRemoved_throwsException() {
        String sql = "UPDATE users SET age = 0 WHERE name = :name AND status = :status";
        Set<String> nullParams = Set.of("name", "status");
        assertThrows(IllegalStateException.class,
                () -> JSqlParserRewriter.removeNullConditions(sql, nullParams));
    }

    @Test
    public void testSelectAllConditionsRemoved_doesNotThrow() throws JSQLParserException {
        // Removing all conditions in SELECT is valid
        String sql = "SELECT * FROM users WHERE name = :name AND age = :age";
        Set<String> nullParams = Set.of("name", "age");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains("WHERE"));
    }

    @Test
    public void testInExpression_conditionRemoved() throws JSQLParserException {
        // When IN clause param is null, entire IN condition should be removed
        String sql = "SELECT * FROM users WHERE name IN (:names) AND status = :status";
        Set<String> nullParams = Set.of("names");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":names"), "IN condition should be removed");
        assertTrue(result.contains(":status"), "other conditions should be preserved");
    }

    @Test
    public void testLikeExpression_conditionRemoved() throws JSQLParserException {
        String sql = "SELECT * FROM users WHERE name LIKE :name AND age > :age";
        Set<String> nullParams = Set.of("name");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":name"));
        assertTrue(result.contains(":age"));
    }

    @Test
    public void testMultipleJoins_conditionRemoved() throws JSQLParserException {
        String sql = "SELECT * FROM users u " +
                "JOIN orders o ON u.id = o.user_id AND o.type = :type " +
                "JOIN payments p ON o.id = p.order_id AND p.status = :payStatus";
        Set<String> nullParams = Set.of("type");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":type"));
        assertTrue(result.contains(":payStatus"));
        assertTrue(result.contains("u.id = o.user_id"));
    }

    @Test
    public void testWhereAndHaving_bothHaveNullParams() throws JSQLParserException {
        String sql = "SELECT dept, COUNT(*) FROM users WHERE status = :status " +
                "GROUP BY dept HAVING COUNT(*) > :threshold";
        Set<String> nullParams = Set.of("status", "threshold");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertFalse(result.contains(":status"));
        assertFalse(result.contains(":threshold"));
        assertFalse(result.contains("HAVING"));
    }

    @Test
    public void testIsNullPattern_preserved_when_paramIsNull() throws JSQLParserException {
        // :name IS NULL pattern should not be removed
        String sql = "SELECT * FROM users WHERE (:name IS NULL OR name = :name)";
        Set<String> nullParams = Set.of("name");
        String result = JSqlParserRewriter.removeNullConditions(sql, nullParams);
        assertTrue(result.contains(":name IS NULL"), ":name IS NULL pattern should be preserved");
    }
}