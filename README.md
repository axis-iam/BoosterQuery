<p align="center">
  <strong>BoosterQuery</strong><br>
  <em>Spring Data JPA Native SQL Enhancement Library</em>
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/Java-21%2B-orange" alt="Java 21+">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0-green" alt="Spring Boot 4.0">
  <img src="https://img.shields.io/badge/Maven%20Central-1.0.0-blue" alt="Maven Central">
</p>

<p align="center">
  <a href="README.zh-CN.md">中文文档</a>
</p>

---

A lightweight library that enhances Spring Data JPA with native SQL execution, automatic SQL rewriting, and result mapping.

## Features

- **Native SQL Execution** — Unified API for paged, list, single-object, count, and DML queries
- **SQL Rewriting** — Automatically removes WHERE/HAVING/JOIN conditions when parameters are null/blank/empty (AST-based via JSqlParser)
- **Result Mapping** — Tuple to Entity, DTO, Record, Interface Projection, Map, or scalar types with underscore-to-camelCase conversion
- **Auto-Limit Protection** — Prevents large result sets by auto-appending LIMIT (default 10,000 rows)
- **Caffeine Caching** — Cache rewritten SQL with configurable size and TTL
- **@BoosterQuery Annotation** — Declarative SQL on repository methods with per-method overrides

## Quick Start

### 1. Add Dependency

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

### 2. Enable BoosterQuery

```java
@SpringBootApplication
@EnableJpaRepositories(repositoryFactoryBeanClass = BoosterQueryRepositoryFactoryBean.class)
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```
### 3. Define Repository

```java
public interface UserRepository extends BoosterQueryRepository<User, Long> { }
```

### 4. Use Enhanced Queries

```java
String sql = "SELECT * FROM t_user WHERE name LIKE :name AND age > :age";
Map<String, Object> params = new HashMap<>();
params.put("name", name);  // null → condition removed automatically
params.put("age", age);    // null → condition removed automatically
List<User> users = repo.boosterQueryList(sql, params);
```

## Repository System

| Interface | Prefix | Description |
|---|---|---|
| `BoosterNativeRepository` | `native*` | Native SQL execution + result mapping |
| `BoosterQueryRepository` | `booster*` | + SQL rewriting + auto-limit + caching |

`BoosterQueryRepository` extends `BoosterNativeRepository`, which extends `JpaRepository`.

## @BoosterQuery Annotation

```java
public interface UserRepository extends BoosterQueryRepository<User, Long> {

    // With -parameters compiler flag, @Param can be omitted
    @BoosterQuery("SELECT * FROM t_user WHERE name LIKE :name AND age > :age")
    List<User> findByConditions(String name, Integer age);

    // @Param is still supported and takes priority over reflection
    @BoosterQuery(
        value = "SELECT user_name, email FROM t_user WHERE status = :status",
        resultType = UserDTO.class,
        enableRewrite = Toggle.TRUE,
        autoLimit = 500
    )
    List<UserDTO> findActiveUsers(@Param("status") String status);
}
```

### Supported Return Types

`@BoosterQuery` automatically infers the result mapping type from the method's return type:

| Return Type | Behavior |
|---|---|
| `List<T>` | Query list, maps each row to `T` |
| `Page<T>` | Paginated query (requires `Pageable` parameter) |
| `Optional<T>` | Single result wrapped in Optional |
| `T` (DTO / Record / Interface / Entity) | Direct single-result mapping |
| `Map<String, Object>` | Single row as key-value map |
| `String`, `BigDecimal`, etc. | Scalar value extraction |
| `long` / `Long` | Count query |
| `int` / `Integer` / `void` | DML execution (returns affected rows) |

**Examples — Direct DTO / Record return:**

```java
// Direct DTO return — no wrapper needed
@BoosterQuery("SELECT user_name, email FROM t_user WHERE id = :id")
UserDTO findUserById(@Param("id") Long id);

// Direct Record return
@BoosterQuery("SELECT SUM(amount) AS totalRevenue, COUNT(*) AS orderCount FROM t_order")
SummaryRecord getOrderSummary();

// Direct interface projection
@BoosterQuery("SELECT name, email FROM t_user WHERE id = :id")
UserProjection findProjectionById(@Param("id") Long id);

// Optional wrapping
@BoosterQuery("SELECT user_name, email FROM t_user WHERE id = :id")
Optional<UserDTO> findOptionalUserById(@Param("id") Long id);
```

> **Note:** Container types (`List`, `Page`, `Optional`) must declare a generic type parameter.
> Raw types like `List` (without `<T>`) will throw `IllegalStateException` at startup.

### Parameter Binding

Parameter names are resolved in the following order:

1. **`@Param` annotation** — explicit, always works
2. **`-parameters` compiler flag** — infers names from method signature via reflection
3. **Single POJO/Map** — extracts fields by name automatically

Spring Boot projects have `-parameters` enabled by default, so `@Param` is typically optional.

For non-Spring-Boot projects, add to `build.gradle.kts`:

```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
```
## Configuration

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

## Security

### ⚠️ SQL Injection Prevention

**NEVER concatenate user input into SQL strings:**

```java
// ❌ DANGEROUS - SQL Injection vulnerability
String userInput = request.getParameter("name");
String sql = "SELECT * FROM t_user WHERE name = '" + userInput + "'";
repo.boosterQueryList(sql, Map.of());
```

**ALWAYS use parameter binding:**

```java
// ✅ SAFE - Parameterized query
String sql = "SELECT * FROM t_user WHERE name = :name";
repo.boosterQueryList(sql, Map.of("name", userInput));
```

### Security Features

BoosterQuery provides the following security protections:
- ✅ Parameter values bound via `setParameter` (prevents SQL injection)
- ✅ Sort field whitelist validation (`[A-Za-z0-9_.]+`)
- ✅ Throws exception when all WHERE conditions removed in UPDATE/DELETE (prevents accidental full-table operations)

### Production Recommendations

Disable SQL logging in production to prevent sensitive information leakage:

```yaml
logging:
  level:
    com.chaosguide.jpa.booster: WARN  # Disable DEBUG logs
```

## Documentation

- [Home](docs/index.html) — Overview and quick start
- [Guide](docs/guide.html) — Detailed usage guide
- [API Reference](docs/api.html) — Complete API documentation

## Development

```bash
./gradlew build          # compile + test
./gradlew test           # run all tests
./gradlew publishToMavenLocal  # publish to local Maven repo
```

Integration tests use TestContainers and auto-skip when Docker is unavailable.

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feat/my-feature`)
3. Commit changes following: `<type>(<scope>): <description>`
4. Push and open a Pull Request

## License

[Apache License 2.0](LICENSE)
