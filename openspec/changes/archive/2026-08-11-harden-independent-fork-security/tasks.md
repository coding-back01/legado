## 1. 协作与范围保护

- [x] 1.1 在实现前检查 `git status --short`、当前 diff 和活动 OpenSpec 变更，记录另一 session 的改动文件；发现目标文件重叠时先点选合并，不覆盖用户改动。
- [x] 1.2 确认本次仅处理继承的 Firebase、旧签名材料和 Android 验证工作流，不修改 Dependabot PR、分支 Ruleset、依赖版本或业务代码。

## 2. 移除上游 Firebase 集成

- [x] 2.1 从根 Gradle、`:app` 和版本目录中移除 Google Services 插件及 Firebase Analytics、Performance 依赖，不调整其他依赖版本。
- [x] 2.2 删除跟踪的 `app/google-services.json`，并在 `.gitignore` 中加入 Firebase 配置和常见签名文件格式的忽略规则。
- [x] 2.3 删除所有 GitHub Actions 中对 `google-services.json` 的读取或包名替换逻辑。
- [x] 2.4 更新应用内隐私政策，准确说明当前构建不再集成 Firebase 统计和性能服务。

## 3. 隔离签名并加固验证工作流

- [x] 3.1 删除 `.github/workflows/legado.jks` 以及 `Test Build` 中复制签名文件、硬编码别名和密码的步骤。
- [x] 3.2 将 `Test Build` 收敛为执行 `:app:testAppDebugUnitTest` 和 `:app:assembleAppDebug` 的验证 job，并只上传明确标注为 Debug 的 GitHub Actions artifact。
- [x] 3.3 删除 `Test Build` 中上游专用的预发布、蓝奏云、测试分支、Telegram、Release 变体和映射文件分发逻辑。
- [x] 3.4 删除 Android 测试与构建步骤的 `continue-on-error`，确保任一验证命令失败都会使 job 失败。
- [x] 3.5 保留手动 `Release Build` 的 Secrets-only 签名校验与两个正式变体，仅删除 Firebase 文件变换，并确认不存在公开签名回退。

## 4. 验证与交付

- [x] 4.1 使用 `git ls-files` 和聚焦文本搜索确认当前跟踪树不再包含继承的 Firebase 配置、旧签名文件、硬编码签名凭据或失效引用。
- [x] 4.2 运行 `./gradlew :app:testAppDebugUnitTest` 和 `./gradlew :app:assembleAppDebug`，记录实际结果；不把未运行的设备测试描述为通过。
- [x] 4.3 检查 GitHub Actions YAML 语法及工作流 job、依赖和条件，确认 Debug artifact 仅在验证成功后上传，正式发布仍在签名 Secrets 缺失时失败。
- [x] 4.4 运行 `openspec validate --all --strict`、`git diff --check`、`git diff --stat`，确认工件与实现一致且没有无关改动。
- [x] 4.5 通过 Pull Request 合并后，按“上游 Firebase 客户端配置已从当前分支移除且本仓库不再使用”的事实说明关闭历史 Secret scanning 告警；不声称上游 Key 已被撤销。
