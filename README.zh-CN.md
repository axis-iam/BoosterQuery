<p align="center">
  <strong>BoosterQuery</strong><br>
  <em>Spring Data JPA 原生 SQL 增强库</em>
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/Java-25%2B-orange" alt="Java 25+">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0-green" alt="Spring Boot 4.0">
  <img src="https://img.shields.io/maven-central/v/com.chaosguide/booster-query?label=Maven%20Central" alt="Maven Central">
</p>

<p align="center">
  <a href="README.md">English</a>
</p>

---

轻量级 Spring Data JPA 增强库，提供原生 SQL 统一执行、智能 SQL 改写和自动结果映射。

## 核心特性

- **原生 SQL 执行** — 统一 API 支持分页、列表、单条、计数和 DML 查询
- **智能 SQL 改写** — 参数为 null/空白/空集合时自动移除 WHERE/HAVING/JOIN 条件（基于 JSqlParser AST）
- **自动结果映射** — Tuple → Entity / DTO / Record / 接口投影 / Map / 基础类型，下划线自动转驼峰
- **Auto-Limit 防护** — 通过数据库无关的最大结果数限制防止大结果集（默认 10,000 行）
- **Caffeine 缓存** — 缓存改写后的 SQL，可配置大小和过期时间
- **@BoosterQuery 注解** — 声明式 SQL 查询，支持逐方法覆盖配置

## 快速开始

### 1. 添加依赖

**Gradle (Kotlin DSL):**
```kotlin
implementation("com.chaosguide:booster-query:1.0.2")
```

**Maven:**
```xml
<dependency>
    <groupId>com.chaosguide</groupId>
    <artifactId>booster-query</artifactId>
    <version>1.0.2</version>
</dependency>
```
### 2. 启用 BoosterQuery

```java
@SpringBootApplication
@EnableJpaRepositories(repositoryFactoryBeanClass = BoosterQueryRepositoryFactoryBean.class)
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### 3. 定义仓库

```java
public interface UserRepository extends BoosterQueryRepository<User, Long> { }
```

### 4. 使用智能查询

```java
String sql = "SELECT * FROM t_user WHERE name LIKE :name AND age > :age";
Map<String, Object> params = new HashMap<>();
params.put("name", name);  // null → 条件自动移除
params.put("age", age);    // null → 条件自动移除
List<User> users = repo.boosterQueryList(sql, params);
```

## 仓库体系

| 接口 | 方法前缀 | 说明 |
|---|---|---|
| `BoosterNativeRepository` | `native*` | 原生 SQL 执行 + 结果映射 |
| `BoosterQueryRepository` | `booster*` | + 智能改写 + 自动限制 + 缓存 |

`BoosterQueryRepository` 继承 `BoosterNativeRepository`，后者继承 `JpaRepository`。

## @BoosterQuery 注解

```java
public interface UserRepository extends BoosterQueryRepository<User, Long> {

    // 开启 -parameters 编译选项后，可省略 @Param
    @BoosterQuery("SELECT * FROM t_user WHERE name LIKE :name AND age > :age")
    List<User> findByConditions(String name, Integer age);

    // @Param 仍然支持，且优先级高于反射参数名
    @BoosterQuery(
        value = "SELECT user_name, email FROM t_user WHERE status = :status",
        resultType = UserDTO.class,
        enableRewrite = Toggle.TRUE,
        autoLimit = 500
    )
    List<UserDTO> findActiveUsers(@Param("status") String status);
}
```

### 支持的返回类型

`@BoosterQuery` 根据方法返回类型自动推断结果映射类型：

| 返回类型 | 行为 |
|---|---|
| `List<T>` | 列表查询，每行映射为 `T` |
| `Page<T>` | 分页查询（需要 `Pageable` 参数） |
| `Optional<T>` | 单条结果包装为 Optional |
| `T`（DTO / Record / 接口投影 / 实体） | 直接单条结果映射 |
| `Map<String, Object>` | 单行作为键值 Map |
| `String`、`Integer`、`Long`、`BigDecimal` 等 | 标量值提取 |
| `@Modifying` + `int` / `Integer` / `void` | DML 执行（返回受影响行数） |

计数方法应显式编写计数表达式，例如 `@BoosterQuery("SELECT COUNT(*) FROM t_user")`。
DML 方法必须标注 Spring Data 的 `@Modifying`，并在事务中执行。未标注
`@Modifying` 的数值返回类型会作为普通标量查询结果处理。

**示例 — 直接返回 DTO / Record：**

```java
// 直接返回 DTO — 无需包装类型
@BoosterQuery("SELECT user_name, email FROM t_user WHERE id = :id")
UserDTO findUserById(@Param("id") Long id);

// 直接返回 Record
@BoosterQuery("SELECT SUM(amount) AS totalRevenue, COUNT(*) AS orderCount FROM t_order")
SummaryRecord getOrderSummary();

// 直接返回接口投影
@BoosterQuery("SELECT name, email FROM t_user WHERE id = :id")
UserProjection findProjectionById(@Param("id") Long id);

// Optional 包装
@BoosterQuery("SELECT user_name, email FROM t_user WHERE id = :id")
Optional<UserDTO> findOptionalUserById(@Param("id") Long id);
```

> **注意：** 容器类型（`List`、`Page`、`Optional`）必须声明泛型参数。
> 使用无泛型的 raw type（如 `List`）会在启动时抛出 `IllegalStateException`。

### 参数绑定

参数名按以下优先级解析：

1. **`@Param` 注解** — 显式声明，始终生效
2. **`-parameters` 编译选项** — 通过反射从方法签名推断参数名
3. **单个 POJO/Map 参数** — 自动按字段名提取

Spring Boot 项目默认开启 `-parameters`，因此 `@Param` 通常可以省略。

非 Spring Boot 项目需在 `build.gradle.kts` 中添加：

```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
```

### PostgreSQL 命名参数类型转换

命名参数需要转换为 PostgreSQL 类型时，推荐使用 Hibernate 安全的
`CAST(:param AS type)` 写法：

```sql
AND gi.status = CAST(:status AS global_identity_status)
```

BoosterQuery 也会在创建 JPA Query 前，将命名参数上的 PostgreSQL 简写转换：

```sql
AND gi.status = :status::global_identity_status
```

会按以下形式执行：

```sql
AND gi.status = CAST(:status AS global_identity_status)
```

列类型转换（如 `created_at::date`）不会被改写。跨 Hibernate 版本或 SQL 会被
BoosterQuery 之外的代码复用时，仍建议优先写 `CAST(:param AS type)`。

## 配置

```yaml
booster:
  query:
    default-limit: 10000
    enable-auto-limit: true
    enable-sql-rewrite: true
    cache:
      enabled: true
      maximum-size: 1000
      expire-after-write: 3600000
```

设置 `booster.query.enable-sql-rewrite=false` 可以全局关闭 null 条件改写。
仓库方法可通过 `@BoosterQuery(enableRewrite = Toggle.FALSE)` 或 `Toggle.TRUE`
逐方法覆盖全局配置。

启用自动限制后，`default-limit` 既限制不分页查询的返回行数，也是 `Pageable`
允许的最大分页大小。分页大小超限时会在执行 count 查询前直接拒绝；合法分页的
count 查询不会被限流，因此 `Page.getTotalElements()` 仍是完整匹配总数。不分页
请求不会执行 count 查询，其 total 等于限流后的内容数量。

## JaCoCo 与 JSqlParser

BoosterQuery 对常见分页 count/sort 使用轻量级字符串转换；只有真正需要
null 条件 AST 改写时才加载 JSqlParser。

如果测试开启 JaCoCo 后，JSqlParser 生成类的大方法（例如
`CCJSqlParserTokenManager.jjMoveNfa_0`）触发 instrumentation 失败，可在测试任务中排除：

```kotlin
tasks.withType<Test> {
    extensions.configure<JacocoTaskExtension> {
        excludes = listOf("net.sf.jsqlparser.*")
    }
}
```

## 安全说明

### ⚠️ SQL 注入防护

**永远不要将用户输入拼接到 SQL 字符串中：**

```java
// ❌ 危险 - SQL 注入漏洞
String userInput = request.getParameter("name");
String sql = "SELECT * FROM t_user WHERE name = '" + userInput + "'";
repo.boosterQueryList(sql, Map.of());
```

**始终使用参数绑定：**

```java
// ✅ 安全 - 参数化查询
String sql = "SELECT * FROM t_user WHERE name = :name";
repo.boosterQueryList(sql, Map.of("name", userInput));
```

### 安全护栏

BoosterQuery 提供以下安全保护：
- ✅ 参数值通过 `setParameter` 绑定（防止 SQL 注入）
- ✅ 排序字段白名单校验（`[A-Za-z0-9_.]+`）
- ✅ UPDATE/DELETE 全条件移除时抛出异常（防止误操作）

### 生产环境建议

生产环境建议关闭 SQL 日志，防止敏感信息泄露：

```yaml
logging:
  level:
    com.chaosguide.boosterquery: WARN  # 关闭 DEBUG 日志
```

## 文档

- [首页](docs/index.html) — 概览与快速开始
- [使用指南](docs/guide.html) — 详细使用教程
- [API 参考](docs/api.html) — 完整 API 文档

## 开发

```bash
./gradlew build          # 编译 + 测试
./gradlew test           # 运行全部测试
./gradlew publishToMavenLocal  # 发布到本地 Maven 仓库
```

集成测试使用 TestContainers，Docker 不可用时自动跳过。

## 贡献

1. Fork 本仓库
2. 创建特性分支（`git checkout -b feat/my-feature`）
3. 按约定提交：`<type>(<scope>): <描述>`
4. Push 并提交 Pull Request

## 许可证

[Apache License 2.0](LICENSE)
