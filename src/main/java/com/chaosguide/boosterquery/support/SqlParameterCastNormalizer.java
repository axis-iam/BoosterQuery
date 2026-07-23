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
package com.chaosguide.boosterquery.support;

/**
 * Normalizes PostgreSQL shorthand casts on named parameters.
 * <p>
 * Hibernate/JPA named parameter parsing can interpret {@code :status::my_enum}
 * as an invalid parameter token. Rewriting that form to {@code CAST(:status AS my_enum)}
 * before creating the JPA query keeps the parameter name unambiguous.
 */
public final class SqlParameterCastNormalizer {

    private SqlParameterCastNormalizer() {
        // utility class
    }

    /**
     * Rewrites {@code :param::postgres_type} to {@code CAST(:param AS postgres_type)}.
     * Column casts such as {@code created_at::date}, string literals, quoted identifiers,
     * and SQL comments are left unchanged.
     *
     * @param sql SQL text, may be {@code null}
     * @return normalized SQL text, or the original value when no rewrite is needed
     */
    public static String normalize(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        StringBuilder out = null;
        int len = sql.length();
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            if (c == '\'') {
                int end = SqlScanner.skipSingleQuoted(sql, i);
                out = append(out, sql, i, end);
                i = end;
                continue;
            }

            if (c == '"') {
                int end = SqlScanner.skipDoubleQuoted(sql, i);
                out = append(out, sql, i, end);
                i = end;
                continue;
            }

            int dollarEnd = SqlScanner.findDollarQuotedLiteralEnd(sql, i);
            if (dollarEnd > i) {
                out = append(out, sql, i, dollarEnd);
                i = dollarEnd;
                continue;
            }

            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                int end = SqlScanner.skipLineComment(sql, i);
                out = append(out, sql, i, end);
                i = end;
                continue;
            }

            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                int end = SqlScanner.skipBlockComment(sql, i);
                out = append(out, sql, i, end);
                i = end;
                continue;
            }

            CastMatch match = matchParamCast(sql, i);
            if (match != null) {
                if (out == null) {
                    out = new StringBuilder(sql.length() + 16);
                    out.append(sql, 0, i);
                }
                out.append("CAST(:")
                        .append(match.paramName())
                        .append(" AS ")
                        .append(match.typeName())
                        .append(')');
                i = match.endIndex();
                continue;
            }

            if (out != null) {
                out.append(c);
            }
            i++;
        }

        return out == null ? sql : out.toString();
    }

    private static CastMatch matchParamCast(String sql, int index) {
        int len = sql.length();
        if (sql.charAt(index) != ':' || (index > 0 && sql.charAt(index - 1) == ':')) {
            return null;
        }

        int nameStart = index + 1;
        if (nameStart >= len || !isParamNameStart(sql.charAt(nameStart))) {
            return null;
        }

        int nameEnd = nameStart + 1;
        while (nameEnd < len && isParamNamePart(sql.charAt(nameEnd))) {
            nameEnd++;
        }

        if (nameEnd + 1 >= len || sql.charAt(nameEnd) != ':' || sql.charAt(nameEnd + 1) != ':') {
            return null;
        }

        int typeStart = skipWhitespace(sql, nameEnd + 2);
        int typeEnd = readTypeName(sql, typeStart);
        if (typeEnd <= typeStart) {
            return null;
        }

        String paramName = sql.substring(nameStart, nameEnd);
        String typeName = sql.substring(typeStart, typeEnd);
        return new CastMatch(paramName, typeName, typeEnd);
    }

    private static int readTypeName(String sql, int start) {
        int len = sql.length();
        int i = start;
        boolean readSegment = false;

        while (i < len) {
            int segmentStart = i;
            if (sql.charAt(i) == '"') {
                i = SqlScanner.skipDoubleQuoted(sql, i);
            } else if (isTypeNameStart(sql.charAt(i))) {
                i++;
                while (i < len && isTypeNamePart(sql.charAt(i))) {
                    i++;
                }
            } else {
                break;
            }

            if (i <= segmentStart) {
                break;
            }
            readSegment = true;

            if (i < len && sql.charAt(i) == '(') {
                i = SqlScanner.skipBalancedParentheses(sql, i);
            }

            while (i + 1 < len && sql.charAt(i) == '[' && sql.charAt(i + 1) == ']') {
                i += 2;
            }

            if (i < len && sql.charAt(i) == '.') {
                i++;
                continue;
            }
            break;
        }

        return readSegment ? i : start;
    }

    private static StringBuilder append(StringBuilder out, String sql, int start, int end) {
        if (out != null) {
            out.append(sql, start, end);
        }
        return out;
    }

    private static int skipWhitespace(String sql, int start) {
        int i = start;
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isParamNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isParamNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isTypeNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isTypeNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private record CastMatch(String paramName, String typeName, int endIndex) {
    }
}
