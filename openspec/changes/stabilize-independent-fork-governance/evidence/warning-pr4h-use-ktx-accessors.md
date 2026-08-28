# PR 4h（GitHub #61）：`UseKtx` 只读访问器小批次证据

## 串行前置条件

前一批 GitHub PR #60 的最终 head 为
`3044e2c2000e0cec597f6abc56b3651f1b363ee7`，merge commit 为
`7ec45fbb08b497cf6103f7b774387198a99508ff`。PR head 和合并后 `master`
的远端检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，117 个
单元测试 0 失败、1 跳过，Android lint 为 0 error、816 warning、18 hint，
Debug 构建、OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## 可安全转换范围

本批只处理以下 7 个只读 getter/访问器 occurrence：

| KTX 访问器 | 数量 | 精确范围 | 等价边界 |
|---|---:|---|---|
| `Bitmap.get` | 2 | `BitmapUtils.getMeanColor`、`ExplosionAnimator` 粒子颜色采样 | 下标操作符委托同一 `Bitmap.getPixel(x, y)`，坐标和异常行为不变 |
| `Locale.layoutDirection` | 1 | `DragSelectTouchHelper.isRtl` | 属性委托同一 locale 布局方向解析，仍与 `LAYOUT_DIRECTION_RTL` 比较 |
| `ViewGroup.isNotEmpty` | 1 | `VerticalSeekBarWrapper.childSeekBar` | 仍只在 `childCount > 0` 时读取第 0 个子项 |
| `View.isVisible` | 3 | `FastScroller`、`RotateLoading`、`ViewExtensions.visible(Boolean)` | 只替换 `visibility ==/!= VISIBLE` getter；不使用会引入 `GONE` 的 KTX setter |

资源、Canvas、URI、SharedPreferences 等其余 119 项仍保持 `PENDING_REVIEW`。
本批不改变像素坐标、RTL 选择、子视图索引、动画启动条件，也不改变
`ViewExtensions.visible(false)` 使用 `INVISIBLE` 而非 `GONE` 的既有语义。

## 聚焦 RED/GREEN

扩展 `UseKtxContractTest`，分别锁定四类源码契约。在旧生产代码上使用 JDK 17
强制执行该测试类，既有 `SparseArray` 测试通过，新增 4 个测试全部按预期失败：

```bash
./gradlew :app:testAppDebugUnitTest \
  --tests io.legado.app.quality.UseKtxContractTest \
  --rerun-tasks --console=plain
```

完成 7 个访问器转换并补齐对应公开 KTX import 后使用同一命令重跑，5/5
测试全部通过，57 个 Gradle 任务实际执行。

## lint 对账

使用 JDK 17 与固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务实际执行并成功。lint XML 为 0 error、809 warning、18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | PENDING |
|---|---:|---:|---:|---:|
| `UseKtx` | 126 | 119 | 7 | 119 |
| **warning 合计** | **816** | **809** | **7** | **766** |

累计状态为 `FIXED=67`、`SUPPRESSED_WITH_REASON=5`、`DEFERRED=43`、
`PENDING_REVIEW=766`。本批没有新增 suppression 或延期项；任务 5.4、5.8
继续保持未完成。

## 本批完整本地验证

2026-08-28 使用 JDK 17.0.17 和本机 Android SDK 强制执行单元测试、Android
lint 与 Debug 构建，131 个 Gradle 任务全部实际执行并成功。XML 汇总为 121 个
单元测试、0 失败、0 error、1 跳过；Android lint 为 0 error、809 warning、
18 hint。`openspec validate --all --strict` 为 3 项通过、0 项失败，
`git diff --check` 成功。

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `ba3e4a7dc0891445f58555bf47e53241d239bd6dba0168512bb782201ed587cb` |
| `lint-results-appDebug.html` | `f392c3b41374c83289b200fcc193e16b7bf1d7b1f957856bbea068a95d42ce19` |
| `lint-results-appDebug.txt` | `069b1b6407a838951423ec889f44a2f3f7ff480a5419ff88064f9048b3525f45` |

上述结果只证明本地提交候选通过；draft PR #61 仍须完成最终 head 与合并后
`master` 的远端和本地验证，才可开始下一 warning 批次。
