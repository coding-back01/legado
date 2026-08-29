# PR 5 持续维护门禁证据

本文记录 `codex/maintenance-gates` 的 RED/GREEN、本地质量检查、Draft Pull Request 范围探针和 CodeQL 告警处置。最终 head 的三类范围探针、告警关闭、合并后 `master`、ruleset 和自动删分支设置仍按任务 6.7–6.11 串行闭环；扫描或 workflow 成功不代替告警审查。

## 验证对象

- 基线：`master@4e1c3474aac3d90f9f02af940dfdfafbb0c07d17`
- 分支：`codex/maintenance-gates`
- Draft Pull Request：[#72](https://github.com/coding-back01/legado/pull/72)
- 日期：2026-08-29
- 范围：Android、Web、OpenSpec/仓库检查、CodeQL 和稳定聚合门禁

## 维护 workflow 结构

原 `.github/workflows/test.yml` 使用 workflow 级路径过滤，只运行 Android 单元测试和 Debug 构建；原 `.github/workflows/web.yml` 也使用 workflow 级路径过滤，未显式运行类型检查和只读 ESLint。两者不能在全部 Pull Request 和 `master` 提交上提供稳定的必需检查名，仓库也没有持续 OpenSpec 或 CodeQL 检查。

PR 5 将二者收敛为不使用 workflow 级 `paths` 的 `.github/workflows/test.yml`，并提供以下 job：

- `识别变更范围`：按提交范围将 Android、Web 和文档/OpenSpec 改动分类；未知路径 fail-safe 地运行 Android 与 Web；`workflow_dispatch` 提供 `android`、`web`、`docs`、`all` 等价探针。
- `Android 质量检查`：JDK 17、Android lint、JVM 单元测试和 Debug APK；lint 报告即使 lint 失败也尝试上传并保留 30 天，成功的 Debug APK 保留 7 天。
- `Web 质量检查`：Node.js 22、pnpm 9.15.9 冻结安装、章节 HTML 安全测试、类型检查、只读 ESLint、构建，以及 Android Web assets 无未提交差异检查。
- `OpenSpec 与仓库检查`：固定 `@fission-ai/openspec@1.8.0`、固定并校验摘要的 actionlint 1.7.12、维护门禁契约测试，以及真实提交范围的 `git diff --check`。
- `CodeQL（Android）` 与 `CodeQL（Web）`：分别使用 `java-kotlin/manual` Debug 构建和 `javascript-typescript/none`，在每个 Pull Request 与 `master` 提交上实际分析。
- `维护门禁`：使用 `if: always()` 聚合所有 job；Android/Web 只允许在分类结果不适用时为 `skipped`，范围识别、仓库检查、CodeQL 或任何适用子任务不是 `success` 时均失败。

workflow 默认只有 `contents: read`；CodeQL job 额外且仅获得 `security-events: write`，没有 `packages: read`、`continue-on-error`、正式签名 Secrets、正式构建或发布步骤。PR 更新可以取消同一 PR 的旧 run；`master` run 不会因后续提交自动取消，避免跳过某个 `master` 提交的 CodeQL 分析。

## 范围分类与 workflow 契约 RED→GREEN

先只增加 `.github/scripts/test_maintenance_workflow.py`，在旧 workflow 上运行：

```bash
python3 .github/scripts/test_maintenance_workflow.py
```

结果为 12 个契约中的 5 个 error、7 个 failure：5 类范围测试因分类器不存在报错，其余测试分别命中 workflow 级路径过滤、缺少 lint/OpenSpec/CodeQL、旧 Web workflow 仍存在、没有固定聚合 gate 等预期缺口。

随后增加 `.github/scripts/classify-maintenance-scope.sh`，重构维护 workflow 并删除旧 Web workflow。同一命令为 12/12 通过，实际覆盖：

- Android-only：`android=true`、`web=false`
- Web-only：`android=false`、`web=true`
- 文档/OpenSpec-only：`android=false`、`web=false`
- Android/Web 混合与 CI 实现改动：`android=true`、`web=true`

`bash -n .github/scripts/classify-maintenance-scope.sh` 同时通过。

## YAML 与 actionlint

使用 actionlint 1.7.12 的官方 Darwin arm64 Release 压缩包，先按官方 checksums 校验：

```text
aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f
```

第一次 actionlint 真实解析发现两处行内 `run: : > ...` 不是合法 YAML，退出 1；改为 block scalar 后再次运行，全部 workflow 通过。CI 使用同版本 Linux amd64 压缩包，并固定校验官方摘要 `8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8` 后才执行。

## Android 全量验证

第一次强制重跑因当前 shell 没有 `ANDROID_HOME`，在 Gradle 任务解析前以 `SDK location not found` 退出，没有进入测试或代码编译，因此不作为代码 RED。随后不创建 `local.properties`，显式使用现有 Android SDK、ADB 35.0.2 和 JDK 17.0.17：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug :app:assembleAppDebug \
  --rerun-tasks --build-cache --no-daemon --warning-mode all
```

首次结果为 `BUILD SUCCESSFUL`，耗时 3 分 10 秒，131/131 个 Gradle 任务实际执行。测试 XML 汇总为 28 个测试套件、129 个测试、0 failure、0 error、1 skip；lint 为 0 error、104 warning、18 hint。Debug APK `legado_app_3.26.082911.apk` 构建成功，大小为 29,943,675 字节。

lint 报告 SHA-256：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `0a2edcde943acb4ca3d1084d94fb29a977a666305cd61727137521d342844b2e` |
| `lint-results-appDebug.html` | `25b63402e01db26a7aa07fb04502b1463b0edf1eaf4c21d36a1090911e940973` |
| `lint-results-appDebug.txt` | `72c5d488bb734d04575002c4c934c197b2c36071bf3cc1d7665d3c9f69bcb5fa` |

CodeQL 修复加入后以同一固定环境再次执行相同的三个 Gradle 任务和 `--rerun-tasks`，
结果为 `BUILD SUCCESSFUL`，耗时 3 分 5 秒，131/131 个任务实际执行；测试增至
136 个，0 failure、0 error、1 skip，lint 仍为 0 error、104 warning、18 hint。
Debug APK `legado_app_3.26.082912.apk` 构建成功，大小为 29,946,766 字节。
最终 XML 与文本报告摘要仍分别为
`0a2edcde943acb4ca3d1084d94fb29a977a666305cd61727137521d342844b2e` 和
`72c5d488bb734d04575002c4c934c197b2c36071bf3cc1d7665d3c9f69bcb5fa`；HTML 因
本次报告页面内容更新，摘要为
`e64cb26f734c9f772b7e0995fa55103d5bac0a821c327fe61c25393d5233ed5a`。

## Web、OpenSpec 与范围边界

在 `modules/web/` 使用 Node.js 22.18.0 和 pnpm 9.15.9 实际运行：

```bash
pnpm install --frozen-lockfile
pnpm test:chapter-html
pnpm type-check
pnpm exec eslint .
pnpm build
```

冻结安装、16 个章节 HTML 安全测试、类型检查、只读 ESLint 和构建全部成功；本地构建按既有脚本不复制 GitHub Actions assets，目标 Android Web assets 无未提交差异。`openspec validate --all --strict` 为 3 passed、0 failed；`git diff --check` 无输出。

CodeQL 修复后再次运行同一 Web 链，16/16 安全测试、类型检查、只读 ESLint 和构建
全部成功；12/12 个 workflow 契约、OpenSpec 3/3 严格校验和 `git diff --check`
同时通过。actionlint 1.7.12 的 Darwin arm64 官方压缩包经摘要
`aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f`
核对后运行成功。

本 PR 不修改最低 API 21、Room schema、书源/订阅源规则、导入 URI、备份格式、普通正式版包名、签名材料或依赖版本；验证 workflow 只产生 Debug APK 和报告，不读取或发布正式签名产物。

## 首轮远端范围探针

Draft PR 首个 head 为
`d6a6b77c20f72923a271ecd81fd48b2085c2a4c0`。自动 Pull Request 完整运行
[`33231305120`](https://github.com/coding-back01/legado/actions/runs/33231305120)
成功，并实际产生以下稳定检查名：`识别变更范围`、`Android 质量检查`、
`Web 质量检查`、`OpenSpec 与仓库检查`、`CodeQL（Android）`、`CodeQL（Web）`
和聚合 `维护门禁`。

同一 head 的等价范围探针结果：

| 探针 | run | 适用任务 | 合法跳过 | 共同结果 |
|---|---:|---|---|---|
| Android-only | [`33231350902`](https://github.com/coding-back01/legado/actions/runs/33231350902) | Android 成功 | Web | 双 CodeQL、仓库检查、`维护门禁` 成功 |
| Web-only | [`33231704853`](https://github.com/coding-back01/legado/actions/runs/33231704853) | Web 成功 | Android | 双 CodeQL、仓库检查、`维护门禁` 成功 |

这些 run 证明实际任务和合法跳过能被 `维护门禁` 正确区分，但还不能完成 6.7：
安全修复产生新 head 后必须重新运行完整、Android-only、Web-only 和文档/OpenSpec-only
四类 run，且双 CodeQL 必须在每个 run 上实际分析。

## 首次 CodeQL 告警与 RED→GREEN

在 `d6a6b77c20f72923a271ecd81fd48b2085c2a4c0` 上手动完成分支双语言分析后，
API 返回 7 个打开的 high 告警，而不是零告警：

| 告警 | 规则 | 数量 | 根因 | 处置 |
|---|---|---:|---|---|
| `#4`–`#7` | `java/android/implicit-pendingintents` | 4 | 通知和媒体动作的 PendingIntent 虽有目标类型，但旧 helper 先构造隐式 Intent 且 Android 12+ 使用 mutable flag | 统一在 helper 中构造显式 class 目标并在 API 23+ 使用 `FLAG_IMMUTABLE`；删除未使用的非泛型 mutable overload |
| `#3` | `java/regex-injection` | 1 | TXT 目录规则允许用户输入并直接编译、匹配 512 KiB 文本 | 保留正则功能语义，增加 4,096 字符编译上限和共享字符访问步数预算；超限规则跳过或回退无规则拆分 |
| `#1`–`#2` | `js/xss-through-dom` | 2 | 上传文件名和大小经 jQuery `.html()` 写入 DOM | 全部动态值与进度改用 `.text()`，不再解释为 HTML |

先在旧实现上运行 `CodeQlHighRiskContractTest`，3/3 均按预期 RED；
`RegexSafetyTest` 在 `RegexSafety` 尚不存在时按预期编译 RED。实现后 PendingIntent、DOM 和
正则契约测试转为 GREEN。新增“全部默认 TXT 目录规则扫描 512 KiB 首块”兼容测试后，
初始 10,000,000 步上限在 `双标题(后向)` 规则上真实失败；最终预算按输入长度使用每字符
128 次访问、单 matcher 绝对上限 100,000,000 步。该测试随后通过，显式 10,000 步的
`(a+)+$` 灾难性回溯测试仍抛出 `RegexTimeoutException`。最终 7/7 个聚焦测试和完整
136 个 JVM 测试均通过。

正则告警描述的是允许用户编辑目录规则这一产品功能，不能用 `Pattern.quote` 破坏规则语义。
当前补偿控制在编译、输入访问和调用方回退三层实际限制资源；若最终 CodeQL 仍因不识别
自定义 `CharSequence` 步数预算而保留 `#3`，只有在最终 head 复验上述控制后，才可按
“未识别补偿控制”的 false positive 精确关闭并记录。其他 6 个告警必须由最终分析自动关闭，
不得人工接受。

## 最终 head 范围探针与告警闭环

安全修复 head 为
`f7a70178d3194a42f9b92322c586e0dfb7740e42`。以下五个 run 均锁定该 SHA，
双 CodeQL 在每个 run 中实际分析，`OpenSpec 与仓库检查` 和最终 `维护门禁` 均成功：

| 类型 | run | Android 质量 | Web 质量 | 结果 |
|---|---:|---|---|---|
| Pull Request 自动完整运行 | [`33233604598`](https://github.com/coding-back01/legado/actions/runs/33233604598) | 成功 | 成功 | 全部 job 与 `维护门禁` 成功 |
| 手动完整分支分析 | [`33233628790`](https://github.com/coding-back01/legado/actions/runs/33233628790) | 成功 | 成功 | 全部 job 与 `维护门禁` 成功 |
| Android-only | [`33241360363`](https://github.com/coding-back01/legado/actions/runs/33241360363) | 成功 | 合法跳过 | 双 CodeQL 与 `维护门禁` 成功 |
| Web-only | [`33241607714`](https://github.com/coding-back01/legado/actions/runs/33241607714) | 合法跳过 | 成功 | 双 CodeQL 与 `维护门禁` 成功 |
| 文档/OpenSpec-only | [`33233637486`](https://github.com/coding-back01/legado/actions/runs/33233637486) | 合法跳过 | 合法跳过 | 双 CodeQL 与 `维护门禁` 成功 |

首次同时提交四个手动探针时，GitHub concurrency 只允许一个 pending run，因后续入队而取消了
尚未执行的 `33233632123` 和 `33233634827`。它们不作为测试失败或通过证据；Android-only
与 Web-only 已在其他手动 run 完成后按上表串行重发并成功。

最终分析自动将原 `#1`–`#7` 全部标记为 fixed，其中旧正则 sink `#3` 因编译入口迁入
`RegexSafety` 而关闭；同一数据流在 helper 的 `Pattern.compile` 处生成新告警 `#8`。
这证明 CodeQL 仍跟踪了用户输入，但没有把 `StepLimitedCharSequence` 的共享访问计数识别为
净化或资源控制。在上述 7/7 聚焦测试、136 个完整 JVM 测试、全部内置规则 512 KiB
兼容测试和五类远端 run 均通过后，才于 2026-08-29 15:52 +0800 精确将 `#8` 按
`false positive` 关闭；评论完整记录 4,096 字符上限、每字符 128 步、100,000,000 步绝对
上限、超限回退和测试名称。关闭后该分支 CodeQL 状态为 open 0、dismissed 1、fixed 7，
没有对规则或其他位置进行批量忽略。

## 远端待完成门禁

以上证据满足任务 6.1–6.8。任务 6.9–6.11 仍保持未完成：证据提交后的最终 PR head
必须再次通过自动 `维护门禁` 且 CodeQL open high/critical 保持为零，随后才可转 Ready、
使用 merge commit 合并并验证 `master`；只有合并后的稳定检查名真实成功后，才允许按现有
ruleset 精确 ID 做最小门禁更新和单独启用自动删分支。正式发布继续冻结。
