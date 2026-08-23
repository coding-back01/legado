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
