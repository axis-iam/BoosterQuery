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
package com.chaosguide.jpa.booster.rewrite;

import com.chaosguide.jpa.booster.support.SqlSanitizer;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * JSqlParser-based SQL condition rewriter.
 * <p>
 * Purpose:
 * Before executing SQL, parses the statement and removes filter conditions that
 * depend on null parameters, covering WHERE, HAVING, JOIN ON/USING, and subqueries,
 * to prevent ineffective filtering and dangerous operations (e.g. full-table update/delete).
 * <p>
 * Features:
 * - Rewrites conditions in WHERE and HAVING clauses
 * - Rewrites JOIN ON/USING conditions
 * - Handles subqueries in SELECT lists and UPDATE SET clauses
 * - Supports subqueries inside CASE WHEN expressions
 * - Supports subqueries on either side of comparison expressions
 * - Supports ANY/ALL subqueries
 * - Safety check on UPDATE/DELETE: fails when all conditions are removed
 */
public class JSqlParserRewriter {

    private JSqlParserRewriter() {
    }

    /**
     * Removes WHERE/ON conditions that reference nullParams from the SQL.
     *
     * @param sql        original SQL
     * @param nullParams set of parameter names whose values are null
     * @return rewritten SQL
     * @throws JSQLParserException   if SQL parsing fails
     * @throws IllegalStateException if all conditions are removed, to prevent dangerous operations
     */
    public static String removeNullConditions(String sql, Set<String> nullParams) throws JSQLParserException {
        if (nullParams == null || nullParams.isEmpty()) {
            return sql;
        }

        Statement statement = CCJSqlParserUtil.parse(sql);
        // Use a custom Visitor to recursively modify the AST
        statement.accept(new SqlRewriteStatementVisitor(nullParams), null);
        // Regenerate the SQL string
        return statement.toString();
    }

    /**
     * Unified entry point: cleans the root expression.
     */
    private static Expression cleanExpressionRoot(Expression expr, Set<String> nullParams) {
        SqlRewriteExpressionVisitor visitor = new SqlRewriteExpressionVisitor(nullParams);
        return visitor.process(expr);
    }

    /**
     * Statement visitor: traverses statement-level nodes (SELECT, UPDATE, DELETE).
     */
    private static class SqlRewriteStatementVisitor extends StatementVisitorAdapter<Void> {
        private final Set<String> nullParams;

        public SqlRewriteStatementVisitor(Set<String> nullParams) {
            this.nullParams = nullParams;
        }

        @Override
        public <S> Void visit(Select select, S context) {
            SqlRewriteSelectVisitor sv = new SqlRewriteSelectVisitor(nullParams);

            // Recursively process subqueries inside CTE (WITH / WITH RECURSIVE) clauses
            if (select.getWithItemsList() != null) {
                for (WithItem withItem : select.getWithItemsList()) {
                    sv.processSelect(withItem.getSelect());
                }
            }

            if (select instanceof PlainSelect ps) {
                sv.visit(ps, null);
            } else if (select instanceof SetOperationList sol) {
                sv.visit(sol, null);
            } else if (select instanceof ParenthesedSelect pSel) {
                sv.visit(pSel, null);
            }
            return null;
        }

        @Override
        public <S> Void visit(Update update, S context) {
            // Recursively process subqueries inside CTE (WITH) clauses
            if (update.getWithItemsList() != null) {
                SqlRewriteSelectVisitor sv = new SqlRewriteSelectVisitor(nullParams);
                for (WithItem<?> withItem : update.getWithItemsList()) {
                    sv.processSelect(withItem.getSelect());
                }
            }

            // Handle subqueries in the SET clause of UPDATE statements
            if (update.getUpdateSets() != null) {
                for (UpdateSet updateSet : update.getUpdateSets()) {
                    ExpressionList<?> values = updateSet.getValues();
                    if (values != null) {
                        for (Expression expr : values) {
                            SubQueryRewriter.rewrite(expr, nullParams);
                        }
                    }
                }
            }

            // Handle the WHERE clause of the UPDATE statement
            Expression where = update.getWhere();
            if (where != null) {
                Expression newWhere = cleanExpressionRoot(where, nullParams);

                // Safety check: prevent full-table update
                if (newWhere == null) {
                    throw new IllegalStateException(
                            "All WHERE conditions removed; refusing to execute UPDATE to prevent full-table update. SQL (sanitized): "
                                    + SqlSanitizer.sanitize(update.toString())
                    );
                }

                update.setWhere(newWhere);
            }
            return null;
        }

        @Override
        public <S> Void visit(Delete delete, S context) {
            // Recursively process subqueries inside CTE (WITH) clauses
            if (delete.getWithItemsList() != null) {
                SqlRewriteSelectVisitor sv = new SqlRewriteSelectVisitor(nullParams);
                for (WithItem<?> withItem : delete.getWithItemsList()) {
                    sv.processSelect(withItem.getSelect());
                }
            }

            // Handle the WHERE clause of the DELETE statement
            Expression where = delete.getWhere();
            if (where != null) {
                Expression newWhere = cleanExpressionRoot(where, nullParams);

                // Safety check: prevent full-table delete
                if (newWhere == null) {
                    throw new IllegalStateException(
                            "All WHERE conditions removed; refusing to execute DELETE to prevent full-table delete. SQL (sanitized): "
                                    + SqlSanitizer.sanitize(delete.toString())
                    );
                }

                delete.setWhere(newWhere);
            }
            return null;
        }
    }

    /**
     * Select visitor: traverses SELECT bodies including WHERE, JOIN, FROM subqueries, etc.
     */
    private static class SqlRewriteSelectVisitor extends SelectVisitorAdapter<Void> {
        private final Set<String> nullParams;

        public SqlRewriteSelectVisitor(Set<String> nullParams) {
            this.nullParams = nullParams;
        }

        @Override
        public <S> Void visit(PlainSelect plainSelect, S context) {
            // 1. Handle subqueries in the SELECT list
            if (plainSelect.getSelectItems() != null) {
                for (SelectItem<?> item : plainSelect.getSelectItems()) {
                    Expression expr = item.getExpression();
                    if (expr != null) {
                        SubQueryRewriter.rewrite(expr, nullParams);
                    }
                }
            }

            // 2. Handle WHERE conditions
            Expression where = plainSelect.getWhere();
            if (where != null) {
                Expression newWhere = cleanExpressionRoot(where, nullParams);
                plainSelect.setWhere(newWhere);
            }

            // 3. Handle HAVING conditions
            Expression having = plainSelect.getHaving();
            if (having != null) {
                Expression newHaving = cleanExpressionRoot(having, nullParams);
                plainSelect.setHaving(newHaving);
            }

            // 4. Recursively handle the FROM clause (if it is a subquery)
            if (plainSelect.getFromItem() != null) {
                plainSelect.getFromItem().accept(new FromItemVisitorAdapter<Void>() {
                    @Override
                    public <S2> Void visit(ParenthesedSelect pSel, S2 ctx) {
                        processSelect(pSel.getSelect());
                        return null;
                    }
                }, null);
            }

            // 5. Recursively handle the JOIN list
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    // 5.1 Handle ON conditions
                    Collection<Expression> onExpressions = join.getOnExpressions();
                    if (onExpressions != null && !onExpressions.isEmpty()) {
                        List<Expression> newOnExpressions = new ArrayList<>();
                        for (Expression onExpr : onExpressions) {
                            Expression newOn = cleanExpressionRoot(onExpr, nullParams);
                            if (newOn != null) {
                                newOnExpressions.add(newOn);
                            }
                        }
                        join.setOnExpressions(newOnExpressions);
                    }

                    // 5.2 Recursively check if the right side of the JOIN is a subquery
                    join.getRightItem().accept(new FromItemVisitorAdapter<Void>() {
                        @Override
                        public <S2> Void visit(ParenthesedSelect pSel, S2 ctx) {
                            processSelect(pSel.getSelect());
                            return null;
                        }
                    }, null);
                }
            }
            return null;
        }

        @Override
        public <S> Void visit(ParenthesedSelect pSel, S context) {
            processSelect(pSel.getSelect());
            return null;
        }

        @Override
        public <S> Void visit(SetOperationList setOpList, S context) {
            // Handle UNION, INTERSECT, and other set operations
            for (Select sel : setOpList.getSelects()) {
                if (sel instanceof PlainSelect ps) {
                    visit(ps, null);
                } else if (sel instanceof SetOperationList sol) {
                    visit(sol, null);
                } else if (sel instanceof ParenthesedSelect pSel) {
                    visit(pSel, null);
                }
            }
            return null;
        }

        /**
         * Unified handler for subqueries.
         */
        private void processSelect(Select select) {
            if (select instanceof PlainSelect ps) {
                visit(ps, null);
            } else if (select instanceof SetOperationList sol) {
                visit(sol, null);
            } else if (select instanceof ParenthesedSelect pSel) {
                visit(pSel, null);
            }
        }
    }

    /**
     * Expression visitor: encapsulates the core logic for recursively cleaning expressions.
     */
    private static class SqlRewriteExpressionVisitor extends ExpressionVisitorAdapter<Void> {
        /**
         * Expression types that are safe to delete (value-filtering types).
         */
        private static final Set<Class<? extends Expression>> SAFE_TO_DELETE_TYPES = Set.of(
                EqualsTo.class,
                NotEqualsTo.class,
                GreaterThan.class,
                GreaterThanEquals.class,
                MinorThan.class,
                MinorThanEquals.class,
                LikeExpression.class,
                Between.class,
                InExpression.class,
                AnyComparisonExpression.class
        );

        private final Set<String> nullParams;

        public SqlRewriteExpressionVisitor(Set<String> nullParams) {
            this.nullParams = nullParams;
        }

        /**
         * Core entry point: takes an expression and returns the cleaned expression (may be null).
         */
        public Expression process(Expression expr) {
            if (expr == null) return null;

            // Handle NOT expressions first, simplifying the inner condition
            if (expr instanceof NotExpression notExpr) {
                Expression inner = process(notExpr.getExpression());
                if (inner == null) {
                    return null; // Inner expression removed, so remove the entire NOT
                }
                notExpr.setExpression(inner);
                return notExpr;
            }

            // Handle logical operators (AND/OR)
            if (expr instanceof AndExpression || expr instanceof OrExpression) {
                return cleanLogicExpression((BinaryExpression) expr);
            }

            // Handle parenthesized expressions
            // In JSqlParser 5.x, (expr) is parsed as ParenthesedExpressionList with one element
            if (expr instanceof ParenthesedExpressionList<?> parenList && parenList.size() == 1) {
                Object first = parenList.getFirst();
                if (!(first instanceof Expression)) {
                    // Not an Expression, return unchanged
                    return expr;
                }
                Expression inner = process((Expression) first);
                if (inner == null) {
                    return null;
                }
                try {
                    @SuppressWarnings("unchecked")
                    ParenthesedExpressionList<Expression> typedList = (ParenthesedExpressionList<Expression>) parenList;
                    typedList.set(0, inner);
                    return typedList;
                } catch (ClassCastException e) {
                    // Unexpected type, return unchanged
                    return expr;
                }
            }

            // Fix JSqlParser 5.3 parsing quirk: IN (:param) AND/OR condition is incorrectly parsed as
            // a single InExpression (right side is AndExpression/OrExpression); decompose into proper logical expression
            if (expr instanceof InExpression inExpr) {
                Expression right = inExpr.getRightExpression();
                if (right instanceof AndExpression || right instanceof OrExpression) {
                    BinaryExpression logicalExpr = (BinaryExpression) right;
                    // Restore IN expression: set AND/OR's left side (the actual param list) as IN's right side
                    inExpr.setRightExpression(logicalExpr.getLeftExpression());
                    // Place the corrected IN expression back as AND/OR's left side
                    logicalExpr.setLeftExpression(inExpr);
                    // Process as normal AND/OR logic recursively
                    return cleanLogicExpression(logicalExpr);
                }
            }

            // Recursively clean subqueries inside the expression
            SubQueryRewriter.rewrite(expr, nullParams);

            // Safety check and deletion decision
            if (isSafeToDelete(expr) && SqlParamUsageVisitor.containsAnyParam(expr, nullParams)) {
                return null;
            }

            return expr;
        }

        /**
         * Cleans a binary logical expression (AND/OR).
         */
        private Expression cleanLogicExpression(BinaryExpression binExpr) {
            Expression left = process(binExpr.getLeftExpression());
            Expression right = process(binExpr.getRightExpression());

            if (left == null && right == null) {
                return null;
            }
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }

            binExpr.setLeftExpression(left);
            binExpr.setRightExpression(right);
            return binExpr;
        }

        /**
         * Checks whether the expression is a value-filtering type that can be safely deleted.
         */
        private boolean isSafeToDelete(Expression expr) {
            return SAFE_TO_DELETE_TYPES.stream()
                    .anyMatch(type -> type.isInstance(expr));
        }
    }

    /**
     * Subquery rewriter: recursively traverses expressions to find and clean subqueries.
     */
    private static class SubQueryRewriter {

        public static void rewrite(Expression expr, Set<String> nullParams) {
            if (expr == null) return;

            SqlRewriteSelectVisitor selectVisitor = new SqlRewriteSelectVisitor(nullParams);

            // Direct subquery (ParenthesedSelect in JSqlParser 5.x)
            if (expr instanceof ParenthesedSelect pSel) {
                selectVisitor.processSelect(pSel.getSelect());
            }

            // Subquery inside EXISTS expression
            if (expr instanceof ExistsExpression exists) {
                Expression rightExpr = exists.getRightExpression();
                if (rightExpr instanceof ParenthesedSelect pSel) {
                    selectVisitor.processSelect(pSel.getSelect());
                } else if (rightExpr != null) {
                    rewrite(rightExpr, nullParams);
                }
            }

            // IN expression (when the right side is a subquery)
            if (expr instanceof InExpression inExpr) {
                if (inExpr.getLeftExpression() != null) {
                    rewrite(inExpr.getLeftExpression(), nullParams);
                }

                Expression rightExpr = inExpr.getRightExpression();
                if (rightExpr instanceof ParenthesedSelect pSel) {
                    selectVisitor.processSelect(pSel.getSelect());
                } else if (rightExpr != null) {
                    rewrite(rightExpr, nullParams);
                }
            }

            // Subqueries inside CASE WHEN expressions
            if (expr instanceof CaseExpression caseExpr) {
                // Handle switch expression
                if (caseExpr.getSwitchExpression() != null) {
                    rewrite(caseExpr.getSwitchExpression(), nullParams);
                }
                // Handle WHEN clauses
                if (caseExpr.getWhenClauses() != null) {
                    for (WhenClause when : caseExpr.getWhenClauses()) {
                        if (when.getWhenExpression() != null) {
                            rewrite(when.getWhenExpression(), nullParams);
                        }
                        if (when.getThenExpression() != null) {
                            rewrite(when.getThenExpression(), nullParams);
                        }
                    }
                }
                // Handle ELSE clause
                if (caseExpr.getElseExpression() != null) {
                    rewrite(caseExpr.getElseExpression(), nullParams);
                }
            }

            // Subqueries inside binary expressions
            if (expr instanceof BinaryExpression binExpr) {
                rewrite(binExpr.getLeftExpression(), nullParams);
                rewrite(binExpr.getRightExpression(), nullParams);
            }

            // ANY/ALL subqueries
            if (expr instanceof AnyComparisonExpression anyExpr) {
                Select anySelect = anyExpr.getSelect();
                if (anySelect != null) {
                    selectVisitor.processSelect(anySelect);
                }
            }

            // Parenthesized expressions
            if (expr instanceof ParenthesedExpressionList<?> parenList) {
                for (Object item : parenList) {
                    if (item instanceof Expression e) {
                        rewrite(e, nullParams);
                    }
                }
            }

            // Function
            if (expr instanceof Function func) {
                ExpressionList<?> params = func.getParameters();
                if (params != null) {
                    for (Expression param : params) {
                        rewrite(param, nullParams);
                    }
                }
            }
        }
    }
}
