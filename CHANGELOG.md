# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.2] - 2026-08-25

### Added
- Added scalar result mapping for numeric `@BoosterQuery` methods and explicit `@Modifying`-based DML execution
- Added page-size and JPA offset validation before SQL rewriting or count-query execution
- Added focused query lookup, auto-configuration, pagination, and MySQL/PostgreSQL integration tests

### Changed
- Auto-limit now also defines the maximum allowed `Pageable` page size while keeping valid pagination counts unrestricted
- Micrometer auto-configuration now remains safely optional when Micrometer is absent
- Updated the consumer documentation and GitHub Pages for query execution, auto-limit pagination, and bean loading

### Fixed
- Fixed numeric scalar repository methods being misclassified as DML operations
- Fixed repository query execution and result-type resolution for scalar, count, and modifying methods
- Removed the test-only Kotlin standard library from the published consumer POM

## [1.0.1] - 2026-07-23

### Fixed
- Removed the unintended `com.chaosguide.jpa.booster` package tree; all APIs and implementation classes now use `com.chaosguide.boosterquery` exclusively

## [1.0.0] - 2026-07-23

This release is superseded by 1.0.1 because it unintentionally included the old package namespace.

### Added
- Unified native SQL execution API: paged, list, single-object, count, and DML queries
- Smart SQL rewriting: automatically removes WHERE/HAVING/JOIN/CTE conditions when parameters are null, blank, or empty (AST-based via JSqlParser)
- DML safety guard: prevents full-table UPDATE/DELETE when all WHERE conditions are removed
- Automatic result mapping: Tuple to Entity, DTO, Record, Map, or scalar types with underscore-to-camelCase alias conversion
- Auto-Limit protection: prevents large result sets by auto-appending LIMIT (default 10,000 rows)
- Caffeine-based SQL caching: caches rewritten SQL, count SQL, and sort SQL with configurable size and TTL
- `@BoosterQuery` declarative annotation for repository methods with per-method rewrite/limit overrides
- Two-tier repository system: `BoosterNativeRepository` (basic) and `BoosterQueryRepository` (smart)
- Micrometer observability metrics via `MetricsRecorder` abstraction (optional dependency)
- Spring Boot auto-configuration via `BoosterQueryAutoConfiguration`
- Full documentation site (home, guide, API reference) with bilingual EN/ZH support
