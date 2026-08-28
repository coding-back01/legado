# PR 4n：`UseKtx` Bitmap 构造小批次证据

## 串行前置条件

前一批 GitHub PR #66 的最终 head 为
`be934c6a876d6fff53b0ea3b2a98ccef1d2b0982`，merge commit 为
`398fcae5e981a1c381fca782d868224ca9a18d48`。PR head 和合并后 `master`
的远端检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，126 个
单元测试 0 失败、1 跳过，Android lint 为 0 error、712 warning、18 hint，
Debug 构建、OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## 可安全转换范围

PR #66 合并后的 lint XML 包含 9 个三参数 `Bitmap.createBitmap(width, height,
config)` occurrence，精确分布为：

- `ACache` 1 个；
- `CircleImageView` 2 个；
- `QRCodeUtils` 4 个；
- `explosion_field/Utils` 1 个；
- `ViewExtensions` 1 个。

AndroidX Core 的顶层 `createBitmap(width, height, config)` 委托同一平台构造，
因此本批只替换调用形式与必要 import；宽高、`Bitmap.Config`、可空声明、
`OutOfMemoryError` 重试、Canvas 绘制顺序、像素写入、回收与返回控制流均保持不变。
没有修改资源、二维码参数、图片缓存格式、阅读数据或公开接口。

## 聚焦 RED/GREEN

扩展 `UseKtxContractTest`，要求 5 个目标文件不再包含
`Bitmap.createBitmap(`，并精确锁定 9 个顶层 `createBitmap(` 调用。旧生产代码运行
该测试类时，既有 10 个测试通过，新增 Bitmap 构造测试在
`UseKtxContractTest.kt:167` 按预期失败；57 个 Gradle 任务实际执行。

完成转换后使用同一 JDK 17 命令重跑，11/11 通过，57 个 Gradle 任务实际执行。

## lint 对账

使用 JDK 17 与固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务实际执行并成功。lint XML 为 0 error、703 warning、18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | PENDING |
|---|---:|---:|---:|---:|
| `UseKtx` | 22 | 13 | 9 | 13 |
| **warning 合计** | **712** | **703** | **9** | **660** |

累计状态为 `FIXED=166`、`SUPPRESSED_WITH_REASON=12`、`DEFERRED=43`、
`PENDING_REVIEW=660`。剩余 13 个 `UseKtx` 精确分为：

- `Canvas.withSave`：6；
- `SharedPreferences.edit`：3；
- `Canvas.withClip`：2；
- `Canvas.withTranslation`：2。

这些类别继续保持 `PENDING_REVIEW`；任务 5.4、5.8 暂不完成。

## 全量验证

使用 JDK 17.0.17 与固定 Android SDK 执行：

```bash
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug \
  :app:assembleAppDebug --rerun-tasks --no-daemon --warning-mode all --console=plain
```

131 个 Gradle 任务全部实际执行并成功。测试 XML 汇总为 28 个测试套件、127 个
测试、0 失败、1 跳过；lint XML 为 0 error、703 warning、18 hint，其中
`UseKtx` 13，三参数 `Bitmap.createBitmap` 0。Debug APK 构建成功。三份 lint
报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `48661525fdd2969260fab838b1161cfb1703b65dbfd8ed69334e9c1afa4c4b5d` |
| `lint-results-appDebug.html` | `e75490bd751b2db6adaeb845ad15e32a4ec7cb74dc182d3474b9b356f0acd1e0` |
| `lint-results-appDebug.txt` | `3add59ffc9e3cf8d2a47245817c5624401aed39e0091b78ecaa67ad7a93e9b6c` |

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check`
无输出。draft PR #67 已创建；当前结果只证明本地候选通过，仍须完成该 PR 最终 head
与合并后 `master` 的远端和本地验证，才可开始下一 warning 批次。实时
`adb devices -l` 为空，本批未运行设备测试，也不将其描述为通过。

## Pull Request 与合并后闭环

PR #67 的最终 head 为 `873f43aadefb60fb3691e0da0d1d1728a8484cf7`，merge
commit 为 `595174680f4206b6b64996d45e003d18adee6104`。最终 head 的
`Android Debug 验证` run `33165160288` 成功；合并后同一 merge commit 的
`Test Build` run `33165534050` 也成功。短期 head 分支按治理序列要求保留。

2026-08-28 从干净的合并后 `master` 使用 JDK 17.0.17 与固定 Android SDK
再次强制执行 131 个 Gradle 任务，127 个单元测试 0 失败、1 跳过，Android lint
为 0 error、703 warning、18 hint，Debug 构建、OpenSpec 严格校验和空白检查
均成功。合并后报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `48661525fdd2969260fab838b1161cfb1703b65dbfd8ed69334e9c1afa4c4b5d` |
| `lint-results-appDebug.html` | `cbba49a92b553db0229dd30de534ee879ed2aaa78267296cdaf023fb23de0c51` |
| `lint-results-appDebug.txt` | `3add59ffc9e3cf8d2a47245817c5624401aed39e0091b78ecaa67ad7a93e9b6c` |

至此 PR 4n 完成全部串行门禁，可以从该 merge commit 开始下一小批次。
