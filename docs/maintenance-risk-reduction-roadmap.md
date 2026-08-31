# 维护风险收敛路线图

## 目的与边界

本文档记录 `coding-back01/legado` 在一次性 fork 治理归档后的后续维护顺序。目标是优先降低
实际安全与兼容风险，并在可验证、可回滚的前提下处理当前 Android lint 的 108 个 warning。
路线图只固定工作边界、顺序、门禁和重新启动条件，不代表任何实现、依赖升级、远端 Pull
Request 处置或设备操作已经获准执行。

当前事实基线为 2026-08-31 的 `master` 提交 `7aa63c27b`：Android lint 为
`0 error / 108 warning / 18 hint`，网页端 ESLint 为 0 error。108 个 warning 已完成三态
审查，没有 `PENDING_REVIEW`；本路线图负责把这些已延期项目按风险重新启动，而不是把它们
重新描述为漏审事项。

## 治理原则

1. 实际风险优先，不以关闭告警、删除草稿或缩短分支列表作为安全完成证据。
2. 各兼容域使用独立 OpenSpec 变更，并严格串行实施；后续变更只在轮到时基于最新事实创建。
3. 能够证明行为等价的项目优先修复；确认属于刻意兼容行为的项目只允许精确局部抑制，并记录
   位置、理由、验证证据和重新启动条件。
4. 不使用全局 lint baseline、全局关闭检查或批量格式化来制造 warning 为零的表象。
5. 每个实现批次失败即停止。没有实际运行的测试、模拟器或真机验证不得描述为通过。
6. 用户设备只允许验证 `io.legado.app.debug`。执行前实时核对包与数据边界；若需要清理既有
   Debug 数据，必须停止并另行取得明确授权。普通正式版及其数据不进入本路线图的设备操作。

## 108 个 warning 的完整去向

| 工作域 | 数量 | 组成 | 处置边界 |
|---|---:|---|---|
| 行为与资源 | 65 | 62 个中风险项，加 `IconDuplicates`、`UnusedAttribute`、`UseCompoundDrawables` 3 个低风险项 | 一个专门变更，按风险与证据依赖拆成串行小批次 |
| 工具链与依赖 | 43 | `AndroidGradlePluginVersion` 4、`GradleDependency` 14、`NewerVersionAvailable` 25 | 按四个兼容域建立独立变更，逐坐标升级或精确抑制 |
| **合计** | **108** |  | 目标是在一个经过完整验证的提交上使 lint 可见 warning 为 0 |

版本类检查依赖远端元数据，后续可能在没有源码变化时出现新提示。因此“0 warning”是目标提交
的可验证状态，不是永久承诺。达到零后，新增非版本 warning 必须阻断合并；版本元数据漂移
必须进入账本并完成审查，但不得仅因外部发布新版本就让未改代码的 `master` 无条件失效。

## 串行工作流

### 1. Element Plus 静态链接安全边界

首个变更只处理 `modules/web/src/components/SourceHelp.vue` 中 11 个 `el-link`：锁定 10 个
同源 `/help/#...` 链接和 1 个固定 HTTPS 外链，禁止未经独立评审的动态 `href`，并为新窗口
链接建立必要的来源隔离合同。现有两条 `GHSA-5m5x-9j46-h678` 告警继续保持打开，用于监测
上游变化；本变更不升级 Element Plus，也不得把上游仅增加风险文档描述为运行时修复。

### 2. GitHub Actions 独立升级

以现有 #73 为调查输入，逐项审查 Actions 大版本的运行时、权限、artifact 和 Node.js 变化，
同步维护工作流的精确契约测试后重新运行完整聚合门禁。替代变更准备并通过评审前不关闭 #73；
不得通过删除或放宽契约测试使现有机器人 Pull Request 变绿。

### 3. 65 个行为与资源 warning

建立一个专门 OpenSpec 变更，内部按以下顺序使用独立小 Pull Request：

1. `DiscouragedApi`；
2. 资源密度与语义，包括 `IconLocation`、`IconDuplicates`；
3. `UnusedResources` 与 `UnusedAttribute`；
4. `Overdraw`；
5. `UselessParent`、`VectorPath`、`UseCompoundDrawables`。

确定性 API 21/API 36 模拟器页面允许保存少量版本化 golden。真机截图、UI hierarchy 和可能
包含用户信息的原始证据只保存在权限受限且 Git 忽略的目录，版本化记录只保存脱敏摘要和哈希。
真机未连接时可以推进不依赖设备的测试基础设施，但不得完成需要真机证据的批次。

### 4. 43 个工具链与依赖 warning

按兼容域依次创建四个 OpenSpec 变更：

1. Android 工具链与构建插件；
2. AndroidX 与 UI 依赖；
3. 解析、网络与运行库；
4. Glide 图片栈。

每个变更内部逐坐标处理。安全升级能够通过最低 API 21、JDK 17、单元测试、lint、Debug 构建、
相关设备或页面回归以及完整 CI 时才可合并；无法安全升级的固定版本使用精确局部抑制和重启条件。
Gradle 9.7.1 与 AGP 8.13.2 已确认不兼容，因此 #42 只在保存失败证据并准备好对应处置记录后
关闭；不立即迁移 AGP 9，不采用未经官方支持矩阵证明的 Gradle 9.5，也不配置可能隐藏安全更新
的永久 ignore。

### 5. Web 生产依赖

以 #44 为调查输入，按 Element Plus、VueUse、Hotkeys、Pinia、Vue Router 等兼容族拆分，
同步提交 `package.json` 与 `pnpm-lock.yaml`，使用固定 pnpm 冻结安装并执行章节 HTML 安全测试、
类型检查、只读 ESLint、构建和 Android Web 静态资源对账。替代变更准备并通过评审前不关闭 #44。

### 6. Web 开发工具链

以 #45 为调查输入，分别处理 TypeScript 与测试脚本、Vite 与 Vue 插件、ESLint 与 Vue 插件、
unplugin 系列及 Node.js 类型。TypeScript 7 的 `TS5112` 必须作为真实 RED 处理，不得把 12 个
大版本更新重新合并为一个不可归因批次。替代变更准备并通过评审前不关闭 #45。

## 统一完成门禁

每个适用变更至少需要：

- 聚焦自动化测试先观察 RED，再实现 GREEN；
- Android 单元测试、lint 和 Debug 构建，或网页端冻结安装、安全测试、类型检查、只读 ESLint
  与构建；
- 同步产物、golden、资源或设备证据的精确对账；
- `openspec validate --all --strict`；
- `git diff --check`；
- 没有无关生成差异、依赖升级、签名材料、Room schema、书源规则、导入 URI 或备份格式变化。

任一批次发现行为回归、证据不足、设备边界不安全或目标版本不兼容时立即停止。补齐测试或经评审
添加精确 suppression 后必须重跑完整适用门禁，不能把失败留到总批次末尾统一解释。

## 明确不在本路线图中的对象

- 未合并但内容已由后续归档覆盖的 `codex/release-verification-evidence`；
- 失败候选草稿 `3.26.083020` 与 `3.26.083021`；
- 三个历史 Release 的 `releaseA` APK、历史包名认知和设备独立数据；
- 新功能、最低 SDK 提升、Room schema、书源或订阅源规则、导入 URI、备份格式、正式签名和
  Release 发布流程修改。

这些对象继续遵循既有审计和兼容决策，不得借风险治理路线图静默删除、迁移、公开或改写。

## 评审与启动方式

本路线图本身不授权实现。每个工作域轮到时必须重新读取当前代码、告警、依赖版本、打开 Pull
Request、设备和 CI 状态，再创建对应 OpenSpec 变更。只有该变更的提案、设计、规范和任务经
人工评审明确批准后，才能进入 Apply；后续工作域不得凭本路线图的总目标提前实施。
