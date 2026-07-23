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

import java.util.ArrayList;
import java.util.List;

final class SqlScanner {

    private SqlScanner() {
        // utility class
    }

    static String stripTrailingSemicolon(String sql) {
        String stripped = sql;
        while (stripped.endsWith(";")) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        return stripped;
    }

    static String stripTopLevelTailClauses(String sql) {
        int cut = findFirstTopLevelTailClause(sql);
        return cut >= 0 ? sql.substring(0, cut).trim() : sql;
    }

    static int findFirstTopLevelTailClause(String sql) {
        int result = -1;
        int orderBy = findLastTopLevelKeyword(sql, "order by");

        for (int index : new int[]{orderBy, findFirstTopLevelPaginationClause(sql)}) {
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    static int findFirstTopLevelPaginationClause(String sql) {
        int result = -1;
        int limit = findLastTopLevelKeyword(sql, "limit");
        int offset = findLastTopLevelKeyword(sql, "offset");
        int fetch = findLastTopLevelKeyword(sql, "fetch");

        for (int index : new int[]{limit, offset, fetch}) {
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    static boolean startsWithKeyword(String sql, String keyword) {
        int i = 0;
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) {
            i++;
        }
        return matchesKeywordAt(sql, i, keyword);
    }

    static int findTopLevelKeyword(String sql, String keyword) {
        return findTopLevelKeyword(sql, keyword, false);
    }

    static int findLastTopLevelKeyword(String sql, String keyword) {
        return findTopLevelKeyword(sql, keyword, true);
    }

    private static int findTopLevelKeyword(String sql, String keyword, boolean last) {
        int depth = 0;
        int found = -1;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'') {
                i = skipSingleQuoted(sql, i);
                continue;
            }
            if (c == '"') {
                i = skipDoubleQuoted(sql, i);
                continue;
            }
            int dollarEnd = findDollarQuotedLiteralEnd(sql, i);
            if (dollarEnd > i) {
                i = dollarEnd;
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                i = skipLineComment(sql, i);
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                i = skipBlockComment(sql, i);
                continue;
            }
            if (c == '(') {
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                depth = Math.max(0, depth - 1);
                i++;
                continue;
            }
            if (depth == 0 && matchesKeywordAt(sql, i, keyword)) {
                found = i;
                if (!last) {
                    return found;
                }
            }
            i++;
        }
        return found;
    }

    static boolean matchesKeywordAt(String sql, int index, String keyword) {
        if (index < 0 || index >= sql.length()) {
            return false;
        }
        if (index > 0) {
            char previous = sql.charAt(index - 1);
            if (isIdentifierPart(previous) || previous == ':' || previous == '.') {
                return false;
            }
        }

        String[] tokens = keyword.split(" ");
        int pos = index;
        for (int t = 0; t < tokens.length; t++) {
            if (t > 0) {
                int beforeWhitespace = pos;
                while (pos < sql.length() && Character.isWhitespace(sql.charAt(pos))) {
                    pos++;
                }
                if (pos == beforeWhitespace) {
                    return false;
                }
            }
            String token = tokens[t];
            if (pos + token.length() > sql.length()
                    || !sql.regionMatches(true, pos, token, 0, token.length())) {
                return false;
            }
            pos += token.length();
        }

        return pos >= sql.length() || !isIdentifierPart(sql.charAt(pos));
    }

    static int endOfKeywordAt(String sql, int index, String keyword) {
        String[] tokens = keyword.split(" ");
        int pos = index;
        for (int t = 0; t < tokens.length; t++) {
            if (t > 0) {
                while (pos < sql.length() && Character.isWhitespace(sql.charAt(pos))) {
                    pos++;
                }
            }
            pos += tokens[t].length();
        }
        return pos;
    }

    static List<String> splitTopLevelComma(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\'') {
                i = skipSingleQuoted(text, i);
                continue;
            }
            if (c == '"') {
                i = skipDoubleQuoted(text, i);
                continue;
            }
            int dollarEnd = findDollarQuotedLiteralEnd(text, i);
            if (dollarEnd > i) {
                i = dollarEnd;
                continue;
            }
            if (c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
                i = skipLineComment(text, i);
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i = skipBlockComment(text, i);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
            i++;
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    static int skipSingleQuoted(String sql, int start) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                i += 2;
            } else if (c == '\'') {
                return i + 1;
            } else {
                i++;
            }
        }
        return sql.length();
    }

    static int skipDoubleQuoted(String sql, int start) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '"' && i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                i += 2;
            } else if (c == '"') {
                return i + 1;
            } else {
                i++;
            }
        }
        return sql.length();
    }

    static int findDollarQuotedLiteralEnd(String sql, int start) {
        if (sql.charAt(start) != '$') {
            return -1;
        }

        int tagEnd = start + 1;
        while (tagEnd < sql.length()) {
            char c = sql.charAt(tagEnd);
            if (c == '$') {
                String tag = sql.substring(start, tagEnd + 1);
                int literalEnd = sql.indexOf(tag, tagEnd + 1);
                return literalEnd >= 0 ? literalEnd + tag.length() : sql.length();
            }
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return -1;
            }
            tagEnd++;
        }
        return -1;
    }

    static int skipLineComment(String sql, int start) {
        int i = start + 2;
        while (i < sql.length() && sql.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    static int skipBlockComment(String sql, int start) {
        int end = sql.indexOf("*/", start + 2);
        return end >= 0 ? end + 2 : sql.length();
    }

    static int skipBalancedParentheses(String sql, int start) {
        int depth = 0;
        int i = start;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'') {
                i = skipSingleQuoted(sql, i);
                continue;
            }
            if (c == '"') {
                i = skipDoubleQuoted(sql, i);
                continue;
            }
            int dollarEnd = findDollarQuotedLiteralEnd(sql, i);
            if (dollarEnd > i) {
                i = dollarEnd;
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                i = skipLineComment(sql, i);
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                i = skipBlockComment(sql, i);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
            i++;
        }
        return sql.length();
    }

    static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
