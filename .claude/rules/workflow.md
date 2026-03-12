# 开发工作流 — BoosterQuery 库

## 任务拆分原则

### 开始任何任务前，先明确三要素

1. **WHAT** — 具体的交付目标（功能/修复/重构）
2. **WHERE** — 涉及哪些包和类（rewrite / executor / support / repository）
3. **CONSTRAINTS** — 哪些公共 API 不能破坏（向后兼容性）

无法明确全部三要素 → 继续细化，不要开始编码。

### 库开发的任务拆分顺序

1. **数据/协议层** — DTO、Record、缓存 Key 定义
2. **核心逻辑层** — Rewriter / Executor / Mapper 中的处理逻辑
3. **集成层** — Repository 接口 / AutoConfiguration 注册
4. **测试层** — 单元测试 → 集成测试（TestContainers）
5. **文档层** — Javadoc + README 更新

每个子任务完成后运行 `./gradlew test` 验证，确保不引入回归。

### 任务粒度

- **太小**：单字段重命名、trivial 注解调整
- **太大**：需要跨多个架构层做决策，且无法在一个会话内测试完成
- **合适**：一个功能路径（Rewriter 新规则 + 测试 + 文档），可测试，可在一个会话内完成

## Plan Mode 使用时机（MANDATORY）

以下场景 MUST 先进入 Plan Mode，产出方案并等待用户确认后再编码：

- 涉及 3 个以上文件的变更
- 新增公共 API（Repository 接口方法、`@BoosterQuery` 注解属性）
- 修改 SQL 改写管线（`rewrite` 包）
- 修改缓存 Key 结构（`RewriteCacheKey`、`CountCacheKey`、`SortCacheKey`）
- 架构层面的重构

## 工作流程序列

```
1. 理解任务 → 明确三要素（WHAT / WHERE / CONSTRAINTS）
2. IF 非 trivial → 进入 Plan Mode
   - 分析涉及文件和影响范围
   - 起草实现方案 + 测试用例清单（见 testing.md）
   - 等待用户确认方案
3. 先写测试（Red 阶段）
   - 将设计的测试用例转化为自动化测试代码
   - 确认测试在无实现时失败（验证测试本身有效）
4. 实现代码（Green 阶段）
   - 编辑前重新读取目标文件（避免基于过时内容修改）
   - 先查找现有工具类，不重复造轮子
   - 每次改动后运行 `./gradlew build`
5. 重构（Refactor 阶段）— 以测试为安全网
6. 运行全量测试 `./gradlew test` — 全绿
7. 提交（见 Git 约定）
8. 更新进度（TodoList）
```

## Pre-Action 检查清单

- [ ] 重新读取目标文件（不依赖记忆中的旧内容）
- [ ] 检查 `support` 包是否已有可复用的工具方法
- [ ] 确认 git 工作区是干净的（或已有明确的 staging 状态）
- [ ] 核对 CLAUDE.md 中的架构层次（不越层调用）

## Post-Action 检查清单

- [ ] `./gradlew build` — 无编译错误
- [ ] `./gradlew test` — 全绿
- [ ] 新增改写规则 → `JSqlParserRewriterTest` 已补充正向 + 反向用例
- [ ] 公共 API 变更 → Javadoc 已更新
- [ ] 有文档影响 → README 相关章节已更新
- [ ] 已提交（常规提交格式）

## Git 提交约定

**格式**：`<type>(<scope>): <简要描述>`

| type | 用途 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（不改变外部行为） |
| `test` | 测试相关 |
| `docs` | 文档 |
| `chore` | 构建、依赖、配置 |

| scope | 对应包 |
|-------|-------|
| `rewrite` | SQL 改写管线 |
| `executor` | BoosterQueryExecutor / SmartSqlQueryExecutor |
| `cache` | BoosterCache / CaffeineBoosterCache |
| `mapper` | JpaResultMapper |
| `repo` | Repository 接口 / 实现 |
| `config` | 配置 / AutoConfiguration |
| `build` | Gradle 构建脚本 |

**示例**：
```
feat(rewrite): 支持 CTE 子句内的条件改写
fix(mapper): 修复无别名列导致的 NullPointerException
test(executor): 补充缓存键哈希碰撞边界测试
```

**规则**：每个逻辑变更一个 commit，不混合不相关的修改。

## 进度跟踪

- 使用 TodoList 工具跟踪多步任务进度
- 每个子任务：`pending` → `in_progress` → `completed`
- 每完成一个子任务，简要说明改动了哪些文件
- 遇到阻塞时立即说明原因和备选方案，不要静默重试
