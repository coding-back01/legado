# PR 4p：`UseKtx` Canvas 状态小批次证据

## 串行前置条件

前一批 GitHub PR #68 的最终 head 为
`25d04d1b53e0dd1ce4f4963d1dca382b2644b985`，merge commit 为
`15956395a4389efc108569877bb4465ed26ad797`。PR head 和合并后 `master`
的远端检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，128 个
单元测试 0 失败、1 跳过，Android lint 为 0 error、700 warning、18 hint，
Debug 构建、OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## 可安全转换范围

PR #68 合并后的 lint XML 包含最后 10 个 `UseKtx` occurrence：

- `DividerNoLast` 的水平、垂直分隔线各 1 个 `Canvas.withSave`；
- `SimulationPageDelegate` 2 个 `Canvas.withClip` 与 2 个 `Canvas.withSave`；
- `SearchView` 和 `ViewExtensions.screenshot` 各 1 个 `Canvas.withTranslation`；
- `QRCodeUtils` 2 对相邻且中间没有操作的 `save()`/`restore()`，直接删除为空操作。

KTX 作用域仍在进入时保存 Canvas 状态、按原顺序执行裁剪/平移/旋转与绘制，并在
退出时恢复同一状态。`SimulationPageDelegate` 另有 1 对涉及 `clipOutPath` 的原始
`save()`/`restore()` 没有被本轮 lint 建议命中，精确保留且由契约锁定为 1 对。
没有修改页面几何计算、Drawable bounds、二维码像素、截图尺寸、阅读进度或公开接口。

## 聚焦 RED/GREEN

扩展 `UseKtxContractTest`，锁定各文件的 KTX 类型与数量、QRCode 空操作移除，以及
Simulation 仅保留 1 对非目标原始状态调用。旧生产代码运行该测试类时，新增测试在
`UseKtxContractTest.kt:207` 按预期失败；13 个测试中 1 个失败，57 个 Gradle
任务实际执行。

完成转换后使用同一 JDK 17 命令重跑，13/13 通过，57 个 Gradle 任务实际执行。

## lint 对账

使用 JDK 17 与固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务实际执行并成功。lint XML 为 0 error、690 warning、18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | SUPPRESSED | DEFERRED | PENDING |
|---|---:|---:|---:|---:|---:|---:|
| `UseKtx` | 10 | 0 | 10 | 0 | 0 | 0 |
| **warning 合计** | **700** | **690** | **10** | **12** | **43** | **647** |

累计状态为 `FIXED=179`、`SUPPRESSED_WITH_REASON=12`、`DEFERRED=43`、
`PENDING_REVIEW=647`。`UseKtx` 自参考 133 项以来累计 `FIXED=126`、
`SUPPRESSED_WITH_REASON=7`、`DEFERRED=0`、`PENDING_REVIEW=0`，已完成该 ID
的全部对账。任务 5.4 仍须等待本 PR 合并及 `master` 双重复验后完成；任务 5.8
继续保持未完成。

## 全量验证

使用 JDK 17.0.17 与固定 Android SDK 执行：

```bash
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug \
  :app:assembleAppDebug --rerun-tasks --no-daemon --warning-mode all --console=plain
```

131 个 Gradle 任务全部实际执行并成功。测试 XML 汇总为 28 个测试套件、129 个
测试、0 失败、1 跳过；lint XML 为 0 error、690 warning、18 hint，其中
`UseKtx` 0。Debug APK 构建成功。三份 lint 报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `87fcacb662c0d8f5d1d38dd7fa3403d0aafe9da7c8422ee74e1ae00b0856bdde` |
| `lint-results-appDebug.html` | `d7f3490fe837dc3fc3abbcfd02006ed976e7f4338b1ac642900e1c355607b59f` |
| `lint-results-appDebug.txt` | `eacd290bc2351b830d79236a704609c3a5218499af62c513bf2e965e16b06c6f` |

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check`
无输出。draft PR 尚未创建；当前结果只证明本地候选通过，仍须完成该 PR 最终 head
与合并后 `master` 的远端和本地验证，才可完成任务 5.4。实时
`adb devices -l` 为空，本批未运行设备测试，也不将其描述为通过。
