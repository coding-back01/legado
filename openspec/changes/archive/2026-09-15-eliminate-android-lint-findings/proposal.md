## Why

当前 `:app:lintAppDebug` 的强制重跑基线仍包含 108 个 warning 和 18 个 hint；这些项目虽已完成三态审查，但继续作为可见噪声存在，也没有阻止后续新增 warning 或 hint。现在需要在保留最低 API 21、既有阅读与规则兼容语义以及固定依赖策略的前提下，一次性把 Android lint 可见 issue 清零并建立持续门禁。

## What Changes

- 使用一个 OpenSpec 变更和一次连续 Apply 覆盖全部 Android lint warning 与 hint；变更内部仍按证据依赖串行分批，任一批次验证失败即停止，不把失败留到末尾统一处理。
- Apply 开始时对目标提交强制重跑 `:app:lintAppDebug` 并锁定完整 occurrence 清单；当前 108 warning/18 hint 以及同一命令新发现的项目都必须逐项进入 `FIXED` 或 `SUPPRESSED_WITH_REASON`，最终不得保留 `DEFERRED` 或 `PENDING_REVIEW`。
- 对可证明行为等价的代码、资源和布局进行修复；对 Android 版本兼容、交互反馈、资源语义或依赖治理要求必须保留的项目，只允许在精确位置添加带理由和重启条件的局部抑制。
- 保持版本更新由现有 Dependabot 策略管理。本变更不升级 Gradle、AGP、Kotlin、AndroidX、Glide 或其他应用依赖，而是对已逐坐标审查的版本提示添加精确抑制；禁止全局关闭 lint ID 或建立 lint baseline。
- 为规则字符串旧式 trim 语义、动态快捷方式使用上报、launcher 图标选择与恢复、资源密度、RSS ripple、Overdraw、漫画菜单、文件路径控件和 launcher 矢量图补充聚焦测试、固定模拟器截图及必要的 Debug 真机证据。
- 在 CI 中解析 lint XML 并要求 issue 数量严格为零，使 error、warning 和 hint 的新增项目都能阻断合并；同步更新维护账本、风险路线图和验证证据。
- 调整现有路线图中“按多个 OpenSpec 变更拆分”的执行方式：兼容域仍保持串行、可归因的内部批次，但统一收口在本变更和一次 Apply 中。
- 非目标：不提高最低 SDK，不修改 Room schema、书源或订阅源规则格式、导入 URI、备份格式、正式签名或发布流程，不处理网页端依赖与 ESLint，也不使用包含正式版个人数据的设备。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `maintenance-quality-baseline`：将 Android lint 的完成条件从“错误清零、warning 可三态延期”加强为目标命令的 error、warning、hint 全部不可见，并建立零 issue 持续门禁与一次性完整对账要求。

## Impact

- Android 应用：`:app` 的 Kotlin、Manifest、资源、布局、快捷方式、主题和测试代码；`:modules:book` 与 `:modules:rhino` 仅通过 `checkDependencies = true` 纳入回归验证。
- 构建与治理：`app/build.gradle`、`gradle/libs.versions.toml`、Gradle wrapper 的精确检查注释、Android lint CI 步骤、报告断言脚本、维护账本和风险路线图。
- 兼容性：必须保持 API 21、旧 `launcherIcon` 偏好、书源规则与 Cookie/URL 的旧式空白裁剪语义、Android 16 以下九个页面的方向继承、API 23+ RSS 点击反馈以及 launcher alias 行为。
- 持久化与迁移：不变更数据库、JSON、备份或导入协议；图标偏好仍保存原字符串值，类型化资源 ID 只作为运行时解析实现。
- 安全：不新增网络、账户或凭据访问；截图和真机证据只允许使用 `io.legado.app.debug` 与无个人内容夹具，原始设备证据不得进入 Git。
- 可观察验收：目标提交强制运行 `:app:lintAppDebug` 后 XML issue 数为 0，相关页面在批准的 API、主题、密度和旋转矩阵中无可见回归，普通启动不得被误记为快捷方式使用。
