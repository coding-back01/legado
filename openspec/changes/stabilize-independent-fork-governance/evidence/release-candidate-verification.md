# 普通版发布候选验证证据

本文记录任务 8.1 及后续候选、真机和公开回验。候选验证期间正式发布继续冻结；除最终
公开候选和归档流程明确要求的操作外，不再向 `master` 合并提交。若远端 `master` 漂移，
本轮候选立即失去公开资格，保留既有草稿审计记录并从新的绿色提交重新锁定。

## 8.1 发布前总门禁与锁定提交

首次候选在 2026-08-30 19:58:23 +0800 锁定于
`31bdae63c7c625e34b1db45f7bb6087d9f2a730a`。首次 Release workflow 暴露草稿 tag
核验缺陷后，通过 PR #76 完成最小修复；2026-08-30 20:40:26 +0800 从 GitHub API
重新读取远端状态，并在 `git fetch origin master` 后确认本地 `origin/master` 一致。
当前唯一有效的锁定值为：

```text
RELEASE_SHA=cef2fbb2dbdb6771686b04c68447a6f5caea964e
```

该提交是 PR #76 的 merge commit，父提交包含旧锁定提交
`31bdae63c7c625e34b1db45f7bb6087d9f2a730a` 和修复 head
`147e3987847e561739ac6aaf7a44345f92b5a91c`。本证据分支
`codex/release-verification-evidence-v2` 直接从新锁定提交创建，未合并回 `master`。

### 有序 PR 与质量债务汇总

| 阶段 | GitHub PR | 状态与证据 |
|---|---|---|
| PR 1 治理基础 | #41 | 已合并；后续远端队列、安全设置和冻结说明已在第一阶段证据中闭环 |
| 插入的安全修复 | #46、#48；证据 #47、#49 | Vite 高危和章节 Web 内容边界已修复并合并 |
| PR 2 分发身份 | #50；证据 #51 | 已合并；更新器、链接和单普通版 Release 契约已验证 |
| PR 3 阻断错误 | #52；证据 #53 | 已合并；Android lint error 与 Web ESLint error 均清零 |
| PR 4 warning 批次 | #54–#71 | 已串行合并；全部 lint ID 已完成三态审查 |
| PR 5 持续门禁 | #72 | 已合并；稳定聚合门禁、CodeQL、ruleset 和自动删分支设置已验证 |
| PR 5b 模拟器 smoke | #74；预检证据 #75 | 已合并；API 21/API 36 发布预检已完成 |
| 候选 fail-closed 修复 | #76 | 已合并；修复草稿 tag 创建与四层 SHA 核验，旧候选失效 |

维护账本当前为 Android lint `0 error / 108 warning / 18 hint`。参考 881 个 warning 加
PR 5b 期间远端版本元数据新增的 4 个低风险提示后，最终三态为 `FIXED=765`、
`SUPPRESSED_WITH_REASON=12`、`DEFERRED=108`、`PENDING_REVIEW=0`；108 个延期项均有
精确风险、原因和重新启动条件。Web ESLint 当前为 0 error。剩余 warning、普通依赖更新
和低风险提示属于规范允许的非阻断债务，不扩大本轮设备或兼容性范围。

### 远端必需检查与安全状态

- 远端 `master` 精确等于 `RELEASE_SHA`。合并后 workflow run
  [`33311511713`](https://github.com/coding-back01/legado/actions/runs/33311511713)
  的 `head_sha` 同样等于 `RELEASE_SHA`，结论为 success。
- 该 run 的 CodeQL Android、CodeQL Web、范围识别、OpenSpec/仓库检查和聚合
  `维护门禁` 均实际成功；Android lint、单元测试、Debug 构建、Web 安全测试、类型检查、
  ESLint、Web 构建、OpenSpec 严格校验、actionlint 和维护门禁契约均已执行，没有 failure
  或 pending。
- ruleset `20653588`（`保护 master 分支`）为 active，只要求已验证的精确 context
  `维护门禁`，并对 CodeQL `high_or_higher` 安全告警执行门禁；删除、非快进和 PR 规则
  保持启用，bypass actor 为空。锁定提交上的 `维护门禁` 为 success。
- Code Scanning 打开告警为 0；Secret Scanning 打开告警为 0。Dependabot 打开告警为
  2 条 medium、0 条 high/critical；两条均为同一
  `GHSA-5m5x-9j46-h678` / `CVE-2025-57665`，分别映射
  `modules/web/package.json` 与 `modules/web/pnpm-lock.yaml`，不是两个独立漏洞。
- GitHub 当前仍报告 Element Plus `<= 2.11.0` 受影响且 `first_patched_version=null`。
  现有 11 个 `el-link` 的 `href` 均为源码固定值，沿用已记录的中危接受理由；出现修复版本
  或引入动态 `href` 时必须重新启动处置。
- Dependabot Security Updates、Secret Scanning、push protection 和私有漏洞报告均为
  enabled。仓库合并后自动删除 head branch 为 enabled。

### 首次候选失败与修复审计

- 首次手动 run
  [`33310335819`](https://github.com/coding-back01/legado/actions/runs/33310335819)
  使用 `expected_sha=31bdae63c7c625e34b1db45f7bb6087d9f2a730a` 和 `--ref master`；其
  `head_sha` 精确等于该旧锁定值。授权核对与唯一普通 APK 构建成功，但“创建并核对草稿
  Release”任务失败。
- 根因是 GitHub draft Release 不会自动创建可通过 Git refs API 读取的真实 tag；旧
  workflow 在创建草稿后立即读取该 tag，因 404 必然失败。失败草稿 ID `379270869`、
  tag name `3.26.083020`、target
  `31bdae63c7c625e34b1db45f7bb6087d9f2a730a`，保持 `draft=true` 且未公开；对应真实
  `refs/tags/3.26.083020` 不存在。
- 失败草稿只含 `legado_app_3.26.083020_release.apk`：asset ID `536572222`、size
  `14517602`、远端 digest
  `sha256:d22ed83e70e67b7203320c23e475d373d46cbe525a38ef90fa6ead6972955013`。
  该记录不得删除或公开，也不得用作任务 8.3–8.10 的 APK 或真机候选。
- PR [#76](https://github.com/coding-back01/legado/pull/76) 在草稿前重新核对远端
  `master`，拒绝覆盖同名 Release/tag，无 force 地创建精确指向 `expected_sha` 的轻量
  tag，再创建草稿并核对 workflow `head_sha`、tag commit、Release target 与
  `expected_sha`。`ReleaseWorkflowContractTest` 新增契约先使 9 项中的 1 项出现 RED，
  修复后 9/9 GREEN；维护门禁契约 12/12、OpenSpec 契约 3/3、actionlint 1.7.12 和
  `git diff --check` 均通过。
- PR #76 head `147e3987847e561739ac6aaf7a44345f92b5a91c` 的 run
  [`33311077955`](https://github.com/coding-back01/legado/actions/runs/33311077955) 全绿，
  merge commit 为当前 `RELEASE_SHA`；合并后的 run `33311511713` 也全绿。因此旧候选
  已失去公开资格，当前候选从新提交重新锁定。

### 仓库队列、Latest 与模拟器结果

- 打开 issue 为 0。打开 PR 只有新的 Dependabot 普通更新 #42、#44、#45、#73；它们
  不在本次候选操作范围内，不关闭、不合并、不删除分支。
- 当前 Latest 仍为公开正式版 `3.26.082216`，目标提交
  `460970675fedb91d8d10aa42447bab8cc13e8a40`，冻结说明和两个历史资产未变化。
- PR 5b 的全部 8 个检查成功。PR #75 的 6 个实际检查成功，Android/Web 文档范围子任务
  合法跳过。合并后 `master` 的最终聚合门禁也成功。
- API 21 AOSP 模拟器发布 smoke 为 2/2，通过耗时 4.968 秒；API 36 为 2/2，通过耗时
  11.914 秒。API 36 的启动导航、公开 RecyclerView、真实软键盘、一次语言切换、7 个
  动态图标和受控 RTL 检查未发现实际回归。原始证据仍只位于 Git 忽略且权限受限的
  `build/release-emulator-evidence/`，版本化文件只保存摘要和哈希。

综上，当前只剩规范允许的非阻断债务；新锁定 SHA 的必需检查绿色，高危/严重安全告警为
零，任务 8.1 在新提交上重新完成。尚未针对新 `RELEASE_SHA` 触发 Release，指定真机门禁
也尚未开始；正式发布继续冻结。
