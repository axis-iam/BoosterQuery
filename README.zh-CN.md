<p align="center">
  <strong>BoosterQuery</strong><br>
  <em>Spring Data JPA 原生 SQL 增强库</em>
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/Java-21%2B-orange" alt="Java 21+">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0-green" alt="Spring Boot 4.0">
  <img src="https://img.shields.io/badge/Maven%20Central-1.0.0-blue" alt="Maven Central">
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
- **Auto-Limit 防护** — 自动追加 LIMIT 防止大结果集（默认 10,000 行）
- **Caffeine 缓存** — 缓存改写后的 SQL，可配置大小和过期时间
- **@BoosterQuery 注解** — 声明式 SQL 查询，支持逐方法覆盖配置

## 快速开始

### 1. 添加依赖

**Gradle (Kotlin DSL):**
```kotlin
implementation("com.chaosguide:booster-query:1.0.0")
```

**Maven:**
```xml
<dependency>
    <groupId>com.chaosguide</groupId>
    <artifactId>booster-query</artifactId>
    <version>1.0.0</version>
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
| `String`、`BigDecimal` 等 | 标量值提取 |
| `long` / `Long` | 计数查询 |
| `int` / `Integer` / `void` | DML 执行（返回受影响行数） |

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
    com.chaosguide.jpa.booster: WARN  # 关闭 DEBUG 日志
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
