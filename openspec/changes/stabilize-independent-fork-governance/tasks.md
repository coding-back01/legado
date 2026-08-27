## 0. 人工实施授权闸门

- [x] 0.1 用户完成人工审阅，并再次明确批准本变更四工件、串行 PR 方案及任务中列出的精确 PR、分支、Latest Release、安全设置和 ruleset 操作；在用户明确授权并据此勾选本项之前，不得执行 1.1 或任何源码、配置、GitHub 远端、设备、Release 与归档操作。当前“可以创建四工件”的授权不视为实施批准。

## 1. 建立实施快照与总变更证据

- [x] 1.1 从最新 `master` 重新核对 `git status --short`、提交、OpenSpec 状态、Android SDK、Node/pnpm 版本和远端仓库身份，确认没有覆盖用户未提交改动。
- [x] 1.2 使用固定环境重新运行 `:app:lintAppDebug`，保存 XML/HTML/文本报告并核对参考基线 `14 errors / 881 warnings / 18 hints`；若数量或根因发生实质性漂移，先更新本变更工件。
- [x] 1.3 在 `modules/web/` 重新运行类型检查、只读 ESLint 和构建，核对参考基线为类型检查/构建通过、ESLint 2 个错误，并记录是否产生静态资源同步差异。
- [x] 1.4 只读快照打开的 PR、issue、远端分支、Latest Release、Dependabot/CodeQL/Secret Scanning/私有漏洞报告和 `master` ruleset，将命令、时间和结果写入实施证据。
- [x] 1.5 新建 `docs/maintenance-baseline.md`，按 lint ID 登记参考数量、当前数量、风险和各状态数量，尚未审查的 ID 或范围标记为阻断完成的 `PENDING_REVIEW`；`FIXED` 记录批次/PR/验证证据，只有 `SUPPRESSED_WITH_REASON` 与 `DEFERRED` 列精确位置或范围、理由和重启条件，完整 occurrence 只放 CI 报告，不得创建全局 lint baseline。
- [x] 1.6 确认本变更不修改最低 API 21、Room schema、书源/订阅源规则、导入 URI、备份格式、普通正式版包名或签名；如实际需要突破任一边界，停止实施并重新评审工件。

## 2. PR 1：治理入口、Dependabot 与反馈边界

- [x] 2.1 在 `codex/fork-governance-foundation` 分支纳入已批准的 OpenSpec 工件，并将 `.github/dependabot.yml` 改为普通更新按月分组、所有 ecosystem/update entry 的普通更新 `open-pull-requests-limit` 之和不超过 5、安全更新不自动合并、兼容性固定依赖显式忽略、AGP/Gradle/Kotlin/KSP 独立处理；用配置检查证明跨生态总上限而非把每个生态都设为 5。
- [x] 2.2 删除功能请求表单；修改 bug 表单以收集当前 fork 版本、设备和复现步骤，强制提醒删除 Cookie、Token、账号、私有 URL、备份内容和个人路径，并把疑似安全问题引导到私有漏洞报告。
- [x] 2.3 删除 Issue 配置中不存在的旧 Wiki/Discussion contact link，并明确本仓库只接受正式版 bug、无 SLA、功能请求默认不受理。
- [x] 2.4 删除失效的 `.github/workflows/autoupdatefork.yml`；将 stale 行为收敛为只处理 `needs-info` 且超过 30 天的 issue，并确保崩溃、数据损坏和安全问题不会因时间自动关闭。
- [x] 2.5 审计 `.github/workflows/web.yml` 和 `cronet.yml` 的旧仓库身份条件：PR 1 不得机械启用上游写操作；保留只读/构建价值或删除无维护价值的定时写入，并记录选择理由。
- [x] 2.6 使用适用的 YAML/schema/actionlint 检查验证 Dependabot、Issue 表单和 workflow；运行 `openspec validate --all --strict` 与 `git diff --check`，确认 PR 1 只包含治理入口范围。
- [x] 2.7 创建 PR 1；在合并前只允许执行两个入口原子前置：记录并按精确名称创建/读回 `needs-info`、`crash`、`data-loss`、`security`、`stale` 标签，以及启用/读回私有漏洞报告并验证表单目标可用。随后等待 PR 检查通过并合并；若任一前置或检查失败则停止后续序列，在原 PR 修复，不提前启用其他安全能力或执行任何批量远端清理。
- [x] 2.8 PR 1 合并且新 Dependabot 配置确认生效后，重新核对作者、状态、精确 head ref 和 head SHA，只关闭当前批准的遗留 Dependabot PR：`#1`、`#2`、`#4`、`#5`、`#6`、`#7`、`#8`、`#9`、`#10`、`#11`、`#12`、`#13`、`#14`、`#15`、`#16`、`#17`、`#18`、`#19`、`#20`、`#22`、`#24`、`#25`、`#26`、`#27`、`#28`、`#29`、`#30`、`#37`、`#38`、`#39`；只删除这些 PR 在核对后 ref/SHA 仍一致的精确 Dependabot 分支，新增、漂移或身份不符对象停止并另行报告。
- [x] 2.9 为遗留远端分支记录删除前 SHA，重新确认均已合并、SHA 未漂移且未被重新使用后，只删除 `codex/add-reading-time-estimation`、`codex/archive-harden-independent-fork-security`、`codex/harden-independent-fork-security`、`codex/initial-release`；禁止用 `codex/*` 扩大目标。
- [x] 2.10 仅在 `3.26.082216` 仍为现有 Latest 且资产未变化时，更新其说明为稳定化期间暂停新正式版、未来停止 `releaseA`、保留历史资产；若 Latest 已变化则停止并请求更新精确授权。
- [x] 2.11 启用并读回验证仓库可用的 Dependabot alerts、Dependabot Security Updates 和 Secret Scanning，确认 2.7 启用的私有漏洞报告仍然可用，并记录 API 前后状态；不在检查 context 尚未存在时修改 `master` 必需检查。
- [x] 2.12 紧接 2.11 读取 Dependabot 与 Secret Scanning 的现有告警并保存分级摘要：高危/严重项完成修复、替换或禁用受影响功能前不得开始 PR 2；中危逐项记录修复或接受理由；低危登记维护清单。API 不可用时如实标记受阻，不推断为零告警。
- [x] 2.13 在 PR 2 前插入 `codex/web-chapter-content-safety` 紧急安全 PR：先为书源正文经 Web API 进入 `ChapterContent.vue` 的 `v-html` 边界建立 RED，只允许从 canonical 小写图片标签重建唯一且非空的 `src`，其余内容全部转义；覆盖隐藏图片请求、畸形输入、线性复杂度和代理回退，提交固定 pnpm 生成的 Android Web assets，并在 PR 与合并后的 `master` 上确认 Web、Android 检查和安全告警状态。

## 3. PR 2：更新器、fork 身份、失效链接与 releaseA 退役

- [x] 3.1 先为 Release JSON 解析、唯一普通资产选择和完整版本比较增加 JVM 测试，覆盖普通版/`releaseA` 顺序互换、相同时间戳、缺失/重复资产、错误 MIME/state、畸形 tag、同日更晚小时、跨日和跨年，并确认测试在旧实现上出现预期 RED；带 `debug` 后缀的安装版本必须得到明确“不支持正式更新”结果且不提供下载。
- [x] 3.2 将更新网络端点改为 `coding-back01/legado` Latest，解析完整 `tag_name`，严格选择 `legado_app_<tag>_release.apk` 的唯一 uploaded APK，并实现可区分的网络、JSON、Release 数据和“已是最新”结果，使 3.1 的测试转为 GREEN。
- [x] 3.3 为 `default_version`、`official_version`、`beta_release_version`、`beta_releaseA_version` 和未知旧偏好增加恢复/升级测试，确认旧实现会改变或阻断选择后，移除面向用户的通道列表并让更新路径永久归一为普通稳定版。
- [x] 3.4 将依赖 live upstream 和固定资产数量的设备 `UpdateTest` 改为确定性测试或发布阶段 smoke；验证成功、失败、超时和空实现路径都会关闭更新等待框。
- [x] 3.5 先增加 Release workflow 契约测试，断言仅允许仓库所有者通过 `workflow_dispatch` 手动触发、核对 `github.actor` 与 `github.triggering_actor`、只构建/上传一个普通版、先建草稿、拒绝 `releaseA` 路径、签名缺失时 fail-closed，并要求输入 `expected_sha` 与实际 `github.sha`、tag commit、Release target 一致；确认测试在旧双 APK workflow 上出现预期 RED。
- [x] 3.6 修改 Release workflow 使 3.5 转为 GREEN：删除双矩阵、`.releaseA` 动态 `sed` 改写、双 artifact 下载和双 APK 文案，只执行一次普通版构建并固定候选名为 `legado_app_<version>_release.apk`；显式检出 `expected_sha`，保留 Secrets-only 签名路径，触发者或 SHA 不一致时在构建/发布前失败。
- [x] 3.7 删除 `app/build.gradle` 中已不可达的 `releaseA` 应用名分支，但保留历史包识别、旧偏好 key、历史 Release 资产和设备数据兼容；静态契约检查不得发现未来 `releaseA` 构建或上传路径。
- [x] 3.8 更新 README/English 的当前维护、Issue、Release、独立签名、有限设备验证、应用不提供内容和无上游官方背书说明；保留原作者版权，并把官网、Google Play、社区和仍有效的上游项目明确标为上游资源。
- [x] 3.9 将贡献者、分享下载、应用内 Web 首页、当前源码/Issue、`package.json` repository/bugs/homepage/license 等当前功能入口改为本 fork，并同步所有已有 locale 的分享和贡献者说明。
- [x] 3.10 将默认 RSS 的失效远程图标改为既有内置 fallback；把失效帮助图片、免责声明和可恢复历史更新日志改为本地资产或不可变引用，对无法恢复的资料写明确缺失说明。
- [x] 3.11 逐项审计应用内帮助、更新日志、README/English、Issue 表单和 workflow 中的旧链接；保留 LICENSE、历史 issue/PR、依赖来源和仍有效上游资源，禁止全局域名替换。
- [x] 3.12 运行更新器 JVM 测试、Release workflow 契约测试、相关 Android 测试、Debug 构建、资源检查、actionlint、链接检查、`openspec validate --all --strict` 和 `git diff --check`，确认没有 Room、规则、备份、签名材料或历史 Release 资产变化。
- [x] 3.13 创建并合并 `codex/fork-distribution-identity` PR 2；检查失败或更新器 live smoke 无法唯一选择普通版时停止后续序列。

## 4. PR 3：Android/Web 阻断错误和高风险 warning

- [x] 4.1 将 AndroidX Startup 显式加入 compile/runtime 依赖，在修改前后分别观察 `MissingClass` RED/GREEN，并核对最终 APK 仍只含预期 Provider；不升级无关依赖。
- [x] 4.2 为并发 key set 的 API 21 路径增加或复用聚焦测试，再将暴露类型改为公开 `MutableSet<String>`，确认两个 `NewApi` error 消失且并发语义不变。
- [x] 4.3 为书籍类型位掩码补充组合测试并观察旧注解契约 RED，再将 `@IntDef` 改为 `flag = true`。
- [x] 4.4 用 RecyclerView 公共 orientation typedef 替换重复 typedef，并以聚焦测试或 lint RED/GREEN 证明横向、纵向行为未改变。
- [x] 4.5 为输入法显示路径建立可验证断言，将错误结果常量改为 `SHOW_IMPLICIT`；用公开 `RecyclerView` 访问导航列表，移除 Material 内部 API 依赖，并验证键盘与导航页面行为。
- [x] 4.6 补齐 6 个缺失翻译键对应的全部 locale 条目，恢复默认资源中的英文 `play_mode`；运行 lint、资源检查和自动截图/UI 层级验证，不把无法人工判断的装饰质量描述为已验证。
- [x] 4.7 修复已确认的 `IntentWithNullActionLaunch`、8 个 locale 逻辑问题和 `AppBundleLocaleChanges`，为可观察行为先建立 RED 测试或检查，再提升这些 ID 的后续阻断策略。
- [x] 4.8 移除网页端未使用的 `RuleSearch`，用具体类型替换 `souce.ts` 中的 `any`，并通过类型检查与 ESLint RED/GREEN 验证。
- [x] 4.9 在 `modules/web/package.json` 固定 `packageManager: "pnpm@9.15.9"`，使用该版本生成并审计 `pnpm-lock.yaml`，不修改依赖版本范围；使用同版本冻结安装运行类型检查、ESLint 和构建，检查同步到 Android assets 的差异只包含预期 Web 产物。
- [x] 4.10 运行 `:app:testAppDebugUnitTest`、`:app:lintAppDebug`、`:app:assembleAppDebug`、网页端冻结安装/类型检查/ESLint/构建、OpenSpec 严格校验和 `git diff --check`，确认 Android lint 0 error、Web ESLint 0 error。
- [x] 4.11 创建并合并 `codex/quality-blockers` PR 3；若任何阻断错误仍存在或测试失败，停止 warning 批次和持续门禁阶段。

## 5. PR 4a...4n：Warning 三态清理

- [x] 5.1 从 PR 3 合并后的 lint XML 重新按 ID 和 occurrence 生成机器清单，与 `docs/maintenance-baseline.md` 的 ID 汇总对账；允许同一 ID 包含多种最终状态数量，但未审查的 ID 或范围必须保持 `PENDING_REVIEW` 并阻断完成。
- [x] 5.2 先处理正确性、启动安全、国际化和 RTL warning；每项行为修改先建立聚焦 RED，再实现 GREEN，并用独立小 PR 合并。
- [x] 5.3 处理无障碍和有可靠证据的性能 warning；截图、UI 层级、基准或单元测试不足时不得标记 `FIXED`。
- [ ] 5.4 将 `UseKtx` 等机械建议拆成独立批次，只转换语义等价且检查可覆盖的位置，避免全项目格式化或顺手重构。
- [ ] 5.5 对 `UnusedResources` 先搜索 XML、代码、反射、名称拼接、书源规则、Web/assets 和运行时动态引用；只有能够排除动态使用的资源才允许删除并运行资源构建验证。
- [ ] 5.6 对确认误报或刻意兼容行为只添加精确局部抑制，并在账本记录文件、位置、理由和验证；不得放宽同 ID 的其他位置。
- [ ] 5.7 对风险或验证成本过高的 occurrence 标记 `DEFERRED`，记录风险、具体原因、剩余数量和可重新启动条件；延期本身不阻止总变更，但缺少记录会阻止。
- [ ] 5.8 每个 warning PR 都运行改动范围测试、Android lint、Debug 构建、OpenSpec 严格校验和 `git diff --check`，更新账本并在前一 PR 合并后才开始下一批。
- [ ] 5.9 完成全部安全批次后生成最终 warning 统计，确认 Android lint 仍为 0 error、没有 `PENDING_REVIEW`，每个 lint ID 的三态数量与完整 CI occurrence 报告对账，且所有局部抑制和延期范围均有精确记录。

## 6. PR 5：持续 CI、CodeQL 与 master 门禁

- [ ] 6.1 扩展 Android 验证 workflow，使用 JDK 17 执行单元测试、`lintAppDebug` 和 Debug 构建；任何命令失败即停止，并始终上传可审计的 lint 报告而不接触正式签名 Secrets。
- [ ] 6.2 重构 Web workflow，使用 Node 20+ 与固定的 pnpm 9.15.9 冻结安装，依次执行类型检查、只读 ESLint 和构建，并检查同步产物是否与提交一致；移除只对旧上游生效的自动提交条件。
- [ ] 6.3 增加独立 OpenSpec/仓库检查，显式安装 `@fission-ai/openspec@1.8.0` 后执行 `openspec validate --all --strict`，并执行适用的 YAML/actionlint 与 `git diff --check`。
- [ ] 6.4 增加 GitHub CodeQL workflow，使用最小必要权限：`contents: read`、上传 SARIF 所需的 `security-events: write`，只有分析需要时才加入 `packages: read`；fork PR 使用不暴露 Secrets 的降权路径，验证失败不得用 `continue-on-error` 隐藏。
- [ ] 6.5 为 Android、Web、OpenSpec 和 CodeQL 建立在每个 PR 与 `master` 提交上都稳定出现的聚合 gate；不得让 workflow 级 `paths` 造成 required context 缺失。Android、Web 等子任务可以按路径合法跳过，但 CodeQL 必须在每个 PR 与 `master` 提交上实际分析，`if: always()` 聚合结果必须区分合法跳过与真实失败并对后者失败。
- [ ] 6.6 为质量和安全工作流设置适当并发、缓存和 artifact 保留策略，确认 PR 验证只产生 Debug/报告，不读取或发布正式签名材料。
- [ ] 6.7 在 PR 5 上分别用 Android-only、Web-only、文档/OpenSpec-only 改动或等价测试实际观察全部聚合 context 稳定存在且成功，记录精确检查名；同时手动对 PR head 运行完整 CodeQL 分支分析。
- [ ] 6.8 在合并 PR 5 前读取其 CodeQL 检查结果、分析/SARIF 摘要和可用告警：高危/严重项修复、替换或禁用受影响功能后才可合并；中危逐项记录判断；低危登记维护清单。扫描成功不得代替告警审查。
- [ ] 6.9 创建并合并 `codex/maintenance-gates` PR 5；合并后再次确认相同聚合检查在 `master` 成功，并重新读取 Dependabot、CodeQL 和 Secret Scanning 告警，任何新增高危/严重项阻止后续阶段。
- [ ] 6.10 仅在 6.9 完成后，读取现有 `master` ruleset 的精确 ID、名称、target、enforcement、conditions、rules 和 bypass actors 并保存完整前置快照；按该 ID 最小加入已验证的精确聚合 context 与高危/严重 code-scanning 阈值，不得削弱或覆盖已有规则，随后读回并逐字段核对。
- [ ] 6.11 单独快照并启用仓库“合并后自动删除 head branch”设置；读回验证后，确认 ruleset 与仓库设置不存在引用漂移、缺失 context 或意外放宽。

## 7. PR 5b：模拟器发布自动化与预检

- [ ] 7.1 在 `codex/release-emulator-smoke` 分支增加离线、唯一 sentinel、足够跨页的本地 TXT fixture 和只用于 Debug 测试数据的导入—正文—翻页—书架断言，不依赖公网或用户文件。
- [ ] 7.2 修正现有设备测试中错误的 provider authority/path，并将依赖公网、固定远端数量或会污染数据的测试从发布 smoke 集合隔离；不得默认运行整个真机 instrumentation 套件。
- [ ] 7.3 优先使用现有 AndroidX Test/Espresso 实现稳定断言；只有系统级路径确实无法覆盖时才增加 UIAutomator test-only 依赖并记录必要性。
- [ ] 7.4 运行单元测试、lint、Debug 构建、可用模拟器聚焦测试、OpenSpec 严格校验和 `git diff --check`；创建并合并 PR 5b，记录其精确 head ref/SHA，合并后确认 `master` 全部聚合门禁绿色。未合并的测试代码或依赖不得进入发布证据。
- [ ] 7.5 从已合并的 `master` 尽力创建或修复干净 API 21 AVD，安装 `appDebug` 并运行启动、导入预览取消和离线 TXT 完整路径；记录通过、实际失败或系统镜像不可用，不伪造结果。
- [ ] 7.6 从同一 `master` 尽力在干净 API 36 AVD 执行同一套路径以及本轮涉及的输入法、导航、语言、RTL和动态资源页面检查；实际回归会阻止发布。
- [ ] 7.7 保存模拟器命令、镜像/API、APK 身份、测试结果、截图和日志；明确这些证据不替代指定真机，也不扩大其他设备兼容承诺。

## 8. 普通版候选、指定真机与正式发布

- [ ] 8.1 汇总 PR 1–5b、warning 三态、稳定聚合 CI、CodeQL、全部安全告警和模拟器结果，确认只剩允许的非阻断债务；从远端 `master` 锁定精确 `RELEASE_SHA`，确认该 SHA 的全部必需检查绿色且高危/严重告警为零。候选验证期间暂停合并；`master` 漂移则取消本轮候选的公开资格、保留草稿审计记录并重新锁定。
- [ ] 8.2 由仓库所有者以 `expected_sha=RELEASE_SHA` 手动触发单普通版 Release workflow 生成草稿；读取 workflow run 并确认 `head_sha` 精确等于 `RELEASE_SHA`，否则立即停止。
- [ ] 8.3 确认草稿只产生 `legado_app_<version>_release.apk`，不产生 `releaseA` 或其他正式资产；通过 GitHub API 核对资产 `size` 和 `digest`，下载完整 APK 后运行 SHA-256、`unzip -t`、`aapt dump badging` 和 `apksigner verify --verbose --print-certs`，确认包名、版本、最低/目标 API 和正式证书身份。
- [ ] 8.4 解析 tag commit 和 Release `target_commitish`，确认二者与 workflow `head_sha`、`RELEASE_SHA` 四者一致；任一目标仍是漂移分支名或 SHA 不一致时不得进入真机验证或公开。
- [ ] 8.5 等待用户连接并指定设备及 Android 版本；实时运行 `adb devices -l`、包查询和签名/版本读取，不使用历史设备记录推断当前状态。
- [ ] 8.6 安装前只读记录 `io.legado.app.release` 的包路径、版本、证书摘要和首次安装时间；通过 `ReaderProvider` 查询书架和书源，将原始响应直接流入计数器且不回显、不落盘 JSON、书名、URL 或路径。当前 RSS URI 映射不能提供独立可靠的 RSS 数量，不将其作为硬不变量；正式包不可自动读取的 SharedPreferences 也不作为硬门禁。
- [ ] 8.7 仅对同签名候选执行 `adb install -r`，安装后复核包名、版本、签名、首次安装时间和 8.6 的数量不变量；不得使用降级、卸载或清数据参数。
- [ ] 8.8 启动普通版并采集 top activity、仅目标 PID 且限定时间窗的 logcat、收敛页面后的截图和 UI hierarchy；若出现隐私协议或其他不可代替授权，停止并报告，不替用户确认。
- [ ] 8.9 验证书架壳和外部可观察状态，使用内联、离线、唯一命名假书源打开导入预览后只点击取消，并确认前后书源数量一致；不得确认导入、删除内容、恢复备份、打开已有书或翻页。
- [ ] 8.10 将真机原始日志、截图和 UI hierarchy 只保存在 Git 忽略且权限受限的本地目录；版本化发布证据只写脱敏数量、状态、摘要和哈希。任何硬不变量无法自动验证时保持发布冻结，不要求用户手动点测来补证据。
- [ ] 8.11 在再次确认 `master` 仍为 `RELEASE_SHA`、四层 SHA 仍一致且全部门禁有效后公开草稿并设为 Latest；Release 说明记录独立签名、普通包名、精确提交、历史 `releaseA` 停止更新、跨签名不能覆盖和应用不提供内容。
- [ ] 8.12 从公开 Release 重新下载普通 APK并复核大小、摘要和签名；调用当前 fork Latest API和更新器 live smoke，确认完整版本与唯一普通资产可被正确识别，并再次核对 tag/Release target 未漂移。
- [ ] 8.13 若发布后发现 P0、安全、签名或数据问题，立即恢复冻结、在原 Release 加警示并通过新验证版本修复；不得静默替换或删除审计记录。

## 9. 总变更验证、归档与最终清理

- [ ] 9.1 逐项对照五份 delta spec、design 和 tasks，确认所有完成项都有代码、命令、CI、远端或设备证据，所有失败/未运行/延期状态均如实记录。
- [ ] 9.2 运行 `:app:testAppDebugUnitTest`、`:app:lintAppDebug`、`:app:assembleAppDebug`、网页端冻结安装/类型检查/ESLint/构建、`openspec validate --all --strict` 和 `git diff --check`，记录实际结果及最终 warning 数量。
- [ ] 9.3 执行 OpenSpec 实现核验，确认普通正式版已公开、更新器回验成功、高危/严重安全问题为零、中危有判断、旧 PR/分支和 ruleset 状态符合规范；条件缺失时不得归档。
- [ ] 9.4 通过 `codex/archive-fork-governance` 最终 PR归档 `stabilize-independent-fork-governance` 并同步主规范；归档 PR只包含规范、证据和必要文档收口，不再混入生产行为修改，并在归档分支运行 `openspec validate --all --strict` 与 `git diff --check`。
- [ ] 9.5 合并归档 PR前确认其严格校验与全部聚合检查绿色；记录本总变更每个已合并 PR 的精确 head ref、head SHA 和 merge 状态，生成待删分支白名单，禁止使用 `codex/*` 或其他 glob 推导目标。
- [ ] 9.6 合并归档 PR后同步本地 `master`，再次运行 `openspec validate --all --strict` 和 `git diff --check`，并核对 GitHub Latest、必需检查、安全告警、打开 PR/issue、远端分支、`git status --short` 与 `openspec list`。
- [ ] 9.7 只删除 9.5 白名单中 head ref 与记录 SHA 仍一致、已合并且不再使用的精确远端分支；对象漂移或新增对象一律保留并报告。保留 PR、Release、CI artifact 和 OpenSpec 归档作为审计记录，并提交最终一次性治理报告。
