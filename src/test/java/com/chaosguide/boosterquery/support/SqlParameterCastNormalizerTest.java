package com.chaosguide.boosterquery.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlParameterCastNormalizerTest {

    @Test
    void rewritesPostgresParamCastToCastFunction() {
        String sql = "select * from global_identities where status = :status::global_identity_status";

        String normalized = SqlParameterCastNormalizer.normalize(sql);

        assertThat(normalized)
                .isEqualTo("select * from global_identities where status = CAST(:status AS global_identity_status)");
    }

    @Test
    void preservesColumnCast() {
        String sql = "select created_at::date from t_user where id = :id";

        String normalized = SqlParameterCastNormalizer.normalize(sql);

        assertThat(normalized).isEqualTo(sql);
    }

    @Test
    void ignoresParamCastInsideStringLiteralAndComment() {
        String sql = """
                select ':status::global_identity_status' as literal
                -- and :commented::global_identity_status
                where status = :status::global_identity_status
                """;

        String normalized = SqlParameterCastNormalizer.normalize(sql);

        assertThat(normalized)
                .contains("':status::global_identity_status'")
                .contains("-- and :commented::global_identity_status")
                .contains("status = CAST(:status AS global_identity_status)");
    }

    @Test
    void supportsQuotedDottedAndArrayTypeNames() {
        String sql = "select * from t where value = :value::public.\"custom_type\"[]";

        String normalized = SqlParameterCastNormalizer.normalize(sql);

        assertThat(normalized)
                .isEqualTo("select * from t where value = CAST(:value AS public.\"custom_type\"[])");
    }

    @Test
    void supportsTypeNamesWithModifiers() {
        String sql = "select * from t where amount = :amount::numeric(10, 2)";

        String normalized = SqlParameterCastNormalizer.normalize(sql);

        assertThat(normalized)
                .isEqualTo("select * from t where amount = CAST(:amount AS numeric(10, 2))");
    }

    @Test
    void returnsNullAndEmptyAsIs() {
        assertThat(SqlParameterCastNormalizer.normalize(null)).isNull();
        assertThat(SqlParameterCastNormalizer.normalize("")).isEmpty();
    }
}
