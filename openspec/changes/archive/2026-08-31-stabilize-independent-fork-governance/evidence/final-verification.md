# `stabilize-independent-fork-governance` 最终实现核验报告

## 核验范围与结论

- 变更：`stabilize-independent-fork-governance`
- schema：`spec-driven`
- 核验分支：`codex/fork-governance-final-closeout`
- 归档合并提交：`31affc67f72051ede1f4ec1bc8ee9c0f7ca69c9f`
- 发布提交：`cef2fbb2dbdb6771686b04c68447a6f5caea964e`
- 最终远端复核时间：2026-08-31 07:41 +0800
- 规范规模：5 个 capability、29 个 Requirement、78 个 Scenario
- 阶段证据：30 份版本化 Markdown；真机与模拟器原始文件只保存在 Git 忽略且权限受限的本地目录

生产实现、持续门禁、普通版发布、发布后更新器回验、OpenSpec 归档、归档后 `master`
复核和精确分支清理均已通过。29 个 Requirement 和 78 个 Scenario 均已建立实现、测试、
负向合同或过程证据锚点并达到终态；任务进度为 91/91。

## 核验记分卡

| 维度 | 结果 | 结论 |
|---|---|---|
| 完整性 | 91/91 tasks；29/29 Requirement 有证据；78/78 Scenario 有处理锚点 | 生产、发布、归档、合并后复核和精确分支清理全部完成 |
| 正确性 | 29/29 Requirement 已达终态 | 未发现规范缺失、实现偏离或未完成硬门禁 |
| 一致性 | 10 项设计决策均有实现或过程证据 | 单总变更、串行 PR、三态 warning、单普通版 Release、分层设备验证和 fail-closed 处置均与设计一致 |

## 当前可复核状态

### 代码、质量与构建

| 项目 | 实际结果 |
|---|---|
| Android JVM 单元测试 | 142 tests、0 failure、0 error、1 个既有 skip |
| Android lint | 0 error、108 warning、18 hint；退出成功 |
| Warning 三态 | `FIXED=765`、`SUPPRESSED_WITH_REASON=12`、`DEFERRED=108`、`PENDING_REVIEW=0` |
| Android Debug 构建 | 成功；`legado_app_3.26.083101.apk` 为 29,946,765 字节 |
| Web 固定环境 | Node.js 22.18.0、pnpm 9.15.9 |
| Web 冻结安装、类型检查、只读 ESLint、构建 | 全部成功；ESLint 0 error；非 GitHub Actions 环境未同步 Android Web assets，工作区无生成差异 |
| OpenSpec 严格校验 | 本报告写入和任务勾选后重新运行，结果记录在“最终命令证据” |
| 空白检查 | 本报告写入和任务勾选后重新运行，结果记录在“最终命令证据” |

完整 warning 处置与重启条件见 `docs/maintenance-baseline.md`。108 个 warning 和 18 个
hint 都是已审查债务，不含 `PENDING_REVIEW`，也不以全局 lint baseline 隐藏。

### GitHub、Release 与安全状态

| 项目 | 2026-08-31 读回结果 |
|---|---|
| 远端 `master` | `31affc67f72051ede1f4ec1bc8ee9c0f7ca69c9f` |
| Latest Release | `3.26.083101`，Release ID `379347961`，公开、非预发布 |
| 唯一普通资产 | `legado_app_3.26.083101_release.apk`，asset ID `536854094`，14,517,611 字节 |
| 资产 SHA-256 | `cd1869d2511b0ce375fc343a9e29f6f38f17f48c52bc67f18c776fda5e1a3c07` |
| Release 身份 | workflow、tag、Release target 均等于发布提交；当前 `master` 包含该提交 |
| APK 身份 | `io.legado.app.release`，minSdk 21，targetSdk 36，v1/v2/v3 签名通过 |
| 正式证书 SHA-256 | `14b0c0828372820a20687221a0f8a8b02603f409cc096f7101ba38f182205283` |
| 锁定提交门禁 | Android、Web、OpenSpec/仓库、双 CodeQL 和聚合 `维护门禁` 均成功 |
| Code Scanning | 0 个打开告警 |
| Secret Scanning | 0 个打开告警；Secret Scanning 与 push protection 已启用 |
| Dependabot | 2 条 medium、0 high/critical；两条均为同一 Element Plus 漏洞的 manifest/lockfile 映射，尚无修复版本，已有接受理由 |
| 私有漏洞报告 | enabled |
| ruleset | ID `20653588`、`保护 master 分支`、active、无 bypass；必需检查为 `维护门禁`，CodeQL 阈值为 `high_or_higher` |
| 打开 issue | 0 |
| 打开 PR | #42、#44、#45、#73，均为新策略生成的普通 Dependabot PR，按规范保留 |
| 远端分支 | 6 条：`master`、4 条普通 Dependabot 分支和 1 条未合并证据分支；29 条白名单分支已精确删除 |

失败草稿 `3.26.083020` 和设备验证失败草稿 `3.26.083021` 继续保持不可见并保留审计记录；
不得删除、替换资产或公开。

## Requirement 与 Scenario 对账

下表逐 Requirement 列出其全部 Scenario 名称。`通过`表示正向行为、负向 fail-closed 合同或
实际失败处置均有代码、测试、CI、远端、模拟器、真机或归档后复核证据。

### `fork-build-security`：2 Requirement / 7 Scenario

| Requirement | Scenario | 实现与测试锚点 | 阶段证据 | 状态 |
|---|---|---|---|---|
| 验证工作流不使用发布签名 | Pull Request 触发验证；验证命令失败 | `.github/workflows/test.yml:1`、`.github/scripts/test_maintenance_workflow.py`；PR/`master` 的 Android、Web、OpenSpec 和双 CodeQL 实际运行，聚合 job 对失败返回失败 | `pr5-maintenance-gates.md` | 通过 |
| 正式发布仅接受受控签名 | 发布 Secrets 完整；非所有者或非手动触发发布；草稿候选尚未通过真机门禁；发布 Secrets 缺失；工作流尝试生成 releaseA | `.github/workflows/release.yml:4`、`app/src/test/java/io/legado/app/help/update/ReleaseWorkflowContractTest.kt:27`；Secrets-only、所有者与 `triggering_actor`、`expected_sha`、单 APK 和草稿合同均受测试约束 | `pr2-verification.md`、`release-candidate-verification.md` | 通过 |

### `fork-distribution-identity`：7 Requirement / 17 Scenario

| Requirement | Scenario | 实现与测试锚点 | 阶段证据 | 状态 |
|---|---|---|---|---|
| 用户可见身份必须区分当前 fork 与原项目 | 用户查看项目首页；用户查看贡献者 | `README.md:1`、`English.md:25`、`app/src/main/res/values/non_translat.xml:6`、`ForkLinkContractTest.kt` | `pr2-link-audit.md`、`pr2-verification.md` | 通过 |
| 当前功能链接与历史来源链接必须分类治理 | 当前操作入口仍指向撤空仓库；远程展示资源失效；链接用于历史归属 | 当前源码、Issue、Release 和贡献者指向本 fork；历史/版权/上游资源保持真实来源；RSS 与帮助资源使用本地或固定引用；由 `ForkLinkContractTest` 和实际链接抽查覆盖 | `pr2-link-audit.md`、`pr2-verification.md` | 通过 |
| 应用内更新只提供当前 fork 的普通稳定版 | Latest 包含普通版和 releaseA；普通版候选缺失或重复；资产状态或格式无效 | `AppReleaseInfo.kt:35`、`StableReleaseParserTest.kt:15`；解析器严格选择唯一 uploaded 普通 APK，公开后真实 Latest JSON 9/9 通过 | `pr2-verification.md`、`release-candidate-verification.md` | 通过 |
| 更新版本比较必须使用完整且可验证的版本 | 同一天存在更晚版本；当前版本相同或更新；版本格式无法验证；Debug 安装手动检查更新 | `AppReleaseInfo.kt:97`、`StableReleaseParserTest.kt`；完整数字字段比较并显式拒绝 Debug/畸形版本 | `pr2-verification.md`、`release-candidate-verification.md` | 通过 |
| 旧更新通道偏好不得改变稳定版选择 | 恢复含旧通道值的备份；用户从旧版本升级 | `StableUpdateChannelTest.kt:11` 覆盖全部旧值和未知值；设置入口已移除，运行时归一到稳定普通版 | `pr2-verification.md` | 通过 |
| 未来 releaseA 分发必须停止且历史数据必须保留 | 创建新的正式 Release；用户仍安装历史 releaseA | Release workflow 与合同测试禁止未来 `releaseA`；新 Latest 只有普通 APK；历史资产、包识别和旧偏好 key 保留，真机未卸载或接管 `releaseA` | `pr2-verification.md`、`release-candidate-verification.md` | 通过 |
| 失效历史资料必须有可审计替代 | 历史分支链接返回不存在 | `app/src/main/assets/updateLog.md:39` 使用不可变提交；免责声明与可恢复资料本地化；无法恢复项写明缺失 | `pr2-link-audit.md`、`pr2-verification.md` | 通过 |

### `maintenance-quality-baseline`：6 Requirement / 14 Scenario

| Requirement | Scenario | 实现与测试锚点 | 阶段证据 | 状态 |
|---|---|---|---|---|
| 阻断级静态检查错误必须清零 | Android lint 仍有错误；网页端 ESLint 仍有错误 | Android lint 当前 0 error；Web ESLint 当前 0 error；CI 直接执行并以退出码阻断 | `pr3-verification.md`、`pr5-maintenance-gates.md`、本报告 | 通过 |
| Warning 必须采用三态账本 | Warning 已安全修复；Warning 是可证明的误报或兼容约束；Warning 无法在本轮安全修复；存在未审查的 warning | `docs/maintenance-baseline.md:41`；26 个 lint ID 数量守恒，765 修复、12 局部抑制、108 延期、0 待审 | `warning-pr3-inventory.md`、`warning-pr4a-rtl.md` 至 `warning-pr4r-final-audit.md` | 通过 |
| 高风险 Warning 不得在后续静默复发 | 新增高风险 Warning；已记录的局部抑制仍然存在 | `HighRiskWarningContractTest.kt:78` 和 lint fatal 配置覆盖 `IntentWithNullActionLaunch`、`DefaultLocale`、`AppBundleLocaleChanges`；抑制只在精确位置 | `pr3-verification.md`、`warning-pr4r-final-audit.md` | 通过 |
| 持续验证必须失败即停止 | 所有适用检查通过；任一必需命令失败；改动未命中某个子任务路径 | `.github/workflows/test.yml:310` 使用 `if: always()` 聚合；Android-only、Web-only、文档-only 和完整探针均验证合法跳过与真实失败 | `pr5-maintenance-gates.md` | 通过 |
| 质量证据必须区分通过、失败与未运行 | 检查因环境不可用而未运行；维护者查看 warning 详情 | 证据文件分别记录 SDK 缺失、GitHub Actions outage、模拟器/设备可用性和真实结果；CI 上传 lint XML/HTML/TXT | 全部阶段证据，重点为 `pr2-verification.md`、`pr5-maintenance-gates.md` | 通过 |
| 质量治理不得破坏兼容敏感接口 | 静态检查建议触及兼容边界 | 最低 API 21、Room schema、规则/备份、导入 URI、普通包名和签名保持；风险依赖/资源进入延期而非机械修改 | `implementation-snapshot.md`、`warning-pr4r-final-audit.md`、`release-candidate-verification.md` | 通过 |

### `release-verification`：7 Requirement / 21 Scenario

| Requirement | Scenario | 实现与测试锚点 | 阶段证据 | 状态 |
|---|---|---|---|---|
| 正式发布必须保持冻结直到全部门禁满足 | 任一硬门禁未满足；仅剩已记录的非阻断债务 | 旧 Latest 先写入冻结说明；只有 warning 三态、门禁、安全、模拟器和真机完成后才公开新 Latest | `pr1-governance.md`、`release-candidate-verification.md` | 通过 |
| 模拟器必须承担可丢弃数据的完整核心路径 | 模拟器完整路径通过；模拟器发现实际回归；某个模拟器环境不可用 | `LocalTxtReleaseSmokeTest.kt:30`、`BookSourceImportPreviewSmokeTest.kt:23`；API 21 与 API 36 均实际 2/2 通过，脚本只运行 `@ReleaseSmoke` | `pr5b-release-emulator-smoke.md`、`release-emulator-preflight.md` | 通过 |
| 指定真机验证必须保持用户内容边界 | 同签名升级并保持关键数据；签名不匹配；验证导入预览；首次隐私确认阻断自动化；测试路径会改变阅读进度；采集真机证据 | 实时核对 Hisense HLTE556N / Android 11；只执行 `adb install -r`；书架、书源和阅读进度 HMAC 不变；假源只预览并取消；原始证据不入 Git | `release-candidate-verification.md` | 通过 |
| 候选 APK 必须完成多层完整性与身份核验 | 候选 APK 全部核验一致；任一核验不一致 | API 大小/digest、下载字节、SHA-256、ZIP、aapt 包信息和 apksigner v1/v2/v3 全部核对；不一致合同 fail-closed | `release-candidate-verification.md` | 通过 |
| 正式 Release 必须先验证后公开 | 全部门禁通过；候选期间 master 漂移；更新器回验 | workflow、tag、target、`master` 五层一致后公开；真实 Latest JSON 9/9；`3.26.083020` 和 `3.26.083021` 展示失败/失效候选的停止行为 | `release-candidate-verification.md` | 通过 |
| 失败必须停止序列并保留审计记录 | 有序 Pull Request 验证失败；已合并变更出现回归；发布后发现严重问题 | PR 2 的 Actions outage、PR 3 的生成资产差异、首次草稿 tag 失败和首次真机候选失败均停止后续；修复使用新提交/PR，失败草稿保留；发布后应急分支未触发但合同仍有效 | `pr2-verification.md`、`pr3-verification.md`、`release-candidate-verification.md` | 通过 |
| 总变更只能在发布闭环后归档 | 发布完成但尚未归档；任一完成条件缺失 | 新普通版公开并回验后才归档；PR #77 合并后重新验证 `master`，随后按精确 ref/SHA lease 删除 29 条白名单分支，零活跃变更 | `archive-closeout.md`、`tasks.md`、本报告 | 通过 |

### `repository-maintenance-governance`：7 Requirement / 19 Scenario

| Requirement | Scenario | 实现与测试锚点 | 阶段证据 | 状态 |
|---|---|---|---|---|
| Dependabot 队列必须受限且按风险分流 | 发现安全更新；到达普通更新周期；更新命中固定依赖 | `.github/dependabot.yml:26`；三生态上限 2+1+2=5，普通更新按月，工具链分组，固定兼容依赖 ignore，无自动合并 | `pr1-governance.md` | 通过 |
| 遗留机器人队列必须在新策略生效后精确清理 | 新策略尚未生效；执行遗留队列清理；清理期间出现新对象 | 新配置合并后才关闭批准的 30 个旧 PR；按核对 ref/SHA 删除分支；新增 #42/#44/#45/#73 保留 | `pr1-governance.md` | 通过 |
| GitHub 原生安全能力必须形成分级门禁 | 发现高危或严重问题；发现中危问题；发现低危问题；安全能力刚启用 | Vite 高危通过紧急 PR 修复；CodeQL 7 个 high 修复并复扫；当前 0 high/critical；Element Plus medium 有无修复版本与固定 href 的接受理由 | `pr1-governance.md`、`pr1b-web-chapter-content-safety.md`、`pr5-maintenance-gates.md`、本报告 | 通过 |
| 公开反馈入口必须匹配个人 fork 边界 | 用户准备提交普通 bug；报告可能包含安全或隐私内容 | `.github/ISSUE_TEMPLATE/01-bugReport.yml:9`；删除功能请求，只收正式版 bug，强制脱敏并引导私有漏洞报告 | `pr1-governance.md` | 通过 |
| Stale 自动关闭只能用于长期缺少信息的问题 | needs-info 长期无响应；严重问题长期未更新 | `.github/workflows/stale.yml:19`；只处理 `needs-info`，30 天关闭，排除 crash/data-loss/security，PR 不参与 | `pr1-governance.md` | 通过 |
| 必需检查必须在可用且绿色后加入分支保护 | 检查尚不存在或未通过；检查已经稳定通过；更新现有 ruleset | 四类路径探针和双 CodeQL 成功后，按精确 ruleset ID 最小加入 `维护门禁` 与 high/critical CodeQL 阈值；无 bypass，原规则保留 | `pr5-maintenance-gates.md`、本报告 | 通过 |
| 仓库不得自动继承其他项目的变更队列 | 其他仓库出现新提交或功能请求；现有自动同步目标失效 | 删除 `autoupdatefork.yml` 和失效自动写入；不建立月报或跨仓库自动合并；README/Issue 明确维护边界 | `pr1-governance.md` | 通过 |

## 已完成任务逐段证据映射

| 已完成任务 | 证据锚点 | 对账结论 |
|---|---|---|
| 0.1 | 用户批准记录、`proposal.md`、`design.md`、五份 delta spec、`tasks.md` | 四工件、串行 PR 和精确远端操作在实施前获得批准 |
| 1.1–1.6 | `implementation-snapshot.md`、`docs/maintenance-baseline.md` | 仓库、工具链、14/881/18、Web 2 error、远端与兼容边界基线完整 |
| 2.1–2.12 | `pr1-governance.md` | Dependabot、Issue/stale、失效 workflow、旧 PR/分支、冻结说明和首轮安全能力闭环 |
| 2.13 | `pr1b-web-chapter-content-safety.md` | 章节 HTML 浏览器边界按 RED→GREEN 修复并合并 |
| 3.1–3.13 | `pr2-link-audit.md`、`pr2-verification.md` | 更新器、身份、链接、旧通道和单普通版 Release workflow 闭环 |
| 4.1–4.11 | `pr3-verification.md` | 14 个 Android lint error 与 2 个 Web ESLint error 清零，高风险 warning 进入阻断策略 |
| 5.1 | `warning-pr3-inventory.md` | PR 3 后完整 occurrence 与账本基线完成对账 |
| 5.2–5.9 | `warning-pr4a-rtl.md` 至 `warning-pr4r-final-audit.md`、`docs/maintenance-baseline.md` | 串行 warning 小批次完成，最终 0 `PENDING_REVIEW` |
| 6.1–6.11 | `pr5-maintenance-gates.md` | 持续 CI、CodeQL、安全告警、ruleset 和自动删分支设置闭环 |
| 7.1–7.4 | `pr5b-release-emulator-smoke.md` | 离线夹具、聚焦 instrumentation、Provider 修正和 PR 合并完成 |
| 7.5–7.7 | `release-emulator-preflight.md` | API 21 / API 36 发布预检实际通过，原始证据仅本地保存 |
| 8.1–8.13 | `release-candidate-verification.md` | 草稿、失败候选、APK、指定真机、公开 Latest 和更新器回验形成完整审计链 |
| 9.1–9.3 | 本报告 | 五份规范、29 个 Requirement、78 个 Scenario 与实现证据完成对账 |
| 9.4–9.5 | `archive-closeout.md`、PR #77 | 总变更归档、主规范同步、归档 PR 门禁和 29 条精确白名单完成 |
| 9.6–9.7 | `archive-closeout.md`、本报告 | 归档后 `master` 全绿复核、29/29 精确原子删除和最终远端读回完成 |

## 30 份阶段证据清单

| # | 文件 | 覆盖范围 |
|---:|---|---|
| 1 | `implementation-snapshot.md` | 实施基线、工具链、远端与兼容边界 |
| 2 | `pr1-governance.md` | 治理入口、Dependabot、Issue、安全能力和旧队列清理 |
| 3 | `pr1b-web-chapter-content-safety.md` | 章节 HTML 紧急安全修复 |
| 4 | `pr2-link-audit.md` | 当前功能、本地替代与历史来源链接分类 |
| 5 | `pr2-verification.md` | 更新器、身份、链接、releaseA 退役和 PR 2 验证 |
| 6 | `pr3-verification.md` | Android/Web 阻断错误和高风险 warning |
| 7 | `warning-pr3-inventory.md` | PR 3 后 warning occurrence 清单 |
| 8 | `warning-pr4a-rtl.md` | RTL |
| 9 | `warning-pr4b-settext-i18n.md` | `SetTextI18n` |
| 10 | `warning-pr4c-hardcoded-text.md` | `HardcodedText` |
| 11 | `warning-pr4d-plurals-candidate.md` | `PluralsCandidate` |
| 12 | `warning-pr4e-accessibility.md` | 无障碍与输入可用性 |
| 13 | `warning-pr4f-layout-performance.md` | 布局性能与延期范围 |
| 14 | `warning-pr4g-use-ktx-sparse-array.md` | `UseKtx` SparseArray |
| 15 | `warning-pr4h-use-ktx-accessors.md` | `UseKtx` 只读访问器 |
| 16 | `warning-pr4i-use-ktx-uri.md` | `UseKtx` URI |
| 17 | `warning-pr4j-use-ktx-styled-attributes.md` | `UseKtx` 样式属性 |
| 18 | `warning-pr4k-use-ktx-colors.md` | `UseKtx` 字符串颜色 |
| 19 | `warning-pr4l-use-ktx-bitmap-drawable.md` | `UseKtx` Bitmap Drawable 与局部抑制 |
| 20 | `warning-pr4m-use-ktx-int-drawable.md` | `UseKtx` 颜色 Drawable |
| 21 | `warning-pr4n-use-ktx-create-bitmap.md` | `UseKtx` Bitmap 构造 |
| 22 | `warning-pr4o-use-ktx-shared-preferences.md` | `UseKtx` SharedPreferences |
| 23 | `warning-pr4p-use-ktx-canvas.md` | `UseKtx` Canvas 状态 |
| 24 | `warning-pr4q-unused-resources.md` | `UnusedResources` 动态引用审计 |
| 25 | `warning-pr4r-final-audit.md` | 剩余 warning 最终三态审计 |
| 26 | `pr5-maintenance-gates.md` | 持续门禁、CodeQL、ruleset 和自动删分支 |
| 27 | `pr5b-release-emulator-smoke.md` | 模拟器发布烟测实现与 PR 闭环 |
| 28 | `release-emulator-preflight.md` | 合并后 API 21 / API 36 发布预检 |
| 29 | `release-candidate-verification.md` | 候选、失败保留、真机、公开与更新器回验 |
| 30 | `archive-closeout.md` | OpenSpec 归档、PR #77、合并后复核、分支白名单和精确删除结果 |

## 设计一致性核验

| 设计决策 | 实际状态 |
|---|---|
| 一个总变更、多个串行 PR、发布后归档 | 遵循；治理、分发、质量、warning、持续门禁、模拟器、候选修复和证据均按依赖顺序推进 |
| 修真实根因并使用 warning 三态账本 | 遵循；无全局 baseline，0 error，0 pending，所有延期有重启条件 |
| 更新器拆成确定性解析与网络边界 | 遵循；JVM fixture 与真实 Latest smoke 分离 |
| 链接按当前功能、本地资产、历史来源分类 | 遵循；没有全局域名替换 |
| 只退役 releaseA 生成链，保留历史兼容 | 遵循；新 Latest 单普通 APK，历史资产和识别仍保留 |
| GitHub 远端变更分阶段落地 | 遵循；标签/私有报告、队列、安全能力、CodeQL、ruleset 均按前置门禁执行 |
| 反馈入口匹配个人维护容量 | 遵循；只保留正式版 bug 与私有安全报告，无 SLA 和功能请求入口 |
| 模拟器承担写数据 E2E，真机只做非破坏验证 | 遵循；真机未打开已有书、未确认导入、未卸载或清数据 |
| 草稿、逐层核验、真机后公开 | 遵循；失败草稿保留，成功候选经完整证据后才设为 Latest |
| 失败与范围扩张使用显式状态 | 遵循；平台 outage、生成资产差异、失败草稿和设备失败均没有伪装为通过 |

## 已知非阻断债务

1. Android lint 仍有 108 个 warning 和 18 个 hint；全部已审查，具体位置、风险、理由和
   重启条件见 `docs/maintenance-baseline.md`。
2. Dependabot 有 2 条 medium 记录，实际是同一 Element Plus 漏洞对应 manifest 与 lockfile；
   GitHub 当前仍返回 `first_patched_version=null`，现有 `el-link` 均使用固定目标，接受理由
   已记录。出现修复版本或动态 `href` 数据流时重新处置。
3. #42、#44、#45、#73 是新月度策略的普通 Dependabot PR，不属于遗留队列，不在本变更中
   强行关闭或合并。
4. `codex/release-verification-evidence` 的 tip 未进入归档 PR，因此不满足“已合并”删除条件；
   它已按 fail-closed 规则保留，不构成运行时、发布或安全债务。

## 优先级问题

### CRITICAL

无。归档闭环 9.4–9.7 已按顺序完成，没有未满足的硬门禁。

### WARNING

未发现新的规范偏离、缺失 Scenario 或未记录的安全/质量阻断。上述 108 warning、2 条
medium 告警和 4 个普通 Dependabot PR 均是规范允许且已有处置依据的非阻断债务。

### SUGGESTION

无。本轮不把新的依赖升级、兼容性扩展或报告自动化混入归档 PR。

## 最终命令证据

### Android

```bash
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug :app:assembleAppDebug
```

结果：`BUILD SUCCESSFUL`；142 tests、0 failure、0 error、1 个既有 skip；lint 为
0 error / 108 warning / 18 hint；Debug APK 为 29,946,765 字节。

### Web

```bash
cd modules/web
corepack pnpm install --frozen-lockfile
corepack pnpm type-check
corepack pnpm exec eslint .
corepack pnpm build
```

结果：Node.js 22.18.0、pnpm 9.15.9；冻结安装、类型检查、只读 ESLint 和构建全部成功。
非 GitHub Actions 环境没有复制 Android Web assets，版本化工作区无生成差异。

### OpenSpec 与补丁

```bash
openspec validate --all --strict
git diff --check
```

结果：归档后的 6 份主规范全部通过，0 failed；`git diff --check` 无输出；
`openspec list --json` 返回零个活跃变更；任务清单为 91/91。

## 最终判断

没有发现生产实现、发布结果、五份归档规范与设计决策之间的未记录偏离。质量、安全、
分发、模拟器、指定真机、公开 Release、OpenSpec 归档、归档后门禁和远端清理均已满足；
中危与 warning 债务均有明确判断。

结论：**`stabilize-independent-fork-governance` 一次性治理已完成，91/91。**
