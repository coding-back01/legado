## Context

参见 `proposal.md` 的 Why。本设计处理的是一个跨 Android、Vue、GitHub Actions、仓库设置、公开文档和正式发布的总治理变更，不能靠单个 lint 修复或一次批量依赖升级完成。

当前关键约束如下：

- Android 基线为 14 个 lint error、881 个 warning、18 个 hint；`:app:assembleAppDebug` 可成功，说明主要阻断来自静态检查而非现有编译错误。
- 网页端类型检查与构建可成功，ESLint 有 2 个错误；`modules/web/` 没有版本化 `pnpm-lock.yaml`，当前 CI 使用 pnpm 9 但执行非冻结安装。
- 当前 30 个打开的 Pull Request 全部来自 Dependabot，实际打开 issue 为 0；旧配置按周运行并允许 Gradle 队列达到 20 个。
- 现有验证工作流只执行 Android 单元测试和 Debug 构建，未执行 Android lint、网页端 ESLint、OpenSpec 严格校验或 CodeQL。
- 更新器硬编码请求已撤空的 `gedoor/legado`；默认通道又依赖不认识当前独立签名的变体推断。若只改 URL，Latest 中普通版与 `releaseA` 会被同时归类为普通版，且版本解析会丢掉末两位。
- `releaseA` 不是 Gradle 正式变体，而是 Release workflow 在矩阵 runner 中临时把 `.release` 改写为 `.releaseA` 后再次执行同一构建。因此停止未来产物不需要删除历史兼容代码或迁移设备数据。
- 当前 Git 历史保留现有上游快照中的贡献者，但 GitHub 将 `coding-back01/legado` 识别为独立仓库；用户可见身份必须主动说明衍生关系。
- 最低 API 21、普通正式版 `io.legado.app.release`、既有私有签名、Room schema、书源/订阅源规则、导入 URI 和备份格式属于不可顺手突破的边界。

## Goals / Non-Goals

**Goals:**

- 用一个可归档的 OpenSpec 总变更保存统一完成定义，同时用串行小 PR 降低审查和回滚风险。
- 把静态检查、依赖、安全、反馈、分发、设备验证和正式发布连成失败即停止的证据链。
- 修复当前已确认的质量和运行时故障，并让剩余债务具有明确状态，不要求以高风险重构换取表面绝对清零。
- 让当前 fork 的身份、更新器、普通版 APK 和 GitHub 入口保持一致，同时保留上游版权和历史来源。
- 在不要求用户手动点测、不污染正式数据的前提下形成模拟器与指定真机的互补验证。

**Non-Goals:**

- 不成为 Legado 社区继任项目，不自动同步 `zj970/legado`、`LegadoTeam/legado` 或其他仓库。
- 不处理普通功能请求、厂商专属新设备能力或与当前维护者设备无关的兼容扩展。
- 不提高最低 SDK，不修改 Room schema、规则/备份格式、导入 URI 或普通正式版签名身份。
- 不要求 881 个 warning 绝对归零，不机械删除无法排除动态引用的资源，不全量执行 `UseKtx` 替换。
- 不升级带兼容性注释的固定依赖，不把工具链大版本升级混入普通维护。
- 不删除历史 `releaseA` APK、包名认知、运行时兼容代码或设备数据，也不设计普通版自动接管 `releaseA` 数据。
- 不在本变更中建设自定义月报、跨仓库巡检服务或自动合并机器人。

## Decisions

### 1. 一个总变更、串行 PR、发布后归档

采用以下有向序列，除阻断当前发布的紧急安全修复外，同一时间只保留一个治理 PR：

```text
人工审阅并明确批准 OpenSpec 四工件与精确远端操作
          │
          ▼
PR 1 治理入口与机器人策略 ──▶ 远端队列清理/安全功能第一阶段
          │
          ▼
PR 2 分发身份、更新器、链接与 releaseA 退役
          │
          ▼
PR 3 Android/Web 阻断错误与高风险 warning
          │
          ▼
PR 4a...4n 按风险类别清理 warning 并持续更新账本
          │
          ▼
PR 5 持续 CI、CodeQL、最终维护基线 ──▶ master ruleset
          │
          ▼
PR 5b 模拟器发布测试夹具与自动化
          │
          ▼
锁定绿色 master SHA → 手动触发草稿 Release → APK 核验 → 指定真机 → 公开普通版
          │
          ▼
PR 6 OpenSpec 归档与最终分支清理
```

本提案创建完成不等于授权实施；只有用户在人工审阅后明确批准四工件和其中列出的远端操作，才可执行第一项实施任务。每个 PR 只在前一阶段合并并验证后开始。未合并的失败在原分支修正；已合并后确认回归则新建 revert PR，不重写 `master`。选择串行而非并行，是因为 warning 数量、CI 检查名、Dependabot 队列和 Release workflow 存在直接依赖；并行会造成基线漂移并使回滚难以归因。

### 2. 先修真实根因，再建立 warning 三态账本

14 个 lint error 按已确认根因处理，不生成全局 baseline：

- 将运行时已经存在但编译 classpath 不可见的 AndroidX Startup 声明为直接 `implementation`，使 Manifest Provider 的依赖归属明确；不以 `MissingClass` 全局抑制代替。
- 将并发 key set 的公开字段类型显式收窄为 `MutableSet<String>`，保留现有并发实现和 desugaring，避免源码检查绑定 API 24 的具体 `KeySetView`。
- 为位掩码 `@IntDef` 声明 `flag = true`。
- 使用 RecyclerView 已公开的 orientation typedef，删除重复且不兼容的自定义 typedef。
- 将输入法调用从结果常量改为输入 flag `SHOW_IMPLICIT`，避免碰巧同值导致的强制弹出行为。
- 用公开 `RecyclerView` 类型访问导航列表，不继续依赖 Material 内部类。
- 补齐现有 locale 缺失条目并恢复默认资源语言；翻译和布局通过资源检查、自动截图与 UI 层级验证，无法可靠判断的纯装饰问题可延期，但 lint error 不可延期。

网页端两个错误分别通过移除未使用声明和用具体结构类型替换 `any` 解决，不顺带执行全项目格式化。新增 `modules/web/pnpm-lock.yaml`，在 `package.json` 固定 `packageManager: "pnpm@9.15.9"`，本地与 CI 使用同一版本和冻结安装；锁文件首次解析出的版本必须单独审计，不能借机修改依赖范围或引入未经验证的大版本升级。OpenSpec CI 显式使用 `@fission-ai/openspec@1.8.0`，避免全局工具漂移。

Warning 以 lint XML 为机器事实源，`docs/maintenance-baseline.md` 按 lint ID 保存原始数量、当前数量、风险以及 `FIXED`、`SUPPRESSED_WITH_REASON`、`DEFERRED` 各状态数量；同一 ID 可以同时包含多种最终处置。实施期间尚未审查的 ID 或范围临时标记为阻断完成的 `PENDING_REVIEW`。`FIXED` 以批次、PR 和验证证据汇总，只有局部抑制和延期项在版本化文档中列出精确位置或范围；完整逐 occurrence 对账由 CI artifact 保存。处理顺序为正确性与启动安全、国际化/RTL、无障碍、性能、可证明安全的机械替换、动态资源审计；`UnusedResources` 只有在静态和运行时命名引用均可排除时才删除，`UseKtx` 单独成批且不得掩盖语义变化。

### 3. 更新器拆成确定性解析与网络边界

更新网络层只请求 `https://api.github.com/repos/coding-back01/legado/releases/latest`。Release JSON 模型增加完整 `tag_name`；纯解析层执行以下契约：

1. 校验 tag 符合当前 `3.yy.MMddHH` 固定宽度格式；未来改变格式时先修改契约和测试，解析器不得猜测。
2. 只接受状态为 uploaded、MIME 为 Android APK 且文件名精确等于 `legado_app_<tag>_release.apk` 的唯一资产。
3. 明确拒绝 `_releaseA.apk`、prerelease/beta、其他 APK、零候选和多候选。
4. 将完整版本拆成数字字段比较，覆盖同日更晚小时、跨日、跨月和跨年；不使用 `dropLast(2)` 或普通字符串偶然顺序。
5. 区分“已经是最新版本”、HTTP 错误、空体、畸形 JSON、无效 Release 和超时，确保等待框在成功与失败路径都关闭。
6. 正式版只接受固定宽度版本；带 `debug` 后缀的 Debug 安装不参与稳定版版本比较，手动检查时返回明确的“Debug 构建不支持正式更新”结果且不提供下载。

面向用户的 `updateToVariant` 列表从设置页删除。该 key 可继续留在备份和旧 SharedPreferences 中，但更新路径永久忽略全部旧值，因此恢复旧备份也不会重新激活 beta 或 `releaseA`。当前独立签名不再参与更新通道推断；用于识别历史安装包的 `AppVariant`/`releaseA` 兼容代码可以保留。

解析、选择和版本比较迁到 JVM 可测试边界，以本地 JSON fixture 覆盖资产顺序互换、同时间戳、错误 MIME/state、缺失/重复/畸形资产、所有旧偏好和网络错误。现有依赖 live upstream 且硬编码资产数量的 `UpdateTest` 不再作为 PR 确定性门禁；live GitHub 只保留发布阶段烟测。

### 4. 链接按“当前功能、本地资产、历史来源”分类

不做全局 `gedoor → coding-back01` 替换，逐类处理：

- **当前功能**：更新 API、分享下载、当前 Release/Issue/源码、贡献者、本地 Web 首页、`package.json` 仓库元数据和 bug 表单指向 `coding-back01/legado`。
- **本地资产**：默认 RSS 的失效 jsDelivr 图标置空并使用既有 `image_rss` fallback；失效帮助图片复制到 assets 后用相对路径；免责声明链接改成本地文件；可恢复的历史更新日志固化为本地归档或不可变提交。
- **上游来源**：LICENSE 中 gedoor 版权、历史 issue/PR、仍在线的 Web 子项目、依赖坐标、官网和社区保持真实来源；README/English 将官网、Google Play、Telegram、Discord、Yuque 等标为上游资源而非本 fork 支持渠道。

README 和 English 同步加入个人 fork、独立签名、有限设备验证、无官方背书、应用不提供内容等身份说明。应用分享和贡献者文案同步所有现有 locale；不借治理之名重写无关翻译。无法恢复的历史内容用明确缺失说明替代，不伪造本仓库不存在的 Wiki、Discussion 或分支。

### 5. releaseA 只退役生成链，不删除历史兼容

先增加 Release workflow 契约测试，并确认旧的双 APK workflow 因 `releaseA` 矩阵、动态改包和双资产约定出现预期 RED；再修改 workflow 使测试转为 GREEN。新 workflow 移除 `release/releaseA` 矩阵和 `sed` 包名改写，只执行一次 `:app:assembleAppRelease`，固定整理为 `legado_app_<version>_release.apk`，草稿 Release 也只下载和核对这一份 artifact。它必须接受并核对锁定的 `expected_sha`，记录实际 `github.sha`，且只允许 tag 与 Release target 指向同一 SHA。`app/build.gradle` 中永远不可达的 `releaseA` 应用名分支可以删除，但包识别枚举、旧偏好 key 和历史说明只在其不会重新启用分发时保留。

三个现有 Release 的 `releaseA` 资产不修改。设备上的 `io.legado.app.releaseA` 是独立 Android 沙箱；普通版不能同包升级、读取或接管其数据。现有 Latest 仅补充稳定化暂停和未来停止 `releaseA` 的说明，不撤回、不改成 prerelease。

### 6. GitHub 远端变更分阶段落地

PR 1 创建并完成本地/PR 配置检查后、合并前，只允许执行两个保证入口原子可用的远端前置：
按精确名称创建并读回 stale 所需的 `needs-info`、`crash`、`data-loss`、`security`、`stale`
标签，以及启用并读回私有漏洞报告、验证表单目标可用。任一前置失败则不合并 PR 1；此时
不得提前启用其他安全能力、关闭 Pull Request、删除分支或修改 Release。

PR 1 合并后才执行其余第一阶段远端操作：

1. 确认新 Dependabot 配置已在默认分支生效。
2. 重新获取打开 PR及远端分支，只操作 `tasks.md` 中批准且身份仍匹配的精确清单；新增对象不自动纳入。
3. 更新现有 Latest 的冻结说明，不改变历史资产或 Latest 身份。
4. 启用仓库可用的 Dependabot alerts、Dependabot Security Updates 和 Secret Scanning，确认私有漏洞报告仍可用，并记录 API 返回状态。
5. 立即读取可用的 Dependabot 与 Secret Scanning 告警；高危/严重项阻止 PR 2 开始，中危记录判断，低危进入清单。

CodeQL 作为版本化 workflow 在后续 PR 引入，使用最小必要权限：至少 `contents: read` 与上传 SARIF 所需的 `security-events: write`，只有分析确实需要时才增加 `packages: read`；来自 fork 的 PR 走不暴露 Secrets 的降权路径。CodeQL 扫描成功只证明分析完成，不代表没有告警，因此 PR 5 合并前必须审查其 PR/分支分析结果，合并到 `master` 后再次读取告警。

所有计划加入 ruleset 的必需 context 必须在每个 PR 和 `master` 提交上稳定出现。Android、Web 等耗时子任务可以按路径合法跳过，但启用原生 code-scanning rule 后 CodeQL 必须在每个 PR 和 `master` 提交上实际运行；workflow 级 `paths` 不得导致 required context 或 CodeQL 分析缺失。使用 `if: always()` 的聚合 gate 区分成功、失败和合法跳过，并对任何实际失败返回失败。只有这些聚合 context 和 CodeQL 结果已在目标提交成功出现，才记录现有 ruleset 的精确 ID 和完整配置快照，做不削弱既有条件、bypass 或规则的最小更新，并读回验证。合并后自动删分支属于独立仓库设置，也要单独快照和核验。

普通依赖按月分组，各 ecosystem/update entry 的 `open-pull-requests-limit` 之和不得超过 5；安全更新独立但不自动合并。失效 `autoupdatefork.yml` 删除；`web.yml`、`cronet.yml` 和 `stale.yml` 分别按当前 fork 的实际需要重构或删除旧仓库身份门槛，不机械启用原上游写操作。

### 7. 反馈入口按隐私与维护容量收敛

删除功能请求表单和不存在的 Wiki/Discussion contact link。Bug 表单要求当前 fork 版本、设备、复现步骤、日志范围和脱敏确认，并明确不承诺 SLA；疑似凭据或安全问题转私有漏洞报告。stale workflow 如保留，只配置 `only-labels: needs-info`、30 天后直接关闭或使用等价的单一期限，并排除崩溃、数据损坏和安全标签；不再给全部 issue/PR 套用 30+5 天关闭。

### 8. 模拟器负责写数据 E2E，真机只做非破坏性发布证据

模拟器使用 `appDebug` 和一次性数据：尽力准备 API 21 与 API 36 AVD，安装离线本地 TXT fixture，经文件关联导入后用 instrumentation 直接读取自绘正文页的文本，断言唯一 sentinel；翻页后断言测试进度增加，返回书架后断言测试书存在。正文是 Canvas 自绘视图，截图/OCR 只能辅助，不作为唯一断言。优先沿用现有 AndroidX Test/Espresso；只有现有能力无法稳定驱动系统级路径时才增加 UIAutomator 的 test-only 依赖，并记录理由。

模拟器测试夹具、instrumentation 代码和 test-only 依赖归入独立的串行 PR 5b；该 PR 合并并确认 `master` 全部门禁绿色后，才运行发布用模拟器预检和生成候选。

指定真机在执行时重新读取设备、Android 版本、包列表、版本和签名，不使用历史记录推断。正式包流程只把可自动取得且不需要 `run-as` 的项目作为硬不变量：包名、版本、证书摘要、首次安装时间，以及通过 `ReaderProvider` 查询后仅在内存中计数的书架和书源数量。当前 RSS URI 映射不能提供独立可靠的 RSS 数量，因此不将其作为证据。正式包无法读取的 SharedPreferences 不作为硬门禁，只验证外部可观察状态。流程为：

1. 只读采集现有包名、签名、版本、首次安装时间、书架数量和书源数量。
2. 仅在候选与现有普通版同签名时执行 `adb install -r`；失败即停止，不卸载、不清数据。
3. 启动应用，采集 top activity、logcat、截图和 UI hierarchy；如果出现隐私协议，停止并报告，不替用户同意。
4. 验证书架壳与上述允许列表中的数据不变量。允许应用正常启动产生版本时间戳或后台状态等附带写入，但不主动新增、删除或修改用户内容。
5. 使用内联、离线、唯一命名的假书源打开预览，只点击取消，再次查询前后数据必须一致。

不得在用户真机运行整个 `connectedAppDebugAndroidTest`，因为现有部分设备测试会联网或写测试数据库；不得打开已有书翻页，因为退出也会保存阅读位置。用户只需连接并指定设备版本，不承担手动点测。`ReaderProvider` 原始 JSON、书名、URL 和路径不得落盘或进入提交；logcat 只采集目标 PID 和限定时间窗，截图与 UI hierarchy 在采集前尽量收敛页面。允许保留的原始日志与界面证据只放在 Git 忽略且权限受限的本地目录，版本化证据只记录脱敏后的数量、状态、摘要和哈希。

### 9. 发布采用草稿、逐层核验、真机后公开

PR 5b 合并并完成适用模拟器预检后，从远端 `master` 锁定一个全部门禁绿色且无未处置高危/严重告警的 `RELEASE_SHA`；候选验证期间不再合并新提交，若 `master` 漂移则取消本轮候选的公开资格、保留草稿审计记录并重新锁定。仓库所有者以该 SHA 作为 `expected_sha` 手动触发 Release workflow，先生成草稿或等价不可见候选。下载 APK 后依次核对 GitHub API `size`/`digest`、本地字节与 SHA-256、`unzip -t`、`aapt dump badging` 和 `apksigner verify --verbose --print-certs`。只有 workflow `head_sha`、tag commit、Release target 都等于 `RELEASE_SHA`，且包名、版本、最低/目标 SDK、签名指纹和文件完整性全部符合预期，才进入指定真机。

全部证据通过后公开为 Latest，并从另一个干净上下文回验 Release API 与更新器都唯一选择普通版。Release 说明记录独立签名、包名、对应提交、历史 `releaseA` 停止更新以及跨签名不能覆盖的迁移风险。完成回验后才创建最终 OpenSpec 归档 PR。

### 10. 失败与范围扩张均采用显式状态

本变更不以“尽可能”掩盖状态：检查结果只能是通过、失败、未运行/受阻；warning 在实施期可以为阻断完成的 `PENDING_REVIEW`，最终只能进入三态。任一硬门禁失败就停止后续 PR、远端 ruleset、Release 和归档。实现中发现的新安全阻断可以插入独立 PR；普通依赖和低危发现进入维护账本。若发现必须改变最低 SDK、持久化格式、签名、包名、用户数据或产品维护承诺，必须先更新 OpenSpec 工件并重新取得用户确认。

## Risks / Trade-offs

- [Warning 清理量大，机械修改可能引入行为回归] → 按 lint ID 和风险类别拆分 PR，先写聚焦测试；动态资源与语义不明项延期，不追求数字美观。
- [首次加入 Web lockfile 会固定一组此前漂移的传递版本] → 使用 pnpm 9 生成，审计 lockfile 选择和许可证，冻结安装验证通过；不同时修改 manifest 版本范围。
- [更新器严格匹配会在 Release 命名漂移时拒绝更新] → 选择 fail-closed 并返回可区分错误；修改命名契约必须同步测试和 workflow，优于下载错误包。
- [停止 releaseA 会让仍使用该包的用户不再收到后续版本] → 保留历史资产和数据，明确停止更新；不发布未经验证的新共存包，也不承诺自动迁移。
- [自动截图无法证明所有翻译和自绘正文正确] → UI 文本/层级和 instrumentation 作为主断言，截图辅助；无法可靠判断的装饰问题记录延期，不声称通过。
- [真机正常启动可能触发后台刷新或版本状态写入] → 只比较允许列表中的用户内容数量和外部状态，不执行主动内容修改路径；需要绝对零写入时只能停在包元数据检查并保持发布冻结。
- [GitHub PR、分支、检查名和安全功能状态会在实施期间漂移] → 每次远端操作前重新读取，并只操作工件批准的对象；功能不可用或 API 拒绝时记录事实，不伪造已启用。
- [设备或模拟器不可用会影响证据完整度] → 指定真机不可用会保持发布冻结；模拟器已尽力准备但环境不可用时可记录为未运行且不单独阻止发布，实际运行发现的回归仍会阻止，模拟器也不能替代指定真机或扩大兼容声明。
- [发布后仍可能发现严重缺陷] → 不静默替换资产；恢复冻结、给原 Release 加警示、用新版本修复并保留审计链。

## Migration Plan

1. 人工审阅四工件并明确批准实施及其中列出的精确远端操作；未取得批准时保持全部实施任务未开始。
2. 在实现开始时从当前 `master` 重新生成 lint、Web、PR、分支、安全设置和 Release 快照，将精确对象及数量写入任务证据；任何与提案实质不同的漂移先评估范围。
3. 创建 PR 1 后先原子准备 stale 标签和私有漏洞报告入口，读回成功且 PR 检查通过后再合并治理入口、Dependabot 策略、反馈边界和失效同步清理；随后执行其余第一阶段远端队列、冻结文案与安全设置操作并立即审计告警。
4. 按 PR 2 完成更新器、身份与链接分类、历史资料本地化和单普通版 Release workflow；用确定性测试验证旧偏好、资产选择和 workflow 契约。
5. 按 PR 3 修复 Android/Web 阻断问题和高风险 warning，观察 RED 后再实现行为修复，运行聚焦测试和完整静态检查。
6. 按 PR 4a...4n 从高风险到机械项逐批清理 warning，每批更新维护账本；风险过高项写明延期，不扩大兼容范围。
7. 按 PR 5 加入完整 CI、CodeQL、报告 artifact 和稳定聚合门禁；审查告警后再合并，并在检查名稳定成功后最小更新 `master` ruleset。
8. 按 PR 5b 合并模拟器 fixture 与自动化，在 `master` 运行适用 API 21/API 36 预检并记录真实状态。
9. 锁定最终绿色 `master` SHA，手动构建唯一普通版草稿 Release，完成文件和指定真机证据；失败时保持草稿/冻结并回到对应 PR修复。
10. 全部门禁通过后公开普通版并回验更新器；随后提交 PR 6 归档 OpenSpec，在归档分支和合并后的 `master` 分别严格校验，再按记录的精确 ref/SHA 清理本变更短期分支。

回滚遵循最小范围：远端设置在确认导致不可用时恢复到操作前快照；未公开草稿可保留或删除失败候选但不得描述为发布；已合并代码使用独立 revert PR；已公开 Release 保留记录并通过警示和修正版处置。
