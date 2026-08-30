# OpenSpec 归档 PR 与分支清理白名单

## 归档与主规范同步

- 原变更：`stabilize-independent-fork-governance`
- schema：`spec-driven`
- 归档目录：`openspec/changes/archive/2026-08-31-stabilize-independent-fork-governance/`
- 归档分支：`codex/archive-fork-governance`
- 首个归档提交：`a836835dfe816628170d7d5f88d58f2d30a06ee1`
- Pull Request：[#77](https://github.com/coding-back01/legado/pull/77)

4 个新增 capability 已创建主规范；`fork-build-security` 的 2 个 MODIFIED Requirement
已智能合并，其他既有 Requirement 和 Scenario 保持不变。五份 delta 均完成反向比对，
主规范中不存在 delta 操作标题。

归档分支在首个 head 上执行 `openspec validate --all --strict` 为 6/6，通过；
`git diff --check` 无输出。GitHub Actions run
[`33326747055`](https://github.com/coding-back01/legado/actions/runs/33326747055)
在同一 head 上完成：范围识别、OpenSpec/仓库检查、CodeQL Android、CodeQL Web 和聚合
`维护门禁` 均为 success；Android/Web 因纯 OpenSpec 文档范围合法 skip。原生 CodeQL
ruleset 检查也为 success。PR 读回为 `OPEN`、`MERGEABLE`、`CLEAN`。

归档 PR 的最终 head 为 `16aed1b044336d22863ad61805c686d94cebeb86`；GitHub Actions
run [`33341515917`](https://github.com/coding-back01/legado/actions/runs/33341515917)
在该 head 上完成双 CodeQL、OpenSpec/仓库检查和聚合 `维护门禁`，全部为 success。
PR 于 2026-08-31 07:26 +0800 合并，merge commit 为
`31affc67f72051ede1f4ec1bc8ee9c0f7ca69c9f`；归档 head 分支随后按仓库设置自动删除。

## 已合并治理 PR 精确身份

2026-08-31 在 #77 合并前通过 GitHub Pull Request API 重新读取以下 31 个已合并治理 PR。
表中的 head SHA 与 PR API 当前值一致；“白名单”表示对应远端分支当前仍存在且 tip 与
head SHA 一致，“已不存在”表示仓库自动删除设置已经完成清理，无需再次操作。

| PR | head ref | head SHA | merge commit | 远端分支现状 |
|---:|---|---|---|---|
| #41 | `codex/fork-governance-foundation` | `55d2501059e0c01afbda324840f1afcc629ebad2` | `0d8e1d580c4a6bfcacd7cec7067808879f5cf8fa` | 存在，白名单 |
| #46 | `codex/vite-security-fix` | `24f8745ca5df2d28b1f11fc2f01be17dc862be39` | `57320b38b89ad29869905a43c8574a8647d59913` | 存在，白名单 |
| #47 | `codex/fork-governance-stage1-evidence` | `800a0ce3af402f6afd67792fbddffa67e0ef80d1` | `47649e562d16bc5c5604d22799a1ef4595fb0883` | 存在，白名单 |
| #48 | `codex/web-chapter-content-safety` | `b46d770039727f43e6c33b1dce64a296944791fc` | `538a58156eb601d19488509868be885e715782cb` | 存在，白名单 |
| #49 | `codex/web-chapter-content-safety-evidence` | `b5b5bb4cfd9ae92a8d1a1e542feb7206ea80cbdc` | `2d61c97cfdf39297407d62372d89765c714b40bd` | 存在，白名单 |
| #50 | `codex/fork-distribution-identity` | `92a138ae69182286210e9003eba042a97cbb89c9` | `09fc2b56f1bcf7c1c5783d0698532a75487676eb` | 存在，白名单 |
| #51 | `codex/fork-distribution-identity-evidence` | `8596f3ccddf44bbe910feb74f9a0beec5b4bdfa3` | `7746729d5228d3c548b3cc940614c9f4dc7b3462` | 存在，白名单 |
| #52 | `codex/quality-blockers` | `6abcdcf4bcf56ad10f64eec73f31b9758af6015f` | `5fa69d4022c9e4df0b76225561b34330489b1f19` | 存在，白名单 |
| #53 | `codex/quality-blockers-evidence` | `a06bd944cef55150535ad928a48fae1ece12f7cc` | `f8adb3fe0b2100ecdc6412f34fac56bcf672010d` | 存在，白名单 |
| #54 | `codex/warnings-correctness-i18n` | `2a57817fce243ca813c3ded2bb4c4b4182f99ad4` | `a8b0eb2d6f5e54a737381a78d905b1c4c3634385` | 存在，白名单 |
| #55 | `codex/warnings-settext-i18n` | `96d3b0165839675224044bc34d538a047f2a26eb` | `86ee65e7313406839d104774ae518143dac60e05` | 存在，白名单 |
| #56 | `codex/warnings-hardcoded-text` | `e79236490072015a9be7999102b3311f2bb68210` | `755abede96b539fc6d2f4de2c6da3943efad5518` | 存在，白名单 |
| #57 | `codex/warnings-plurals-candidate` | `054b9391cb96640787ee38c126ea137b435d87a9` | `033dbe5db1c202ebaed97e31ad052f4dcd774873` | 存在，白名单 |
| #58 | `codex/warnings-accessibility` | `0991a53f5908ea30f0601964fc14828081593ca7` | `4eff2225d76ec0b425f22d95acc1e4dc26508094` | 存在，白名单 |
| #59 | `codex/warnings-layout-performance` | `d8b33d4770b0066ee8d051a8f21a6a1a763388d2` | `d21786470f06855b86f5767f3c625d05f39972d5` | 存在，白名单 |
| #60 | `codex/warnings-use-ktx-batch` | `3044e2c2000e0cec597f6abc56b3651f1b363ee7` | `7ec45fbb08b497cf6103f7b774387198a99508ff` | 存在，白名单 |
| #61 | `codex/warnings-use-ktx-accessors` | `d3d26e49038e7c9895f3b7f2c3c54ef2a2723842` | `8c1dd1d2915cd5644693b2b3befed52a8ce18930` | 存在，白名单 |
| #62 | `codex/warnings-use-ktx-uri` | `b27b78577be946bca10cfa67b046acd94dc6ff67` | `080ddf962b079809c0ab82ba88cfa0137936480c` | 存在，白名单 |
| #63 | `codex/warnings-use-ktx-styled-attributes` | `52cbb7509e5b3f6933b86999ce242e769d999d05` | `afff26a7ad262b1257728c063e9efa2038d8e5af` | 存在，白名单 |
| #64 | `codex/warnings-use-ktx-colors` | `547fc56ae4b56554201b8618f39810ccce89e4cb` | `eb9782f8386a94d459a971ee2f26f77bf2dfd70b` | 存在，白名单 |
| #65 | `codex/warnings-use-ktx-bitmap-drawable` | `86a018870122db41d90efae2a0ebde813c120114` | `35797d31febb7de021f36667cebe0f3de9b6234a` | 存在，白名单 |
| #66 | `codex/warnings-use-ktx-int-drawable` | `be934c6a876d6fff53b0ea3b2a98ccef1d2b0982` | `398fcae5e981a1c381fca782d868224ca9a18d48` | 存在，白名单 |
| #67 | `codex/warnings-use-ktx-create-bitmap` | `873f43aadefb60fb3691e0da0d1d1728a8484cf7` | `595174680f4206b6b64996d45e003d18adee6104` | 存在，白名单 |
| #68 | `codex/warnings-use-ktx-shared-preferences` | `25d04d1b53e0dd1ce4f4963d1dca382b2644b985` | `15956395a4389efc108569877bb4465ed26ad797` | 存在，白名单 |
| #69 | `codex/warnings-use-ktx-canvas` | `828ba3681dce8b08abd239071939cbaf5bdfb6d8` | `2b08b43634dd2c94d17f3d89ab458fc7ff25d10b` | 存在，白名单 |
| #70 | `codex/warnings-unused-resources-audit` | `180fac6ed8916d0450b7041f49b74faba64a8c07` | `d5c1065ae76898008bea2c6e8df8f45f247e119f` | 存在，白名单 |
| #71 | `codex/warnings-final-audit` | `836d4d90f1128159f88a23fcf733b1c87f8d19ea` | `4e1c3474aac3d90f9f02af940dfdfafbb0c07d17` | 存在，白名单 |
| #72 | `codex/maintenance-gates` | `973d402cfbceade68bf2525d598ea76c12d1975d` | `580f1913882e8c24f7a3b9a56dbe9d99791edd61` | 存在，白名单 |
| #74 | `codex/release-emulator-smoke` | `a5c593f8496b047caebe3ad8df572b74bf1e0f68` | `92034f3f77efb4c38ca292cfaae838c4890d0267` | 已不存在 |
| #75 | `codex/release-emulator-preflight` | `04e0a682038167b8384050d4c3c6849f7edf2467` | `31bdae63c7c625e34b1db45f7bb6087d9f2a730a` | 已不存在 |
| #76 | `codex/release-draft-tag-fix` | `147e3987847e561739ac6aaf7a44345f92b5a91c` | `cef2fbb2dbdb6771686b04c68447a6f5caea964e` | 已不存在 |

## 非 PR 证据分支判定

| 分支 | 当前 tip | 与 #77 的关系 | 处置 |
|---|---|---|---|
| `codex/release-verification-evidence-v2` | `ef01f00b422a0cf082daee72caa1da72f6404c48` | 是 #77 首个归档提交的直接祖先，归档 PR 合并后即进入 `master` | 加入白名单 |
| `codex/release-verification-evidence` | `182b67140b4eb4d1dbdace589a6c15f67447e61e` | 与 #77 的 merge-base 仅为 `31bdae63c7c625e34b1db45f7bb6087d9f2a730a`，自身 tip 未被 #77 包含 | 不满足“已合并”，保留并报告 |

`codex/archive-fork-governance` 是当前 #77 head。它不进入手工删除白名单；合并后由
`delete_branch_on_merge=true` 自动删除，并在任务 9.6 读回确认。

## 任务 9.7 精确删除白名单

白名单只由上表标记“存在，白名单”的 28 个已合并 PR head 和
`codex/release-verification-evidence-v2` 组成，共 29 条。任务 9.7 执行时必须逐条再次满足：

1. 远端 ref 仍存在；
2. tip 与本文件记录的 SHA 完全一致；
3. #77 合并后的 `master` 包含该 tip；
4. 没有打开 PR、tag 或 Release 使用该 ref；
5. 删除使用精确 ref 与记录 SHA 的 lease，不使用 glob。

任何一项不满足都保留该分支并报告。4 条普通 Dependabot 分支、未合并的
`codex/release-verification-evidence`、`master` 和任何执行期间新增分支均不在白名单。

## 归档后时序

9.4–9.5 完成后，#77 的最终 head 仍须重新通过全部聚合检查方可合并。合并后再执行 9.6
的 `master` 同步、OpenSpec/空白检查、Latest、安全、PR/issue、ruleset、分支和工作区复核；
最后按本白名单执行 9.7。由于 9.6–9.7 必然发生在归档 PR 合并后，它们的勾选与最终报告
将通过不含生产行为的纯证据收口 PR 进入 `master`。

## 任务 9.6：归档合并后的总复核

2026-08-31 07:41 +0800，本地 `master`、`origin/master` 和 PR #77 merge commit 均为
`31affc67f72051ede1f4ec1bc8ee9c0f7ca69c9f`，工作区干净。归档合并后的 `master`
GitHub Actions run
[`33341839234`](https://github.com/coding-back01/legado/actions/runs/33341839234)
在同一 SHA 上完成：CodeQL Android、CodeQL Web、OpenSpec/仓库检查和聚合 `维护门禁`
均为 success；Android/Web 质量子任务因纯文档范围按契约合法 skip。

本地 `openspec validate --all --strict` 为 6/6，通过；`git diff --check` 无输出；
`openspec list --json` 返回零个活跃变更；`git status --short` 无输出。远端复核结果如下：

- Latest 仍为 `3.26.083101`，Release ID `379347961`，公开、非预发布；唯一资产为
  `legado_app_3.26.083101_release.apk`，asset ID `536854094`、14,517,611 字节、
  SHA-256 `cd1869d2511b0ce375fc343a9e29f6f38f17f48c52bc67f18c776fda5e1a3c07`。
- tag 与 Release target 仍为发布提交
  `cef2fbb2dbdb6771686b04c68447a6f5caea964e`；当前 `master` 包含该提交且没有改动发布资产。
- Code Scanning 与 Secret Scanning 打开告警均为 0；Dependabot 打开告警为 2 条 medium、
  0 条 high/critical，均为同一 Element Plus 漏洞且 GitHub 仍未提供修复版本。
- 私有漏洞报告、Dependabot Security Updates、Secret Scanning 和 push protection 均保持启用。
- ruleset `20653588` 保持 active、无 bypass，必需检查仍为 `维护门禁`，CodeQL
  `security_alerts_threshold` 仍为 `high_or_higher`。
- 打开 issue 为 0；打开 PR 仅 #42、#44、#45、#73，均为新策略生成且按规范保留的普通
  Dependabot PR。

## 任务 9.7：精确删除与最终读回

删除前从本文件解析出 29 条唯一白名单，并逐条重新验证：远端 ref 存在、tip 与记录 SHA
一致、tip 是 `origin/master` 的祖先、没有打开 PR、没有同名 tag、没有 Release 以该分支
为 target。29/29 全部满足，0 条漂移或受阻。

删除使用每条 `refs/heads/<精确分支>:<记录 SHA>` 的独立
`--force-with-lease`，并由一次 `git push --atomic` 原子提交全部 29 个精确删除 refspec；
未使用 glob。GitHub 返回 29 条 deleted，随后 `git ls-remote --heads origin` 只读回 6 条：

- `master`：`31affc67f72051ede1f4ec1bc8ee9c0f7ca69c9f`；
- 4 条应保留的 Dependabot 分支，分别对应 #42、#44、#45、#73；
- 未合并且明确不在白名单中的 `codex/release-verification-evidence`：
  `182b67140b4eb4d1dbdace589a6c15f67447e61e`。

白名单分支删除不会删除其已合并提交、Pull Request、Actions run、artifact、Release 或本
OpenSpec 归档。最终状态没有新增或漂移对象被纳入授权范围。
