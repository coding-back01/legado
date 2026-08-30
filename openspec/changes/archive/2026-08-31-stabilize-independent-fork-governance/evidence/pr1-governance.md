# PR 1 治理入口决策与验证证据

## 范围

- 分支：`codex/fork-governance-foundation`
- 基线提交：`460970675fedb91d8d10aa42447bab8cc13e8a40`
- 目标：只处理 Dependabot 普通更新策略、Issue 入口、失效自动写入和 stale 边界；不提前修改更新器、发布工作流、质量错误或持续门禁。

## Dependabot 决策

三个 ecosystem/update entry 均使用 `monthly`，普通更新的
`open-pull-requests-limit` 分配如下：

| ecosystem | 上限 |
|---|---:|
| Gradle | 2 |
| GitHub Actions | 1 |
| npm | 2 |
| **跨生态合计** | **5** |

所有普通更新分组均显式声明 `applies-to: version-updates`，因此不会把普通更新分组
配置冒充为安全更新策略。Gradle Wrapper、AGP、Kotlin 与 KSP 分别进入四个独立工具链组，
其余普通 Android 依赖进入 `android-libraries`；GitHub Actions 作为一组；npm 依赖按
production 与 development 分组。本仓库不存在 Dependabot 自动合并 workflow，本次也不
增加自动合并。

`gradle/libs.versions.toml` 中带“不更新版本”或最低 Android 兼容性说明的依赖均使用
精确坐标或精确 group pattern 列入 `ignore`，并显式列出普通 semver major、minor、patch
三种 `update-types`。Dependabot security-only job 不应用 `update-types` 条件，因此普通更新
保持冻结，受支持的安全修复仍可创建独立 PR。安全 PR 仍须通过人工兼容评估，不能自动合并，
也不能用来跳过任务 2.11–2.12 的告警门禁。

## Issue 与 stale 决策

- 删除功能请求表单，只保留普通正式版错误表单。
- 错误表单使用现有小写 `bug` 标签，收集当前 fork 完整版本、Android 版本、设备、现象、期望结果和最小复现步骤。
- 公开提交前必须确认已经移除 Cookie、Token、账号、私有 URL、备份内容、个人文件路径和其他个人数据；疑似安全问题引导至私有漏洞报告。
- Issue 配置删除已失效的旧 Wiki、Discussion 和其他公开反馈 contact link，只保留私有安全报告入口。维护范围、无 SLA 与功能请求默认不受理已在错误表单首屏明确说明。
- stale workflow 只选择带 `needs-info` 的 Issue，连续 30 天未补充后关闭；Pull Request stale 被禁用，`crash`、`data-loss`、`security` 标签被豁免。
- 只读快照显示远端尚无 `needs-info`、`crash`、`data-loss`、`security` 和 `stale` 标签。PR 1 创建并通过静态检查后、合并前必须按这些精确名称创建并读回；任一标签缺失时不得合并或把策略描述为可操作。

私有漏洞报告在基线快照中为关闭状态，而 PR 1 会把它设为唯一安全入口。为避免入口上线后
短暂不可用，PR 1 合并前必须先启用并读回私有漏洞报告，并验证错误表单和 Issue 配置中的
目标可访问；这一步之外不得提前执行任务 2.8–2.12 的其他远端治理。

## Workflow 审计决策

- 删除 `autoupdatefork.yml`：它会每天从已撤空的 `gedoor/legado` 拉取、合并并直接推送 `master`，没有适合个人稳定 fork 的安全价值。
- 删除 `cronet.yml`：其有价值路径全部会下载外部二进制、修改构建配置并用 `ACTIONS_TOKEN` 创建 Pull Request，且以 `continue-on-error` 隐藏失败；机械更换仓库条件会扩大自动写入权限。保留脚本供未来独立兼容性变更人工审计。
- 保留 `web.yml` 的构建价值，但删除旧上游身份变量、时间版本生成和自动提交步骤，并将 workflow 权限显式收窄为 `contents: read`。Node、pnpm、冻结安装和产物一致性检查留到任务 4.9 与 6.2，不在 PR 1 提前扩展。

## 验证记录

PR 1 提交前执行并记录：

1. PyYAML 解析全部剩余 Dependabot、Issue 和 workflow YAML。
2. SchemaStore 的 `dependabot-2.0.json`、`github-issue-config.json`、`github-issue-forms.json` 和 `github-workflow.json` 校验目标文件。
3. `actionlint` 1.7.12 校验全部剩余 workflow。
4. 自定义只读断言核对三项月度配置、跨生态普通更新上限合计 5、所有 group 的 `applies-to`、固定依赖 ignore 和不存在自动合并路径。
5. `openspec validate --all --strict`、`git diff --check` 和 PR 范围审计。

GitHub 合并后的平台读回是任务 2.7 之后的独立证据，不能由本地 YAML/schema 成功替代。

### 提交前实际结果

执行时间：2026-08-23 11:33 +0800。

| 检查 | 结果 |
|---|---|
| 全部剩余 YAML 的 PyYAML 解析 | 通过 |
| SchemaStore Dependabot 配置 | 通过 |
| SchemaStore Issue 配置与错误表单 | 通过 |
| SchemaStore 全部剩余 GitHub workflow | 通过 |
| `actionlint` 1.7.12 全部剩余 workflow | 通过，退出码 0 |
| Dependabot 合同断言 | `DEPENDABOT_LIMIT_SUM=5`、四个独立工具链组、固定依赖 ignore 共 13 项且不阻断 security-only job |
| Issue 与 workflow 合同断言 | 通过；没有旧上游写入、自动提交或 Cronet 自动建 PR 路径 |
| `openspec validate --all --strict` | 3 项通过、0 项失败 |
| `git diff --check` | 通过，无输出 |

SchemaStore 来自其在线公开 schema；`actionlint` 由 `actionlint-py` 1.7.12.24 提供，
实际 actionlint 版本为 1.7.12。工具成功仅证明静态配置合法；Dependabot 平台解析和默认
分支 Issue/workflow 入口仍须在 PR 1 合并后读回，标签与私有漏洞报告按下一节在合并前原子准备。

## PR 1 合并前远端原子前置

执行时间：2026-08-23 11:43 +0800。操作前重新确认 PR `#41` 为打开状态，head ref 为
`codex/fork-governance-foundation`，head SHA 为
`7e8ead0386489c6bfcc08d240babdf3412b78bb3`；五个目标标签均不存在，私有漏洞报告为
`enabled=false`。

按任务 2.7 只创建并读回以下精确标签：

| 标签 | 颜色 | 说明 |
|---|---|---|
| `needs-info` | `D4C5F9` | 等待报告者补充复现资料 |
| `crash` | `B60205` | 应用崩溃问题；不得因时间自动关闭 |
| `data-loss` | `B60205` | 数据损坏或丢失风险；不得因时间自动关闭 |
| `security` | `D93F0B` | 安全问题；公开内容不得包含敏感信息 |
| `stale` | `EDEDED` | 长期未补充资料 |

随后通过 `PUT /repos/coding-back01/legado/private-vulnerability-reporting` 启用私有漏洞报告，
GET 读回为 `{"enabled":true}`。未登录访问表单目标会按 GitHub 预期重定向到登录页；仓库
API 的已启用状态是入口可用性的权威读回。此阶段没有启用其他安全能力，没有关闭 Pull
Request、删除分支或修改 Release。

## PR 1 检查、合并与默认分支读回

PR `#41` 的最终 head ref 为 `codex/fork-governance-foundation`，最终 head SHA 为
`55d2501059e0c01afbda324840f1afcc629ebad2`。Pull Request 检查 run
`32616497170` 的 `Android Debug 验证` 成功后，PR 于 2026-08-23 11:59 +0800 合并，
merge commit 为 `0d8e1d580c4a6bfcacd7cec7067808879f5cf8fa`。随后 `master` push run
`32616712378` 在同一 merge commit 上成功。

默认分支读回确认新的 `.github/dependabot.yml`、Issue 表单、Issue 配置和 workflow 已生效；
五个标签的名称、颜色和说明仍与合并前记录一致，私有漏洞报告仍为 `enabled=true`。

## 遗留 Dependabot 队列与分支清理

PR 1 合并后先重新读取批准清单中的作者、状态、head ref 和 head SHA。Dependabot 因新配置
生效自动关闭并删除了 19 个旧对象：`#1`、`#2`、`#4`、`#5`、`#6`、`#7`、`#8`、
`#9`、`#10`、`#11`、`#14`、`#15`、`#16`、`#17`、`#24`、`#26`、`#28`、
`#37`、`#38`。其中 `#1` 在自动关闭前从快照 SHA
`d7bda9504ca79115ab1f79db5b89b55069e617c3` 漂移为
`52a4315f33f8eb7fcd97c82406db0d7e6cde8ae2`；该对象未执行人工关闭或人工分支删除。

剩余 11 个旧对象在作者仍为 `dependabot[bot]`、状态仍为打开、ref 与快照一致且 head SHA
未漂移后逐项人工关闭，并只按各自精确 ref/SHA 删除对应分支：`#12`、`#13`、`#18`、
`#19`、`#20`、`#22`、`#25`、`#27`、`#29`、`#30`、`#39`。删除后读回确认
30 条旧 Dependabot 分支均已不存在。

新月度策略产生的 `#42`、`#43`、`#44`、`#45` 不在旧清理白名单中，全部保留。
安全修复 PR `#46` 合并后，Dependabot 自动重算了 `#45` 的 head；这种新增或漂移不会
被既有授权自动纳入清理。当前不合并范围过大的 `#45`。

## 已合并遗留分支清理

执行前逐项确认以下四条分支的 tip 与快照 SHA 一致、均为已合并 PR 的祖先、没有未进入
`master` 的提交，也未被打开的 PR、tag 或 Release 使用。随后用各自 expected SHA lease
精确删除，并读回确认不存在：

| 分支 | 删除前 SHA |
|---|---|
| `codex/add-reading-time-estimation` | `fb9495116a925b594fbf567cbdd734b6119b8ddb` |
| `codex/archive-harden-independent-fork-security` | `d68de3010c7169e58014dedde116496300b3b3b6` |
| `codex/harden-independent-fork-security` | `c6790744e00946aefb6aa49e0450cd8e145355f8` |
| `codex/initial-release` | `3e47dd8d29ddece7581b1072f342fa73b6fde1d1` |

没有使用 `codex/*` 或其他 glob 扩大目标。

## Latest Release 冻结说明读回

修改前重新确认 Latest 仍为 Release ID `374875912`、tag `3.26.082216`，target 为
`460970675fedb91d8d10aa42447bab8cc13e8a40`，且两个资产与前置快照一致。只追加稳定化
冻结说明后再次读回：Release 仍为公开、非 prerelease 的 Latest，target 未变化；普通版资产
仍为 `14,690,479` 字节、SHA-256
`397dafa82918ec4bb8c588eca90285660fcb5b3f2ed07452e73099dbaa5a8412`，`releaseA`
资产仍为 `14,690,475` 字节、SHA-256
`eeba03eb61da79abc8751809abbf973a146a47ccf31caae6504d70a8f5529583`。说明明确短期暂停
新正式版、未来只生成普通版、保留历史 `releaseA` 资产和独立数据，并重申应用不提供内容。

## 安全能力与首次告警处置

2026-08-24 20:35 +0800 的读回状态如下：

| 项目 | 读回结果 |
|---|---|
| Dependabot alerts | 已启用；状态接口返回 `204`，告警列表可枚举 |
| Dependabot Security Updates | 已启用；自动安全修复接口返回 `200` |
| Secret Scanning | `enabled` |
| Push Protection | `enabled` |
| 私有漏洞报告 | `enabled=true` |
| 自动合并 | `allow_auto_merge=false` |
| 合并后自动删分支 | `delete_branch_on_merge=false`，留待任务 6.11 |

没有在稳定检查 context 尚不存在时修改 `master` ruleset。Secret Scanning 打开告警为 0；
全历史仍只有快照中已解决的 Google API Key 告警 `#1`，没有把 API 不可用解释为零告警。

Dependabot 首次读取到 4 条打开告警。Vite 高危 `GHSA-fx2h-pf6j-xcff` 和中危
`GHSA-v6wh-96g9-6wx3` 的首个修复版均为 `6.4.3`，中危
`GHSA-4w7w-66w2-5vf9` 的首个修复版为 `6.4.2`。紧急安全 PR `#46` 只将 Vite 下限
提升到 `^6.4.3`，并将伴随插件下限提升到明确支持 Vite 6 的
`@vitejs/plugin-vue ^5.2.4`；PR head 为
`24f8745ca5df2d28b1f11fc2f01be17dc862be39`，Web run `32726805315` 成功，merge
commit 为 `57320b38b89ad29869905a43c8574a8647d59913`，`master` run `32726996887`
成功。GitHub 随后在 2026-08-24 20:25 +0800 将三条 Vite 告警标记为 `fixed`。

剩余 Element Plus 中危 `GHSA-5m5x-9j46-h678` 没有 GitHub 提供的修复版本；上游
`2.11.0` 仅补充风险文档，组件仍直接透传 `href`，因此不能把版本升级描述为运行时修复。
仓库实际使用的 11 个 `el-link` 全部是源码固定值：10 个同源 `/help/#...` 锚点和 1 个
固定 HTTPS 域名；不存在动态绑定、props、API、存储或用户输入到 `href` 的数据流。该中危
按当前可达性记录为可容忍风险并保留打开状态；未来任何动态 URL 必须先限制协议和目标
allowlist，并增加回归检查。当前高危/严重打开告警为 0，低危告警为 0。
