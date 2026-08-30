# PR 3 合并后 warning occurrence 清单

## 生成身份与命令

PR 3 的生产代码 merge commit 为 `5fa69d4022c9e4df0b76225561b34330489b1f19`。本清单在随后只增加 OpenSpec 合并证据的 `master` 提交 `f8adb3fe0b2100ecdc6412f34fac56bcf672010d` 上生成；两者的 Android、Web 与构建配置相同。

2026-08-27 使用 JDK 17 和本机 Android SDK 强制重跑全部 lint 任务，不复用旧报告：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:lintAppDebug --rerun-tasks --console=plain
```

103 个任务全部实际执行，命令成功，结果为 0 error、869 warning、18 hint。

## 完整 occurrence 报告

完整逐 occurrence 报告保存在 Git 忽略的本机构建目录，版本化证据只记录摘要和内容哈希：

| 报告 | 字节数 | SHA-256 |
|---|---:|---|
| `lint-results-appDebug.xml` | 959849 | `5b47c767f8cffa6b737dca3abd7f1a1a99ec1b822a1bc492112ec287ca333efd` |
| `lint-results-appDebug.html` | 528766 | `75f363d42ff9ca17199d36390fdb859fbb73105fd32e19e6a118aeebc28d1b57` |
| `lint-results-appDebug.txt` | 257911 | `98ed9708749178f3d98150d8e6d6e5f82bdabfc7a3e6f5c1ab3a0312b61dff70` |

PR 5 建立持续门禁后，完整报告将作为 CI artifact 保存；在此之前不把本地文件提交进 Git，也不创建全局 lint baseline。

## lint ID 对账

XML 中 23 个仍有 warning occurrence 的 lint ID 如下：

| lint ID | 当前数量 |
|---|---:|
| `AndroidGradlePluginVersion` | 4 |
| `Autofill` | 1 |
| `ContentDescription` | 2 |
| `DiscouragedApi` | 11 |
| `GradleDependency` | 14 |
| `HardcodedText` | 14 |
| `IconDuplicates` | 1 |
| `IconLocation` | 6 |
| `InefficientWeight` | 1 |
| `KeyboardInaccessibleWidget` | 1 |
| `NewerVersionAvailable` | 21 |
| `Overdraw` | 41 |
| `PluralsCandidate` | 5 |
| `RtlHardcoded` | 7 |
| `RtlSymmetry` | 2 |
| `SetTextI18n` | 8 |
| `TextFields` | 1 |
| `UnusedAttribute` | 1 |
| `UnusedResources` | 593 |
| `UseCompoundDrawables` | 1 |
| `UseKtx` | 132 |
| `UselessParent` | 1 |
| `VectorPath` | 1 |
| **合计** | **869** |

`ReportShortcutUsage` 1 项和 `TrimLambda` 17 项的 severity 为 hint，不计入 warning 三态。

与 `docs/maintenance-baseline.md` 逐 ID 对账后：

- 参考 881 个 warning = 12 个 `FIXED` + 869 个 `PENDING_REVIEW`；
- 当前 XML 的 869 个 warning 与账本当前数量及 `PENDING_REVIEW` 总数完全一致；
- `AppBundleLocaleChanges`、`DefaultLocale`、`IntentWithNullActionLaunch` 当前均为 0，并已按 1、8、1 记入 `FIXED`；
- `UnusedResources` 和 `UseKtx` 分别已有 1 个安全修复，其余 593 和 132 个 occurrence 仍保持 `PENDING_REVIEW`；
- 当前没有 `SUPPRESSED_WITH_REASON` 或 `DEFERRED`，也没有未登记的 lint ID。

因此 warning 阶段可以从该清单开始逐批审查；在 869 个 `PENDING_REVIEW` 全部进入最终三态前，总变更仍被阻断完成。
