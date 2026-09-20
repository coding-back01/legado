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
