# SQL 安全规则 — BoosterQuery 库

## 核心原则

BoosterQuery 的核心价值是安全地执行动态原生 SQL。
任何代码变更都不得削弱以下安全约束。

---

## 参数绑定安全

### MUST DO

- 所有参数通过 JPA `query.setParameter(name, value)` 绑定 — 命名参数格式 `:paramName`
- `ParameterBinder` 是唯一合法的参数绑定入口，不绕过

### NEVER DO

- **NEVER** 将参数值拼接进 SQL 字符串（字符串连接 / `String.format` / 文本块拼接）
- **NEVER** 信任外部传入的参数名（Sort 字段等需白名单验证）

### Sort 注入防护

```java
// Sort 参数必须通过 SqlHelper.validateSortColumns() 白名单验证
// 禁止将 Sort.Order.getProperty() 直接拼接进 ORDER BY 子句
```

---

## DML 全表操作防护

### 规则

当 `UPDATE` 或 `DELETE` 的所有 `WHERE` 条件均被移除（所有参数为 null），
MUST 抛出 `SqlRewriteException`，绝不执行全表操作。

```java
// JSqlParserRewriter 中的安全检查（不得删除或绕过）
if (原SQL有WHERE条件 && 改写后WHERE条件全部消失) {
    throw new SqlRewriteException("拒绝执行：动态改写后 UPDATE/DELETE 失去全部 WHERE 条件，" +
                                  "可能导致全表操作。原始 SQL：" + SqlSanitizer.sanitize(sql));
}
```

### 适用范围

- `UPDATE ... WHERE` — 所有条件被移除时拦截
- `DELETE ... WHERE` — 所有条件被移除时拦截
- `SELECT` — 不拦截（全表扫描是合法查询）

---

## JSqlParser 使用规则

### 解析失败处理

JSqlParser 遇到不支持的 SQL 语法时，MUST 抛出异常中止执行，**不得降级返回原始 SQL**：

> **原因**：降级返回原始 SQL 时，null 参数对应的条件仍在 SQL 中（如 `WHERE age = :age`），
> 绑定 null 值会导致 `age = NULL` 永远为 false（SQL 三值逻辑），返回错误结果；
> 不绑定则 JPA 抛出 `QueryException: Named parameter [age] not set`。
> 两种情况都比直接报错更危险（静默返回错误数据 vs 明确失败）。

```java
// CORRECT：解析失败时抛出异常，明确中止
try {
    return JSqlParserRewriter.removeNullConditions(sql, nullParams);
} catch (JSQLParserException | RuntimeException e) {
    log.error("SQL 改写失败（sanitized）：{}", SqlSanitizer.sanitize(sql), e);
    throw new SqlRewriteException("SQL 改写失败：" + e.getMessage(), e);
}

// WRONG：降级返回原始 SQL（会导致 null 参数条件错误求值或未绑定参数异常）
// return sql;
```

### AST 改写而非字符串操作

- 所有条件移除操作通过 JSqlParser AST 访问器实现
- NEVER 用正则表达式或字符串替换来修改 SQL 结构
- 例外：`LimitAppender.appendLimit()` 的字符串追加（已标注 `@Deprecated`，不新增此类操作）

### SQL 注释处理

- 参数名提取（`BoosterSqlRewriter` 中的正则 `:paramName`）MUST 先剥离 SQL 注释
- 防止注释中的 `:paramName` 被错误识别为需要绑定的参数

---

## 缓存安全

### 缓存键稳定性

缓存键必须使用不可变、哈希稳定的类型：

```java
// CORRECT：使用 Set<String>（AbstractSet.hashCode() 为元素哈希值之和，与顺序无关；equals() 比较元素集合）
record RewriteCacheKey(String sql, Set<String> nullParams) {}

// ALSO CORRECT：使用排序后的不可变 List（显式保证顺序一致性，但增加了不必要的排序开销）
record RewriteCacheKey(String sql, List<String> nullParams) {
    RewriteCacheKey(String sql, Set<String> nullParams) {
        this(sql, nullParams.stream().sorted().toList());
    }
}
```

> **注**：Java `Set.hashCode()` 和 `Set.equals()` 的语义保证与元素遍历顺序无关，
> `HashSet` 作为 record 字段用于缓存键是安全的。

### 缓存隔离

- 不同数据库方言（MySQL / PostgreSQL）使用同一缓存是安全的（SQL 语义相同）
- 但多租户场景下，若不同租户的 SQL 模板相同但参数不同，缓存键仍可复用（参数值不入缓存键，这是设计决策）

---

## 日志脱敏

- 日志中的 SQL 必须通过 `SqlSanitizer.sanitize()` 处理后再输出
- NEVER 在日志中输出参数值（可能包含 PII 或敏感业务数据）
- DEBUG 级别可输出改写前后 SQL 对比（脱敏后）

```java
// CORRECT
log.debug("SQL 改写完成：原始={}, 改写后={}",
          SqlSanitizer.sanitize(originalSql),
          SqlSanitizer.sanitize(rewrittenSql));

// WRONG
log.info("执行参数：{}", params); // 参数值可能敏感
```

---

## API 向后兼容性

- 公共接口（`BoosterNativeRepository`、`BoosterQueryRepository`）的方法签名一旦发布，NEVER 在同一 major 版本中变更参数类型或移除方法
- `@BoosterQuery` 注解属性新增时 MUST 提供默认值，不破坏现有注解使用
- `SmartSqlRewriterConfig` 的配置属性 key（`booster.query.*`）一旦发布不得重命名
