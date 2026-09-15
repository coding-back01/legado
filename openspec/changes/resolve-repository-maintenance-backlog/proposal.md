## Why

Android lint 清零后，仓库仍有一条可修复但机器人未能生成补丁的高危传递依赖告警、四个相互冲突或门禁失败的 Dependabot Pull Request，以及已失去用途的历史分支和工作树。若继续分别处理，这些事项会反复争用同一份 Gradle、Web 锁文件和仓库治理状态，因此需要在一个可审计、失败即停止的变更中统一闭环。

## What Changes

- 在最新 `master` 上将 ESLint 链路中的 `js-yaml` 从 `4.3.1` 更新到最低安全版本 `4.3.2` 或更高兼容安全版本，并为 Dependabot 无法自动修复传递依赖的情况建立人工最小补丁和复核流程。
- 将 Gradle `8.13 → 9.7.1` 与 Android Gradle Plugin `8.13.2 → 9.4.0` 作为同一工具链迁移处理，修复 wrapper 文本一致性及 AGP 9 构建脚本兼容问题，并验证 JDK 17、Kotlin/KSP、三个 Android 模块和 API 21/23/36 边界。
- 从最新 `master` 重建 Web 依赖升级，不直接合并陈旧机器人分支；在同一次 Apply 内按生产运行时、构建工具、类型系统和 lint 工具的依赖关系分批迁移，修复 TypeScript 7 的 `TS5112` 等兼容问题，并同步提交确定性的 Android Web 静态资产。
- 升级后重新核对 Element Plus 静态链接安全合同和 Dependabot 告警；高危/严重告警必须清零，中危告警必须被修复或保留有当前证据支持的精确接受理由。
- 替代实现通过全部门禁后，按精确编号、head ref 和 SHA 关闭 Dependabot PR #42、#45、#80、#81，删除对应机器人分支，并清理已被 `master` 包含的本地历史分支、`legado-reading-time` 工作树及已被归档证据取代的 `codex/release-verification-evidence` 分支。
- 整个范围使用一个 OpenSpec 和一次连续 Apply 串行完成；任一内部批次失败时停止后续变更和远端清理，不把部分完成描述为闭环。
- 不删除或公开 `3.26.083020`、`3.26.083021` 两个审计草稿，不触发正式发布，不改变 `master` 保护、自动删已合并分支设置或既有安全能力。

## Capabilities

### New Capabilities

- 无。

### Modified Capabilities

- `repository-maintenance-governance`：补充传递安全依赖自动更新失败时的人工兜底、陈旧机器人 PR 的替代闭环，以及远端和本地 Git 对象的精确清理要求。
- `maintenance-quality-baseline`：补充 Android 工具链联合升级、Web 大版本依赖分批迁移、生成资产一致性和一次 Apply 失败即停止的验收合同。

## Impact

- Android 构建：`gradle/wrapper/`、`gradlew`、`gradlew.bat`、`gradle/libs.versions.toml`、根工程及 `app`、`modules/book`、`modules/rhino` 的 Gradle 配置；必要时同步维护 CI 契约检查。
- Web 构建：`modules/web/package.json`、`modules/web/pnpm-lock.yaml`、TypeScript/Vite/ESLint 配置与聚焦测试，以及 `app/src/main/assets/web/vue/` 中由 Web 构建同步的静态产物。
- 仓库治理：`.github/dependabot.yml`、安全告警状态、四个机器人 Pull Request 及其精确分支、一个远端证据分支、已合并本地分支和附加工作树。
- 兼容性：不得提高最低 API 21，不改变 `io.legado.app.release`、Room schema、备份格式、书源/订阅源规则、导入 URI、JSON 字段、阅读数据或现有网页端用户流程；不得顺带升级带固定说明且不在本变更清单内的依赖。
- 可观察验收：Android 与 Web 全部适用门禁及 CodeQL 成功，Web 构建后版本化资产无漂移，Android lint 继续保持零 issue，高危/严重安全告警为零，既定中危判断可审计，四个旧 PR 和批准清理对象全部闭环，审计草稿与仓库保护状态保持不变。
