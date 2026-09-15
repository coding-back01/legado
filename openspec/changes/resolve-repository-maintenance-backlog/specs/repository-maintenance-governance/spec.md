## MODIFIED Requirements

### Requirement: Dependabot 队列必须受限且按风险分流
系统 MUST 启用 Dependabot Security Updates，让可自动解析的安全更新及时创建独立 Pull Request 且永不自动合并；普通依赖更新 MUST 按月分组，并使所有 ecosystem/update entry 配置的普通更新 `open-pull-requests-limit` 之和不超过 5。带兼容性注释的固定依赖 MUST 被明确忽略，AGP、Gradle、Kotlin 和 KSP 的更新 MUST 与普通依赖分离。当高危或严重传递依赖已有兼容安全版本但 Dependabot 报告无法自动更新时，系统 MUST 从最新受保护分支建立独立人工修复，限制依赖图和锁文件漂移，并保持相同的评审与门禁要求；自动化失败不得被解释为没有可执行修复。

#### Scenario: 发现安全更新
- **WHEN** Dependabot 识别出受支持且可自动解析的安全修复
- **THEN** 系统创建可独立审查的安全更新 Pull Request
- **AND** 该 Pull Request 不会自动合并

#### Scenario: 传递安全依赖自动更新失败
- **WHEN** 高危或严重传递依赖已有满足上游版本约束的安全版本，但 Dependabot 报告 `security_update_not_possible` 或未生成 Pull Request
- **THEN** 维护者从最新受保护分支建立只包含必要依赖声明和锁文件变化的人工修复
- **AND** 在依赖图、安全告警和适用质量门禁通过前，该告警继续阻止合并与正式发布

#### Scenario: 安全版本确实不可解析
- **WHEN** 包仓库、上游版本约束或平台兼容性证明当前不存在可安装的安全组合
- **THEN** 系统记录精确阻断证据和重新检查条件
- **AND** 不通过忽略告警、降低严重级别或无关的大范围升级制造已修复结论

#### Scenario: 到达普通更新周期
- **WHEN** 普通依赖到达月度检查周期
- **THEN** 系统按配置分组创建更新 Pull Request
- **AND** 跨全部生态同时打开的普通更新 Pull Request 配置上限不超过 5 个

#### Scenario: 更新命中固定依赖
- **WHEN** 候选版本涉及带有最低 Android 兼容性说明的固定依赖
- **THEN** Dependabot 不自动创建突破该固定版本的普通更新
- **AND** 该升级只能通过独立兼容性变更评估

## ADDED Requirements

### Requirement: 替代依赖变更必须精确闭环原机器人队列
从最新受保护分支实施的替代依赖变更 MUST 在自身全部必需检查成功并进入 `master` 后，重新读取被替代机器人 Pull Request 的编号、状态、head ref 和 head SHA，只关闭已明确列入批准清单且内容已被替代的对象。系统 MUST 在关闭记录中关联替代变更，并仅按重新核对的精确 ref/SHA 删除对应远端分支；不得使用分支名前缀、glob 或过期快照扩大清理范围。

#### Scenario: 替代变更尚未合并
- **WHEN** 新的安全、Android 工具链或 Web 依赖替代变更尚未进入 `master`，或任一必需检查失败
- **THEN** 系统保留原机器人 Pull Request 和对应分支
- **AND** 不把实现分支上的局部成功视为已经替代

#### Scenario: 替代变更已经通过并合并
- **WHEN** 替代变更已从最新 `master` 完成评审、通过全部必需检查并合并
- **THEN** 系统按执行时重新读取的精确编号、head ref 和 SHA 关闭批准清单中的原机器人 Pull Request
- **AND** 关闭记录明确关联替代变更后才删除对应远端分支

#### Scenario: 清理前对象发生漂移
- **WHEN** 任一 Pull Request 的状态、head ref 或 head SHA 与批准清单不一致
- **THEN** 系统停止该对象的关闭和分支删除
- **AND** 未漂移对象也只能继续执行各自已经满足的精确操作

### Requirement: 本地工作树与历史分支清理必须可证明安全
维护闭环中的本地清理 MUST 先确认主工作树与附加工作树均无未提交或 stash 内容，并逐个证明目标分支已被当前 `master` 包含，或其唯一未合并内容已由可定位的后续提交和归档证据完整取代。系统 MUST 先移除占用目标分支的附加工作树，再按精确分支名删除本地分支；无法完成任一证明的对象必须保留。

#### Scenario: 附加工作树干净且分支已合并
- **WHEN** 附加工作树无未提交内容，其分支相对当前 `master` 没有独立提交
- **THEN** 系统可以先移除该附加工作树，再删除对应本地分支
- **AND** 清理后主工作树和 `master` 跟踪状态保持不变

#### Scenario: 历史分支包含未合并提交
- **WHEN** 历史分支仍包含 `master` 不可达的提交
- **THEN** 系统必须比较其内容与后续提交、归档证据或明确替代记录
- **AND** 只有能够证明内容已被完整取代时才可按精确名称删除

#### Scenario: 工作树或 stash 含有内容
- **WHEN** 任一目标工作树存在未提交改动、未跟踪文件或相关 stash
- **THEN** 系统停止该工作树和分支的清理
- **AND** 不使用强制删除、重置或清空操作绕过保护

### Requirement: 维护清理不得改变发布审计与仓库保护状态
依赖和 Git 对象清理 MUST NOT 删除、公开、改写或设为 Latest 的失败及过期草稿 Release，MUST NOT 触发正式发布，并且 MUST 保持 `master` ruleset、CodeQL、Secret Scanning、Dependabot alerts、自动删除已合并分支和其他既有安全设置不弱于清理前状态。

#### Scenario: 清单包含历史草稿 Release
- **WHEN** 维护者核对到失败或已经被后续正式版取代的草稿 Release
- **THEN** 系统将其保留为不可见审计记录
- **AND** 不把仓库卫生清理授权扩展为删除或公开 Release

#### Scenario: 清理完成后复核仓库设置
- **WHEN** 所有批准的 Pull Request 和分支操作完成
- **THEN** 系统读回默认分支、ruleset 和安全能力状态并确认没有被削弱
- **AND** 任一意外漂移都使维护闭环失败
