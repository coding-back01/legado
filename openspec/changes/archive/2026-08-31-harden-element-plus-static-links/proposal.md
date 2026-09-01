## Why

仓库当前有两条映射到同一 `GHSA-5m5x-9j46-h678` 的 Element Plus 中危告警；上游只在
文档中要求调用方校验 URL，没有提供组件级运行时修复。现有 11 个 `el-link` 虽然全部使用
源码固定地址，但这一安全前提尚未成为持续门禁，未来引入动态 `href` 时可能在没有重新评审
的情况下打开 XSS、恶意协议或开放重定向入口。

## What Changes

- 建立网页端静态链接安全合同，精确锁定当前允许的 10 个同源帮助锚点和 1 个固定 HTTPS
  外链，并拒绝未登记的目标、危险协议和动态 `href` 绑定。
- 要求所有在新窗口打开的受管链接隔离 opener，并避免向外部目标泄露来源信息。
- 增加确定性、无网络依赖的安全契约测试，并把它接入现有 Web 质量门禁及其仓库契约测试。
- 保持两条 Element Plus 告警打开以继续监测上游；本变更不升级或替换 Element Plus，也不把
  超出告警版本范围描述为组件运行时修复。
- 明确未来需要动态 URL 时必须另建 OpenSpec 变更，先设计协议、同源或目标域名 allowlist
  以及拒绝行为，再允许数据进入链接属性。

## Capabilities

### New Capabilities

- `web-link-security`：规定内置网页端受管链接的固定目标、动态 URL 禁入、新窗口隔离和持续
  安全门禁。

### Modified Capabilities

无。

## Impact

- 受影响模块：`modules/web/` 网页端、构建同步的 `app/src/main/assets/web/vue/` 静态产物，
  以及承载 Web 质量门禁的 `.github/workflows/test.yml` 和对应仓库契约测试。
- 用户可观察结果：现有 11 个帮助/工具入口的文字、目标和打开方式保持不变；非法或未经登记
  的后续链接改动不能通过必需检查。
- 兼容性：不改变 Android 最低 SDK、Room、书源/订阅源规则、JavaScript 规则、导入 URI、
  JSON、备份、持久化数据、包名、签名或 Release 资产。
- 依赖：不修改 `element-plus`、Vue、pnpm lockfile 或其他依赖版本。
- 安全风险：源码扫描契约只能保护版本化模板，不能替代未来动态 URL 的运行时净化；因此动态
  URL 明确保持禁止，告警继续开放并保留现有风险重启条件。
