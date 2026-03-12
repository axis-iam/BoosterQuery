# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-03-12

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
