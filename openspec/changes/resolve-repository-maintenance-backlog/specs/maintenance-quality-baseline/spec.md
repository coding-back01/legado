## ADDED Requirements

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
