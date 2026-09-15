# 维护风险收敛路线图

## 目的与边界

本文档记录 `coding-back01/legado` 在一次性 fork 治理归档后的后续维护顺序。目标是优先降低
实际安全与兼容风险，并在可验证、可回滚的前提下完成 Android lint 的 108 个 warning 和
18 个 hint 清零。
路线图固定工作边界、顺序、门禁和重新启动条件；Android lint 小节记录本次已授权 Apply，
其他依赖升级、远端 Pull Request 处置或设备操作仍不因本文档而自动获准。

历史事实基线为 2026-08-31 的 `master` 提交 `7aa63c27b`：Android lint 为
`0 error / 108 warning / 18 hint`，网页端 ESLint 为 0 error。本次以单一 OpenSpec 变更
`eliminate-android-lint-findings` 的一次连续 Apply 处理全部 126 个 occurrence；最终强制
lint XML 为 0 issue，完整处置与验证事实以该变更的实施证据为准。

## 治理原则

1. 实际风险优先，不以关闭告警、删除草稿或缩短分支列表作为安全完成证据。
2. Android lint 全部兼容域收口在 `eliminate-android-lint-findings` 一个 OpenSpec 变更的一次
   Apply 中，仍按证据依赖严格串行；不得为单个 warning/hint 另建变更或跳过中间止损点。
3. 能够证明行为等价的项目优先修复；确认属于刻意兼容行为的项目只允许精确局部抑制，并记录
   位置、理由、验证证据和重新启动条件。
4. 不使用全局 lint baseline、全局关闭检查或批量格式化来制造 warning 为零的表象。
5. 每个实现批次失败即停止。没有实际运行的测试、模拟器或真机验证不得描述为通过。
6. 用户设备只允许验证 `io.legado.app.debug`。执行前实时核对包与数据边界；若需要清理既有
   Debug 数据，必须停止并另行取得明确授权。普通正式版及其数据不进入本路线图的设备操作。

## 108 个 warning 与 18 个 hint 的完整去向

| 工作域 | 数量 | 组成 | 处置边界 |
|---|---:|---|---|
| 行为与资源 | 65 | 62 个中风险项，加 `IconDuplicates`、`UnusedAttribute`、`UseCompoundDrawables` 3 个低风险项 | 同一变更内按行为、资源、Overdraw 与剩余布局串行处理 |
| 工具链与依赖 | 43 | `AndroidGradlePluginVersion` 4、`GradleDependency` 14、`NewerVersionAvailable` 25 | 同一变更内逐坐标精确抑制，不升级版本 |
| hint | 18 | `ReportShortcutUsage` 1、`TrimLambda` 17 | 同一变更内先锁定兼容行为，再实施真实修复 |
| **合计** | **126** |  | 同一次 Apply 的最终 lint XML 必须为 0 issue |

版本类检查依赖远端元数据，后续可能在没有源码变化时出现新提示。因此“0 issue”是目标提交
的可验证状态，不代表依赖已经升级或兼容风险消失。达到零后，XML 门禁会阻断任意新增 issue；
版本元数据漂移必须先进入账本并完成逐坐标审查，不得通过全局关闭检查恢复绿色。

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

### 3. Android lint 单一变更的一次 Apply

`eliminate-android-lint-findings` 内部按以下顺序串行执行，不拆成多个 OpenSpec 或并行兼容域：

1. 工作树、JDK 17、固定 SDK、API 21/23/36 AVD、授权 Debug 真机和强制 lint 基线预检；
2. XML 对账、只读 golden 与显式更新基础设施；
3. 旧式 trim、动态快捷方式、launcher 图标和方向继承；
4. 位图密度、重复图标、死资源和 RSS ripple；
5. `Overdraw`、漫画菜单、文件路径控件和 launcher3 矢量路径；
6. Gradle、AGP、AndroidX、运行库与 Glide 的逐坐标版本提示；
7. XML 零 issue CI 门禁、维护账本和最终验证。

每批必须先有聚焦测试或治理前基线，再实施、重跑验证并守恒对账 occurrence。任一批次出现
测试失败、视觉差异、兼容回归或证据不足时立即停止，修复并复验当前批次后才能继续；不得把
失败留到末尾统一解释，也不得以 baseline、全局 ID 关闭或降低 `checkDependencies` 制造清零。

确定性模拟器证据可保存最小版本化 golden。真机只允许操作 `io.legado.app.debug` 与
`io.legado.app.debug.test`；正式版包和个人数据不得读取、启动、清理、覆盖或卸载。原始截图、
UI hierarchy 与设备日志只保存在 Git 忽略的构建目录，版本化文档只记录脱敏摘要和哈希。

版本提示的精确抑制不表示依赖已升级。Gradle、AGP、Kotlin、AndroidX、运行库与 Glide 的实际
升级仍由 Dependabot 或独立升级评审触发，并须按各声明旁的重启条件重新验证。

### 4. Web 生产依赖

以 #44 为调查输入，按 Element Plus、VueUse、Hotkeys、Pinia、Vue Router 等兼容族拆分，
同步提交 `package.json` 与 `pnpm-lock.yaml`，使用固定 pnpm 冻结安装并执行章节 HTML 安全测试、
类型检查、只读 ESLint、构建和 Android Web 静态资源对账。替代变更准备并通过评审前不关闭 #44。

### 5. Web 开发工具链

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

Android lint 清零已由 `eliminate-android-lint-findings` 的提案、设计、规范和任务授权，并只在
该变更的一次 Apply 中实施。其他尚未启动的 Web、Actions 或依赖升级工作域仍须重新读取当前
代码、告警、版本、设备和 CI 状态，再建立各自经评审的变更；不得把本次 lint 精确抑制解释为
对未来依赖升级、远端 Pull Request 或发布流程的预先授权。
