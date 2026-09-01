## ADDED Requirements

### Requirement: GitHub Actions 大版本升级必须逐项审查并替代失效队列
系统 MUST 从最新 `master` 评估 GitHub Actions 大版本升级，并为目标版本的运行时、runner
下限、凭据、缓存、artifact、权限和事件筛选语义保存可验证的判断。目标组合 MUST 为
`actions/checkout@v7`、`actions/setup-java@v6`、`gradle/actions/setup-gradle@v6`、
`actions/upload-artifact@v7`、`actions/download-artifact@v8`、`actions/stale@v11`、
`pnpm/action-setup@v6` 和 `actions/setup-node@v7`；CodeQL、JDK、Node.js、pnpm、Gradle、
AGP 和应用依赖不得借此变更升级。落后当前 `master` 且无法通过现行仓库契约的机器人 Pull
Request MUST NOT 直接合并，只能在替代 Pull Request 完成评审与全部必需检查后按可追踪的
替代关系关闭。

#### Scenario: 候选分支落后且契约失败
- **WHEN** GitHub Actions 更新 Pull Request 已落后当前 `master`，并因仍要求旧版本的精确
  仓库契约而失败
- **THEN** 系统保留该失败作为真实兼容性 RED，并从最新 `master` 建立替代变更
- **AND** 不通过删除、跳过或模糊契约断言使原 Pull Request 可合并

#### Scenario: 任一大版本缺少兼容证据
- **WHEN** 目标 Action 的运行时、runner 下限、权限、缓存、凭据或 artifact 行为尚未完成审查
- **THEN** 系统不得批准或合并该升级
- **AND** 已完成审查的其他 Action 不得被用于推断该目标兼容

#### Scenario: 替代 Pull Request 通过完整门禁
- **WHEN** 从最新 `master` 创建的替代 Pull Request 已通过人工评审和全部必需检查
- **THEN** 系统可以关闭被替代的机器人 Pull Request，并在关闭记录中关联替代 Pull Request
- **AND** 该动作不得被解释为允许普通依赖自动合并

### Requirement: Action 升级必须保持 stale 的问题范围和最小权限
升级后的 stale 工作流 MUST 继续只处理带 `needs-info` 标签且超过 30 天未补充资料的 issue，
MUST 禁止对 Pull Request 执行 stale 或 close，并 MUST 继续豁免崩溃、数据损坏和安全 issue。
工作流权限 MUST 限定为读取仓库内容和写入 issue，不得新增 Pull Request、Release、Actions
或其他写权限。

#### Scenario: Pull Request 长期无活动
- **WHEN** 任意 Pull Request 超过 stale 时间阈值且没有新活动
- **THEN** stale 工作流不得标记或关闭该 Pull Request

#### Scenario: 严重 issue 带有豁免标签
- **WHEN** issue 带有 `crash`、`data-loss` 或 `security` 标签
- **THEN** stale 工作流不得仅因时间流逝标记或关闭该 issue

#### Scenario: needs-info issue 长期未补充
- **WHEN** issue 带有 `needs-info` 标签、没有豁免标签且超过 30 天未补充资料
- **THEN** stale 工作流可以按既有消息和恢复说明关闭该 issue
- **AND** 执行期间只使用 issue 写权限和内容读权限
