# repository-maintenance-governance Specification

## Purpose

本能力用于约束个人稳定 fork 的依赖更新、安全告警、反馈入口、远端清理和分支保护，使仓库能够低维护成本运行，同时不制造社区继任或自动同步上游的承诺。

## Requirements

### Requirement: Dependabot 队列必须受限且按风险分流
系统 MUST 启用 Dependabot Security Updates，让安全更新及时创建独立 Pull Request 且永不自动合并；普通依赖更新 MUST 按月分组，并使所有 ecosystem/update entry 配置的普通更新 `open-pull-requests-limit` 之和不超过 5。带兼容性注释的固定依赖 MUST 被明确忽略，AGP、Gradle、Kotlin 和 KSP 的更新 MUST 与普通依赖分离。

#### Scenario: 发现安全更新
- **WHEN** Dependabot 识别出受支持的安全修复
- **THEN** 系统创建可独立审查的安全更新 Pull Request
- **AND** 该 Pull Request 不会自动合并

#### Scenario: 到达普通更新周期
- **WHEN** 普通依赖到达月度检查周期
- **THEN** 系统按配置分组创建更新 Pull Request
- **AND** 跨全部生态同时打开的普通更新 Pull Request 配置上限不超过 5 个

#### Scenario: 更新命中固定依赖
- **WHEN** 候选版本涉及带有最低 Android 兼容性说明的固定依赖
- **THEN** Dependabot 不自动创建突破该固定版本的普通更新
- **AND** 该升级只能通过独立兼容性变更评估

### Requirement: 遗留机器人队列必须在新策略生效后精确清理
系统 MUST 先合并并确认新的 Dependabot 策略，再关闭执行前重新核对且明确列入任务的遗留机器人 Pull Request，并按核对时记录的精确 head ref/SHA 删除其远端分支。已合并的遗留分支只能按同一精确清单删除，不得使用 `codex/*` 或其他 glob；未列入清单的新对象不得被批量操作。

#### Scenario: 新策略尚未生效
- **WHEN** 新 Dependabot 配置尚未合并或无法确认有效
- **THEN** 系统不得批量关闭现有机器人 Pull Request

#### Scenario: 执行遗留队列清理
- **WHEN** 新策略已经生效且维护者重新核对了 Pull Request 类型、状态和分支
- **THEN** 系统只关闭任务清单中的遗留机器人 Pull Request
- **AND** 只删除清单中的对应分支和已合并遗留分支

#### Scenario: 清理期间出现新对象
- **WHEN** 执行时发现未列入批准清单的新 Pull Request 或分支
- **THEN** 系统保留该对象
- **AND** 不把既有授权扩展到该对象

### Requirement: GitHub 原生安全能力必须形成分级门禁
系统 MUST 启用仓库可用的 Dependabot alerts、Dependabot Security Updates、Secret Scanning、私有漏洞报告和 CodeQL，并在各能力启用或完成首次扫描后立即读取可用告警。扫描成功不得被解释为零告警；高危和严重问题阻止后续合并与正式发布，中危必须记录修复或接受理由，低危进入维护清单。

#### Scenario: 发现高危或严重问题
- **WHEN** 受信任的安全告警报告高危或严重问题且尚未解决
- **THEN** 后续治理 Pull Request 不得合并
- **AND** 正式发布保持冻结

#### Scenario: 发现中危问题
- **WHEN** 安全告警报告中危问题
- **THEN** 维护者必须记录修复、替换、禁用功能或接受风险的理由
- **AND** 未记录判断前不得完成治理

#### Scenario: 发现低危问题
- **WHEN** 安全告警报告低危问题
- **THEN** 系统将其登记到维护清单
- **AND** 该问题本身不阻止正式发布

#### Scenario: 安全能力刚启用
- **WHEN** Dependabot、Secret Scanning 或 CodeQL 首次变为可用或完成分析
- **THEN** 系统立即读取其告警或分析结果并按严重级别处置
- **AND** 在读取失败时将状态记录为受阻而不是零告警

### Requirement: 公开反馈入口必须匹配个人 fork 边界
仓库 MUST 只提供正式版 bug 报告和私有安全报告入口，不提供功能请求表单，也不承诺响应时限。公开 bug 表单 MUST 提醒并要求报告者移除 Cookie、Token、账号、私有 URL、备份内容和个人文件路径，疑似安全问题 MUST 转向私有渠道。

#### Scenario: 用户准备提交普通 bug
- **WHEN** 用户打开公开 bug 表单
- **THEN** 表单要求版本、设备、复现步骤和脱敏确认
- **AND** 明确功能请求默认不受理

#### Scenario: 报告可能包含安全或隐私内容
- **WHEN** 用户准备报告凭据泄露、安全缺陷或包含私密数据的问题
- **THEN** 表单引导其使用私有漏洞报告
- **AND** 不要求在公开 issue 中粘贴敏感内容

### Requirement: Stale 自动关闭只能用于长期缺少信息的问题
系统 MUST 禁止通用 stale 自动关闭。只有带 `needs-info` 标记且连续超过 30 天未补充资料的问题可以自动关闭；崩溃、数据损坏和安全问题不得仅因时间流逝自动关闭。

#### Scenario: needs-info 长期无响应
- **WHEN** issue 已标记 `needs-info` 且超过 30 天没有所需补充
- **THEN** 系统可以自动关闭该 issue
- **AND** 关闭说明必须指出可在补齐资料后重新提交或恢复

#### Scenario: 严重问题长期未更新
- **WHEN** issue 涉及崩溃、数据损坏或安全问题但没有近期活动
- **THEN** 系统不得仅因无活动自动关闭

### Requirement: 必需检查必须在可用且绿色后加入分支保护
系统 MUST 先创建并在不同路径类型上成功运行稳定聚合检查，再将其加入 `master` 必需检查。修改前 MUST 记录精确 ruleset ID 和完整配置，只能做不削弱既有条件、规则或 bypass 的最小更新，并在修改后读回核对。合并后自动删除短期分支属于独立仓库设置；分支保护不得引用不存在、名称漂移或持续失败的检查。

#### Scenario: 检查尚不存在或未通过
- **WHEN** 预期检查尚未在目标提交上产生成功结果
- **THEN** 系统不得把该检查加入 `master` 必需检查

#### Scenario: 检查已经稳定通过
- **WHEN** 预期聚合检查已在 Android、网页端和文档类改动的目标提交上稳定存在且成功
- **THEN** 系统可以将其加入 `master` ruleset
- **AND** 后续不满足该检查的 Pull Request 无法合并

#### Scenario: 更新现有 ruleset
- **WHEN** 维护者准备将稳定检查加入 `master` ruleset
- **THEN** 系统按记录的精确 ruleset ID 做最小修改并保留其他全部规则
- **AND** 读回结果与前置快照的差异只包含已批准的新增门禁

### Requirement: 仓库不得自动继承其他项目的变更队列
系统 MUST 删除失效的上游自动同步流程，不建立跨仓库自动合并或自定义月度巡检报告。来自 `zj970/legado`、`LegadoTeam/legado` 或其他项目的补丁只能在明确需要时逐补丁调查，并通过新的 Explore/OpenSpec 决策进入本 fork。

#### Scenario: 其他仓库出现新提交或功能请求
- **WHEN** 继任或相关仓库出现普通功能、厂商专属适配或维护提交
- **THEN** 本仓库不会自动抓取、合并或创建候选 Pull Request
- **AND** 只有维护者明确提出需要后才开始独立评估

#### Scenario: 现有自动同步目标失效
- **WHEN** 自动同步工作流仍引用已撤空仓库或不存在的分支
- **THEN** 系统移除该工作流
- **AND** 不把失败目标机械改成未经批准的新上游

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
