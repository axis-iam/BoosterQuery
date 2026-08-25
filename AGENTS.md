# AGENTS.md

This file provides guidance to Codex when working with code in this repository.

## Project Overview

BoosterQuery is a Spring Data JPA native SQL enhancement library (`com.chaosguide:booster-query:1.0.2`). It provides unified query execution, result mapping, parameter binding, and smart SQL rewriting (removing null-parameter conditions and applying a database-agnostic max result limit). Written in Java, built with Gradle (Kotlin DSL), targeting Spring Boot 4.0.3.

## Build & Test Commands

```bash
./gradlew build          # compile + test
./gradlew test           # run all tests
./gradlew test --tests "com.chaosguide.boosterquery.rewrite.BoosterSqlRewriterTest"  # single test class
./gradlew test --tests "*.BoosterSqlRewriterTest.testMethodName"                     # single test method
./gradlew publishToMavenLocal  # publish to local Maven repo
```

Integration tests (MySQL/PostgreSQL) use TestContainers and auto-skip when Docker is unavailable (`disabledWithoutDocker = true`).

Development requires JDK 25. Docker is optional for unit tests and required to run the MySQL/PostgreSQL integration tests locally. The published artifact is currently consumed from `mavenLocal()`; use `./gradlew publishToMavenLocal` before testing it from another local project.

## Architecture

### Two-tier Repository System

- `BoosterNativeRepository<T, ID>` — basic enhanced interface with `nativeQuery*`, `nativeCount`, `nativeExecute` methods. Implemented by `BoosterNativeJpaRepository`, delegates to `BoosterNativeExecutor`.
- `BoosterQueryRepository<T, ID>` — smart interface with `boosterQuery*`, `boosterCount`, `boosterExecute`. Implemented by `BoosterQueryJpaRepository`, delegates to `BoosterQueryExecutor` which wraps the basic executor with SQL rewriting + caching + auto-limit.

### Query Execution Flow

1. Repository method or `@BoosterQuery` annotation → `BoosterSqlRepositoryQuery` (query lookup)
2. Parameters bound via `ParameterBinder` (supports Map, POJO via reflection, `@Param`; `@Param` can be omitted when compiled with `-parameters` flag — Spring Boot default)
3. Smart path: `BoosterQueryExecutor.prepareQuery()` → `BoosterSqlRewriter` → `JSqlParserRewriter` (AST-based condition removal); auto-limit uses `LimitAppender.hasLimit()` and JPA `query.setMaxResults()`
4. Results mapped by `JpaResultMapper`: Tuple → Entity / DTO / Record / Map / primitive (with underscore-to-camelCase alias conversion)

### SQL Rewriting Pipeline (rewrite package)

`BoosterSqlRewriter` orchestrates: collects null/blank/empty params → `JSqlParserRewriter` removes dependent conditions from WHERE/HAVING/JOIN ON using JSqlParser AST visitors → safety check prevents full-table UPDATE/DELETE when all WHERE conditions removed.

### Key Design Decisions

- `BoosterQueryRepositoryFactoryBean` is required for `@BoosterQuery` support (injects config + cache, registers custom `BoosterQueryLookupStrategy`)
- Caffeine-based `BoosterCache` caches rewritten SQL, count SQL, and sort SQL (keyed by record types: `RewriteCacheKey`, `CountCacheKey`, `SortCacheKey`)
- Auto-configuration entry point: `BoosterQueryAutoConfiguration` (registered via `META-INF/spring/AutoConfiguration.imports`)

### Package Layout

| Package | Responsibility |
|---|---|
| `annotation` | `@BoosterQuery` definition |
| `cache` | Cache interface + Caffeine impl |
| `config` | `BoosterQueryConfig` + properties binding |
| `executor` | `BoosterNativeExecutor` (basic), `BoosterQueryExecutor` (smart) |
| `repository` | Repository interfaces + implementations |
| `repository.query` | `@BoosterQuery` lookup strategy |
| `repository.support` | Repository factory |
| `rewrite` | SQL rewriting (orchestrator + JSqlParser AST visitors) |
| `support` | `JpaResultMapper`, `ParameterBinder`, `LimitAppender`, `SqlHelper` |

## Key Dependencies

- Spring Boot 4.0.3 (platform BOM)
- JSqlParser 5.3 — SQL parsing and AST manipulation
- Caffeine 3.2.3 — SQL query caching
- Testcontainers BOM 1.21.3; Spring Boot dependency management currently resolves Testcontainers 2.0.3 for MySQL 8.0.33 + PostgreSQL 17 integration tests

## Language

All code comments, Javadoc, log messages, and exception messages use **English**. The bilingual README and website are intentional exceptions. No fully-qualified class names in code — always use imports.

## Coding Standards

### Java Style

- Use Java 25 features: record, sealed class, pattern matching (including switch null/guarded), text block, sequenced collections
- Prefer immutable objects: declare fields `final`, use `List.of()` / `Map.of()` for collections
- Validate method parameters with `Objects.requireNonNull()`, not assert
- Exception handling: custom exceptions extend `RuntimeException` with context info; never swallow exceptions or catch `Exception`
- Use `Optional` as return type to express "may be absent", not for fields or parameters
- Keep stream operations readable on a single line; extract to local variables when exceeding 3 steps
- Logging via SLF4J (`private static final Logger log = LoggerFactory.getLogger(Xxx.class)`)
- **No fully-qualified class names** (e.g. `new java.util.ArrayList<>()`) — always import and use short names

### Naming Conventions

- Class names: `PascalCase`, no `I` prefix for interfaces (test interfaces excepted, e.g. `ITestSmartUserRepository`)
- Method names: `camelCase`, boolean methods use `is/has/can/should` prefix
- Constants: `UPPER_SNAKE_CASE`
- Package names: all lowercase, no word separators
- Test classes: `{TestedClass}Test.java`, integration tests in `integration/` sub-package

### SQL Conventions

- SQL keywords uppercase: `SELECT`, `FROM`, `WHERE`, `JOIN`, `ORDER BY`
- Named parameters use `:paramName` format (JPA native query style)
- Table/column names use underscores (`t_user`, `user_name`), DTO fields use camelCase
- When adding SQL rewrite rules, always add both positive and negative test cases in `JSqlParserRewriterTest`

## Namespace

- All public APIs and implementation code belong under `com.chaosguide.boosterquery`.
- Do not introduce alternative or legacy package namespaces.
- Spring Boot auto-configuration is registered from `com.chaosguide.boosterquery`.

## Documentation Sources

- `README.md` and `README.zh-CN.md` document library consumers.
- `docs/` is the source for the GitHub Pages website. Keep its code examples aligned with the public API and package namespace.
- `.codex/instructions/` contains development-agent guidance. These files are committed so a fresh clone receives the same working conventions.

## Extended Rules

See `.codex/instructions/` for detailed process rules:

| File | Content |
|------|------|
| `review.md` | Dialectical multi-agent review process (triggers, three roles, BoosterQuery-specific dimensions) |
| `testing.md` | Testing strategy (layers, SQL rewrite test rules, test case templates, naming conventions) |
| `workflow.md` | Development workflow (task breakdown, planning triggers, Git commit conventions, checklists) |
| `sql-safety.md` | SQL safety rules (parameter binding, DML protection, JSqlParser fallback, cache key stability) |

## Task Tracking

Use the agent's plan/task tracker for multi-step work. `.dev/` is local workspace material and is intentionally not required for a fresh clone.
