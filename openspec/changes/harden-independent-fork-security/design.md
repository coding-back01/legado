## 背景

详见 `proposal.md`。当前 `:app` 应用了 Google Services 插件并依赖 Firebase Analytics 与 Performance，跟踪树包含上游 `google-services.json`。旧 `Test Build` 还会复制仓库内的 `legado.jks`、写入硬编码密码并允许 Gradle 构建步骤失败后继续执行；新的手动 `Release Build` 已经具备通过 GitHub Actions Secrets 恢复和校验独立签名的路径。

本变更跨越 Android 构建配置、隐私说明和 GitHub Actions，且涉及签名身份与遥测数据边界，因此需要在实现前固定安全决策。它不触及运行时业务模块、最低 SDK 21、Room 数据、规则格式、导入接口或备份。

## 目标与非目标

**目标：**

- 让独立仓库的本地与 CI Debug 构建不依赖上游 Firebase 项目。
- 让 `Test Build` 成为不接触发布签名的失败即停止验证入口。
- 让手动 `Release Build` 成为唯一正式签名发布入口，并保持缺少 Secrets 时主动失败。
- 防止常见 Firebase 配置与签名文件再次误提交。
- 使用聚焦修改，避免覆盖另一 session 的无关工作区改动。

**非目标：**

- 不重写已经公开的 Git 历史，也不声称历史凭据恢复保密。
- 不代替上游所有者撤销 Firebase Key 或旧签名。
- 不创建新的 Firebase 项目，不保留 Analytics 或 Performance 的占位集成。
- 不处理 Dependabot PR、依赖升级、分支 Ruleset 或网页端构建流程。
- 不改变应用数据、协议和阅读功能行为。

## 技术决策

### 1. 完整移除 Firebase，而不是隐藏客户端 Key

从根插件声明、`:app` 插件和依赖、版本目录、工作流及跟踪树中移除 Google Services 与 Firebase 条目，并同步更新隐私政策。Firebase Android 客户端 Key 最终会进入 APK，把 `google-services.json` 改存 Actions Secret 只能隐藏仓库文件，不能建立真正的保密边界；继续使用上游受限 Key 又要求本仓库依赖一个无权管理的外部项目，因此两种替代方案均不采用。

如果未来需要遥测，应通过新的 OpenSpec 变更接入仓库所有者控制的项目，并重新评估隐私说明、包名和签名证书限制。

### 2. `Test Build` 收敛为 Debug 验证工作流

删除公开签名步骤、发布变体矩阵、映射文件处理以及上游专用的预发布、蓝奏云、测试分支和 Telegram 分发任务。验证入口执行 `:app:testAppDebugUnitTest` 与 `:app:assembleAppDebug`，并可把 Debug APK 作为 GitHub Actions artifact 留给维护者下载。

选择 Debug 构建而不是未签名 Release，是因为 Debug APK 可直接用于开发验证且使用独立应用标识，不会被误认为正式发布包。选择固定验证入口而不是临时生成 CI Release 签名，是为了避免产生证书身份不稳定但外观看似正式的 APK。

工作流保留与 Android 相关的现有路径过滤，Web-only 变更继续由独立 Web 工作流负责。本次不把状态检查加入 Ruleset；待新验证工作流在实际 PR 上稳定运行后再单独启用。

### 3. 构建与测试失败必须向上传播

验证命令在同一脚本中以严格错误处理运行，删除构建步骤的 `continue-on-error`。单元测试或 assemble 任一失败都会终止 job，artifact 上传仅在前置命令成功时发生。

上传外部渠道不再属于此工作流，因此无需保留“上传失败但构建成功”的特殊语义。

### 4. 手动正式发布继续使用 Secrets

保留 `.github/workflows/release.yml` 的 `workflow_dispatch`、签名 Secrets 完整性检查、临时恢复签名文件和 `release`、`releaseA` 两变体。仅移除已失效的 `google-services.json` 替换逻辑；签名文件仍在 runner 内生成并且不进入 artifact 或仓库。

正式签名文件使用仓库现有的 `RELEASE_KEY_STORE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` Secrets 契约。缺失或校验失败时保持 fail-closed，不增加公开签名回退。

### 5. 忽略规则覆盖本地服务配置与签名格式

在 `.gitignore` 中加入 `/app/google-services.json` 以及 `*.jks`、`*.keystore`、`*.p12`、`*.pfx`。这些规则只防止新文件误提交；已经跟踪的旧文件需要在本变更中显式删除，历史暴露则通过告警说明而不是破坏性改写解决。

### 6. 实施时采用重叠检测

开始每个文件修改前重新读取 `git status --short` 和目标 diff。若另一 session 修改了同一文件，暂停该文件的应用，先比较意图再做点选合并；不覆盖、不回退、不批量格式化另一 session 的工作。

## 风险与权衡

- [移除 Firebase 后不再获得 Analytics 和 Performance 数据] → 这是独立仓库当前期望的隐私边界；未来需要时使用自有项目重新接入。
- [旧 Key、旧签名和密码仍存在于历史与上游] → 将其视为永久公开，不再用于本仓库身份；不以历史重写冒充轮换。
- [Debug artifact 不能覆盖安装正式版] → 文件名和工作流说明明确标注 Debug，只用于开发验证；正式安装包仅由手动发布工作流产生。
- [Fail-closed 暴露既有测试或构建失败] → 先在本地运行相同命令并修复真实问题，再考虑把检查设为分支必需项。
- [删除版本目录条目可能与依赖更新产生冲突] → 仅删除本变更已不再引用的 Firebase 和 Google Services 条目，不调整任何其他版本。
- [另一 session 同时修改相同文件] → 逐文件复查共享工作区，出现重叠立即暂停并保留双方改动。

## 迁移计划

1. 记录共享工作区状态，确认另一 session 的改动文件。
2. 删除 Firebase 插件、依赖、客户端配置和工作流引用，更新隐私政策与忽略规则。
3. 删除旧签名文件与硬编码签名步骤，把 `Test Build` 收敛为 Debug 验证。
4. 保留并检查 `Release Build` 的 Secrets 签名路径，删除其中的 Firebase 文件变换。
5. 运行聚焦单元测试、Debug 构建、OpenSpec 严格校验和补丁检查。
6. 通过受保护分支的 Pull Request 合并；确认当前提交不再触发上游 Firebase 配置的使用后，按事实说明关闭历史 Secret scanning 告警。

若需要回滚运行时变化，可以恢复 Firebase 集成，但只能接入仓库所有者控制的新项目；不得恢复公开 `legado.jks` 或硬编码密码。CI 调整可独立回退到上一版本的验证命令，但正式发布仍必须保持 Secrets-only。
