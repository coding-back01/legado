## ADDED Requirements

### Requirement: Android lint 可见 issue 必须清零
系统 MUST 以启用依赖检查的 `:app:lintAppDebug` 强制重跑报告作为唯一验收事实源，并在治理完成时使报告中的 error、warning 和 hint 数量全部为零。系统 MUST NOT 通过全局关闭 lint ID、建立 lint baseline 或降低检查范围制造零 issue 结果。

#### Scenario: 强制重跑发现既有清单外项目
- **WHEN** Apply 开始时的强制重跑发现参考清单之外的新 error、warning 或 hint
- **THEN** 系统将新 occurrence 纳入同一变更的完整对账和处置范围
- **AND** 在它仍可见时不得完成治理

#### Scenario: 最终报告仍有可见项目
- **WHEN** 最终 lint XML 包含任意 issue 节点
- **THEN** Android 质量门禁失败
- **AND** 系统不得把该变更标记为完成

#### Scenario: 通过全局机制隐藏项目
- **WHEN** 变更引入全局 lint baseline、全局关闭目标 lint ID 或减少 `checkDependencies` 覆盖
- **THEN** Android 质量门禁失败
- **AND** 即使报告显示零 issue 也不得视为完成

### Requirement: 全部 lint 处置必须在单一变更的一次 Apply 中闭环
系统 MUST 使用一个 OpenSpec 变更和一次连续 Apply 完成当前 Android lint 清单，内部按证据依赖串行执行可归因批次。Apply MUST 在修改生产代码前完成 SDK、模拟器、Debug 测试设备和事实基线预检；任一必需前置条件缺失时必须在实施前停止。

#### Scenario: Apply 前置条件满足
- **WHEN** 固定 SDK、所需 API 模拟器、授权的 Debug 测试设备和强制 lint 基线均已确认可用
- **THEN** 系统在同一次 Apply 中按任务顺序处理全部行为、资源、hint、版本提示和持续门禁
- **AND** 不为兼容域另建 OpenSpec 变更

#### Scenario: Apply 前置条件缺失
- **WHEN** 必需的模拟器、授权 Debug 测试设备或可复现 lint 基线任一不可用
- **THEN** 系统在修改生产代码前停止并记录受阻项
- **AND** 不把部分准备工作描述为一次性治理已经开始或完成

#### Scenario: 内部批次验证失败
- **WHEN** 任一内部批次出现测试失败、视觉回归、兼容性回归或 occurrence 对账不守恒
- **THEN** 系统立即停止后续批次并保留可审计失败证据
- **AND** 不跳过失败批次继续制造最终零 issue 报告

### Requirement: 每个 warning 与 hint 必须有可审计处置
系统 MUST 将强制基线中的每个 warning 和 hint 记录为 `FIXED` 或 `SUPPRESSED_WITH_REASON`。局部抑制 MUST 指向精确代码、资源、布局、版本坐标或二进制资源路径，并记录保留理由、验证证据和重新启动条件；治理完成时不得残留 `DEFERRED` 或 `PENDING_REVIEW`。

#### Scenario: 建议修改能够证明行为等价
- **WHEN** 聚焦测试、截图或运行时证据证明建议修改保持既有行为
- **THEN** 系统实施修改并把 occurrence 记录为 `FIXED`

#### Scenario: 兼容行为必须保留
- **WHEN** occurrence 对应最低 API、旧偏好、交互反馈、资源语义、旧式规则解析语义或独立依赖治理要求
- **THEN** 系统只在该精确位置添加带理由的局部抑制
- **AND** 把 occurrence 记录为 `SUPPRESSED_WITH_REASON`

#### Scenario: 版本提示已有独立更新渠道
- **WHEN** 版本提示对应由 Dependabot 管理或带兼容性固定说明的具体坐标
- **THEN** 系统不得为清除提示而升级依赖
- **AND** 只允许在对应版本声明处记录精确抑制及重新评估条件

### Requirement: lint 清零必须保持兼容敏感行为
系统 MUST 保持最低 API 21、书源和订阅源规则、Cookie 与 URL 处理、旧 `launcherIcon` 偏好、Android 16 以下方向继承、API 23 及以上 RSS 点击反馈、launcher alias、通知和动态快捷方式行为。任何运行时改动 MUST 具备与其风险相匹配的自动化、模拟器或授权 Debug 真机证据。

#### Scenario: 旧式空白裁剪
- **WHEN** 规则、URL、Cookie、章节名或文件内容包含大于 U+0020 的 Unicode 空白字符
- **THEN** 清理 `TrimLambda` 后的行为与原有 `trim { it <= ' ' }` 保持一致

#### Scenario: 恢复旧图标偏好
- **WHEN** 用户已有字符串形式的 `launcherIcon` 偏好或图标选择对话框发生配置重建
- **THEN** 系统仍能选择相同 launcher alias 并恢复对话框状态
- **AND** 不依赖运行时资源名称反射

#### Scenario: 普通启动应用
- **WHEN** 用户通过非动态快捷方式入口启动书架、阅读或朗读页面
- **THEN** 系统不得报告任何动态快捷方式被使用

#### Scenario: 通过动态快捷方式启动
- **WHEN** 用户实际使用书架、上次阅读或朗读动态快捷方式并到达对应入口
- **THEN** 系统只报告对应 shortcut ID 的一次有效使用

#### Scenario: 保留方向与点击反馈
- **WHEN** 用户在 Android 16 以下进入九个继承前页方向的页面，或在 API 23 及以上点击 RSS 项
- **THEN** 页面方向与 RSS ripple 行为保持治理前语义

### Requirement: 视觉与设备证据必须可重复且保护用户数据
系统 MUST 使用固定 API、主题、密度、窗口模式和无个人内容夹具生成可重复视觉证据。版本化 golden 只能通过显式更新流程修改，差异必须可审查；真机验证只能针对 `io.legado.app.debug`，不得读取、清理或改变正式版个人数据。

#### Scenario: 布局或图片资源发生变化
- **WHEN** 变更触及 Overdraw、位图密度、矢量路径、漫画菜单、文件路径控件或 RSS 前景
- **THEN** 系统在批准的 API 21、23、36、日夜主题、手机或平板、旋转及适用密度矩阵中生成对比证据
- **AND** 未经显式接受的视觉、位置、点击区域或像素差异必须阻止对应批次

#### Scenario: 更新 golden
- **WHEN** 维护者显式批准新的预期渲染结果
- **THEN** 系统通过独立更新命令改写最小必要 golden
- **AND** CI 不得自动接受或覆盖 golden 差异

#### Scenario: 执行真机验证
- **WHEN** 系统验证 launcher、通知、动态快捷方式或厂商旋转行为
- **THEN** 只使用可丢弃或明确授权的 Debug 测试设备和无个人内容夹具
- **AND** 原始截图、UI hierarchy 与设备日志不得进入 Git

### Requirement: CI 必须持续拒绝任何 Android lint issue
系统 MUST 在 Android lint 完成后解析预期 XML 报告并断言 issue 数量为零。报告缺失、格式错误、解析失败或出现任意新 error、warning、hint 时，稳定聚合检查必须失败；完整报告仍须按现有策略上传供审计。

#### Scenario: 报告有效且没有 issue
- **WHEN** lint XML 存在、可解析且不包含 issue 节点
- **THEN** 零 issue 断言通过
- **AND** 工作流继续执行其余必需检查

#### Scenario: 报告缺失或不可解析
- **WHEN** lint XML 未生成、路径漂移或 XML 解析失败
- **THEN** 零 issue 断言失败
- **AND** 系统不得把缺少证据解释为零 issue

#### Scenario: 新增任意严重级别项目
- **WHEN** lint XML 包含此前未出现或重新出现的任意 issue
- **THEN** 稳定聚合检查失败并保留完整报告 artifact
- **AND** 该项目必须经过新的精确审查后才能恢复绿色
