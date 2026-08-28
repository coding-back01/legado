# PR 4m：`UseKtx` 颜色 Drawable 小批次证据

## 串行前置条件

前一批 GitHub PR #65 的最终 head 为
`86a018870122db41d90efae2a0ebde813c120114`，merge commit 为
`35797d31febb7de021f36667cebe0f3de9b6234a`。PR head 和合并后 `master`
的远端检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，125 个
单元测试 0 失败、1 跳过，Android lint 为 0 error、722 warning、18 hint，
Debug 构建、OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## 可安全转换范围

PR #65 合并后的 lint XML 包含 10 个 `Int.toDrawable` occurrence，映射到 5 个
文件中的 9 个实际 `ColorDrawable(Int)` 构造；`ThemeBottomNavigationVIew` 的同一
构造被 lint 重复登记一次。KTX `Int.toDrawable()` 直接使用 receiver 构造
`ColorDrawable`，因此本批保留全部颜色整数、透明色、TransitionDrawable 前后顺序、
空背景兜底和下游赋值不变，只替换调用形式与必要 import。

精确范围为 `DrawableUtils` 2 个、`ReadBookConfig` 3 个、`Selector` 1 个、
`ThemeBottomNavigationVIew` 1 个实际构造和 `ViewUtils` 2 个。`ReadBookConfig`
仍需使用 `ColorDrawable` 进行类型判断，因此保留该 import；没有修改资源、主题偏好、
阅读配置格式或公开接口。

## 聚焦 RED/GREEN

扩展 `UseKtxContractTest`，要求 5 个目标文件不再包含 `ColorDrawable(`，并精确
锁定 9 个 `.toDrawable()` 调用。旧生产代码运行该测试类时，既有 9 个测试通过，
新增颜色 Drawable 测试在 `UseKtxContractTest.kt:152` 按预期失败；57 个 Gradle
任务实际执行。

完成转换后使用同一 JDK 17 命令重跑，10/10 通过，57 个 Gradle 任务实际执行。

## lint 对账

使用 JDK 17 与固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务实际执行并成功。lint XML 为 0 error、712 warning、18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | PENDING |
|---|---:|---:|---:|---:|
| `UseKtx` | 32 | 22 | 10 | 22 |
| **warning 合计** | **722** | **712** | **10** | **669** |

累计状态为 `FIXED=157`、`SUPPRESSED_WITH_REASON=12`、`DEFERRED=43`、
`PENDING_REVIEW=669`。剩余 22 个 `UseKtx` 精确分为：

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

131 个 Gradle 任务全部实际执行并成功。测试 XML 汇总为 28 个测试套件、126 个
测试、0 失败、1 跳过；lint XML 为 0 error、712 warning、18 hint，其中
`UseKtx` 22，`Int.toDrawable` 0。Debug APK 构建成功。三份 lint 报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `a25dc68f01909b8cd160a09a14a2037c45f473eef75eb5ef0e5b90e7df18b11e` |
| `lint-results-appDebug.html` | `61c76cf081082a1fadbeea5dac1ef14f00a3e6f76e53c8a1c1538fed89675be3` |
| `lint-results-appDebug.txt` | `a484512e7b240d1166ffcb0e9d4a64af311183f6cbd28ada49e807a12aebb4fd` |

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check`
无输出。draft PR #66 已创建；当前结果只证明本地提交候选通过，仍须完成该 PR
最终 head 与合并后 `master` 的远端和本地验证，才可开始下一 warning 批次。当前
没有连接设备，本批未运行设备测试，也不将其描述为通过。
