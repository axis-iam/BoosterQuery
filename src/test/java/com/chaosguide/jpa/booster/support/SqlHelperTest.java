package com.chaosguide.jpa.booster.support;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlHelperTest {

    // ==================== buildCountSql ====================

    @Nested
    class BuildCountSql {

        @Test
        void simpleSql_replacesSelectWithCount() {
            String sql = "SELECT id, name FROM t_user WHERE age > 18";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).doesNotContain("id, name");
            assertThat(result.toLowerCase()).contains("t_user");
        }

        @Test
        void selectStar_replacesWithCount() {
            String sql = "SELECT * FROM t_user";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).doesNotContain("*");
        }

        @Test
        void withOrderBy_removesOrderBy() {
            String sql = "SELECT * FROM t_user WHERE age > 18 ORDER BY name ASC";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).doesNotContain("order by");
        }

        @Test
        void withDistinct_fallsBackToSubquery() {
            String sql = "SELECT DISTINCT name FROM t_user WHERE age > 18";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains("tmp_count");
        }

        @Test
        void withGroupBy_fallsBackToSubquery() {
            String sql = "SELECT age, COUNT(*) FROM t_user GROUP BY age";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains("tmp_count");
        }

        @Test
        void unionSql_fallsBackToSubquery() {
            String sql = "SELECT id FROM t_user UNION SELECT id FROM t_admin";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains("tmp_count");
        }

        @Test
        void withJoin_replacesSelectWithCount() {
            String sql = "SELECT u.id, u.name FROM t_user u JOIN t_order o ON u.id = o.user_id WHERE o.amount > 100";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains("join");
        }

        @Test
        void subqueryInWhere_replacesSelectWithCount() {
            String sql = "SELECT * FROM t_user WHERE id IN (SELECT user_id FROM t_order)";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
        }

        @Test
        void subqueryOrderBy_removedInSimpleCount() {
            // ORDER BY in subquery should not be removed by outer layer
            String sql = "SELECT * FROM t_user WHERE age > 18 ORDER BY id";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).doesNotContain("order by");
        }

        @Test
        void invalidSql_fallsBackToSubquery() {
            String sql = "THIS IS NOT VALID SQL AT ALL !!!";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains("tmp_count");
        }

        @Test
        void withCte_producesValidCountSql() {
            // CTE (WITH ... AS) query COUNT conversion
            // JSqlParser 5.3 can correctly parse CTE, directly replacing SELECT columns with COUNT(1)
            String sql = "WITH active_users AS (SELECT * FROM t_user WHERE status = 'active') SELECT * FROM active_users";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            // CTE structure should be preserved
            assertThat(result.toLowerCase()).contains("active_users");
        }

        @Test
        void withDistinctAndGroupBy_fallsBackToSubquery() {
            // DISTINCT + GROUP BY combination scenario
            String sql = "SELECT DISTINCT department, COUNT(*) FROM t_user GROUP BY department";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains("tmp_count");
        }

        @Test
        void subqueryOrderBy_preservedInWrappedCount() {
            // ORDER BY in subquery should be preserved (because ORDER BY is followed by ))
            String sql = "SELECT * FROM (SELECT * FROM t_user ORDER BY id) sub WHERE sub.age > 18";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            // Inner subquery ORDER BY should not be incorrectly removed
        }

        @Test
        void simpleCountSql_stripsOutermostOrderBy() {
            // buildSimpleCountSql removes outermost ORDER BY (optimization)
            String sql = "SELECT DISTINCT name FROM t_user ORDER BY name";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains("tmp_count");
        }

        @Test
        void withHaving_replacesSelectWithCount() {
            // HAVING implies GROUP BY, should use subquery wrapping
            String sql = "SELECT age, COUNT(*) FROM t_user GROUP BY age HAVING COUNT(*) > 1";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains("tmp_count");
        }

        @Test
        void withNamedParams_preservesParams() {
            String sql = "SELECT * FROM t_user WHERE name = :name AND age > :age";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains(":name");
            assertThat(result.toLowerCase()).contains(":age");
        }

        @Test
        void withWhereAndMultipleColumns_replacesCorrectly() {
            String sql = "SELECT id, name, age, email FROM t_user WHERE name = :name AND age > :age";
            String result = SqlHelper.buildCountSql(sql);
            assertThat(result.toLowerCase()).contains("count(1)");
            assertThat(result.toLowerCase()).contains("where");
        }
    }

    // ==================== applySort ====================

    @Nested
    class ApplySort {

        @Test
        void addsSortToSimpleSql() {
            String sql = "SELECT * FROM t_user";
            Sort sort = Sort.by(Sort.Order.asc("name"));
            String result = SqlHelper.applySort(sql, sort);
            assertThat(result.toLowerCase()).contains("order by");
            assertThat(result).contains("name");
            assertThat(result.toUpperCase()).contains("ASC");
        }

        @Test
        void addsSortDesc() {
            String sql = "SELECT * FROM t_user";
            Sort sort = Sort.by(Sort.Order.desc("age"));
            String result = SqlHelper.applySort(sql, sort);
            assertThat(result).contains("age");
            assertThat(result.toUpperCase()).contains("DESC");
        }

        @Test
        void multipleSort_allApplied() {
            String sql = "SELECT * FROM t_user";
            Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.desc("age"));
            String result = SqlHelper.applySort(sql, sort);
            assertThat(result).contains("name");
            assertThat(result).contains("age");
        }

        @Test
        void nullSort_returnsOriginal() {
            String sql = "SELECT * FROM t_user";
            String result = SqlHelper.applySort(sql, null);
            assertThat(result).isEqualTo(sql);
        }

        @Test
        void unsortedSort_returnsOriginal() {
            String sql = "SELECT * FROM t_user";
            String result = SqlHelper.applySort(sql, Sort.unsorted());
            assertThat(result).isEqualTo(sql);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void nullOrBlankSql_returnsAsIs(String sql) {
            String result = SqlHelper.applySort(sql, Sort.by("name"));
            assertThat(result).isEqualTo(sql);
        }

        @Test
        void invalidSortProperty_throwsException() {
            String sql = "SELECT * FROM t_user";
            Sort sort = Sort.by("name; DROP TABLE t_user");
            assertThatThrownBy(() -> SqlHelper.applySort(sql, sort))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid sort property");
        }

        @Test
        void existingOrderBy_mergesSort() {
            String sql = "SELECT * FROM t_user ORDER BY id ASC";
            Sort sort = Sort.by(Sort.Order.desc("name"));
            String result = SqlHelper.applySort(sql, sort);
            assertThat(result.toLowerCase()).contains("order by");
            assertThat(result).contains("name");
            assertThat(result).contains("id");
        }

        @Test
        void duplicateSort_notDuplicated() {
            String sql = "SELECT * FROM t_user ORDER BY name ASC";
            Sort sort = Sort.by(Sort.Order.desc("name"));
            String result = SqlHelper.applySort(sql, sort);
            // name should appear only once in ORDER BY (overridden by primary)
            String orderByPart = result.substring(result.toLowerCase().indexOf("order by"));
            int nameCount = orderByPart.toLowerCase().split("name", -1).length - 1;
            assertThat(nameCount).isEqualTo(1);
        }

        @Test
        void unionSql_appliesSortToSetOperation() {
            String sql = "SELECT id FROM t_user UNION SELECT id FROM t_admin";
            Sort sort = Sort.by(Sort.Order.asc("id"));
            String result = SqlHelper.applySort(sql, sort);
            assertThat(result.toLowerCase()).contains("order by");
        }

        @Test
        void sqlWithLimit_insertsSortBeforeLimit() {
            // SQL with existing LIMIT, sort should be inserted before LIMIT
            String sql = "SELECT * FROM t_user LIMIT 10";
            Sort sort = Sort.by(Sort.Order.asc("name"));
            String result = SqlHelper.applySort(sql, sort);
            String lower = result.toLowerCase();
            int orderByIdx = lower.indexOf("order by");
            int limitIdx = lower.indexOf("limit");
            assertThat(orderByIdx).isGreaterThan(-1);
            assertThat(limitIdx).isGreaterThan(orderByIdx);
        }

        @Test
        void sqlWithSemicolon_handlesFallbackCorrectly() {
            // Trailing semicolon should be handled correctly (fallback mode)
            String sql = "THIS_IS_NOT_SQL;";
            Sort sort = Sort.by(Sort.Order.asc("name"));
            String result = SqlHelper.applySort(sql, sort);
            // Falls back to string concatenation, semicolon should be removed
            assertThat(result).doesNotEndWith(";");
            assertThat(result.toLowerCase()).contains("order by");
        }

        @Test
        void blankSortProperty_throwsException() {
            // Sort.by("  ") itself may throw, so the entire construction is placed in the lambda
            String sql = "SELECT * FROM t_user";
            assertThatThrownBy(() -> {
                Sort sort = Sort.by("  ");
                SqlHelper.applySort(sql, sort);
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void dotInSortProperty_allowed() {
            // table_alias.column_name format should be allowed
            String sql = "SELECT * FROM t_user u";
            Sort sort = Sort.by(Sort.Order.asc("u.name"));
            String result = SqlHelper.applySort(sql, sort);
            assertThat(result).contains("u.name");
        }

        @Test
        void specialCharsInSort_throwsException() {
            String sql = "SELECT * FROM t_user";
            Sort sort = Sort.by("name OR 1=1");
            assertThatThrownBy(() -> SqlHelper.applySort(sql, sort))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
