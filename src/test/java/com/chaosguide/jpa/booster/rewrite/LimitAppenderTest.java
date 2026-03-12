package com.chaosguide.jpa.booster.rewrite;

import com.chaosguide.jpa.booster.support.LimitAppender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LimitAppenderTest {

    @Nested
    class HasLimit {

        @Test
        void detectsLimitWithOrderBy() {
            assertThat(LimitAppender.hasLimit("SELECT * FROM t ORDER BY id DESC LIMIT 10")).isTrue();
        }

        @Test
        void detectsLimitWithoutOrderBy() {
            assertThat(LimitAppender.hasLimit("SELECT * FROM t LIMIT 10")).isTrue();
        }

        @Test
        void detectsLimitCaseInsensitive() {
            assertThat(LimitAppender.hasLimit("SELECT * FROM t limit 10")).isTrue();
            assertThat(LimitAppender.hasLimit("SELECT * FROM t Limit 10")).isTrue();
            assertThat(LimitAppender.hasLimit("SELECT * FROM t LIMIT 10")).isTrue();
        }

        @Test
        void detectsLimitWithOffset() {
            assertThat(LimitAppender.hasLimit("SELECT * FROM t ORDER BY id LIMIT 10 OFFSET 20")).isTrue();
        }

        @Test
        void noLimit_returnsFalse() {
            assertThat(LimitAppender.hasLimit("SELECT * FROM t_user WHERE id = 1")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        void nullOrBlank_returnsFalse(String sql) {
            assertThat(LimitAppender.hasLimit(sql)).isFalse();
        }

        @Test
        void subqueryHasLimit_outerDoesNot_detectsAsTrue() {
            // Current implementation: LIMIT in subquery is also detected (known limitation).
            // This is a documentation test for boundary behavior.
            String sql = "SELECT * FROM (SELECT * FROM t LIMIT 5) sub WHERE sub.age > 18";
            // hasLimit detects LIMIT in subquery.
            // Note: this prevents auto-LIMIT from being appended to the outer query.
            assertThat(LimitAppender.hasLimit(sql)).isTrue();
        }

        @Test
        void limitAsColumnName_notDetected() {
            // "LIMIT" appearing as part of a column name (not surrounded by spaces) should not match.
            // Current regex requires " LIMIT " with spaces.
            assertThat(LimitAppender.hasLimit("SELECT user_limit FROM t_user")).isFalse();
        }

        @Test
        void limitWithMultipleSpaces() {
            assertThat(LimitAppender.hasLimit("SELECT * FROM t ORDER BY id  LIMIT  10")).isTrue();
        }

        @Test
        void limitInUpdateStatement() {
            assertThat(LimitAppender.hasLimit("UPDATE t_user SET status = 'x' WHERE id = 1 LIMIT 1")).isTrue();
        }

        @Test
        void limitInDeleteStatement() {
            assertThat(LimitAppender.hasLimit("DELETE FROM t_user WHERE id = 1 LIMIT 1")).isTrue();
        }

        @Test
        void noOrderBy_noLimit_returnsFalse() {
            assertThat(LimitAppender.hasLimit("SELECT u.name, u.age FROM t_user u WHERE u.status = 'active'")).isFalse();
        }
    }
}
