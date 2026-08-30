# fork-distribution-identity Specification

## Purpose

本能力用于让独立签名的个人 fork 在应用、仓库和发布渠道中使用一致且不误导的身份，并为稳定版更新、上游归属、失效链接和历史 `releaseA` 数据建立兼容边界。

## Requirements

### Requirement: 用户可见身份必须区分当前 fork 与原项目
应用和仓库文档 MUST 明确说明当前构建是 `coding-back01/legado` 独立维护、独立签名的个人衍生版本，应用本身不提供内容，且未获得原项目官方背书。当前维护、Issue、Release 和贡献者入口 MUST 指向本仓库；原作者版权和当前提交历史中的上游贡献者归属 MUST 保留。

#### Scenario: 用户查看项目首页
- **WHEN** 用户打开中文或英文项目首页
- **THEN** 页面能够区分当前 fork 的维护与发布入口和上游资源
- **AND** 明确独立签名、有限设备验证及应用不提供内容

#### Scenario: 用户查看贡献者
- **WHEN** 用户从应用或仓库打开贡献者入口
- **THEN** 入口展示当前仓库提交历史中的贡献者
- **AND** 文案说明其中包含原项目与本分支贡献者

### Requirement: 当前功能链接与历史来源链接必须分类治理
系统 MUST 将当前维护、下载、Issue、源码和用户操作入口指向本 fork；失效的远程图片或运行时图标 MUST 使用仓库内资产、内置占位或空值降级。历史 issue/PR、版权、仍有效的上游依赖和社区资源 MUST 保留其真实来源并明确标为上游，不得进行全局域名替换。

#### Scenario: 当前操作入口仍指向撤空仓库
- **WHEN** 用户点击更新、分享下载、当前源码、Issue 或仓库主页入口
- **THEN** 系统不得把用户发送到已撤空的旧 Release、Wiki、Discussion 或源码路径
- **AND** 入口必须指向有效的本 fork 目标或本地说明

#### Scenario: 远程展示资源失效
- **WHEN** 默认 RSS 图标、帮助图片或项目图标的远程地址不可用
- **THEN** 系统使用本地资产、内置占位或明确空值正常展示
- **AND** 核心页面不得因该远程资源失败而失去可用性

#### Scenario: 链接用于历史归属
- **WHEN** 链接记录原 issue、PR、作者、依赖来源或仍有效的上游社区
- **THEN** 系统保留真实上游目标或改用可核验的不可变历史引用
- **AND** 不将历史来源伪装为本 fork 原创内容

### Requirement: 应用内更新只提供当前 fork 的普通稳定版
应用内更新器 MUST 只查询 `coding-back01/legado` 的 Latest Release，并且只能接受名称与 Release 完整 tag 精确对应的唯一普通版 APK。更新器 MUST 拒绝 `releaseA`、beta、其他 APK、重复候选、缺失候选、错误 MIME、未完成上传和无法验证的版本格式。

#### Scenario: Latest 包含普通版和 releaseA
- **WHEN** Latest Release 同时包含 `_release.apk` 与 `_releaseA.apk`
- **THEN** 更新器只选择与 tag 精确对应的 `_release.apk`
- **AND** 不受资产顺序或相同创建时间影响

#### Scenario: 普通版候选缺失或重复
- **WHEN** Latest Release 不含普通版候选或含有多个符合条件的普通版候选
- **THEN** 更新器返回可区分的发布数据错误
- **AND** 不猜测或下载任意 APK

#### Scenario: 资产状态或格式无效
- **WHEN** 候选资产的 MIME、上传状态、文件名或 Release tag 不符合受支持契约
- **THEN** 更新器拒绝该资产
- **AND** 不把格式异常描述为“已经是最新版本”

### Requirement: 更新版本比较必须使用完整且可验证的版本
更新器 MUST 使用完整 Release tag 与当前正式版安装版本进行确定性比较，不得截断小时位或依赖普通字符串偶然排序。同一天更晚版本、跨日和跨年版本 MUST 被正确识别；相同或更旧版本 MUST 报告已是最新版本。带 `debug` 后缀的 Debug 安装不属于正式更新契约，MUST 返回明确的不支持结果且不得提供正式 APK 下载。

#### Scenario: 同一天存在更晚版本
- **WHEN** 当前安装版本早于同一天发布的完整远端版本
- **THEN** 更新器报告存在新版本

#### Scenario: 当前版本相同或更新
- **WHEN** 当前安装版本等于或晚于远端完整版本
- **THEN** 更新器报告已是最新版本
- **AND** 不显示下载提示

#### Scenario: 版本格式无法验证
- **WHEN** 当前版本或 Release tag 不符合受支持的版本契约
- **THEN** 更新器返回明确的版本数据错误
- **AND** 不通过截断或字典序猜测结果

#### Scenario: Debug 安装手动检查更新
- **WHEN** 当前安装版本带有 `debug` 后缀
- **THEN** 更新器说明 Debug 构建不参与正式更新
- **AND** 不比较或提供正式 APK 下载

### Requirement: 旧更新通道偏好不得改变稳定版选择
面向用户的更新通道设置 MUST 被移除。备份或旧安装中保存的 `default_version`、`official_version`、`beta_release_version`、`beta_releaseA_version` 及未知值 MUST 被永久归一为普通稳定版行为，不得重新暴露 beta 或 `releaseA` 更新路径。

#### Scenario: 恢复含旧通道值的备份
- **WHEN** 用户恢复包含任一旧更新通道值的备份
- **THEN** 应用仍只检查当前 fork 的普通稳定版
- **AND** 设置界面不出现空白、失效或已退役通道

#### Scenario: 用户从旧版本升级
- **WHEN** 旧安装保留 beta 或 `releaseA` 更新偏好后升级到治理版本
- **THEN** 更新检查忽略旧值并使用普通稳定版契约

### Requirement: 未来 releaseA 分发必须停止且历史数据必须保留
正式发布流程 MUST NOT 再构建或发布新的 `releaseA` APK。系统 MUST 保留现有历史 Release 中的 `releaseA` 资产、运行时兼容代码、`io.legado.app.releaseA` 包名认知和设备上的独立数据；不得卸载、清除或自动迁移该数据，也不得让普通版冒充 `releaseA` 的原地升级。

#### Scenario: 创建新的正式 Release
- **WHEN** 治理完成后触发正式发布
- **THEN** Release 只包含普通版 APK
- **AND** 不生成或上传新的 `releaseA` APK

#### Scenario: 用户仍安装历史 releaseA
- **WHEN** 设备存在 `io.legado.app.releaseA` 及其独立数据
- **THEN** 普通版安装不会删除、覆盖或接管该数据
- **AND** 历史 Release 资产仍可用于审计和重装

### Requirement: 失效历史资料必须有可审计替代
应用内更新日志和帮助中仍有价值但远端已删除的资料 MUST 恢复为仓库内归档或可核验的不可变引用；无法恢复的内容 MUST 用明确说明替代失效链接，不得伪造当前仓库中不存在的 Wiki、Discussion 或历史分支。

#### Scenario: 历史分支链接返回不存在
- **WHEN** 更新日志引用的历史远端分支已经删除
- **THEN** 系统使用本地归档、不可变提交或明确的缺失说明
- **AND** 不把链接机械改到不存在的本 fork 同名路径
