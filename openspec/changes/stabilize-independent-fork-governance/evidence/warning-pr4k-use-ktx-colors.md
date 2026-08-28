# PR 4k：`UseKtx` 字符串颜色小批次证据

## 串行前置条件

前一批 GitHub PR #63 的最终 head 为
`52cbb7509e5b3f6933b86999ce242e769d999d05`，merge commit 为
`afff26a7ad262b1257728c063e9efa2038d8e5af`。PR head 和合并后 `master`
的远端检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，123 个
单元测试 0 失败、1 跳过，Android lint 为 0 error、748 warning、18 hint，
Debug 构建、OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## 可安全转换范围

PR #63 合并后的 lint XML 包含 16 个 `String.toColorInt` occurrence，映射到
6 个文件中的 15 个实际 `Color.parseColor(String)` 调用；`RotateLoading` 的同一
调用被 lint 重复登记一次。KTX `String.toColorInt()` 委托同一
`Color.parseColor(String)`，因此本批保留全部颜色字符串、返回值、异常类型、
捕获边界和下游赋值不变，只替换调用形式与必要 import。

精确范围为 `ArcView` 1 个、`ReadBookConfig` 4 个、`ReadMenu` 2 个、
`RotateLoading` 1 个实际调用、`ThemeConfig` 4 个和 `ThemeStore` 3 个。
`ThemeConfig` 仍使用 `Color.WHITE` 与 `Color.BLACK`，因此保留其 `Color` import；
没有修改颜色资源、主题偏好、阅读配置格式或公开接口。

## 聚焦 RED/GREEN

扩展 `UseKtxContractTest`，要求 6 个目标文件不再包含 `Color.parseColor(`，并精确
锁定转换后 22 个 `.toColorInt()` 调用，其中 7 个为既有调用、15 个为本批转换。
旧生产代码运行该测试类时，既有 7 个测试通过，新增颜色测试在
`UseKtxContractTest.kt:111` 按预期失败；57 个 Gradle 任务实际执行。

完成转换后使用同一 JDK 17 命令重跑，8/8 通过，57 个 Gradle 任务实际执行。

## lint 对账

使用 JDK 17 与固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务实际执行并成功。lint XML 为 0 error、732 warning、18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | PENDING |
|---|---:|---:|---:|---:|
| `UseKtx` | 58 | 42 | 16 | 42 |
| **warning 合计** | **748** | **732** | **16** | **689** |

累计状态为 `FIXED=144`、`SUPPRESSED_WITH_REASON=5`、`DEFERRED=43`、
`PENDING_REVIEW=689`。剩余 42 个 `UseKtx` 精确分为：

- `Bitmap.toDrawable`：10；
- `Int.toDrawable`：10；
- `createBitmap`：9；
- `Canvas.withSave`：6；
- `SharedPreferences.edit`：3；
- `Canvas.withClip`：2；
- `Canvas.withTranslation`：2。

这些类别继续保持 `PENDING_REVIEW`；任务 5.4、5.8 暂不完成。

## 全量验证

使用 JDK 17.0.17 与固定 Android SDK 执行：

```bash
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug \
  :app:assembleAppDebug --rerun-tasks --console=plain
```

131 个 Gradle 任务全部实际执行并成功。测试 XML 汇总为 28 个测试套件、124 个
测试、0 失败、1 跳过；lint XML 为 0 error、732 warning、18 hint，其中
`UseKtx` 42。Debug APK 构建成功。三份 lint 报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `6c58752d04020847221f4beae8652237d14eeae6a8fc42b2a4fed27a72f5a69d` |
| `lint-results-appDebug.html` | `1f800c34c0728dc5ff2958f3d966147aad026ca93909c458003f5b62bfc7ceb2` |
| `lint-results-appDebug.txt` | `b5c6bc01f2e5a60921e2a443ab6f323613e4e0711e1c01679a8012135068db90` |

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check`
无输出。当前结果只证明本地提交候选通过；仍须创建 draft PR，并完成该 PR 最终
head 与合并后 `master` 的远端和本地验证，才可开始下一 warning 批次。当前没有
连接设备，本批未运行设备测试，也不将其描述为通过。
