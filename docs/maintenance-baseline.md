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
| `Autofill` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 影响输入体验与隐私语义 |
| `ContentDescription` | 中 | 2 | 2 | 0 | 0 | 0 | 2 | 无障碍可访问性 |
| `DefaultLocale` | 高 | 8 | 0 | 8 | 0 | 0 | 0 | PR 3 对内部标识统一使用 `Locale.ROOT` 并提升为 fatal；土耳其语行为测试与 lint 已验证 |
| `DiscouragedApi` | 中 | 11 | 11 | 0 | 0 | 0 | 11 | 需逐项确认公开替代及行为等价性 |
| `GradleDependency` | 低 | 14 | 14 | 0 | 0 | 0 | 14 | 多数涉及兼容固定版本，不得机械升级 |
| `HardcodedText` | 中 | 14 | 0 | 14 | 0 | 0 | 0 | PR 4c（#56）将运行时文本、提示和无障碍说明替换为现有或全 locale 资源 |
| `IconDuplicates` | 低 | 1 | 1 | 0 | 0 | 0 | 1 | 资源维护债务 |
| `IconLocation` | 中 | 6 | 6 | 0 | 0 | 0 | 6 | 资源密度与打包行为 |
| `InefficientWeight` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 布局性能，需验证界面不变 |
| `IntentWithNullActionLaunch` | 高 | 1 | 0 | 1 | 0 | 0 | 0 | PR 3 为 QQ 跳转设置显式 `ACTION_VIEW` 并提升为 fatal；契约测试与 lint 已验证 |
| `KeyboardInaccessibleWidget` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 键盘与无障碍可达性 |
| `NewerVersionAvailable` | 低 | 21 | 21 | 0 | 0 | 0 | 21 | 依赖维护提示，不得突破兼容固定版本 |
| `Overdraw` | 中 | 41 | 41 | 0 | 0 | 0 | 41 | 性能与视觉行为需逐布局验证 |
| `PluralsCandidate` | 低 | 5 | 5 | 0 | 0 | 0 | 5 | 本地化表达建议 |
| `RtlHardcoded` | 中 | 7 | 0 | 7 | 0 | 0 | 0 | PR 4a（#54）将物理方向间距与 gravity 改为逻辑方向；布局契约与 lint 已验证 |
| `RtlSymmetry` | 中 | 2 | 0 | 2 | 0 | 0 | 0 | PR 4a（#54）为单侧逻辑内边距补齐显式零起始值；布局契约与 lint 已验证 |
| `SetTextI18n` | 中 | 8 | 0 | 4 | 4 | 0 | 0 | PR 4b（#55）本地化显示数字与跳转提示；4 个 ASCII 数字输入保留精确兼容抑制 |
| `TextFields` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 输入控件行为与可用性 |
| `UnusedAttribute` | 低 | 1 | 1 | 0 | 0 | 0 | 1 | 资源维护债务 |
| `UnusedResources` | 中 | 594 | 593 | 1 | 0 | 0 | 593 | PR 3 显式加入 Startup 后 lint 可达性分析减少 1 项；其余可能存在反射、名称拼接或规则动态引用，删除前必须审计 |
| `UseCompoundDrawables` | 低 | 1 | 1 | 0 | 0 | 0 | 1 | 可证明等价后再机械调整 |
| `UseKtx` | 低 | 133 | 132 | 1 | 0 | 0 | 132 | PR 3 的显式 Intent 构造安全消除 1 项；其余只处理语义等价位置，不全量替换 |
| `UselessParent` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 布局层级变化需视觉验证 |
| `VectorPath` | 中 | 1 | 1 | 0 | 0 | 0 | 1 | 图形精度与渲染风险 |
| **合计** |  | **881** | **838** | **39** | **4** | **0** | **838** | 逐 ID 审查中；PR 3、PR 4a、PR 4b 与 PR 4c 已处置 43 项 |

## 精确局部抑制

| lint ID | 文件与范围 | 数量 | 理由 | 重新启动条件 | 证据 |
|---|---|---:|---|---|---|
| `SetTextI18n` | `BaseReadBookActivity.showSimulatedReading` 的起始章节和每日章节输入 | 2 | 两个字段保存 ASCII 整数并直接由 `toInt()` 回读；本地化数字或分组符会破坏往返解析 | 字段改为数值模型绑定，或解析器能够可靠接受当前 locale 的数字与分组符 | PR 4b（#55）聚焦契约、完整单元测试与 lint |
| `SetTextI18n` | `CheckSourceConfig.onFragmentCreated` 的超时秒数输入 | 1 | 字段保存 ASCII 整数并直接由 `toLong()` 回读；本地化数字或分组符会破坏往返解析 | 超时字段改为数值模型绑定，或解析器能够可靠接受当前 locale 的数字与分组符 | PR 4b（#55）聚焦契约、完整单元测试与 lint |
| `SetTextI18n` | `ReplaceEditActivity.upReplaceView` 的替换超时毫秒输入 | 1 | 规则字段保存 ASCII 整数并直接由 `toLong()` 回读；改变持久化输入语义可能破坏既有规则编辑 | 规则编辑字段改为数值模型绑定，或解析器能够可靠接受当前 locale 的数字与分组符 | PR 4b（#55）聚焦契约、完整单元测试与 lint |

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
