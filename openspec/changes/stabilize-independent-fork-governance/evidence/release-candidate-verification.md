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

## 8.2–8.4 新草稿候选、APK 与提交身份核验

2026-08-30 21:03:07 +0800 在最终只读门禁确认 `master`、合并后门禁、安全告警、Latest、
失败草稿和下一个版本名均未漂移后，以
`expected_sha=cef2fbb2dbdb6771686b04c68447a6f5caea964e` 和 `--ref master` 手动触发
Release workflow。run
[`33313117814`](https://github.com/coding-back01/legado/actions/runs/33313117814) 于
21:10:56 +0800 成功结束，授权核对、唯一普通 APK 构建、草稿创建与 SHA 核验三个 job
全部成功；run `head_sha` 精确等于 `RELEASE_SHA`。

新草稿 Release ID 为 `379285557`，tag name 为 `3.26.083021`，保持 `draft=true`、
`published_at=null` 和 `prerelease=false`。它只含一个资产：

| 字段 | 核验值 |
|---|---|
| asset ID | `536629304` |
| 名称 | `legado_app_3.26.083021_release.apk` |
| 状态 / MIME | `uploaded` / `application/vnd.android.package-archive` |
| GitHub API 大小 | `14,517,617` 字节 |
| 本地下载大小 | `14,517,617` 字节 |
| GitHub API SHA-256 | `7c1fc5e2bce8e92259bf57160efca58563637d6408cfad519194604aed4d850f` |
| 本地 SHA-256 | `7c1fc5e2bce8e92259bf57160efca58563637d6408cfad519194604aed4d850f` |

下载进程正常退出后，`unzip -t` 报告没有压缩数据错误。Android SDK build-tools 35.0.0
的 `aapt dump badging` 读取到包名 `io.legado.app.release`、versionCode `16658`、
versionName `3.26.083021`、compileSdk 36、minSdk 21 和 targetSdk 36。
`apksigner verify --verbose --print-certs` 报告 v1、v2、v3 签名均通过，唯一签名者证书
SHA-256 为 `14b0c0828372820a20687221a0f8a8b02603f409cc096f7101ba38f182205283`，与当前
公开 Latest 普通 APK 的正式证书摘要完全一致。

四层提交身份于 21:21:30 +0800 再次独立读取并全部一致：

| 层级 | SHA |
|---|---|
| `RELEASE_SHA` | `cef2fbb2dbdb6771686b04c68447a6f5caea964e` |
| workflow `head_sha` | `cef2fbb2dbdb6771686b04c68447a6f5caea964e` |
| `refs/tags/3.26.083021` commit | `cef2fbb2dbdb6771686b04c68447a6f5caea964e` |
| Release `target_commitish` | `cef2fbb2dbdb6771686b04c68447a6f5caea964e` |

此时远端 `master` 仍等于同一 SHA，公开 Latest 仍为 `3.26.082216`；旧失败草稿
`3.26.083020` 及其资产保持原样。新草稿只取得进入指定真机验证的资格，尚未公开或设为
Latest；正式发布继续冻结。

综上，当前只剩规范允许的非阻断债务；新锁定 SHA 的必需检查绿色，高危/严重安全告警为
零，任务 8.1–8.4 已在新提交与新草稿上完成。新草稿只取得进入指定真机验证的资格，
尚未取得公开资格。

## `3.26.083021` 真机尝试失败与候选废弃

2026-08-30 夜间实时读取到用户指定设备为 Hisense HLTE556N、Android 11 / API 30；
版本化证据不记录设备序列号。安装前普通版为 `3.26.082216` / versionCode `16572`，
首次安装时间为 `2026-08-21 17:36:54`，APK SHA-256 为
`397dafa82918ec4bb8c588eca90285660fcb5b3f2ed07452e73099dbaa5a8412`，证书 SHA-256 为
`14b0c0828372820a20687221a0f8a8b02603f409cc096f7101ba38f182205283`。`ReaderProvider`
原始响应直接流入内存计数器且未回显、未落盘，书架为 1、书源为 230。

仅执行 `adb install -r` 后命令返回 `Success`；没有使用降级、卸载或清数据参数。安装后
普通版为 `3.26.083021` / versionCode `16658`，首次安装时间不变，安装 APK SHA-256 与
草稿资产一致，证书与安装前一致，书架仍为 1、书源仍为 230。随后收敛到显式
`MainActivity` 的有效观察窗口连续 8 秒保持主界面，任务栈根和顶部均为主界面；书架标记
存在，没有隐私协议，目标 PID 的 3 秒日志窗口没有 `FATAL EXCEPTION` 或
`AndroidRuntime`。

离线假书源路径通过
`legado://import/bookSource?src=<URL 编码 JSON>` 打开仅含唯一假源的预览，只点击取消后
返回主界面；书源总数前后均为 230，唯一假源 URL 前后均不存在，没有确认导入、隐私协议
或崩溃。有效路径的脱敏证据哈希如下：

- 收敛后 UI hierarchy：`06b8da3f...`
- 收敛后截图：`01673194...`
- 假书源预览 UI hierarchy：`99c81220...`
- 假书源预览截图：`9eb355db...`
- 取消后截图：`d403ee47...`

但是首次桌面启动及早期主界面启动尝试受到设备既有独立任务栈影响，自动恢复到已有
`ReadBookActivity`。自动化没有点击或翻页并立即停止应用，但源码审计确认
`ReadBookViewModel.initData(...).onFinally { ReadBook.saveRead() }` 最终会更新并持久化
`durChapterTime`、章节索引和章节位置。安装前只采集了书架数量，没有采集逐书阅读进度的
脱敏聚合摘要，因此不能自动证明这次误入没有改变用户阅读状态；不得用后续主界面和数量
不变量替代该缺失证据。

因此草稿 `3.26.083021` / Release ID `379285557` 被永久标记为“设备验证失败，禁止
公开”，继续保持 `draft=true`，保留 tag、资产、workflow 和本地原始证据作为审计记录，
不删除、不替换、不设为 Latest。任务 8.5–8.10 在该候选上不完成，正式发布继续冻结；
下一候选必须在安装前后使用同一仅存于权限受限本地目录的随机密钥，对按稳定伪标识排序的
`durChapterIndex`、`durChapterPos`、`durChapterTime` 生成聚合 HMAC，并在任何启动动作前
精确清理仅属于普通版的残留任务栈。

## 8.5–8.10 `3.26.083101` 指定真机验证

### 新候选与静态身份

2026-08-31 01:05:58 +0800 在再次确认远端 `master`、维护门禁、安全告警、Latest 与
失败草稿均未漂移后，以同一 `RELEASE_SHA` 手动触发 Release workflow
[`33324289372`](https://github.com/coding-back01/legado/actions/runs/33324289372)。run 于
01:12:30 +0800 成功结束，`head_sha` 精确等于 `RELEASE_SHA`，授权、唯一普通 APK 构建、
草稿创建与 tag/Release target 核验三个 job 全部成功。

新草稿 Release ID 为 `379347961`，tag name 为 `3.26.083101`，保持 `draft=true`、
`published_at=null` 和 `prerelease=false`。草稿只含资产 ID `536854094`：
`legado_app_3.26.083101_release.apk`，状态为 `uploaded`，MIME 为
`application/vnd.android.package-archive`，远端与本地大小均为 14,517,611 字节，远端与
本地 SHA-256 均为
`cd1869d2511b0ce375fc343a9e29f6f38f17f48c52bc67f18c776fda5e1a3c07`。

独立下载后 `unzip -t` 没有压缩数据错误；`aapt dump badging` 读取到包名
`io.legado.app.release`、versionCode `16658`、versionName `3.26.083101`、compileSdk 36、
minSdk 21 和 targetSdk 36。`apksigner` 确认 v1、v2、v3 签名均通过，唯一签名者证书
SHA-256 为 `14b0c0828372820a20687221a0f8a8b02603f409cc096f7101ba38f182205283`。
workflow `head_sha`、`refs/tags/3.26.083101`、Release `target_commitish`、远端
`master` 与 `RELEASE_SHA` 五者均为
`cef2fbb2dbdb6771686b04c68447a6f5caea964e`。

### 安装前实时基线

执行时通过 `adb devices -l` 重新确认唯一连接设备为 Hisense HLTE556N，Android 11 / API
30；设备序列号不进入版本化证据。旧失败候选留下的实际 activity stack 已为空，系统仅保留
`sz=0` 的 RecentTaskInfo 缓存。为使该设备允许 shell 访问 `ReaderProvider`，先以
`NEW_TASK | MULTIPLE_TASK | CLEAR_TASK` 原始 flag 显式启动 `MainActivity`；3 秒后根/顶部均
为主界面，实际 task 中不存在 `ReadBookActivity`。这一启动发生在安装前摘要采集之前，
保证新候选安装前后的比较使用对称主界面状态。

安装前普通版为 `3.26.083021` / versionCode `16658`，首次安装时间仍为
`2026-08-21 17:36:54`。安装 APK SHA-256 为
`7c1fc5e2bce8e92259bf57160efca58563637d6408cfad519194604aed4d850f`，证书 SHA-256 与新
候选一致。包路径已只读记录在本地权限受限证据中，不写入版本化文档。

`ReaderProvider` 的书籍和书源原始响应直接通过管道进入本地计数器，未回显、未落盘；书架
为 1、书源为 230。随机 HMAC 密钥只保存在本地权限 `600` 文件中；按书籍 URL 的稳定伪标识
排序后，将 `durChapterIndex`、`durChapterPos`、`durChapterTime`、`durChapterTitle` 和
`lastCheckCount` 共同纳入阅读状态聚合，安装前摘要为
`0c9859596ad5ed72f2b0257daa84c290d12cb6e8607078d483b90ef6f248a67c`。该摘要无法在缺少
本地随机密钥时用于推断书名、URL、路径或单本书进度。

### 原位升级与主界面观察

确认安装前 APK 与候选证书相同后，只执行 `adb install -r`；命令返回 `Success`，没有使用
降级、卸载、清数据或其他安装参数。安装后普通版为 `3.26.083101` / versionCode `16658`，
首次安装时间不变；从设备拉取的安装 APK SHA-256 与新草稿完全一致，证书也与安装前和候选
一致。

包替换后再次确认没有实际 Legado stack，随后使用同一原始 flag 显式启动
`MainActivity`。连续 8 秒后 resumed activity、任务栈根与顶部均为主界面，实际 task 中没有
`ReadBookActivity`；UI hierarchy 存在 `rv_bookshelf`，没有隐私协议。目标 PID 日志没有
`FATAL EXCEPTION` 或 `AndroidRuntime`。此时书架仍为 1、书源仍为 230，阅读状态聚合摘要
与安装前完全一致。

### 离线假书源预览取消

使用唯一名称 `Codex Release Smoke 3.26.083101` 和只指向本机 loopback 丢弃端口的 URL
构造内联 JSON，经 `legado://import/bookSource?src=<URL 编码 JSON>` 打开预览，不依赖公网。
首次通用 VIEW 调用只打开系统 `ResolverActivity`，没有进入应用、没有点击任何候选项，也
没有改变书源；自动化只发送系统返回键关闭选择器。随后按清单中精确导出的
`OnLineImportActivity` 重新打开同一 URI，预览显示唯一假源和 `tv_cancel`。

自动化只根据 `tv_cancel` 的 UI bounds 点击取消，没有点击 `tv_ok`、确认导入或任何书籍。
取消后返回 `MainActivity`，没有出现隐私协议、阅读页或崩溃。书源总数前后均为 230，唯一
假源 URL 前后命中数均为 0；书架仍为 1，阅读状态聚合摘要仍与安装前一致。版本化脱敏证据
哈希如下：

- 主界面 UI hierarchy：`a86bdeb042554f512f95a3ec318fa166ffc7b30ec957fa936fa95a7d460a3909`
- 主界面截图：`53f2adf8d502b3489dc0c7f1ddc4d98be21ef8ba3ce4181f254259d437495b5a`
- 主界面目标 PID 日志：`b4831eea11f07855e6eb4f523631a6def7819026f7a20d50a8837fa2b90b47a9`
- 假书源预览 UI hierarchy：`577cadf84cad41ad4fbf0c9d06692b1c16572c54a3ca21a0a6821e47d5e1ff13`
- 假书源预览截图：`c7563f7b99384adc7e704acb77eddcdfacfb7a65130b0908563f7c6f2ec91e6d`
- 取消后 UI hierarchy：`a86bdeb042554f512f95a3ec318fa166ffc7b30ec957fa936fa95a7d460a3909`
- 取消后截图：`f85f4412d7ad5b417e6f6aa8e2b617977fb84e6f3de4acf9de03c270d13cdd4a`
- 最终目标 PID 日志：`386e098c6b8d5311a8617a9ab120f45254c7b3b9a93413d9ddf67c4f88f87e06`

全部原始 APK、随机密钥、计数摘要、日志、截图和 UI hierarchy 只保存在 Git 忽略目录
`build/release-device-evidence/3.26.083101/`，目录权限为 `700`，文件权限均为 `600`；原始
ReaderProvider JSON 从未写入文件。完成采集后应用已 force-stop。由此任务 8.5–8.10 在新
候选上完成；`3.26.083101` 仍是草稿，正式发布继续冻结到 8.11 最终远端读回通过。
