# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BoosterQuery is a Spring Data JPA native SQL enhancement library (`com.chaosguide:booster-query:1.0.0`). It provides unified query execution, result mapping, parameter binding, and "smart SQL rewriting" (removing null-parameter conditions, auto-appending LIMIT). Written in Java, built with Gradle (Kotlin DSL), targeting Spring Boot 4.0.0.

## Build & Test Commands

```bash
./gradlew build          # compile + test
./gradlew test           # run all tests
./gradlew test --tests "com.chaosguide.jpa.booster.rewrite.BoosterSqlRewriterTest"  # single test class
./gradlew test --tests "*.BoosterSqlRewriterTest.testMethodName"                     # single test method
./gradlew publishToMavenLocal  # publish to local Maven repo
```

Integration tests (MySQL/PostgreSQL) use TestContainers and auto-skip when Docker is unavailable (`disabledWithoutDocker = true`).

## Architecture

### Two-tier Repository System

- `BoosterNativeRepository<T, ID>` — basic enhanced interface with `nativeQuery*`, `nativeCount`, `nativeExecute` methods. Implemented by `BoosterNativeJpaRepository`, delegates to `BoosterQueryExecutor`.
- `BoosterQueryRepository<T, ID>` — smart interface with `boosterQuery*`, `boosterCount`, `boosterExecute`. Implemented by `BoosterQueryJpaRepository`, delegates to `BoosterQueryExecutor` which wraps the basic executor with SQL rewriting + caching + auto-limit.

### Query Execution Flow

1. Repository method or `@BoosterQuery` annotation → `BoosterSqlRepositoryQuery` (query lookup)
2. Parameters bound via `ParameterBinder` (supports Map, POJO via reflection, `@Param`; `@Param` can be omitted when compiled with `-parameters` flag — Spring Boot default)
3. Smart path: `BoosterQueryExecutor.prepareQuery()` → `BoosterSqlRewriter` → `JSqlParserRewriter` (AST-based condition removal) → `LimitAppender`
4. Results mapped by `JpaResultMapper`: Tuple → Entity / DTO / Record / Map / primitive (with underscore-to-camelCase alias conversion)

### SQL Rewriting Pipeline (rewrite package)

`BoosterSqlRewriter` orchestrates: collects null/blank/empty params → `JSqlParserRewriter` removes dependent conditions from WHERE/HAVING/JOIN ON using JSqlParser AST visitors → safety check prevents full-table UPDATE/DELETE when all WHERE conditions removed.

### Key Design Decisions

- `BoosterJpaRepositoryFactoryBean` is required for `@BoosterQuery` support (injects config + cache, registers custom `BoosterQueryLookupStrategy`)
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

- Spring Boot 4.0.0 (platform BOM)
- JSqlParser 5.3 — SQL parsing and AST manipulation
- Caffeine 3.2.3 — SQL query caching
- TestContainers 1.21.3 — MySQL + PostgreSQL integration tests

## Language

All code comments, Javadoc, log messages, exception messages, and documentation use **English**. Follow this convention strictly. No fully-qualified class names in code — always use imports.

## Coding Standards

### Java Style

- Use Java 21+ features: record, sealed class, pattern matching (including switch null/guarded), text block, sequenced collections
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

## Extended Rules

See `.claude/rules/` for detailed process rules:

| File | Content |
|------|------|
| `review.md` | Dialectical multi-agent review process (triggers, three roles, BoosterQuery-specific dimensions) |
| `testing.md` | Testing strategy (layers, SQL rewrite test rules, test case templates, naming conventions) |
| `workflow.md` | Development workflow (task breakdown, Plan Mode triggers, Git commit conventions, checklists) |
| `sql-safety.md` | SQL safety rules (parameter binding, DML protection, JSqlParser fallback, cache key stability) |

## Task Tracking

See **`.dev/TODO.md`** for the detailed task list and acceptance criteria.

### Maintenance Rules (MANDATORY)

- Before starting a task, set its status to `[~]`
- After completing a task, set its status to `[x]` and add completion notes in the detail section
- When adding new tasks, append to `.dev/TODO.md` under the appropriate PHASE — do not list tasks in this file
- Keep this file concise — no duplicating task items here
