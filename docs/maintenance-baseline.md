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
| `MissingClass` | 1 | 1 | 显式声明 AndroidX Startup 直接依赖 |
| `MissingTranslation` | 6 | 6 | 补齐缺失 locale 并恢复默认英文资源 |
| `NewApi` | 2 | 2 | 将并发 key set 暴露为公共 `MutableSet<String>` 接口 |
| `RestrictedApi` | 1 | 1 | 改用公开 `RecyclerView` 类型 |
| `WrongConstant` | 4 | 4 | 修正位掩码、orientation typedef 与输入法 flag |
| **合计** | **14** | **14** | 治理完成时必须为 0 |

## Warning 三态账本

状态说明：

- `PENDING_REVIEW`：尚未完成风险审查，阻止总治理完成。
- `FIXED`：已通过聚焦验证安全修复。
- `SUPPRESSED_WITH_REASON`：经证明为误报或刻意兼容行为，只在精确位置局部抑制。
- `DEFERRED`：本轮无法安全修复，已记录风险、原因和重新启动条件。

| lint ID | 风险 | 原始数量 | 当前数量 | FIXED | SUPPRESSED | DEFERRED | PENDING | 初始判断 |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `AndroidGradlePluginVersion` | 低 | 4 | 4 | 0 | 0 | 0 | 4 | 工具链提示；不得混入普通依赖升级 |
| `AppBundleLocaleChanges` | 高 | 1 | 1 | 0 | 0 | 0 | 1 | 可能改变按应用语言行为，列入后续阻断 |
| `Autofill` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 影响输入体验与隐私语义 |
| `ContentDescription` | 中 | 2 | 2 | 0 | 0 | 0 | 2 | 无障碍可访问性 |
| `DefaultLocale` | 高 | 8 | 8 | 0 | 0 | 0 | 8 | 可能在特定语言环境产生错误结果，列入后续阻断 |
| `DiscouragedApi` | 中 | 11 | 11 | 0 | 0 | 0 | 11 | 需逐项确认公开替代及行为等价性 |
| `GradleDependency` | 低 | 14 | 14 | 0 | 0 | 0 | 14 | 多数涉及兼容固定版本，不得机械升级 |
| `HardcodedText` | 中 | 14 | 14 | 0 | 0 | 0 | 14 | 影响本地化与可维护性 |
| `IconDuplicates` | 低 | 1 | 1 | 0 | 0 | 0 | 1 | 资源维护债务 |
| `IconLocation` | 中 | 6 | 6 | 0 | 0 | 0 | 6 | 资源密度与打包行为 |
| `InefficientWeight` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 布局性能，需验证界面不变 |
| `IntentWithNullActionLaunch` | 高 | 1 | 1 | 0 | 0 | 0 | 1 | 可能导致错误 Intent 行为，列入后续阻断 |
| `KeyboardInaccessibleWidget` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 键盘与无障碍可达性 |
| `NewerVersionAvailable` | 低 | 21 | 21 | 0 | 0 | 0 | 21 | 依赖维护提示，不得突破兼容固定版本 |
| `Overdraw` | 中 | 41 | 41 | 0 | 0 | 0 | 41 | 性能与视觉行为需逐布局验证 |
| `PluralsCandidate` | 低 | 5 | 5 | 0 | 0 | 0 | 5 | 本地化表达建议 |
| `RtlHardcoded` | 中 | 7 | 7 | 0 | 0 | 0 | 7 | RTL 布局兼容 |
| `RtlSymmetry` | 中 | 2 | 2 | 0 | 0 | 0 | 2 | RTL 布局对称性 |
| `SetTextI18n` | 中 | 8 | 8 | 0 | 0 | 0 | 8 | 本地化与格式化语义 |
| `TextFields` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 输入控件行为与可用性 |
| `UnusedAttribute` | 低 | 1 | 1 | 0 | 0 | 0 | 1 | 资源维护债务 |
| `UnusedResources` | 中 | 594 | 594 | 0 | 0 | 0 | 594 | 可能存在反射、名称拼接或规则动态引用，删除前必须审计 |
| `UseCompoundDrawables` | 低 | 1 | 1 | 0 | 0 | 0 | 1 | 可证明等价后再机械调整 |
| `UseKtx` | 低 | 133 | 133 | 0 | 0 | 0 | 133 | 只处理语义等价位置，不全量替换 |
| `UselessParent` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 布局层级变化需视觉验证 |
| `VectorPath` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 图形精度与渲染风险 |
| **合计** |  | **881** | **881** | **0** | **0** | **0** | **881** | 逐 ID 审查中 |

## 精确局部抑制

当前没有批准的局部抑制。新增记录必须包含 lint ID、文件与位置、理由、验证证据和对应 PR。

## 精确延期项

当前没有完成审查的延期项。新增记录必须包含 lint ID、精确范围、剩余数量、行为风险、延期原因、重新启动条件和对应 PR。

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
