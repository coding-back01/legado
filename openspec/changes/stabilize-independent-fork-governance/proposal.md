## Why

当前仓库已经从停止维护的上游复制为独立签名的个人 fork，但质量、依赖、反馈、分发和发布流程仍沿用上游遗留状态：Android lint 为 14 个错误、881 个警告和 18 个提示，网页端 ESLint 有 2 个错误，30 个待处理 PR 全部由旧 Dependabot 策略产生，多个应用内及仓库链接已经失效，应用内更新器也无法从当前 fork 安全选择普通版 APK。若不一次建立可持续基线，后续修复仍可能在缺少门禁、设备证据和明确维护边界的情况下发布。

## What Changes

- 将项目定位固定为“独立签名的个人稳定 fork”：保留最低 API 21 和既有兼容接口，只声明维护者指定且实际验证过的设备，不承担社区继任或厂商专属新能力承诺。
- 清零 14 个 Android lint error、2 个网页端 ESLint error 和已识别的高风险 warning；对其余 warning 逐 lint ID 审查，采用 `FIXED`、`SUPPRESSED_WITH_REASON`、`DEFERRED` 三态清账，并建立中文维护基线。
- 建立 Android lint、单元测试、Debug 构建、网页端类型检查/ESLint/构建、OpenSpec 严格校验和 CodeQL 的持续门禁；所有必需检查在任意改动路径上都产生稳定聚合结果，未满足门禁时不得发布正式版。
- 重配 Dependabot：启用安全更新并立即创建独立 PR但不自动合并，普通更新按月分组，所有生态普通更新上限之和不超过 5，兼容性固定依赖显式忽略，AGP/Gradle/Kotlin/KSP 作为独立工具链变更。
- 先让新 Dependabot 策略生效，再精确关闭 30 个旧机器人 PR并删除对应分支；删除任务中明确列出的 4 个已合并遗留分支。执行前必须重新核对对象及 ref/SHA，未列入任务的新远端对象不在授权范围内。
- 启用 Dependabot alerts、Dependabot Security Updates、Secret Scanning、私有漏洞报告和 CodeQL；各能力启用后立即读取可用告警，高危/严重问题阻止后续合并与发布，中危必须记录判断，低危进入维护清单。
- 只保留正式版 bug 和私有安全报告入口，不承诺响应时间；删除功能请求表单。通用 stale 自动关闭被移除，仅允许 `needs-info` 超过 30 天后关闭，并强化公开日志、书源和备份的脱敏提示。
- 修复应用内更新器，使其只读取 `coding-back01/legado` 的 Latest Release、严格选择唯一普通版 `_release.apk`、可靠比较完整版本，并永久忽略旧 beta/`releaseA` 通道偏好；网络和解析行为改用确定性夹具测试。
- **BREAKING（分发层）**：今后停止构建和发布 `releaseA`，并移除面向用户的 beta/`releaseA` 更新通道；三个历史 Release 的 `releaseA` APK、运行时兼容代码、包名和既有设备数据继续保留，不执行卸载、清数据或数据接管。
- 全量治理已证实失效或误导的用户可见、运行时和维护链接：当前维护入口指向本 fork，失效远程图片改用本地资产或内置占位，历史资料恢复为本地归档或不可变引用；仍有效的上游依赖、社区资源和历史 issue/PR 引用保留并明确标注归属。
- 正式发布短期冻结，现有 Latest 保留并注明稳定化状态。预发布代码、质量、安全门禁通过且适用模拟器预检已完成后，只允许仓库所有者手动生成一个受控签名的普通版草稿候选；候选完成完整性和指定真机门禁后才公开，随后才归档总变更。
- 建立分层自动验证：干净模拟器承担导入、阅读和翻页等会写数据的完整路径；指定真机只执行同签名升级、启动、受限日志/截图/UI 层级、可自动读取的数据数量前后对比及“导入预览后取消”等非破坏性路径，不要求用户手动点测，原始设备证据不得进入 Git。

## Capabilities

### New Capabilities

- `maintenance-quality-baseline`：规定 Android、网页端和 OpenSpec 的绿色基线、warning 三态账本、持续门禁及证据要求。
- `repository-maintenance-governance`：规定个人 fork 的 Dependabot、安全告警、反馈入口、远端清理和分支保护策略。
- `fork-distribution-identity`：规定本 fork 的用户可见身份、更新器资产选择、上游归属、失效链接治理和 `releaseA` 退役兼容边界。
- `release-verification`：规定发布冻结、模拟器与真机验证边界、普通版 APK 核验、发布及失败回滚条件。

### Modified Capabilities

- `fork-build-security`：将现有验证与正式签名要求扩展为受质量和安全门禁约束的普通单 APK 发布流程，并明确不再生成未来 `releaseA` 产物。

## Impact

- Android：`app/` 的 Kotlin 更新逻辑、资源、本地化、assets、Gradle 配置、单元测试和设备测试；不修改 Room schema、书源/订阅源规则、导入 URI、备份格式或普通正式版包名与签名身份。
- 网页端：`modules/web/` 的 TypeScript/ESLint 问题、确定性检查和构建门禁；避免无关批量格式化和静态资源同步差异。
- 仓库与文档：`.github/workflows/`、Dependabot、Issue 表单、仓库安全设置、`README.md`、`English.md`、`package.json`、应用内帮助和新增 `docs/maintenance-baseline.md`。
- 远端状态：旧 Dependabot PR及其精确分支、任务列出的 4 个已合并遗留分支、现有 Latest 说明、仓库安全功能、`master` ruleset 和首个治理后普通版 Release。
- 依赖：不顺带升级普通依赖；仅允许为已确认 lint 根因或安全告警进行最小、可验证的依赖声明/安全更新，带兼容性注释的固定版本不得突破。
- 安全与迁移：普通正式版继续使用 `io.legado.app.release` 和既有私有签名完成同签名升级；不得把 `releaseA` 数据自动迁入普通版，不得在用户真机卸载、清数据、确认导入或修改阅读进度。

### 可观察验收条件

- Android lint 为 0 个错误，网页端 ESLint 为 0 个错误；原始 warning 的每个 lint ID 均有数量、状态、理由和重启条件，完整逐条报告可从 CI artifact 获取。
- Android 单元测试、Debug 构建、网页端类型检查/ESLint/构建、固定版本 OpenSpec 严格校验和所需 GitHub 聚合检查实际通过；高危/严重安全问题为零，中危均有处置记录。
- 当前 fork 的更新器只返回普通版 APK；不存在 beta 或 `releaseA` 误选，旧偏好恢复不改变选择，已证实的失效用户入口均已修复或有明确的本地替代。
- 新 Dependabot 队列符合限流与分组策略，旧机器人 PR和目标分支完成精确清理；功能请求入口和通用 stale 已移除。
- 实际运行的模拟器结果被记录，指定真机完成授权范围内的升级、启动和数据保持验证；未执行或环境不可用的项目不得描述为通过。
- 新普通版 APK 的远端大小、摘要、ZIP 完整性、包名、版本和签名全部核验，且 workflow `head_sha`、tag commit、Release target 与锁定的绿色 `master` SHA 一致后发布；应用内更新器能够正确识别该 Release，随后归档分支与归档后的 `master` 均通过 OpenSpec 严格校验。
