# 测试策略 — BoosterQuery 库

## 核心原则

- 功能设计阶段 MUST 产出完整测试用例清单，与方案一起审批
- NEVER 先写实现再补测试 — 至少同步编写
- NEVER 弱化断言使测试通过 — 应修复代码本身
- 每个测试必须是确定性的（deterministic）和独立的

## 测试分层

### 层 1：纯单元测试（无 Spring 上下文）

**适用包**：`rewrite`、`support`（Mapper、Binder、Helper）、`cache`

- 工具：JUnit 5 + AssertJ，无任何 `@SpringBootTest`
- 覆盖对象：`JSqlParserRewriter`、`SmartSqlRewriter`、`JpaResultMapper`、`ParameterBinder`、`LimitAppender`
- NEVER mock JSqlParser 的内部 AST — 用真实 SQL 字符串测试
- SQL 改写测试必须覆盖改写前后的完整 SQL 字符串对比

### 层 2：集成测试（TestContainers）

**适用包**：`integration/` 子包

- 工具：`@SpringBootTest` + TestContainers（MySQL 8 + PostgreSQL 15）
- 验证端到端流程：`@BoosterQuery` → SQL 改写 → 参数绑定 → 结果映射
- 每个集成测试类标注 `@DisabledWithoutDocker`，Docker 不可用时自动跳过
- NEVER 用 H2 替代 TestContainers — 两者 SQL 方言不同

## SQL 改写测试规则（强制）

**每次新增或修改 SQL 改写规则时，MUST 同时在 `JSqlParserRewriterTest` 中添加：**

1. **正向测试** — 参数为 null/空时，条件被正确移除
2. **反向测试** — 参数有值时，条件被保留，SQL 不变

```java
// 正向：条件被移除
@Test
void should_removeWhereCondition_when_paramIsNull() {
    String sql = "SELECT * FROM t_user WHERE age > :age";
    String result = JSqlParserRewriter.removeNullConditions(sql, Set.of("age"));
    assertThat(result).isEqualToIgnoringWhitespace("SELECT * FROM t_user");
}

// 反向：条件被保留
@Test
void should_keepWhereCondition_when_paramHasValue() {
    String sql = "SELECT * FROM t_user WHERE age > :age";
    String result = JSqlParserRewriter.removeNullConditions(sql, Set.of());
    assertThat(result).isEqualToIgnoringWhitespace(sql);
}
```

## 测试用例设计模板

每个功能点 MUST 覆盖以下维度：

### 1. 正向路径（Happy Path）
- 标准输入 → 预期输出
- 多种合法输入变体

### 2. 边界条件（CRITICAL）

**参数边界：**
- [ ] `null` 参数
- [ ] 空字符串 `""` 参数
- [ ] 空白字符串 `"  "` 参数
- [ ] 空集合 `Collections.emptyList()` 参数
- [ ] 单元素集合 vs 多元素集合
- [ ] SQL 注入 payload（`'; DROP TABLE --`）
- [ ] Unicode 特殊字符（中文、Emoji）
- [ ] 超长 SQL（> 10K 字符的复杂嵌套查询）

**SQL 结构边界：**
- [ ] 单 WHERE 条件（移除后 WHERE 消失）
- [ ] 多条件 AND/OR 组合，部分参数为 null
- [ ] JOIN ON 条件中的参数
- [ ] HAVING 中的参数
- [ ] 子查询中的参数
- [ ] CTE（WITH 子句）中的参数

**DML 安全边界：**
- [ ] UPDATE 全条件移除 → 应抛出 `SqlRewriteException`
- [ ] DELETE 全条件移除 → 应抛出 `SqlRewriteException`
- [ ] UPDATE/DELETE 至少保留一个条件 → 正常执行

### 3. 异常路径
- 非法 SQL 语法 → 验证异常类型和消息
- JSqlParser 无法解析的方言 → 验证降级行为（不应抛出）
- 参数名不存在于 SQL 中 → 验证静默忽略

### 4. 组合场景
- 多参数全为 null
- 多参数部分为 null（不同组合）
- 分页（`Pageable`）+ 动态条件同时使用

## 测试命名约定

```java
// 推荐：should_<动作>_when_<条件>
void should_removeCondition_when_paramIsNull()
void should_throwException_when_allDeleteConditionsRemoved()
void should_keepCondition_when_paramHasValue()

// SQL 改写参数化测试
@ParameterizedTest
@MethodSource("provideNullParamScenarios")
void should_rewriteSql_when_paramsAreNull(String inputSql, Set<String> nullParams, String expectedSql)
```

## 完成验收清单

- [ ] `./gradlew test` — 全绿（包含单元测试）
- [ ] `./gradlew build` — 无编译错误，无 unchecked 警告
- [ ] 新增改写规则：`JSqlParserRewriterTest` 中正向 + 反向用例均已添加
- [ ] DML 变更：`SmartSqlRewriterTest` 中安全拦截用例已覆盖
- [ ] 结果映射变更：`JpaResultMapperTest` 中新类型已覆盖
- [ ] IF Docker 可用 → 集成测试（MySQL + PostgreSQL）均通过
