## 1. 实施前基线与停止条件

- [x] 1.1 在开始改动前记录当前分支、`HEAD`、`git status --short`、Node.js 与 pnpm 版本，确认只保留用户已有改动，并在事实与提案快照不一致时先停止实施和更新 OpenSpec 工件。
- [x] 1.2 重新对账 `modules/web/src/` 中全部 `el-link`，确认仍只有 `SourceHelp.vue` 的 11 个固定目标，文字、`href`、`target="_blank"` 与规范清单一致，且不存在 `:href`、`v-bind:href` 或其他运行时 URL 数据流。
- [x] 1.3 只读核对 `modules/web/package.json`、`modules/web/pnpm-lock.yaml` 与 GitHub Dependabot API，确认 Element Plus 版本、同一 `GHSA-5m5x-9j46-h678` 的两条中危告警及 `first_patched_version=null`（当前无修补版本）事实；如出现修复版本、告警关闭或新增 high/critical 告警，停止沿用当前风险结论并先更新设计。
- [x] 1.4 读取 `.github/workflows/test.yml`、`.github/scripts/test_maintenance_workflow.py` 和 `modules/web/package.json` 的现有 Web 门禁，记录冻结安装、章节 HTML 测试、类型检查、只读 ESLint、构建同步与稳定聚合检查的当前命令，避免实现时替换或弱化既有检查。

## 2. 静态链接合同与 RED 证据

- [x] 2.1 使用 Node.js 内置模块新增确定性静态链接合同脚本及聚焦测试，递归扫描 `modules/web/src/` 的全部 Vue 文件，并按“文件、可见文字、完整 `href`”精确对账 11 项清单；对新增、删除、重复、移动、未知目标、危险协议、动态绑定、重复或无法唯一解析的属性以及隔离属性缺失一律 fail-closed。
- [x] 2.2 在 `modules/web/package.json` 增加独立只读的静态链接安全命令，在 `.github/workflows/test.yml` 的 Web 质量任务中将它放在冻结安装之后、类型检查和构建之前，并扩展 `.github/scripts/test_maintenance_workflow.py` 以断言该命令持续存在且参与稳定聚合门禁。
- [x] 2.3 在尚未修改 `SourceHelp.vue` 时运行新增安全命令和相关仓库契约测试，保存静态链接检查因现有 11 个链接缺少显式 `rel="noopener noreferrer"` 而失败的预期 RED；若失败原因不是该缺口，先修正测试或更新设计，不得通过减少清单、跳过链接或把失败降级为 warning 获得 GREEN。

## 3. 最小 GREEN 改动

- [x] 3.1 只为 `SourceHelp.vue` 清单内的 11 个 `target="_blank"` 链接增加显式 `rel="noopener noreferrer"`，逐项确认链接文字、完整目标、图标、布局、顺序和新窗口打开方式均未改变。
- [x] 3.2 重新运行静态链接安全命令和聚焦测试并取得 GREEN，同时用负向样例证明未知固定目标、动态 `href`、危险协议、数量漂移和隔离属性缺失都会产生非零退出码。
- [x] 3.3 运行 `python3 .github/scripts/test_maintenance_workflow.py`，确认 Web 路径分类、冻结只读门禁、新安全命令和稳定聚合检查合同全部通过，且没有引入 `continue-on-error`、合法跳过伪装成功或写回仓库的行为。

## 4. 固定工具链与生成产物对账

- [x] 4.1 使用仓库声明的 pnpm 9.15.9 执行 `pnpm install --frozen-lockfile`，确认安装没有修改 `modules/web/pnpm-lock.yaml`、Element Plus 或其他依赖版本；发生锁文件或解析漂移时停止实施。
- [x] 4.2 在 `modules/web/` 依次运行 `pnpm test:chapter-html`、新增静态链接安全命令、`pnpm type-check`、`pnpm exec eslint .` 和 `pnpm build`，逐项记录真实退出结果，不把未运行检查描述为通过。
- [x] 4.3 对账 `pnpm build` 同步到 `app/src/main/assets/web/vue/` 的全部差异，确认生成文件只反映 11 个链接的隔离属性且没有依赖、无关页面、格式化或其他静态资源漂移；随后重复构建并确认生成结果稳定。

## 5. 本地行为冒烟

- [x] 5.1 使用本地 Web 页面或既有网页容器检查最终 DOM，逐项核对 11 个锚点的可见文字、解析后目标、`target="_blank"` 和包含且仅包含 `noopener`、`noreferrer` 的 `rel` token；不得为此访问或登录外部网站。
- [x] 5.2 在不访问外部站点的前提下，通过本地同源页面或拦截导航的等价检查覆盖 10 个帮助锚点与固定外部工具入口，确认入口仍按既有方式打开目标且原页面不暴露可被新页面控制的 opener；如本地环境无法验证 referrer 行为，明确记录未验证项并阻止把它宣称为已验证。

## 6. 最终证据与人工评审边界

- [x] 6.1 完成实现后再次通过只读 API 核对 Dependabot：两条 Element Plus 中危告警应继续保持 open，且没有新增 high/critical 告警；任何外部状态漂移都单独记录，不得把告警消失归因于本变更。
- [x] 6.2 运行 `openspec validate --all --strict`、`git diff --check`、`git diff --stat` 和 `git status --short`，确认 OpenSpec、生产改动、测试、工作流及 Android Web 静态产物范围完整且没有无关差异、凭据、签名材料或锁文件改动。
- [x] 6.3 整理 RED/GREEN、完整 Web 检查、生成产物对账、本地 DOM 冒烟和告警复核证据并提交人工评审；评审批准前不得合并、归档变更、关闭或 dismiss 告警，也不得声称 Element Plus 组件漏洞已经修复。
