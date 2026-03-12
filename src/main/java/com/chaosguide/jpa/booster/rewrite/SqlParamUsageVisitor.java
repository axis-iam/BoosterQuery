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

import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;

import java.util.List;
import java.util.Set;

/**
 * JSqlParser-based SQL parameter usage analyzer.
 * <p>
 * Traverses the expression AST to determine whether a given expression subtree
 * references any of the specified named parameters, avoiding false positives
 * from naive string matching (e.g. ":param" appearing inside a string literal).
 * <p>
 * Supported expression types:
 * - Comparison expressions (=, !=, &gt;, &lt;, LIKE, etc.)
 * - BETWEEN expressions
 * - CASE WHEN expressions (all branches)
 * - Function calls (including nested functions)
 * - ANY/ALL subquery parameter checks
 * - Logical operators (AND/OR/NOT)
 * - Comparison expressions with subqueries and EXISTS expressions
 * - Arithmetic operations, string concatenation, window functions, and other common expressions
 */
public class SqlParamUsageVisitor extends ExpressionVisitorAdapter<Void> {

    private final Set<String> targetParams;
    private boolean found = false;

    private SqlParamUsageVisitor(Set<String> targetParams) {
        this.targetParams = targetParams;
    }

    /**
     * Static entry point: checks whether the expression references any parameter in nullParams.
     */
    public static boolean containsAnyParam(Expression expr, Set<String> nullParams) {
        if (expr == null || nullParams == null || nullParams.isEmpty()) {
            return false;
        }
        SqlParamUsageVisitor visitor = new SqlParamUsageVisitor(nullParams);
        expr.accept(visitor, null);
        return visitor.found;
    }

    // ============ Core callbacks: parameter detection ============

    @Override
    public <S> Void visit(JdbcNamedParameter parameter, S context) {
        if (targetParams.contains(parameter.getName())) {
            found = true;
        }
        return null;
    }

    @Override
    public <S> Void visit(JdbcParameter parameter, S context) {
        return null;
    }

    // ============ Comparison operators (full coverage) ============

    @Override
    public <S> Void visit(EqualsTo expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(NotEqualsTo expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(GreaterThan expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(GreaterThanEquals expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(MinorThan expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(MinorThanEquals expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(LikeExpression expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }
    // ============ Arithmetic operators ============

    @Override
    public <S> Void visit(Addition expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(Subtraction expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(Multiplication expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(Division expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(Modulo expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    // ============ String operations ============

    @Override
    public <S> Void visit(Concat expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    // ============ Bitwise operations ============

    @Override
    public <S> Void visit(BitwiseAnd expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(BitwiseOr expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    @Override
    public <S> Void visit(BitwiseXor expr, S context) {
        visitBinaryExpr(expr);
        return null;
    }

    // ============ Generic binary expression handling ============

    /**
     * Generic binary expression handler (recursively checks left and right subtrees).
     */
    private void visitBinaryExpr(BinaryExpression expr) {
        if (found) {
            return;
        }
        if (expr.getLeftExpression() != null) {
            expr.getLeftExpression().accept(this, null);
        }
        if (!found && expr.getRightExpression() != null) {
            expr.getRightExpression().accept(this, null);
        }
    }

    // ============ Logical operators ============

    @Override
    public <S> Void visit(AndExpression andExpr, S context) {
        if (found) return null;
        if (andExpr.getLeftExpression() != null) {
            andExpr.getLeftExpression().accept(this, null);
        }
        if (!found && andExpr.getRightExpression() != null) {
            andExpr.getRightExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(OrExpression orExpr, S context) {
        if (found) return null;
        if (orExpr.getLeftExpression() != null) {
            orExpr.getLeftExpression().accept(this, null);
        }
        if (!found && orExpr.getRightExpression() != null) {
            orExpr.getRightExpression().accept(this, null);
        }
        return null;
    }
    // ============ Special expression types ============

    @Override
    public <S> Void visit(InExpression inExpression, S context) {
        if (found) return null;
        if (inExpression.getLeftExpression() != null) {
            inExpression.getLeftExpression().accept(this, null);
        }
        // In JSqlParser 5.x, the right side is unified as getRightExpression()
        // which may be an ExpressionList, ParenthesedSelect, etc.
        if (!found && inExpression.getRightExpression() != null) {
            inExpression.getRightExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(Between between, S context) {
        if (found) return null;
        if (between.getLeftExpression() != null) {
            between.getLeftExpression().accept(this, null);
        }
        if (!found && between.getBetweenExpressionStart() != null) {
            between.getBetweenExpressionStart().accept(this, null);
        }
        if (!found && between.getBetweenExpressionEnd() != null) {
            between.getBetweenExpressionEnd().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(IsNullExpression isNull, S context) {
        if (found) return null;
        if (isNull.getLeftExpression() != null) {
            isNull.getLeftExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(ExistsExpression exists, S context) {
        if (found) return null;
        if (exists.getRightExpression() != null) {
            exists.getRightExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(CaseExpression caseExpr, S context) {
        if (found) return null;
        if (caseExpr.getSwitchExpression() != null) {
            caseExpr.getSwitchExpression().accept(this, null);
        }
        if (!found && caseExpr.getWhenClauses() != null) {
            for (WhenClause when : caseExpr.getWhenClauses()) {
                if (when.getWhenExpression() != null) {
                    when.getWhenExpression().accept(this, null);
                }
                if (!found && when.getThenExpression() != null) {
                    when.getThenExpression().accept(this, null);
                }
                if (found) break;
            }
        }
        if (!found && caseExpr.getElseExpression() != null) {
            caseExpr.getElseExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(WhenClause whenClause, S context) {
        if (found) return null;
        if (whenClause.getWhenExpression() != null) {
            whenClause.getWhenExpression().accept(this, null);
        }
        if (!found && whenClause.getThenExpression() != null) {
            whenClause.getThenExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(Function function, S context) {
        if (found) return null;
        ExpressionList<?> params = function.getParameters();
        if (params != null) {
            for (Expression param : params) {
                param.accept(this, null);
                if (found) break;
            }
        }
        return null;
    }

    @Override
    public <S> Void visit(AnyComparisonExpression anyExpr, S context) {
        // ANY/ALL expression: subqueries handled at the outer level
        return null;
    }

    @Override
    public <S> Void visit(ParenthesedSelect pSel, S context) {
        // Subqueries handled by the outer SqlRewriteExpressionVisitor
        return null;
    }

    /**
     * Handles parenthesized expressions and expression lists.
     * In JSqlParser 5.x, Parenthesis extends ExpressionList, so this covers both.
     */
    @Override
    public <S> Void visit(ExpressionList<? extends Expression> exprList, S context) {
        if (found) return null;
        for (Expression expr : exprList) {
            expr.accept(this, null);
            if (found) break;
        }
        return null;
    }
    @Override
    public <S> Void visit(NotExpression notExpr, S context) {
        if (found) return null;
        if (notExpr.getExpression() != null) {
            notExpr.getExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(SignedExpression signedExpr, S context) {
        if (found) return null;
        if (signedExpr.getExpression() != null) {
            signedExpr.getExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(CastExpression cast, S context) {
        if (found) return null;
        if (cast.getLeftExpression() != null) {
            cast.getLeftExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(ExtractExpression extract, S context) {
        if (found) return null;
        if (extract.getExpression() != null) {
            extract.getExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(IntervalExpression interval, S context) {
        if (found) return null;
        if (interval.getExpression() != null) {
            interval.getExpression().accept(this, null);
        }
        return null;
    }

    @Override
    public <S> Void visit(AnalyticExpression analyticExpr, S context) {
        if (found) return null;
        if (analyticExpr.getExpression() != null) {
            analyticExpr.getExpression().accept(this, null);
        }
        if (!found && analyticExpr.getPartitionExpressionList() != null) {
            for (Expression expr : analyticExpr.getPartitionExpressionList()) {
                expr.accept(this, null);
                if (found) break;
            }
        }
        if (!found && analyticExpr.getOrderByElements() != null) {
            for (OrderByElement orderBy : analyticExpr.getOrderByElements()) {
                if (orderBy.getExpression() != null) {
                    orderBy.getExpression().accept(this, null);
                    if (found) break;
                }
            }
        }
        return null;
    }

    // ============ Literal types (contain no parameters, skip) ============

    @Override
    public <S> Void visit(Column column, S context) { return null; }

    @Override
    public <S> Void visit(StringValue stringValue, S context) { return null; }

    @Override
    public <S> Void visit(LongValue longValue, S context) { return null; }

    @Override
    public <S> Void visit(DoubleValue doubleValue, S context) { return null; }

    @Override
    public <S> Void visit(DateValue dateValue, S context) { return null; }

    @Override
    public <S> Void visit(TimeValue timeValue, S context) { return null; }

    @Override
    public <S> Void visit(TimestampValue timestampValue, S context) { return null; }

    @Override
    public <S> Void visit(NullValue nullValue, S context) { return null; }

    @Override
    public <S> Void visit(HexValue hexValue, S context) { return null; }
}
