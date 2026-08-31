## Purpose

本能力用于保证内置网页端帮助区域只渲染经过审查的固定链接，并通过确定性门禁阻止危险协议、
未知目标或未经设计的动态 URL 进入可点击链接，同时保持现有帮助入口和用户操作方式不变。

## ADDED Requirements

### Requirement: 受管链接必须来自精确静态清单
系统 MUST 只允许帮助区域渲染以下 11 个源码固定目标：

- `/help/#appHelp`
- `/help/#ruleHelp`
- `/help/#jsHelp`
- `/help/#xpathHelp`
- `/help/#regexHelp`
- `/help/#txtTocRuleHelp`
- `/help/#debugHelp`
- `/help/#httpTTSHelp`
- `/help/#webDavBookHelp`
- `/help/#webDavHelp`
- `https://regexr-cn.com/`

目标匹配 MUST 使用完整字符串，系统 MUST NOT 接受大小写、前后空白、编码变体、额外查询、
额外片段或其他协议作为等价目标。

#### Scenario: 用户打开既有帮助链接
- **WHEN** 用户点击清单中的任一同源帮助锚点
- **THEN** 系统在新窗口打开对应的既有帮助目标
- **AND** 链接文字和目标保持当前合同不变

#### Scenario: 用户打开固定外部工具
- **WHEN** 用户点击正则表达式在线验证工具
- **THEN** 系统只打开 `https://regexr-cn.com/`
- **AND** 不接受该域名的 HTTP、子域名或路径变体作为替代目标

#### Scenario: 模板出现未登记的固定目标
- **WHEN** 受管链接被修改为精确清单之外的固定地址
- **THEN** 持续安全门禁失败
- **AND** 该改动不得进入受保护分支

### Requirement: 动态 URL 必须保持禁止
当前受管链接 MUST NOT 从组件属性、用户输入、网络响应、存储数据、规则内容或其他运行时数据
绑定目标地址。未来若需要动态 URL，系统 MUST 先通过独立 OpenSpec 变更定义协议、同源或目标
域名 allowlist、解析失败行为和回归场景，未经该流程不得放宽本要求。

#### Scenario: 模板引入动态链接绑定
- **WHEN** 受管链接出现 `:href`、`v-bind:href` 或等价的运行时目标绑定
- **THEN** 持续安全门禁失败
- **AND** 该改动不得以现有静态链接风险接受理由通过

#### Scenario: 运行时数据准备进入链接目标
- **WHEN** 新需求拟将用户、网络、存储或规则数据用作链接目标
- **THEN** 当前能力拒绝该数据流
- **AND** 维护者必须先完成独立安全设计与评审

### Requirement: 新窗口链接必须隔离来源上下文
所有受管链接在保持新窗口打开行为时 MUST 隔离新页面对原页面的 opener 访问；外部目标还
MUST NOT 接收来源页面信息。合同检查 MUST 覆盖每个受管链接，不得只检查外部目标。

#### Scenario: 新窗口打开同源帮助
- **WHEN** 用户从帮助区域打开任一同源固定链接
- **THEN** 新页面不能通过 opener 控制原页面

#### Scenario: 新窗口打开外部工具
- **WHEN** 用户打开固定外部工具链接
- **THEN** 新页面不能通过 opener 控制原页面
- **AND** 请求不携带来源页面信息

### Requirement: 静态链接门禁必须确定且完整
系统 MUST 使用不依赖公网、浏览器登录态或 Element Plus 上游状态的确定性检查，对受管链接
数量、完整目标、动态绑定和新窗口隔离属性进行完整对账。该检查 MUST 进入现有 Web 质量门禁，
任何失败 MUST 使稳定聚合检查失败。

#### Scenario: 当前合同保持完整
- **WHEN** 受管链接仍为清单中的 11 个固定目标且全部满足新窗口隔离要求
- **THEN** 静态链接安全检查成功
- **AND** 检查不访问外部网络

#### Scenario: 链接数量或属性发生漂移
- **WHEN** 链接被增加、删除、重复，或缺失规定的隔离属性
- **THEN** 静态链接安全检查失败
- **AND** 聚合维护门禁报告真实失败而不是合法跳过

#### Scenario: 只修改网页端相关路径
- **WHEN** Pull Request 命中网页端质量检查范围
- **THEN** 静态链接安全检查实际执行
- **AND** 其结果参与受保护分支所需的稳定聚合检查
