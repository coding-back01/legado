# PR 4g（GitHub #60）：`UseKtx` 的 `SparseArray.size` 小批次证据

## 串行前置条件

前一批 GitHub PR #59 的最终 head 为
`d8b33d4770b0066ee8d051a8f21a6a1a763388d2`，merge commit 为
`d21786470f06855b86f5767f3c625d05f39972d5`。PR 与合并后 `master` 的远端
检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，116 个单元测试
0 失败、1 跳过，Android lint 为 0 error、822 warning、18 hint，Debug 构建、
OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## `UseKtx` 分类与本批边界

从 PR #59 合并后的 lint XML 读取到 132 个 `UseKtx` occurrence，按建议类型分为：

| 建议 | 数量 |
|---|---:|
| `String.toUri` | 41 |
| `Context.withStyledAttributes` | 20 |
| `String.toColorInt` | 16 |
| `Bitmap.toDrawable` | 10 |
| `Int.toDrawable` | 10 |
| `createBitmap` | 9 |
| `Canvas.withSave` | 6 |
| `SparseArray.size` | 6 |
| `SharedPreferences.edit` | 3 |
| `View.isVisible` | 3 |
| `Bitmap.get` | 2 |
| `Canvas.withClip` | 2 |
| `Canvas.withTranslation` | 2 |
| `ViewGroup.isNotEmpty` | 1 |
| `Locale.layoutDirection` | 1 |
| **合计** | **132** |

本批只处理 `RecyclerAdapter.kt` 中 6 处 `SparseArray.size()`。导入公开的
`androidx.core.util.size` 后，属性读取仍委托同一个 `SparseArray.size()` API，
不会改变 header/footer 的索引、计数、遍历顺序、空值或通知时机。URI、资源、
Drawable、Canvas、SharedPreferences 等其余 126 项涉及解析、资源密度、绘制状态、
事务或可见性语义，继续保持 `PENDING_REVIEW`，不混入本批。

## 聚焦 RED/GREEN

新增 `UseKtxContractTest`，要求两个 `SparseArray` 接收者不再使用 Java 风格
`size()`，并分别保留 3 次数量访问。旧生产代码运行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
./gradlew :app:testAppDebugUnitTest \
  --tests io.legado.app.quality.UseKtxContractTest \
  --rerun-tasks --console=plain
```

结果为 1 个测试、1 个预期失败，失败位置是禁止旧 `headerItems.size()` 的断言。
完成 6 处属性转换后的首次 GREEN 尝试在 Kotlin 编译期揭示缺少
`androidx.core.util.size` import；补齐该公开扩展 import 后使用同一命令重跑，
1/1 通过，57 个 Gradle 任务实际执行。该中间失败未被描述为通过。

## lint 对账

使用 JDK 17 和固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务实际执行并成功。lint XML 结果为 0 error、816 warning、
18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | PENDING |
|---|---:|---:|---:|---:|
| `UseKtx` | 132 | 126 | 6 | 126 |
| **warning 合计** | **822** | **816** | **6** | **773** |

累计状态为 `FIXED=60`、`SUPPRESSED_WITH_REASON=5`、`DEFERRED=43`、
`PENDING_REVIEW=773`。本批没有新增 suppression 或延期项；任务 5.4、5.8
继续保持未完成，直到后续机械建议批次完成审查并分别通过完整 PR 闭环。

## 本批完整本地验证

2026-08-28 使用 JDK 17.0.17 和本机 Android SDK 强制执行：

```bash
./gradlew :app:testAppDebugUnitTest \
  :app:lintAppDebug \
  :app:assembleAppDebug \
  --rerun-tasks --console=plain
```

131 个 Gradle 任务全部实际执行并成功。XML 汇总为 117 个单元测试、0 失败、
0 error、1 跳过；Android lint 为 0 error、816 warning、18 hint，Debug APK
成功生成。`openspec validate --all --strict` 为 3 项通过、0 项失败，
`git diff --check` 成功。

本次报告哈希为：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `41652e608324a993c9c6db459bd701f91d88038cc653eca42e285666824d90e6` |
| `lint-results-appDebug.html` | `a4858c4958ee4c16372a2d6e357c7942d87b5ec9e7639df9b565c5a90ee89487` |
| `lint-results-appDebug.txt` | `d95ae9316b671fcd28b840babf68af87fc3c2cdc722d45947005b5d5c1f31f25` |

## Pull Request 与合并后闭环

PR #60 的最终 head 为 `3044e2c2000e0cec597f6abc56b3651f1b363ee7`，merge
commit 为 `7ec45fbb08b497cf6103f7b774387198a99508ff`。最终 head 的
`Android Debug 验证` run `33100762081` 成功；合并后同一 merge commit 的
`Test Build` run `33101201820` 也成功。

2026-08-28 从干净的合并后 `master` 使用 JDK 17.0.17 与固定 Android SDK
再次强制执行 131 个 Gradle 任务，117 个单元测试 0 失败、1 跳过，Android lint
为 0 error、816 warning、18 hint，Debug 构建、OpenSpec 严格校验和空白检查
均成功。合并后报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `41652e608324a993c9c6db459bd701f91d88038cc653eca42e285666824d90e6` |
| `lint-results-appDebug.html` | `284bb354d5cc716acdd11d082a6c262ba0fc97c1f03885282c34f25e1acdf315` |
| `lint-results-appDebug.txt` | `d95ae9316b671fcd28b840babf68af87fc3c2cdc722d45947005b5d5c1f31f25` |

至此 PR 4g 完成全部串行门禁，可以从该 merge commit 开始下一小批次。
