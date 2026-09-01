## Why

Dependabot Pull Request #73 将 8 个 GitHub Action 跨多个大版本升级，但其分支当前落后
`master` 2 个提交，并因维护工作流的精确版本契约仍要求旧版本而失败。直接合并该分支既会
绕过对 Node 24、缓存许可、artifact 完整性和 stale 权限语义的审查，也可能覆盖随后完成的
治理变更，因此需要从最新 `master` 建立可归因、失败即停止的替代变更。

## What Changes

- 从最新 `master` 统一升级三个工作流中的 8 个 Action 大版本：
  `actions/checkout@v7`、`actions/setup-java@v6`、`gradle/actions/setup-gradle@v6`、
  `actions/upload-artifact@v7`、`actions/download-artifact@v8`、`actions/stale@v11`、
  `pnpm/action-setup@v6` 和 `actions/setup-node@v7`。
- 在接受升级前显式核对 Node 24 与最低 runner `2.327.1`、checkout 凭据与清理语义、
  Gradle 缓存许可和首次缓存失效、pnpm/Node 缓存边界、artifact 上传下载兼容性与摘要校验，
  以及 stale 只处理 `needs-info` issue 的权限和筛选语义。
- 扩展维护与发布工作流契约，使三个工作流的目标 Action 版本、发布唯一 APK 传递闭环、
  CodeQL `--no-build-cache` 和现有最小权限边界均被精确锁定；不得通过删除或模糊断言使门禁
  变绿。
- 以当前 #73 的失败作为真实 RED，从最新 `master` 实现并验证替代变更；替代 Pull Request
  通过评审与完整 CI 后才能关闭 #73，并记录替代关系。
- 非目标：不升级 CodeQL、pnpm 9.15.9、Node.js 22、Gradle、AGP 或应用依赖；不修改应用
  行为、持久化数据、最低 SDK、签名材料、发布授权逻辑或 stale 的业务策略；验证期间不触发
  正式 Release。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `repository-maintenance-governance`：增加 GitHub Actions 大版本升级的逐项审查、精确契约、
  stale 语义保持以及落后 Dependabot Pull Request 的替代关闭条件。
- `maintenance-quality-baseline`：要求 CI Action 运行时兼容且三个工作流的版本和关键安全边界
  由失败即停止的仓库契约覆盖。
- `release-verification`：要求发布 workflow 的 artifact 上传下载在不创建 Release 的验证中
  证明唯一 APK 路径保持一致，并在摘要不匹配时失败。

## Impact

- 受影响文件：`.github/workflows/test.yml`、`.github/workflows/release.yml`、
  `.github/workflows/stale.yml`、`.github/scripts/test_maintenance_workflow.py`、
  `app/src/test/java/io/legado/app/help/update/ReleaseWorkflowContractTest.kt`，以及必要的聚焦
  工作流契约测试文件。
- 受影响系统：GitHub-hosted runner、Actions artifact 服务、Gradle Actions 缓存、Dependabot
  Pull Request #73 和 `master` 必需检查；正式发布 Secrets 不进入非发布验证。
- 向后兼容：应用二进制、包名、签名、Room 数据、书源/订阅源规则、导入 URI、备份格式和
  历史 Release 均不变化，无数据迁移。工作流改用 Node 24 Action，GitHub-hosted runner 满足
  最低版本；若未来改用低于 `2.327.1` 的 self-hosted runner，门禁必须失败并停止升级。
- 安全风险：`gradle/actions@v6` 默认启用受独立条款约束的 enhanced 缓存，因此本变更显式
  选择 100% MIT 的 `basic` provider；升级后首次运行预计缓存未命中；
  `download-artifact@v8` 的摘要不一致默认失败必须保留；checkout 凭据、正式签名 Secrets、
  stale 写权限和 CodeQL 无构建缓存边界不得扩大。
- 可观察验收：替代 Pull Request 在最新 `master` 上产生 Android、Web、CodeQL、OpenSpec/
  仓库检查和稳定聚合门禁的绿色结果；发布 artifact 契约证明上传和下载仍得到唯一预期 APK；
  stale 契约证明 Pull Request、崩溃、数据损坏和安全 issue 不会被时间规则误关；未触发任何
  正式 Release，且 #73 只在上述证据可读后按替代关系关闭。
