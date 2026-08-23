# 一次性治理实施快照

## 快照元数据

- 变更：`stabilize-independent-fork-governance`
- 快照时间：2026-08-23 11:03:01 +0800
- 授权事实：用户于 2026-08-23 明确回复“批准实施 `stabilize-independent-fork-governance`”。
- 仓库：`coding-back01/legado`
- 默认分支：`master`
- 本地、`master` 与 `origin/master` 提交：`460970675fedb91d8d10aa42447bab8cc13e8a40`
- 提交说明：`Merge pull request #40 from coding-back01/codex/stabilize-reading-time-estimation`
- 工作区边界：开始实施时只有 `openspec/changes/stabilize-independent-fork-governance/` 未跟踪，没有其他用户改动。

## 本地仓库与工具链

| 项目 | 实际状态 |
|---|---|
| `origin` | `git@github.com:coding-back01/legado.git` |
| `upstream` | `https://github.com/zj970/legado.git` |
| GitHub CLI | 已以 `coding-back01` 登录；后续命令必须显式使用 `--repo coding-back01/legado` |
| 仓库可见性 | 公开、独立仓库、非 GitHub fork；默认分支为 `master` |
| Java | 当前 shell 为 21.0.3；本机另有 JDK 17.0.17，实施和 CI 使用 JDK 17 |
| Gradle | 8.13 |
| Node.js | 24.16.0，满足 Node.js 20+ |
| pnpm | 当前 shell 为 10.29.3；网页治理将固定为 9.15.9 |
| OpenSpec | 1.8.0 |
| Android SDK | `/Users/back/Library/Android/sdk`，已安装 `platforms/android-36` |
| ADB | 1.0.41，platform-tools 35.0.2 |
| 模拟器镜像 | 当前只发现 Android 30 Google APIs arm64；API 21/API 36 状态留待 PR 5b 阶段处理 |

执行 `git fetch origin --prune` 后，本地 `master` 与 `origin/master` 仍保持同一提交。执行 `openspec validate --all --strict` 得到 3 项通过、0 项失败。

## Android 静态检查参考运行

在提交 `460970675fedb91d8d10aa42447bab8cc13e8a40` 上执行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
./gradlew :app:lintAppDebug --rerun-tasks --console=plain
```

- 退出码：1。
- 实际重跑 Gradle task：103 个。
- 结果：14 个 error、881 个 warning、18 个 hint。
- 结论：与提案参考基线完全一致，error ID 构成也未漂移。
- 报告：`app/build/reports/lint-results-appDebug.xml`、`.html`、`.txt`。
- 账本：`docs/maintenance-baseline.md`。

## 网页端参考运行

在 `modules/web/` 使用快照时现有依赖执行：

| 命令 | 退出码 | 结果 |
|---|---:|---|
| `pnpm type-check` | 0 | 通过 |
| `pnpm exec eslint .` | 1 | 2 个 error、0 个 warning |
| `pnpm build` | 0 | 通过；非 GitHub Actions 环境明确跳过 assets 同步 |

ESLint 精确基线：

- `src/source.d.ts:82:6`：`RuleSearch` 未使用，`@typescript-eslint/no-unused-vars`。
- `src/utils/souce.ts:52:41`：显式 `any`，`@typescript-eslint/no-explicit-any`。

构建前后 `git status --short` 均只有本 OpenSpec 变更目录，未产生 tracked 静态资源差异。

## GitHub 远端只读快照

只读快照窗口为 2026-08-23 11:01:27–11:04:40 +0800，GitHub CLI 身份为
`coding-back01`，所有查询均显式使用仓库 `coding-back01/legado`。本节不记录 API
返回的 secret 值，也没有执行任何远端写操作。

### Pull Request、Issue 与分支

- 打开的 Pull Request：30 个，作者全部为 `dependabot[bot]`。
- 打开的真实 Issue：0 个。仓库字段 `open_issues_count=30` 包含上述 Pull Request，不能解释为 30 个 Issue。
- 远端分支：35 条，包括 `master`、30 条对应 Dependabot 分支和 4 条已合并的 `codex/*` 遗留分支。
- `master` 为受保护分支；其余 34 条分支在分支列表中均为 `protected=false`。
- 仓库设置 `delete_branch_on_merge=false`。

执行前批准清单中的 30 个 Dependabot Pull Request 快照如下：

| PR | head ref | head SHA |
|---:|---|---|
| 1 | `dependabot/github_actions/actions/stale-11` | `d7bda9504ca79115ab1f79db5b89b55069e617c3` |
| 2 | `dependabot/github_actions/actions/setup-node-7` | `12afea4bef1286ad8adc4577782255dc4b0f1299` |
| 4 | `dependabot/npm_and_yarn/modules/web/vueuse/shared-14.4.0` | `5127bcd72de2f70ba1ab39072456d2a1f66fc07c` |
| 5 | `dependabot/github_actions/actions/upload-artifact-7` | `e4656e9e50193ddefabae69706dfcf921e43fa22` |
| 6 | `dependabot/npm_and_yarn/modules/web/eslint-plugin-vue-10.10.0` | `daa1ec7591632a53ff049df908899e9c27285e18` |
| 7 | `dependabot/github_actions/stefanzweifel/git-auto-commit-action-7.2.0` | `f42bbac905b2a9431629a2d7db1a3070f93e7b5c` |
| 8 | `dependabot/npm_and_yarn/modules/web/vitejs/plugin-vue-6.0.8` | `57b1e91c9e11cd78ec2ca073ee8fad818a80e778` |
| 9 | `dependabot/npm_and_yarn/modules/web/npm-run-all2-9.0.3` | `d39257f7b5b0fc576c0888e34afd210c57874b90` |
| 10 | `dependabot/npm_and_yarn/modules/web/unplugin-auto-import-21.1.0` | `53166a4d6c1048d8ec53115e4ff67e3ff3e42d50` |
| 11 | `dependabot/gradle/kotlin_ksp-3c4b77df4d` | `bea5d2a29b217b9e7fce1def40111efb8044548b` |
| 12 | `dependabot/gradle/com.github.jenly1314-zxing-lite-3.5.0` | `e36c1e463104850185168f54c2e516dbeff8e737` |
| 13 | `dependabot/gradle/com.squareup.okhttp3-okhttp-5.4.0` | `8b052880322c12c3dcb2ba7d9145f3ed74f603da` |
| 14 | `dependabot/gradle/activity-1.13.0` | `842259c65b01694787b8c9fe632f9f3c20b7cedb` |
| 15 | `dependabot/gradle/media3-1.11.0` | `ee747678e7f8a401bc410d8cae4b69e2130db62e` |
| 16 | `dependabot/gradle/com.google.protobuf-protobuf-javalite-4.35.1` | `82b50fe27e3b3dbaa0b4457b8198548b4995d130` |
| 17 | `dependabot/gradle/org.mozilla-rhino-1.9.1` | `4071a49571bc13bfc90e6c14ffc672acbfa050e1` |
| 18 | `dependabot/gradle/agp-9.3.1` | `f6d783aaaad08b6b541863b35deb332ec19ea3f2` |
| 19 | `dependabot/gradle/kotlin-2.4.10` | `43edaf64f7e5c79ac03abb5f2d6af6d2471d325d` |
| 20 | `dependabot/gradle/androidx.constraintlayout-constraintlayout-2.2.2` | `655743c37693d6568a66f7674d61ee9a21a5da0e` |
| 22 | `dependabot/gradle/coroutines-1.11.0` | `4527b22edf5b3cd7767b610af4e89255ea569bbb` |
| 24 | `dependabot/gradle/androidx.recyclerview-recyclerview-1.4.0` | `69ce46d89f76b8066824e90367309e2176bb090d` |
| 25 | `dependabot/gradle/com.github.bumptech.glide-compose-1.0.0-beta10` | `0f54007eb55892a4c77ad2a51c0b13af7dacf355` |
| 26 | `dependabot/gradle/room-2.8.4` | `e82fed806ec8b14c2c2194914ede8c40ea328e3c` |
| 27 | `dependabot/gradle/com.jayway.jsonpath-json-path-3.0.0` | `a35f393c1bc59e648a56d869afd23c9f295857d9` |
| 28 | `dependabot/gradle/gradle-wrapper-9.7.0` | `3f1f2504964a1ff8bd35cf29350437fe94294c3a` |
| 29 | `dependabot/gradle/glide-5.0.9` | `3e03cbe403b7308f48960225c83085c159c48d9f` |
| 30 | `dependabot/gradle/core-1.19.0` | `02142868b2ce9458c05628ee775273729ed20d65` |
| 37 | `dependabot/github_actions/actions/checkout-7` | `96c3a1d577df3c94755c0cdb2d634f29e7802071` |
| 38 | `dependabot/gradle/cn.hutool-hutool-crypto-5.8.47` | `76faecf4acef39f45fe14710f3e4311a3d012f52` |
| 39 | `dependabot/gradle/cn.wanghaomiao-JsoupXpath-2.5.5` | `ba24f5d2d590529932f36735ce3821eadab082be` |

执行前批准清单中的 4 条遗留分支快照如下：

| 分支 | SHA |
|---|---|
| `codex/add-reading-time-estimation` | `fb9495116a925b594fbf567cbdd734b6119b8ddb` |
| `codex/archive-harden-independent-fork-security` | `d68de3010c7169e58014dedde116496300b3b3b6` |
| `codex/harden-independent-fork-security` | `c6790744e00946aefb6aa49e0450cd8e145355f8` |
| `codex/initial-release` | `3e47dd8d29ddece7581b1072f342fa73b6fde1d1` |

以上 SHA 只作为前置快照。PR 1 合并后执行清理前仍须重新读取作者、状态、head ref
和 head SHA；任何漂移、新增或身份不符对象均不在本次授权范围内。

### Latest Release 与资产

当前 Latest Release 为 `legado_app_3.26.082216`：

| 项目 | 值 |
|---|---|
| Release ID | `374875912` |
| tag | `3.26.082216` |
| target commit | `460970675fedb91d8d10aa42447bab8cc13e8a40` |
| tag 类型 | lightweight tag，直接指向上述提交 |
| 状态 | `draft=false`、`prerelease=false` |
| 发布时间 | 2026-08-22 16:49:01 +0800 |

资产快照：

| 资产 | 大小 | SHA-256 | 下载计数 |
|---|---:|---|---:|
| `legado_app_3.26.082216_release.apk` | 14,690,479 字节 | `397dafa82918ec4bb8c588eca90285660fcb5b3f2ed07452e73099dbaa5a8412` | 1 |
| `legado_app_3.26.082216_releaseA.apk` | 14,690,475 字节 | `eeba03eb61da79abc8751809abbf973a146a47ccf31caae6504d70a8f5529583` | 1 |

两项资产均为 `uploaded`，由 `github-actions[bot]` 上传。后续只有在 tag、Latest
身份、资产名称、大小和摘要仍与本快照一致时，才可执行已批准的冻结说明更新。

### 安全能力与告警

| 能力 | 快照状态 | 告警结论 |
|---|---|---|
| Dependabot alerts | 关闭；状态接口返回 `Vulnerability alerts are disabled.` | 列表不可枚举，不能写成 0 |
| Dependabot Security Updates | `enabled=false`、`paused=false` | 尚未启用 |
| CodeQL default setup | `not-configured`，且无 CodeQL workflow | API 无分析记录，不能写成 0 告警 |
| Secret Scanning | 已启用 | 打开告警 0；全历史 1 条已解决告警 |
| Push Protection | 已启用 | 未发现绕过记录 |
| Non-provider patterns | 关闭 | 后续任务未要求启用 |
| Validity checks | 关闭 | 后续任务未要求启用 |
| 私有漏洞报告 | 关闭 | 尚未启用 |

Secret Scanning 历史告警 `#1` 为继承的 Google API Key，位于历史提交
`51c16a1efcb036cc18e9cdb6d592e691b3da5816` 的 `app/google-services.json:24`，状态为
`resolved`，解决方式为 `wont_fix`，说明为继承的 Firebase 客户端配置已经移除且不再使用；
`validity=unknown`、`publicly_leaked=true`、`push_protection_bypassed=false`。快照过程主动
删除了 API 返回的 secret 值，任何证据和日志都不得写入该值。

### `master` ruleset

`master` 的保护来自 repository ruleset，而非经典 Branch Protection：

| 项目 | 值 |
|---|---|
| ruleset ID | `20653588` |
| 名称 | `保护 master 分支` |
| target / enforcement | `branch` / `active` |
| include / exclude | `["~DEFAULT_BRANCH"]` / `[]` |
| bypass actors | `[]` |
| 当前用户可绕过 | `never` |

现有规则为 `deletion`、`non_fast_forward` 和 `pull_request`。Pull Request 规则允许
`merge`、`squash`、`rebase`，要求解决 review thread，批准数为 0；当前没有 required
status checks 规则。经典 Branch Protection API 返回 `404 Branch not protected` 与此不矛盾，
因为分支列表中的 `protected=true` 来自 ruleset。只有 PR 5 已产生并验证稳定检查后，才允许
按精确 ID 对此配置做不削弱其他字段的最小修改。

### 只读查询命令

关键命令如下；安全接口均使用 `X-GitHub-Api-Version: 2022-11-28`，所有 `gh api`
均为 GET，没有使用 `-X`、`--method`、`-f` 或 `--input`：

```bash
gh auth status
gh api repos/coding-back01/legado
gh api 'repos/coding-back01/legado/pulls?state=open&per_page=100&sort=created&direction=asc'
gh api 'repos/coding-back01/legado/issues?state=open&per_page=100&sort=created&direction=asc'
gh api 'repos/coding-back01/legado/branches?per_page=100'
gh api 'repos/coding-back01/legado/tags?per_page=100'
gh api repos/coding-back01/legado/releases/latest
gh api repos/coding-back01/legado/git/ref/tags/3.26.082216
gh api -i repos/coding-back01/legado/vulnerability-alerts
gh api -i repos/coding-back01/legado/automated-security-fixes
gh api -i 'repos/coding-back01/legado/dependabot/alerts?state=open&per_page=100'
gh api -i repos/coding-back01/legado/code-scanning/default-setup
gh api -i 'repos/coding-back01/legado/code-scanning/alerts?state=open&per_page=100'
gh api -i 'repos/coding-back01/legado/secret-scanning/alerts?state=open&per_page=100'
gh api repos/coding-back01/legado/private-vulnerability-reporting
gh api 'repos/coding-back01/legado/rulesets/20653588?includes_parents=true'
gh api 'repos/coding-back01/legado/rules/branches/master'
```

## 兼容性边界确认

本变更保持以下边界不变：最低 API 21、Room schema、书源/订阅源规则、导入 URI、备份格式、普通正式版包名 `io.legado.app.release` 与既有私有签名身份。若后续实现需要突破任一边界，必须停止序列并重新评审 OpenSpec 工件。
