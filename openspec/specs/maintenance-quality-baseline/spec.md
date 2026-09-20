# maintenance-quality-baseline Specification

## Purpose

本能力用于为 Android、网页端和 OpenSpec 建立可重复、可审计且失败即停止的维护质量基线，使遗留问题被逐类处置，并防止新问题在缺少证据时进入正式发布。

## Requirements

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

### Requirement: Android 构建工具链必须作为兼容组合迁移
Gradle 和 Android Gradle Plugin 的不兼容大版本更新 MUST 在同一工具链批次中联合评估，并与 JDK 17、既有 Kotlin/KSP 组合、全部 Android 插件声明和构建脚本保持兼容。候选组合 MUST 使 `app`、`modules/book` 和 `modules/rhino` 的配置、编译、单元测试、lint、Debug 构建及 Android CodeQL 实际构建成功，且 wrapper 脚本和二进制来源可验证；不得通过降低检查范围、停用插件或顺带升级无关固定依赖绕过失败。

#### Scenario: 单独升级 Gradle 导致 AGP 不兼容
- **WHEN** 新 Gradle 删除当前 AGP 依赖的内部 API，导致配置阶段或插件应用失败
- **THEN** 系统不得合并单独的 Gradle 更新
- **AND** 必须改用经过验证的 Gradle 与 AGP 兼容组合

#### Scenario: 单独升级 AGP 要求更高 Gradle
- **WHEN** 新 AGP 的最低 Gradle 版本高于仓库当前 wrapper
- **THEN** 系统不得合并单独的 AGP 更新
- **AND** 必须在同一批次迁移 wrapper、插件 DSL 和受影响构建脚本

#### Scenario: 工具链组合通过完整验证
- **WHEN** 候选 Gradle、AGP、JDK、Kotlin 和 KSP 组合完成迁移
- **THEN** 三个 Android 模块的适用配置、代码生成、单元测试、lint、Debug 构建和 CodeQL 构建全部成功
- **AND** 最低 API 21、目标 API 36、Android lint 零 issue 和既有兼容接口保持不变

#### Scenario: wrapper 文件不符合仓库文本合同
- **WHEN** wrapper 更新引入尾随空格、异常换行、不可验证下载地址或不匹配的 wrapper 二进制
- **THEN** 仓库检查失败
- **AND** 系统不得以 wrapper 由工具生成作为忽略理由

### Requirement: Web 大版本依赖必须按兼容关系分批迁移
Web 生产和开发依赖的大版本更新 MUST 从最新 `master` 重建，并在一个连续 Apply 中按运行时框架、构建工具、类型系统和 lint 工具的兼容关系形成可归因批次。每个批次 MUST 运行章节 HTML 聚焦测试、静态链接安全测试、类型检查、ESLint 和生产构建；触及构建输出的批次还 MUST 同步 Android 内置 Web 静态资产并证明再次构建无漂移。系统 MUST NOT 直接合并落后且失败的聚合机器人分支，或用一次全量锁文件刷新隐藏具体失败来源。

#### Scenario: TypeScript 命令行语义发生变化
- **WHEN** 新 TypeScript 因配置文件和显式输入组合等语义变化产生 `TS5112` 或等价错误
- **THEN** 系统在保持测试覆盖和严格类型检查的前提下迁移测试命令或专用配置
- **AND** 不通过删除测试、关闭严格检查或忽略退出码恢复绿色

#### Scenario: 运行时框架或路由大版本升级
- **WHEN** Vue、Pinia、Vue Router、Element Plus、VueUse 或其他运行时依赖发生大版本变化
- **THEN** 系统验证现有书架、书源编辑、帮助链接及构建入口仍保持既有可观察行为
- **AND** 不改变静态链接 allowlist、动态 URL 禁止规则或新窗口隔离合同

#### Scenario: Web 构建改变内置静态资产
- **WHEN** 生产构建生成新的哈希文件、清单、脚本或样式
- **THEN** 系统只提交由批准依赖和源码确定生成的完整资产集合
- **AND** 在相同固定 Node.js 与 pnpm 环境再次构建后，版本化资产目录保持干净

#### Scenario: 内部依赖批次失败
- **WHEN** 任一批次的安装、聚焦测试、类型检查、ESLint、构建或资产一致性检查失败
- **THEN** 系统停止后续依赖批次并保留可归因失败证据
- **AND** 不继续进行机器人 Pull Request 或分支清理

### Requirement: 维护欠账必须在单一变更的一次 Apply 中按门禁顺序闭环
当前安全依赖、Android 工具链、Web 依赖和批准 Git 清理 MUST 使用一个 OpenSpec 变更及一次连续 Apply 完成，内部顺序 MUST 为事实基线与精确对象快照、高危安全修复、Android 工具链迁移、Web 依赖迁移、全量验证、替代 Pull Request 合并后远端闭环和本地清理。任一阶段失败 MUST 停止所有依赖该阶段的后续操作；远端关闭、分支删除和工作树移除只能发生在替代实现已经进入 `master` 之后。

#### Scenario: Apply 前置快照完整
- **WHEN** 最新 `master`、安全告警、四个机器人 Pull Request、目标分支 SHA、工作树、stash、草稿 Release 和仓库保护状态均已重新读取
- **THEN** 系统可以按规定顺序开始安全和依赖迁移
- **AND** 后续清理只能使用该次 Apply 中再次核验的精确对象

#### Scenario: 高危安全修复未完成
- **WHEN** 高危传递依赖仍处于受影响版本，或修复后的依赖图和 Web 门禁未通过
- **THEN** 系统停止 Android、Web 普通依赖迁移及全部清理操作
- **AND** 不把告警接受、机器人失败或开发依赖范围作为继续条件

#### Scenario: 全部实现门禁成功但尚未合并
- **WHEN** 实现分支上的全部 Android、Web、CodeQL、OpenSpec 和仓库检查成功，但替代 Pull Request 尚未进入 `master`
- **THEN** 系统保持 OpenSpec 变更活跃并保留原机器人队列和 Git 对象
- **AND** 不提前执行只允许合并后进行的外部清理

#### Scenario: 替代变更与清理全部完成
- **WHEN** 替代实现已进入 `master`、合并后门禁成功、安全告警满足分级要求且批准对象已精确清理
- **THEN** 系统记录最终证据并允许归档该 OpenSpec
- **AND** 主工作树干净且本地 `master` 与 `origin/master` 一致
