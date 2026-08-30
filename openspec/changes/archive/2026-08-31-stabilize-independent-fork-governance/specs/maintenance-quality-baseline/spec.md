## Purpose

本能力用于为 Android、网页端和 OpenSpec 建立可重复、可审计且失败即停止的维护质量基线，使遗留问题被逐类处置，并防止新问题在缺少证据时进入正式发布。

## ADDED Requirements

### Requirement: 阻断级静态检查错误必须清零
系统 MUST 在治理完成时使 `:app:lintAppDebug` 和网页端 ESLint 均以成功状态退出，且错误数量均为零；不得通过全局关闭检查或建立覆盖全部遗留项的 baseline 把真实错误隐藏为成功。

#### Scenario: Android lint 仍有错误
- **WHEN** `:app:lintAppDebug` 报告任意 error 或返回非零状态
- **THEN** Android 质量门禁失败
- **AND** 系统不得将该提交标记为可正式发布

#### Scenario: 网页端 ESLint 仍有错误
- **WHEN** 网页端 ESLint 报告任意 error 或返回非零状态
- **THEN** 网页端质量门禁失败
- **AND** 系统不得将该提交标记为可正式发布

### Requirement: Warning 必须采用三态账本
系统 MUST 对治理参考运行中出现的每个 lint ID 记录原始数量、当前数量、风险判断以及 `FIXED`、`SUPPRESSED_WITH_REASON`、`DEFERRED` 各状态数量；同一 ID 可以包含多种最终处置。活跃实施期允许将尚未审查的 ID 或范围标记为阻断完成的 `PENDING_REVIEW`。`FIXED` MUST 记录批次、Pull Request 和验证证据；`SUPPRESSED_WITH_REASON` MUST 记录精确位置和局部理由；`DEFERRED` MUST 记录精确范围、风险、延期原因和重新启动条件。完整 occurrence 对账 MUST 保存在 CI artifact，而不是全部写入版本化账本。

#### Scenario: Warning 已安全修复
- **WHEN** 某 lint ID 的所有目标 occurrence 均通过行为保持和聚焦验证
- **THEN** 维护账本将对应范围标记为 `FIXED`
- **AND** 记录修复后的剩余数量和验证证据

#### Scenario: Warning 是可证明的误报或兼容约束
- **WHEN** 某个 occurrence 无法通过源码调整消除且维护者能够证明其为误报或刻意兼容行为
- **THEN** 系统只允许在精确位置进行局部抑制
- **AND** 维护账本将其标记为 `SUPPRESSED_WITH_REASON` 并记录理由

#### Scenario: Warning 无法在本轮安全修复
- **WHEN** 修复某 lint ID 需要高风险行为变化、缺少可靠验证条件或无法排除动态引用
- **THEN** 系统允许将对应范围标记为 `DEFERRED`
- **AND** 记录具体风险、原因和重新启动条件

#### Scenario: 存在未审查的 warning
- **WHEN** 任一参考 lint ID 的三态数量无法与完整 occurrence 报告对账，或仍包含 `PENDING_REVIEW`
- **THEN** 总治理变更不得标记为完成

### Requirement: 高风险 Warning 不得在后续静默复发
系统 MUST 将本轮确认会影响正确性、语言环境、启动安全或 App Bundle 语言行为的 lint ID 纳入阻断策略；至少包括 `IntentWithNullActionLaunch`、`DefaultLocale` 和 `AppBundleLocaleChanges`。系统 MUST NOT 使用覆盖全部项目的 lint baseline 隐藏新增高风险 occurrence。

#### Scenario: 新增高风险 Warning
- **WHEN** Pull Request 新增受阻断策略覆盖的高风险 lint occurrence
- **THEN** 必需检查失败
- **AND** 合并与正式发布均被阻止

#### Scenario: 已记录的局部抑制仍然存在
- **WHEN** 某个高风险 ID 的 occurrence 已按精确位置记录为 `SUPPRESSED_WITH_REASON`
- **THEN** 系统允许该已审查位置继续存在
- **AND** 不得因此放宽同一 ID 在其他位置的检查

### Requirement: 持续验证必须失败即停止
Pull Request 和 `master` 的适用验证 MUST 执行 Android 单元测试、Android lint、Debug 构建、网页端类型检查、网页端 ESLint、网页端构建和固定工具版本的 OpenSpec 严格校验。每个计划加入分支保护的聚合检查 MUST 在任意改动路径上稳定产生结果；Android、Web 等子任务可按路径合法跳过，但启用原生 code-scanning rule 时 CodeQL MUST 在每个 Pull Request 和 `master` 提交上实际运行。任一实际执行的必需命令失败时，聚合检查 MUST 返回失败，不得上传或展示为成功的替代结论。

#### Scenario: 所有适用检查通过
- **WHEN** 变更命中的 Android、网页端、OpenSpec 和仓库配置检查均实际运行并成功
- **THEN** 质量门禁可以报告通过
- **AND** 对应日志和报告保持可审计

#### Scenario: 任一必需命令失败
- **WHEN** 任一适用的必需命令返回非零状态、超时或未产生预期报告
- **THEN** 工作流返回失败
- **AND** 后续步骤不得把该次运行描述为绿色基线

#### Scenario: 改动未命中某个子任务路径
- **WHEN** Pull Request 只修改 Android、网页端或文档中的一类路径
- **THEN** 不适用的子任务可以被明确标记为合法跳过
- **AND** 对应稳定聚合检查仍然产生可用于 ruleset 的成功或失败结果

### Requirement: 质量证据必须区分通过、失败与未运行
系统 MUST 只把实际成功完成的检查描述为通过；失败、环境不可用和未运行 MUST 分别记录。完整 lint occurrence 报告 MUST 作为可下载的 CI 证据保存，版本化维护文档只记录按 lint ID 汇总的基线及精确例外。

#### Scenario: 检查因环境不可用而未运行
- **WHEN** 某项检查因系统镜像、设备、网络或外部服务不可用而无法执行
- **THEN** 交付记录将其标记为未运行或受阻
- **AND** 不得据此推断对应行为已经通过

#### Scenario: 维护者查看 warning 详情
- **WHEN** 维护者需要审计某次 CI 的全部 lint occurrence
- **THEN** 系统提供该次运行生成的完整报告 artifact
- **AND** `docs/maintenance-baseline.md` 提供对应 lint ID 的数量、状态和处置理由

### Requirement: 质量治理不得破坏兼容敏感接口
本能力 MUST 保留最低 API 21、普通正式版包名和既有兼容接口，并且 MUST NOT 为消除静态检查提示而擅自修改 Room schema、书源或订阅源规则、导入 URI、备份格式或带兼容性注释的固定依赖版本。

#### Scenario: 静态检查建议触及兼容边界
- **WHEN** 某项建议修复需要提高最低 SDK、改变持久化格式或突破明确固定的依赖版本
- **THEN** 系统不得把该修改作为机械 warning 清理合并
- **AND** 必须将其延期或建立独立兼容性变更
