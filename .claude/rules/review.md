# 辩证式多 Agent 评审规则

## 触发场景（MANDATORY）

以下场景 MUST 并行启动 3 个 subagent 进行辩证评审，不得跳过：

- 功能梳理 / 需求分析
- 新建需求 / 方案设计
- 代码审查 / PR Review
- 架构决策 / 技术选型
- SQL 改写管线变更

## 三角色并行分析

**Agent A — 方案倡导者 (Advocate)**
- 提出最优实现方案，论证优势和可行性
- 给出具体实现路径和代码示例

**Agent B — 批判审查者 (Critic)**
- 找出方案中的漏洞、风险、边界条件问题
- 质疑假设：SQL 解析失败？缓存污染？并发安全？向后兼容？
- 列出可能失败的场景

**Agent C — 替代方案者 (Alternative)**
- 提出完全不同的实现思路
- 从 API 设计、维护成本、技术债角度评估 trade-off

## 综合评估（主 Agent 执行）

1. **矛盾识别** — 三方观点的冲突点
2. **风险矩阵** — 按影响程度 × 发生概率排序
3. **决策建议** — 推荐方案 + 理由，标注遗留问题

## 输出格式

```
## 评审摘要
- 架构评估：✅ / ⚠️ / ❌ — [一句话结论]
- 代码质量：✅ / ⚠️ / ❌ — [一句话结论]
- 需求风险：✅ / ⚠️ / ❌ — [一句话结论]
- 综合决策：[推进 / 修改后推进 / 打回重新设计]
```

## BoosterQuery 专项评估维度

### 方案设计时必须评估

- [ ] 是否影响现有公共 API（`BoosterNativeRepository` / `BoosterQueryRepository` 接口方法）
- [ ] 对 SQL 改写管线（`SmartSqlRewriter → JSqlParserRewriter → LimitAppender`）的侵入程度
- [ ] JSqlParser 版本兼容性（当前 5.3，新语法是否被支持）
- [ ] 缓存键（`RewriteCacheKey` / `CountCacheKey` / `SortCacheKey`）是否需要变更
- [ ] 是否需要同时更新 MySQL 和 PostgreSQL 集成测试

### 代码审查时必须评估

- [ ] 是否符合 `.claude/rules/` 各规范文件的要求
- [ ] SQL 改写类变更是否在 `JSqlParserRewriterTest` 中同时添加了正向 + 反向测试
- [ ] 异常是否细粒度分类（不 catch `Exception`，使用 `JSQLParserException` 等具体类型）
- [ ] 新增 `ClassValue` / Caffeine 缓存的键设计是否稳定（使用不可变类型）
- [ ] 反射操作是否有对应的 `setAccessible(true)` + 异常显式失败处理

## 严格性要求

- 三个 agent MUST 独立运行，不能互相参考
- 每个 agent MUST 给出明确结论，不能模糊敷衍
- IF 三方一致认为有严重风险 → MUST 阻止实施，报告用户
- IF 变更涉及公共 API → MUST 额外评估向后兼容性和 Maven 发布影响
