## 为什么

当前仓库继承了上游 Firebase 客户端配置、公开签名文件及允许构建失败继续执行的测试工作流。作为独立开发仓库继续沿用这些资产，会把本仓库构建与上游遥测项目和公开签名身份绑定，并可能让失败的构建显示为成功，因此需要在首次独立发布前完成隔离和加固。

## 变更内容

- 移除继承的 Firebase Google Services 配置、Gradle 插件与 Analytics、Performance 依赖，并同步清理工作流引用和隐私政策说明。
- 删除仓库中的旧 `legado.jks` 及测试工作流内的硬编码签名配置；测试构建不得使用正式发布签名材料。
- 将旧测试工作流收敛为面向 Pull Request 和 `master` 的验证流程，使用 Debug 构建产物，不再执行上游专用的预发布、蓝奏云、测试分支和 Telegram 分发。
- 让单元测试或 Gradle 构建失败直接导致工作流失败，不再通过 `continue-on-error` 掩盖失败。
- 保留现有手动正式发布工作流，并继续要求通过 GitHub Actions Secrets 注入本仓库自己的签名材料。
- 在忽略规则中排除本地 Firebase 配置和签名文件，避免再次误提交。
- **非目标**：不重写 Git 历史、不轮换或撤销上游凭据、不新增本仓库 Firebase 项目、不处理现有 Dependabot PR、不升级依赖、不调整已创建的 GitHub 分支规则。

## 能力

### 新增能力

- `fork-build-security`：规定独立仓库的遥测隔离、签名材料边界、验证工作流失败语义和正式发布签名要求。

### 修改能力

无。当前主规范中没有对应能力。

## 影响

- 受影响范围：`app/` 的 Gradle 配置与隐私政策、根 Gradle 插件声明、`gradle/libs.versions.toml`、`.gitignore`、`.github/workflows/test.yml`、`.github/workflows/release.yml` 及旧签名文件。
- 用户可感知变化：独立构建不再初始化 Firebase Analytics 或 Performance，也不再向上游 Firebase 项目发送相关数据；正式 APK 仍由手动发布工作流生成。
- 兼容性：不修改最低 SDK、应用数据、Room 结构、书源与订阅源规则、JSON、导入 URI、备份或网页端接口，不需要数据迁移。
- 安全边界：历史提交和上游仓库中的公开材料不会因本变更恢复保密；旧签名必须视为已公开，后续正式发布只能使用本仓库所有者控制的新签名。
- 验收条件：跟踪树中不存在继承的 Firebase 配置和签名二进制；Debug 构建无需 Firebase 文件即可通过；验证工作流遇到测试或构建失败时返回失败；正式发布工作流在签名 Secrets 缺失时明确失败，并且不会退回公开签名。
