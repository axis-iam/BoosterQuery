# 风险导向评审规则

## 深度评审触发场景

以下高风险场景 SHOULD 从方案、风险和替代路径三个独立视角进行评审；工具和并发条件允许时可使用独立 agent：

- 公共 API 或 Maven 消费契约变更
- 架构决策 / 技术选型
- SQL 改写管线变更
- 参数绑定、DML 防护或日志脱敏变更
- 跨新旧命名空间的兼容性变更

普通文档、构建配置和局部实现变更采用单次审查即可，不强制多 Agent。

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
- [ ] 对 SQL 改写管线（`BoosterQueryExecutor → BoosterSqlRewriter → JSqlParserRewriter`）的侵入程度
- [ ] JSqlParser 版本兼容性（当前 5.3，新语法是否被支持）
- [ ] 缓存键（`RewriteCacheKey` / `CountCacheKey` / `SortCacheKey`）是否需要变更
- [ ] 是否需要同时更新 MySQL 和 PostgreSQL 集成测试

### 代码审查时必须评估

- [ ] 是否符合 `.codex/instructions/` 各规范文件的要求
- [ ] SQL 改写类变更是否在 `JSqlParserRewriterTest` 中同时添加了正向 + 反向测试
- [ ] 异常是否细粒度分类（不 catch `Exception`，使用 `JSQLParserException` 等具体类型）
- [ ] 新增 `ClassValue` / Caffeine 缓存的键设计是否稳定（使用不可变类型）
- [ ] 反射操作是否有对应的 `setAccessible(true)` + 异常显式失败处理

## 严格性要求

- 使用多 Agent 时，各视角 MUST 独立运行，不能互相参考
- 每个评审视角 MUST 给出明确结论，不能模糊敷衍
- IF 多个独立视角一致认为有严重风险 → MUST 阻止实施，报告用户
- IF 变更涉及公共 API → MUST 额外评估向后兼容性和 Maven 发布影响
