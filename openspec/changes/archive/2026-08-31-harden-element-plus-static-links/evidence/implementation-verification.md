# 实施验证证据

## 基线

- 实施前 `master`、`HEAD` 与 `origin/master` 均为
  `7aa63c27bea3d967aad9e8950a2a3f6d324a51a7`。
- 工作区仅包含本变更及维护路线图；Node.js 为 `v24.16.0`。仓库根目录默认 pnpm 为
  `10.29.3`，但 `modules/web/` 根据 `packageManager` 解析为 pnpm `9.15.9`，实际冻结安装、
  检查和构建均显式使用 pnpm `9.15.9`。
- 全部 Vue 源码仍只有 `SourceHelp.vue` 使用 `el-link`，共 11 个固定目标，没有动态 `href`。
- GitHub 只读 API 显示两条 Element Plus 告警均为 open、medium、
  `GHSA-5m5x-9j46-h678`、`first_patched_version=null`；open high/critical 告警为 0。

## RED 与 GREEN

- 新增合同的 6 个正向和负向用例先全部通过；在尚未修改 `SourceHelp.vue` 时，仓库源码扫描以
  `受管 el-link 必须显式使用 rel="noopener noreferrer"` 退出 1，维护门禁契约测试 13 项
  全部通过。这是本变更接受的真实 RED。
- 只为 11 个既有链接增加 `rel="noopener noreferrer"` 后，静态链接命令通过，并报告
  `静态链接安全合同通过：11 个受管链接`；6 个负向用例和 13 个维护门禁契约测试继续通过。

## 固定工具链与生成产物

- `corepack pnpm --version` 为 `9.15.9`；`pnpm install --frozen-lockfile` 成功，锁文件前后
  SHA-256 均为 `99ed1ab8f828093b30b55255af76ffd4cf1aba874303381874b0d61e6db4f42e`。
- 章节 HTML 测试 16 项、静态链接测试 6 项与 11 项清单、类型检查、只读 ESLint 和 Vite
  构建均通过。
- 使用与 CI 一致的 Node.js `v22.23.2` 和 pnpm `9.15.9` 连续构建并同步两次，Android Web
  资产树摘要两次均为 `07a2577242157b488fa8a4a2fb28fa113457cca89387e91621407d2e19702b0f`。
- 同工具链下的干净 `HEAD` 构建与仓库原资产完全一致。当前生成差异由 SourceHelp 变更级联：
  主包新增 11 组 `rel`；组件 scoped ID 随源码变化；CSS 归一化 scoped ID 后完全一致；两个
  异步 chunk 归一化主包文件名后完全一致。没有发现依赖、无关页面逻辑或格式化漂移。

## 本地 DOM 冒烟

- 在本地 `/#/bookSource` 的“帮助信息”面板读取实际 DOM，共 11 个 `a.el-link`；每项可见文字、
  原始和解析后目标、`target="_blank"` 以及按顺序解析的 `noopener`、`noreferrer` token 均与
  清单一致。
- 点击同源 `/help/#appHelp` 后，本地新页 `document.referrer` 为空；内置浏览器中的 opener
  表现为 `undefined`，没有 `postMessage`、`close` 或 `focus` 能力，不能控制原页。
- 按设计没有访问 `https://regexr-cn.com/`。外部请求的服务端 referrer 没有直接观测；这里只
  记录外链实际 DOM 的 `noreferrer` token 和同源本地等价行为，不把未执行的外部请求验证描述
  为已通过。

## 告警复核

- 实施后只读 API 仍显示两条 Element Plus 告警为 open、medium、
  `GHSA-5m5x-9j46-h678`、`first_patched_version=null`，open high/critical 告警为 0。
- 本变更没有关闭、dismiss 或重新分类告警，也不把调用侧静态合同描述为 Element Plus 运行时
  漏洞修复。

## 最终仓库检查

- `python3 .github/scripts/test_maintenance_workflow.py`：13 项通过。
- 固定 `actionlint` 1.7.12：官方 darwin/arm64 归档 SHA-256 校验通过，工作流检查通过。
- `openspec validate --all --strict`：7 项通过、0 项失败。
- `git diff --check` 及全部未跟踪新文件的独立空白检查通过。
- `modules/web/pnpm-lock.yaml` 无差异；仓库根目录没有误生成的 `pnpm-lock.yaml` 或
  `node_modules`；变更范围没有签名材料、凭据、`local.properties` 或 Firebase 配置。
- 本变更是 Web 专项，没有修改 Android Kotlin/Java、Room、规则格式、导入、备份、包名或
  签名，因此未运行 Android 单元测试、lint、APK/AAB 构建或设备验证，不能把这些项目描述为
  已通过。
