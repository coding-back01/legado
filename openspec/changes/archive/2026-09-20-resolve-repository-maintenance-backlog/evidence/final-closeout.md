# 仓库维护欠账最终收尾证据

## 替代实现与合并后门禁

- 替代 PR 为 [#85](https://github.com/coding-back01/legado/pull/85)，最终实现 head 为 `8881bd3466484dcc3ae6c2b34341b18d1ef852c8`，merge commit 为 `791f53cc0598696b20461ba6bbb785952f791da9`。
- PR workflow run `35447621161` 和 merge commit 对应的 `master` workflow run `35482403819` 全部成功；后者实际完成 Android lint 零 issue、Android 单元测试、Debug APK、Web 全套检查、Android/Web CodeQL、OpenSpec/仓库检查和稳定聚合门禁。
- 合并后 Dependabot 告警 #6 及 Element Plus 告警 #1/#5 均为 `fixed`；打开的 high/critical Dependabot、Code Scanning 和 Secret Scanning 告警均为 0。

## 原 Dependabot 队列闭环

- PR #42 保持原 ref `dependabot/gradle/gradle-wrapper-2739417140` 与 head `271896bc1a4c8b99bdfd4ae7f25ded815c2155d9`，Dependabot 于 `2026-09-20T01:51:58Z` 自动关闭并删除分支。已在 [关联说明](https://github.com/coding-back01/legado/pull/42#issuecomment-5746931944) 中记录原 Gradle 9.7.1 范围由 PR #85 替代，不重新打开或重建分支。
- PR #45 保持原 ref `dependabot/npm_and_yarn/modules/web/web-development-4abf6744d0` 与 head `7ad3277cb2c8472738861c5f9a6a4e9cff507679`，Dependabot 于 `2026-09-20T01:53:36Z` 自动关闭并删除分支。已在 [关联说明](https://github.com/coding-back01/legado/pull/45#issuecomment-5746932348) 中记录原 Web 开发依赖范围由 PR #85 替代。
- PR #81 保持原 ref `dependabot/npm_and_yarn/modules/web/web-production-aa57cd536c` 与 head `6554a8a935d91aba6cc8b352c384fa584008121e`，Dependabot 于 `2026-09-20T01:52:41Z` 自动关闭并删除分支。已在 [关联说明](https://github.com/coding-back01/legado/pull/81#issuecomment-5746932733) 中记录原 Web 生产依赖范围由 PR #85 替代。
- PR #80 的原执行清单是 AGP `8.13.2 → 9.4.0`、head `b349e42379117535ac92a6de0f83f1576e3d7da6`，该范围已由 PR #85 替代。Dependabot 随后把同一编号和 ref 刷新为 AGP `9.4.0 → 9.4.1`、head `97c1d865e1b4b56805a5f7f500f5abfb288e331e`；[关联说明](https://github.com/coding-back01/legado/pull/80#issuecomment-5746932559) 明确保留这一新普通更新，PR 和分支均未关闭或删除。
- 最终开放 PR 为本次范围外的 #80、#86 和 #87；开放 Issue 为 0。#86/#87 是替代合并后由 Dependabot 新建的 Web 生产/开发依赖更新，均按设计保留。

## 旧发布证据分支

- `codex/release-verification-evidence` 在删除前仍精确指向锁定 SHA `182b67140b4eb4d1dbdace589a6c15f67447e61e`，只包含首次普通版候选锁定证据及任务 8.1 勾选。
- 归档目录 `openspec/changes/archive/2026-08-31-stabilize-independent-fork-governance/` 已保留 311 行最终候选证据，明确记录首次候选、失效原因、后续重新锁定、草稿、设备验证与归档结果，完整取代旧分支内容。
- 核对替代关系和远端 SHA 后，已按精确 ref 删除该远端分支；没有使用 glob 或强制改写。

## 本地工作树与历史分支

- 清理前主工作树和 `/Users/back/legado-reading-time` 附加工作树均无修改或未跟踪文件，stash 为空；本地 `master` 通过纯快进更新到 `791f53cc0598696b20461ba6bbb785952f791da9`，与 `origin/master` 一致。
- `codex/add-reading-time-estimation` 仍精确指向 `fb9495116a925b594fbf567cbdd734b6119b8ddb` 且被当前 `master` 包含；已先移除干净的附加工作树，再使用安全删除移除该本地分支。
- 设计列出的其余 37 个已合并本地分支均逐一通过 `merge-base --is-ancestor`，再按精确名称使用安全删除移除；没有使用名称前缀或 glob，也没有发现独立提交。
- 在远端删除和内容替代证明均成立后，单独删除未直接合并的本地 `codex/release-verification-evidence`，删除前再次确认其 SHA 为 `182b67140b4eb4d1dbdace589a6c15f67447e61e`。
- 执行远端引用 prune 后，本地只保留 `master` 与基于最新 `master` 的 `codex/resolve-repository-maintenance-backlog-closeout`；工作树只剩主工作树，stash 仍为空。远端只保留 `master` 以及本次无权清理的 #80/#86/#87 三个 Dependabot 分支。

## 仓库保护与安全能力

- 最终读回确认仓库仍为公开、未归档、未禁用，默认分支为 `master`，`delete_branch_on_merge=true`。
- ruleset `20653588`（`保护 master 分支`）仍为 active、无 bypass actor，目标为默认分支；删除保护、非快进保护、Pull Request、必需状态 `维护门禁` 和 CodeQL `high_or_higher` 规则均保持启用。
- Dependabot Security Updates、Secret Scanning、push protection 和私有漏洞报告均为 enabled；打开的 Dependabot、Code Scanning 和 Secret Scanning 告警均为空。`secret_scanning_non_provider_patterns` 与 `secret_scanning_validity_checks` 仍保持前置时的 disabled 状态，本变更没有更改它们。

## Release 与 tag 审计边界

- Release 列表仍为前置快照中的 6 条，没有新增 Release。Latest 仍是 `3.26.083101`，Release ID `379347961`、目标 `cef2fbb2dbdb6771686b04c68447a6f5caea964e`、`published_at=2026-08-30T17:29:47Z`，未被本变更改变。
- `3.26.083020` 仍是 draft，Release ID `379270869`、`published_at=null`、目标 `31bdae63c7c625e34b1db45f7bb6087d9f2a730a`，且没有对应 Git tag；其失败候选资产保持不变。
- `3.26.083021` 仍是标记“设备验证失败，禁止公开”的 draft，Release ID `379285557`、`published_at=null`、目标 `cef2fbb2dbdb6771686b04c68447a6f5caea964e`；对应 tag 仍精确指向同一提交，资产保持不变。
- 远端 tag 清单与前置一致，没有因本变更创建、删除或移动 tag；没有调用 Release workflow，也没有公开、删除或设置任何草稿为 Latest。

## 归档前最终校验

- `openspec validate --all --strict` 为 8 passed、0 failed，包含本变更及全部主规范。
- `git diff --check` 无输出；工作树差异只包含本 OpenSpec 的任务勾选和最终证据，没有生产代码、生成资产或仓库配置变化。
- 本地 `master` 与 `origin/master` 均为 `791f53cc0598696b20461ba6bbb785952f791da9`，双向祖先检查通过；收尾分支基于该提交创建。
- 最终本地分支、工作树、stash、远端分支与 GitHub 开放 PR 清单互相一致；全部 50 个实施任务均有对应结果或证据，可以进入独立 OpenSpec 归档流程。
