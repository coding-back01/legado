# 维护质量基线

## 适用范围

本文档记录 `coding-back01/legado` 个人稳定 fork 的 Android lint 与网页端 ESLint 治理基线。完整逐条 Android lint occurrence 以构建生成的 XML/HTML/文本报告和后续 CI artifact 为事实源；本文档只保存按 lint ID 的汇总、风险判断和精确例外。

历史参考提交为 `460970675fedb91d8d10aa42447bab8cc13e8a40`，参考运行时间为 2026-08-23 11:02 +0800。本次单一变更 `eliminate-android-lint-findings` 的 Apply 起始提交为 `826a49d2199b571fc8533b9ae0005fd8e0d70369`；起始强制报告为 108 个 warning、18 个 hint、0 个 error。PR #84 首轮合并参考 CI 的 runner 新增 API 37 后，又发现 3 个 `OldTargetApi` warning；它们与起始报告累计形成 111 个 warning 的完整治理范围，最终强制报告为 0 个 issue。

## Android lint 参考命令

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
./gradlew :app:lintAppDebug --rerun-tasks --console=plain
```

历史参考运行退出码为 1，结果为 14 个 error、881 个 warning 和 18 个 hint。本次 Apply 使用 JDK 17、固定 SDK、`--rerun-tasks --no-daemon --warning-mode all` 强制重跑，最终退出码为 0。完整本地报告位于：

- `app/build/reports/lint-results-appDebug.xml`
- `app/build/reports/lint-results-appDebug.html`
- `app/build/reports/lint-results-appDebug.txt`

PR 5 的维护 workflow 在每次适用运行中上传 `android-lint-<提交 SHA>` artifact，
保留 30 天；可从对应 `Test Build` run 的 Artifacts 区下载完整 XML、HTML 和文本报告。

## 阻断错误基线

| lint ID | 原始数量 | 当前数量 | 计划处置 |
|---|---:|---:|---|
| `MissingClass` | 1 | 0 | PR 3 显式声明 AndroidX Startup 直接依赖；lint 与 APK Manifest 已验证 |
| `MissingTranslation` | 6 | 0 | PR 3 补齐全部现有 locale 并恢复默认英文资源；资源测试、lint 和模拟器界面证据已记录 |
| `NewApi` | 2 | 0 | PR 3 将并发 key set 暴露为公共 `MutableSet<String>` 接口；并发测试与 lint 已验证 |
| `RestrictedApi` | 1 | 0 | PR 3 改用公开 `RecyclerView` 类型；聚焦测试与 lint 已验证 |
| `WrongConstant` | 4 | 0 | PR 3 修正位掩码、orientation typedef 与输入法 flag；聚焦测试与 lint 已验证 |
| **合计** | **14** | **0** | PR 3 已清零；全量验证完成前仍不视为可合并 |

## Warning 处置账本

状态说明：`FIXED` 表示已通过聚焦验证安全修复；`SUPPRESSED_WITH_REASON` 表示经证据确认必须保留，并只在精确位置抑制。`DEFERRED` 和 `PENDING_REVIEW` 在最终账本中必须为 0。

| lint ID | 风险 | 历史原始 | 本次累计纳入 | 最终可见 | 本次 FIXED | 本次 SUPPRESSED_WITH_REASON | DEFERRED | PENDING_REVIEW | 证据与结论 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| `AndroidGradlePluginVersion` | 低 | 4 | 4 | 0 | 0 | 4 | 0 | 0 | wrapper 单文件路径与 AGP 版本声明精确抑制；版本未升级 |
| `AppBundleLocaleChanges` | 高 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 既有修复与 fatal 保持；本次起始报告未出现 |
| `Autofill` | 中 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 既有日期字段契约保持；本次起始报告未出现 |
| `ContentDescription` | 中 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 既有无障碍修复保持；本次起始报告未出现 |
| `DefaultLocale` | 高 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 既有 `Locale.ROOT` 修复与 fatal 保持 |
| `DiscouragedApi` | 中 | 11 | 11 | 0 | 2 | 9 | 0 | 0 | 两个资源名称反射改为类型化 ID；九个 `behind` 入口按 Manifest 节点保留 |
| `GradleDependency` | 低 | 14 | 14 | 0 | 0 | 14 | 0 | 0 | AndroidX、Material 与测试库逐坐标抑制；版本未升级 |
| `HardcodedText` | 中 | 14 | 0 | 0 | 0 | 0 | 0 | 0 | 既有字符串资源修复保持 |
| `IconDuplicates` | 低 | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 两个语义不同的二进制资源按精确路径保留 |
| `IconLocation` | 中 | 6 | 6 | 0 | 6 | 0 | 0 | 0 | 六张生产位图迁入 `drawable-nodpi`，解码和 golden 已验证 |
| `InefficientWeight` | 中 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 既有布局修复保持 |
| `IntentWithNullActionLaunch` | 高 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 既有显式 Intent 与 fatal 保持 |
| `KeyboardInaccessibleWidget` | 中 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 既有键盘与点击入口契约保持 |
| `NewerVersionAvailable` | 低 | 21 | 25 | 0 | 0 | 25 | 0 | 0 | 25 个起始坐标均在版本声明处审计；Download 的远程提示后来消失但仍保留精确记录 |
| `OldTargetApi` | 低 | 0 | 3 | 0 | 0 | 3 | 0 | 0 | PR #84 runner 新增 API 37 后出现；应用与两个库的 `targetSdk 36` 逐文件保留，未伪装完成 API 37 迁移 |
| `Overdraw` | 中 | 41 | 41 | 0 | 4 | 37 | 0 | 0 | 四个等价背景删除；37 个语义背景逐布局保留 |
| `PluralsCandidate` | 低 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 既有死资源修复保持 |
| `RtlHardcoded` | 中 | 7 | 0 | 0 | 0 | 0 | 0 | 0 | 既有逻辑方向修复保持 |
| `RtlSymmetry` | 中 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 既有逻辑间距修复保持 |
| `SetTextI18n` | 中 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 既有四处修复与四处精确兼容抑制保持 |
| `TextFields` | 中 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 既有日期选择字段精确抑制保持 |
| `UnusedAttribute` | 低 | 1 | 1 | 0 | 0 | 1 | 0 | 0 | API 23+ RSS ripple 按根节点保留，API 21/23/36 交互已验证 |
| `UnusedResources` | 中 | 594 | 2 | 0 | 2 | 0 | 0 | 0 | 删除死布局与全 locale 的 `del_all`，同步退役旧契约断言 |
| `UseCompoundDrawables` | 低 | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 文件路径控件因 View Binding、20dp 箭头和整行点击合同按根节点保留 |
| `UseKtx` | 低 | 133 | 0 | 0 | 0 | 0 | 0 | 0 | 既有 126 个修复与 7 个可空兼容抑制保持 |
| `UselessParent` | 中 | 1 | 1 | 0 | 0 | 1 | 0 | 0 | 漫画菜单外层背景覆盖进度行边距，按精确容器保留 |
| `VectorPath` | 中 | 1 | 1 | 0 | 0 | 1 | 0 | 0 | launcher3 路径无法在 0.01% 阈值内继续降精度，按精确 `<path>` 保留 |
| **合计** |  | **881** | **111** | **0** | **14** | **97** | **0** | **0** | 111 = 14 + 97，起始与 PR CI 新增 occurrence 累计数量守恒 |

PR 5b 于 2026-08-30 重跑 lint 时，`NewerVersionAvailable` 从 21 项漂移为 25 项：同一 Kotlin 版本由 6 个增为 9 个插件坐标提示，并新识别 Download 插件更新。本次 Apply 将起始报告中的 25 个坐标全部记录为 `SUPPRESSED_WITH_REASON`；即使 Download 提示在后续远程查询中消失，也没有把它伪装成源码修复或从起始数量中删除。

PR #84 首轮工作流在合并参考提交 `fde15dd126f631a8a51095ee5c62b06c3f2a3ffe` 上执行，lint 因 runner 比本地固定 SDK 多安装了 API 37，对 `app`、`modules/book`、`modules/rhino` 的 `targetSdk 36` 各报 1 个 `OldTargetApi`。这 3 项已按精确 Gradle 文件路径纳入账本；在 API 37 平台迁移完成构建、行为和设备矩阵前，不为消除提示直接提升 `targetSdk`。

## 精确局部抑制

| lint ID | 文件与范围 | 数量 | 理由 | 重新启动条件 | 证据 |
|---|---|---:|---|---|---|
| `SetTextI18n` | `BaseReadBookActivity.showSimulatedReading` 的起始章节和每日章节输入 | 2 | 两个字段保存 ASCII 整数并直接由 `toInt()` 回读；本地化数字或分组符会破坏往返解析 | 字段改为数值模型绑定，或解析器能够可靠接受当前 locale 的数字与分组符 | PR 4b（#55）聚焦契约、完整单元测试与 lint |
| `SetTextI18n` | `CheckSourceConfig.onFragmentCreated` 的超时秒数输入 | 1 | 字段保存 ASCII 整数并直接由 `toLong()` 回读；本地化数字或分组符会破坏往返解析 | 超时字段改为数值模型绑定，或解析器能够可靠接受当前 locale 的数字与分组符 | PR 4b（#55）聚焦契约、完整单元测试与 lint |
| `SetTextI18n` | `ReplaceEditActivity.upReplaceView` 的替换超时毫秒输入 | 1 | 规则字段保存 ASCII 整数并直接由 `toLong()` 回读；改变持久化输入语义可能破坏既有规则编辑 | 规则编辑字段改为数值模型绑定，或解析器能够可靠接受当前 locale 的数字与分组符 | PR 4b（#55）聚焦契约、完整单元测试与 lint |
| `TextFields` | `dialog_simulated_reading.xml` 的 `start_date` 日期选择字段 | 1 | 日期只由 `DatePickerDialog` 选择；`inputType="none"` 阻止软键盘直接编辑，字段仍保留键盘焦点与点击入口 | 字段改为允许可靠文本解析的直接输入，或替换为不会触发该检查的日期选择控件 | PR 4e（#58）聚焦契约、完整单元测试与 lint |
| `UseKtx` | `ReadBookConfig.Config.curBgDrawable` 的内置与文件背景分支 | 2 | 两个解码结果均为可空 `Bitmap`；原构造在空值时返回空 `BitmapDrawable`，KTX 只接受非空 receiver，安全调用会改变兜底 Drawable | KTX 提供保持空 `BitmapDrawable` 语义的可空重载，或建立页面证据后明确批准改变解码失败兜底 | PR 4l（#65）聚焦契约、完整单元测试与 lint；2 个实际构造 |
| `UseKtx` | `BookCover.upDefaultCover` 的自定义默认封面构造 | 1 | 解码结果为可空 `Bitmap`；直接改用 KTX 无法保留原空 `BitmapDrawable` 返回语义 | KTX 提供等价可空重载，或建立默认封面解码失败行为测试后明确批准改变兜底 | PR 4l（#65）聚焦契约、完整单元测试与 lint |
| `UseKtx` | `WelcomeActivity.upBackgroundImage` 的深色与普通欢迎图分支 | 4 | 两个实际构造的解码结果为可空 `Bitmap`，且空值时仍会设置背景并提前返回；安全调用会改变回退到父类背景的控制流；lint 对每个构造重复登记一次 | KTX 提供等价可空重载，或建立两种主题下解码失败的 Activity 行为测试后明确批准改变回退控制流 | PR 4l（#65）聚焦契约、完整单元测试与 lint；2 个实际构造、4 个 occurrence |

## 本次治理的精确抑制

| lint ID | 精确范围 | 起始 occurrence | SUPPRESSED_WITH_REASON | 保留理由与重新启动条件 | 证据 |
|---|---|---:|---:|---|---|
| `AndroidGradlePluginVersion` | wrapper 的唯一 `distributionUrl` 文件路径；版本目录 `agp` 声明覆盖三个 AGP 插件 | 4 | 4 | 工具链需成组升级；独立升级通过 JDK 17、三插件和 API 21/23/36 矩阵后删除 | 实施证据“Gradle 与 AGP”“版本提示批次” |
| `DiscouragedApi` | Manifest 九个 `screenOrientation="behind"` activity 节点 | 11 | 9 | 保持 Android 16 以下及厂商方向处理；九页完成自适应布局后重评。另两个资源反射 occurrence 已修复 | 实施证据“Launcher 图标类型化资源”“九个 behind 页面” |
| `GradleDependency` | `appcompat`、`constraintlayout`、`core`、`fragment`、`material`、`media`、`collection` 与四个 AndroidX Test/Annotation 声明 | 14 | 14 | 运行库、UI 与测试栈需逐坐标独立升级；各声明旁记录对应回归条件 | 实施证据“版本提示批次最终对账” |
| `NewerVersionAvailable` | Kotlin、Glide、Gson、JsonPath、JsoupXpath、Coroutines、OkHttp、ZXing Lite、Rhino、Glide Compose、Download 的版本声明 | 25 | 25 | 工具链、解析、网络、图片和最低 Android 兼容域不能混升；各声明旁记录独立重评条件 | 实施证据“版本提示批次最终对账” |
| `OldTargetApi` | `app/build.gradle`、`modules/book/build.gradle`、`modules/rhino/build.gradle` 的三个 `targetSdk 36` 声明 | 3 | 3 | 已发布与验证基线仍为 API 36；独立 API 37 迁移完成 API 21/23/36/37 构建、行为与设备矩阵后删除 | 实施证据“PR #84 API 37 runner 漂移处置” |
| `IconDuplicates` | `drawable-nodpi/image_legado.png` 与 `mipmap-xxxhdpi/ic_launcher.png` | 1 | 1 | 字节相同但 RSS fallback 与旧 launcher 密度语义不同；任一资源体系退役后重评 | 实施证据“重复图标的精确路径抑制” |
| `UnusedAttribute` | `item_rss.xml` 根节点的 foreground | 1 | 1 | 保留 API 23+ 整项 ripple；最低版本升至 23 或拆分版本布局后重评 | 实施证据“RSS 根项交互与 foreground” |
| `Overdraw` | `evidence/overdraw-disposition.tsv` 中 37 个 `SUPPRESSED_WITH_REASON` 根布局 | 41 | 37 | 背景承担 ripple、透明遮罩、主题兜底或内容语义；对应语义退役后逐布局重评。另四处已删除 | 实施证据“Overdraw 精确分类”至“最终对账” |
| `UselessParent` | `view_manga_menu.xml` 的进度行父层 | 1 | 1 | 外层背景覆盖进度行边距；改用单一 Surface 且 golden 等价后重评 | 实施证据“漫画菜单无用父布局处置” |
| `UseCompoundDrawables` | `item_path_picker.xml` 根节点 | 1 | 1 | 保留 View Binding、20dp 动态箭头、RTL 与整行点击合同；箭头资源化后重评 | 实施证据“文件路径 compound drawable 处置” |
| `VectorPath` | `ic_launcher3.xml` 的唯一长 `<path>` | 1 | 1 | 继续降精度会改变书法轮廓；源图重制或新优化器通过 0.01% 阈值后重评 | 实施证据“launcher3 长矢量路径处置” |

`trimAsciiControlAndSpace()` 自身另有一个精确 `@Suppress("TrimLambda")`，用于保留 U+0000—U+0020 旧式裁剪语义；起始 17 个调用点均已改为命名扩展，因此在下方 hint 数量守恒中计为 17 个 `FIXED`，不重复计入起始 suppression。

## Hint 处置账本

| lint ID | 本次开始 | 最终可见 | FIXED | SUPPRESSED_WITH_REASON | DEFERRED | PENDING_REVIEW | 证据与结论 |
|---|---:|---:|---:|---:|---:|---:|---|
| `ReportShortcutUsage` | 1 | 0 | 1 | 0 | 0 | 0 | 三个稳定 shortcut ID 只在真实入口消费后上报，普通启动和 Intent 重放不误报 |
| `TrimLambda` | 17 | 0 | 17 | 0 | 0 | 0 | 17 个调用点改用保留 U+0000—U+0020 语义的命名扩展，聚焦边界测试通过 |
| **合计** | **18** | **0** | **18** | **0** | **0** | **0** | 18 = 18 + 0；hint 与 warning 分开守恒 |

最终强制 lint XML SHA-256 为 `3782329f5ec7fc80243d071df4885364697fe238cd16d0fd49637d3010e96025`，标准库清单工具输出 `总计: 0`。完整起始清单、处置映射、golden、测试失败重试和真机数据边界见 `openspec/changes/archive/2026-09-15-eliminate-android-lint-findings/implementation-evidence.md`。

## 网页端 ESLint 基线

参考运行 `pnpm exec eslint .` 退出码为 1，包含 2 个 error、0 个 warning：

- `modules/web/src/source.d.ts:82:6`：`RuleSearch` 未使用。
- `modules/web/src/utils/souce.ts:52:41`：使用显式 `any`。

治理完成时网页端 ESLint 必须以 0 个 error 成功退出。
