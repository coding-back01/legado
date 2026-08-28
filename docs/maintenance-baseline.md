# 维护质量基线

## 适用范围

本文档记录 `coding-back01/legado` 个人稳定 fork 的 Android lint 与网页端 ESLint 治理基线。完整逐条 Android lint occurrence 以构建生成的 XML/HTML/文本报告和后续 CI artifact 为事实源；本文档只保存按 lint ID 的汇总、风险判断和精确例外。

参考提交为 `460970675fedb91d8d10aa42447bab8cc13e8a40`，参考运行时间为 2026-08-23 11:02 +0800。

## Android lint 参考命令

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
./gradlew :app:lintAppDebug --rerun-tasks --console=plain
```

参考运行退出码为 1，结果为 14 个 error、881 个 warning 和 18 个 hint。完整本地报告位于：

- `app/build/reports/lint-results-appDebug.xml`
- `app/build/reports/lint-results-appDebug.html`
- `app/build/reports/lint-results-appDebug.txt`

PR 5 建立持续门禁后，在此补充完整报告 artifact 的稳定名称和获取方式。

## 阻断错误基线

| lint ID | 原始数量 | 当前数量 | 计划处置 |
|---|---:|---:|---|
| `MissingClass` | 1 | 0 | PR 3 显式声明 AndroidX Startup 直接依赖；lint 与 APK Manifest 已验证 |
| `MissingTranslation` | 6 | 0 | PR 3 补齐全部现有 locale 并恢复默认英文资源；资源测试、lint 和模拟器界面证据已记录 |
| `NewApi` | 2 | 0 | PR 3 将并发 key set 暴露为公共 `MutableSet<String>` 接口；并发测试与 lint 已验证 |
| `RestrictedApi` | 1 | 0 | PR 3 改用公开 `RecyclerView` 类型；聚焦测试与 lint 已验证 |
| `WrongConstant` | 4 | 0 | PR 3 修正位掩码、orientation typedef 与输入法 flag；聚焦测试与 lint 已验证 |
| **合计** | **14** | **0** | PR 3 已清零；全量验证完成前仍不视为可合并 |

## Warning 三态账本

状态说明：

- `PENDING_REVIEW`：尚未完成风险审查，阻止总治理完成。
- `FIXED`：已通过聚焦验证安全修复。
- `SUPPRESSED_WITH_REASON`：经证明为误报或刻意兼容行为，只在精确位置局部抑制。
- `DEFERRED`：本轮无法安全修复，已记录风险、原因和重新启动条件。

| lint ID | 风险 | 原始数量 | 当前数量 | FIXED | SUPPRESSED | DEFERRED | PENDING | 初始判断 |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `AndroidGradlePluginVersion` | 低 | 4 | 4 | 0 | 0 | 0 | 4 | 工具链提示；不得混入普通依赖升级 |
| `AppBundleLocaleChanges` | 高 | 1 | 0 | 1 | 0 | 0 | 0 | PR 3 关闭语言资源拆分并提升为 fatal；聚焦契约与 lint 已验证 |
| `Autofill` | 中 | 1 | 0 | 1 | 0 | 0 | 0 | PR 4e（#58）将只由日期选择器赋值的字段明确排除自动填充；聚焦契约与 lint 已验证 |
| `ContentDescription` | 中 | 2 | 0 | 2 | 0 | 0 | 0 | PR 4e（#58）为锁定章节图标补齐全 locale 语义，并将装饰性主题图标预览移出无障碍树 |
| `DefaultLocale` | 高 | 8 | 0 | 8 | 0 | 0 | 0 | PR 3 对内部标识统一使用 `Locale.ROOT` 并提升为 fatal；土耳其语行为测试与 lint 已验证 |
| `DiscouragedApi` | 中 | 11 | 11 | 0 | 0 | 0 | 11 | 需逐项确认公开替代及行为等价性 |
| `GradleDependency` | 低 | 14 | 14 | 0 | 0 | 0 | 14 | 多数涉及兼容固定版本，不得机械升级 |
| `HardcodedText` | 中 | 14 | 0 | 14 | 0 | 0 | 0 | PR 4c（#56）将运行时文本、提示和无障碍说明替换为现有或全 locale 资源 |
| `IconDuplicates` | 低 | 1 | 1 | 0 | 0 | 0 | 1 | 资源维护债务 |
| `IconLocation` | 中 | 6 | 6 | 0 | 0 | 0 | 6 | 资源密度与打包行为 |
| `InefficientWeight` | 中 | 1 | 0 | 1 | 0 | 0 | 0 | PR 4f（#59）在固定宽度父容器中用 `0dp + weight` 保持开关占用同一剩余宽度，避免重复测量 |
| `IntentWithNullActionLaunch` | 高 | 1 | 0 | 1 | 0 | 0 | 0 | PR 3 为 QQ 跳转设置显式 `ACTION_VIEW` 并提升为 fatal；契约测试与 lint 已验证 |
| `KeyboardInaccessibleWidget` | 中 | 1 | 0 | 1 | 0 | 0 | 0 | PR 4e（#58）保留日期字段键盘焦点与点击入口，并明确禁止软键盘直接编辑 |
| `NewerVersionAvailable` | 低 | 21 | 21 | 0 | 0 | 0 | 21 | 依赖维护提示，不得突破兼容固定版本 |
| `Overdraw` | 中 | 41 | 41 | 0 | 0 | 41 | 0 | PR 4f（#59）完成逐位置审查；根背景移除需要日夜模式与透明页面截图基线，本轮精确延期 |
| `PluralsCandidate` | 低 | 5 | 0 | 5 | 0 | 0 | 0 | PR 4d（#57）删除 5 个经全仓动态引用审计确认无使用的资源；聚焦契约与 lint 已验证 |
| `RtlHardcoded` | 中 | 7 | 0 | 7 | 0 | 0 | 0 | PR 4a（#54）将物理方向间距与 gravity 改为逻辑方向；布局契约与 lint 已验证 |
| `RtlSymmetry` | 中 | 2 | 0 | 2 | 0 | 0 | 0 | PR 4a（#54）为单侧逻辑内边距补齐显式零起始值；布局契约与 lint 已验证 |
| `SetTextI18n` | 中 | 8 | 0 | 4 | 4 | 0 | 0 | PR 4b（#55）本地化显示数字与跳转提示；4 个 ASCII 数字输入保留精确兼容抑制 |
| `TextFields` | 中 | 1 | 0 | 0 | 1 | 0 | 0 | PR 4e（#58）保留日期选择器专用字段的 `inputType="none"`，并在精确视图记录兼容理由 |
| `UnusedAttribute` | 低 | 1 | 1 | 0 | 0 | 0 | 1 | 资源维护债务 |
| `UnusedResources` | 中 | 594 | 2 | 592 | 0 | 2 | 0 | PR 3 显式加入 Startup 后减少 1 项；PR 4d（#57）删除 5 个已审计复数候选；PR 4q 对剩余 588 项完成动态引用和死资源闭包审计，删除 586 项并因既有翻译与 RTL 契约精确延期 2 项，当前已完成全 ID 对账 |
| `UseCompoundDrawables` | 低 | 1 | 1 | 0 | 0 | 1 | 0 | PR 4f（#59）审查确认会改变两个文件选择器的 View Binding、图标尺寸与点击区域，本轮精确延期 |
| `UseKtx` | 低 | 133 | 0 | 126 | 7 | 0 | 0 | PR 3 与 PR 4g 至 4o 已完成 116 项安全转换；PR 4l（#65）另精确抑制 7 个可空兼容 occurrence；PR 4p（#69）完成 10 个 Canvas 状态转换，当前已完成全 ID 对账 |
| `UselessParent` | 中 | 1 | 1 | 0 | 0 | 1 | 0 | PR 4f（#59）审查确认需移动漫画菜单边距、背景和测量职责，缺少稳定页面截图，本轮精确延期 |
| `VectorPath` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 图形精度与渲染风险 |
| **合计** |  | **881** | **104** | **765** | **12** | **45** | **59** | 逐 ID 审查中；PR 3 与 PR 4a 至 PR 4q 已完成 777 项修复/抑制，并精确延期 45 项 |

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

## 精确延期项

| lint ID | 精确范围 | 数量 | 行为风险与延期原因 | 重新启动条件 | 证据 |
|---|---|---:|---|---|---|
| `UnusedResources` | `app/src/main/res/layout/dialog_progressbar_view.xml:2`；8 个支持 locale 的 `strings.xml` 中 `del_all` 对应 1 个 base occurrence | 2 | 删除后既有 `RtlLayoutContractTest` 和 `BlockingTranslationResourceTest` 分别失败，会撤销已完成的 RTL 与六键全 locale 契约 | 通过新的 OpenSpec 评审明确退役进度布局或 `del_all`，并在证明不存在运行时入口后同步替换相应跨批契约测试 | PR 4q `warning-pr4q-unused-resources.md` 的全量 RED、聚焦 GREEN 与 lint 对账 |
| `Overdraw` | 41 个根布局背景位置，逐文件与行号见 PR 4f 证据 | 41 | 根背景可能承担日夜主题、不透明对话框或透明页面兜底；批量删除会改变实际渲染，当前没有逐页面截图基线 | 为清单中每个页面建立日夜模式截图或像素差异基线，并覆盖透明窗口与弹窗背景后逐批重启 | PR 4f（#59）`warning-pr4f-layout-performance.md` |
| `UselessParent` | `app/src/main/res/layout/view_manga_menu.xml:85` | 1 | 扁平化需要把子容器 margin 改为父容器 padding，并改变背景和测量职责；缺少稳定漫画菜单夹具与截图 | 建立漫画页面固定夹具、菜单 UI 层级和日夜模式截图，证明 SeekBar、前后章按钮位置与点击区域不变 | PR 4f（#59）`warning-pr4f-layout-performance.md` |
| `UseCompoundDrawables` | `app/src/main/res/layout/item_path_picker.xml:2` | 1 | 合并为单 TextView 会改变两个文件选择器的 View Binding 字段、动态 Drawable 尺寸、文本着色和整行点击区域 | 为文件管理与文件选择对话框建立路径面包屑导航测试、图标/文字截图和点击区域断言后独立重构 | PR 4f（#59）`warning-pr4f-layout-performance.md` |

## Hint 清单

Hint 不进入 warning 三态，但保留数量用于审计：

| lint ID | 数量 | 说明 |
|---|---:|---|
| `ReportShortcutUsage` | 1 | 非阻断使用建议 |
| `TrimLambda` | 17 | 非阻断机械建议 |
| **合计** | **18** | 不得描述为 warning 已处理 |

## 网页端 ESLint 基线

参考运行 `pnpm exec eslint .` 退出码为 1，包含 2 个 error、0 个 warning：

- `modules/web/src/source.d.ts:82:6`：`RuleSearch` 未使用。
- `modules/web/src/utils/souce.ts:52:41`：使用显式 `any`。

治理完成时网页端 ESLint 必须以 0 个 error 成功退出。
