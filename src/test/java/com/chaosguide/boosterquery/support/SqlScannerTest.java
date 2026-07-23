package com.chaosguide.boosterquery.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlScannerTest {

    @Test
    void findTopLevelKeyword_ignoresNestedQueriesStringsAndComments() {
        String sql = """
                select * from (
                  select * from t_user order by created_at
                ) u
                where u.note = 'literal order by'
                /* comment order by */
                order by u.id
                """;

        int index = SqlScanner.findLastTopLevelKeyword(sql, "order by");

        assertThat(sql.substring(index).trim()).startsWith("order by u.id");
    }

    @Test
    void stripTopLevelTailClauses_preservesNestedOrderByAndDollarQuotedText() {
        String sql = """
                select * from (
                  select * from t_user order by created_at
                ) u
                where u.note = $$text order by ignored$$
                order by u.id limit :limit
                """;

        String stripped = SqlScanner.stripTopLevelTailClauses(sql.trim());

        assertThat(stripped)
                .contains("select * from t_user order by created_at")
                .contains("$$text order by ignored$$")
                .doesNotEndWith("limit :limit");
        assertThat(stripped.trim()).endsWith("$$text order by ignored$$");
    }

    @Test
    void splitTopLevelComma_ignoresFunctionArgumentsAndLiterals() {
        assertThat(SqlScanner.splitTopLevelComma("lower(name) asc, coalesce(label, 'a,b') desc, id"))
                .containsExactly("lower(name) asc", "coalesce(label, 'a,b') desc", "id");
    }

    @Test
    void findFirstTopLevelPaginationClause_ignoresNestedAndCommentedClauses() {
        String sql = """
                select * from (
                  select * from t_user limit 10
                ) u
                -- limit 5
                where u.id = :id offset :offset
                """;

        int index = SqlScanner.findFirstTopLevelPaginationClause(sql);

        assertThat(sql.substring(index).trim()).startsWith("offset :offset");
    }

    @Test
    void findFirstTopLevelPaginationClause_ignoresNamedParameters() {
        String sql = "select * from t_user where max_rows = :limit and start_at = :offset";

        assertThat(SqlScanner.findFirstTopLevelPaginationClause(sql)).isEqualTo(-1);
    }
}
