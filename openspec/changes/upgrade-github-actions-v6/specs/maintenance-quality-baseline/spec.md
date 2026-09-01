## ADDED Requirements

### Requirement: 工作流依赖运行时和关键安全合同必须失败即停止
Pull Request、`master`、发布和 stale 工作流使用的 JavaScript Action MUST 与执行 runner
兼容；本次目标 Action 运行于 Node 24，runner 版本 MUST 不低于 `2.327.1`。仓库契约 MUST
精确覆盖三个工作流中的目标 Action 大版本，并继续覆盖 CodeQL v4、Android CodeQL
`--no-build-cache`、JDK 17、Node.js 22、pnpm 9.15.9、只读 Web 构建、正式签名 Secrets
隔离和稳定聚合门禁。任一版本或安全边界漂移 MUST 使仓库检查失败，不能以部分工作成功代替。

#### Scenario: 目标 Action 版本与契约不一致
- **WHEN** 任一工作流使用的目标 Action 大版本与批准组合不一致
- **THEN** 仓库契约检查失败并指出发生漂移的工作流和 Action
- **AND** 稳定聚合门禁报告失败

#### Scenario: runner 不满足 Node 24 下限
- **WHEN** 工作流在低于 `2.327.1` 的 runner 上执行 Node 24 Action
- **THEN** 对应检查必须失败或被明确阻止
- **AND** 系统不得把未执行的后续构建描述为通过

#### Scenario: 维护契约真实 RED 被同步修复
- **WHEN** Action 大版本升级使旧版本精确断言失败
- **THEN** 系统只将断言更新为逐工作流批准的新版本并补齐缺失覆盖
- **AND** 不删除 JDK、Node.js、pnpm、CodeQL、缓存、签名隔离或聚合门禁断言

#### Scenario: 工作流实现文件发生变化
- **WHEN** Pull Request 修改测试、发布、stale 工作流或其仓库契约
- **THEN** 变更范围识别必须触发 Android、Web、CodeQL、OpenSpec 和仓库验证的完整适用门禁
- **AND** 每个实际运行的失败均阻止稳定聚合门禁通过

### Requirement: Gradle Action 缓存升级必须显式选择开放边界并保留可审计失败
使用 `gradle/actions/setup-gradle@v6` 的工作流 MUST 显式选择 100% MIT 的 `basic` 缓存
provider，不得加载默认的 enhanced 商业缓存组件，并将升级后的首次缓存未命中视为预期冷启动
而非构建失败。缓存保存或恢复异常 MUST 在作业摘要或日志中可见，且 Android CodeQL 构建
MUST 继续使用 `--no-build-cache`，不得用缓存命中替代 Java/Kotlin 源码编译证据。

#### Scenario: 缓存 provider 配置漂移
- **WHEN** `setup-gradle@v6` 没有显式选择 `basic` provider 或改为 enhanced provider
- **THEN** 仓库契约检查失败
- **AND** 变更不得在缺少新的独立评审时合并

#### Scenario: 升级后首次运行缓存未命中
- **WHEN** 新缓存协议使既有 Gradle 缓存失效
- **THEN** 工作流重新填充缓存并继续执行真实构建
- **AND** 不将预期缓存未命中报告为应用兼容性回归

#### Scenario: CodeQL Android 任务执行
- **WHEN** CodeQL 分析 Java/Kotlin 源码
- **THEN** Gradle 构建禁用 build cache 并实际编译目标源码
- **AND** 缓存恢复成功不得代替 CodeQL 可处理源码的证据

#### Scenario: 缓存操作失败
- **WHEN** Gradle 缓存保存或恢复失败
- **THEN** 工作流日志或摘要必须暴露该状态
- **AND** 不得伪造缓存成功或隐藏后续真实构建结果
