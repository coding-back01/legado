# PR 4i：`UseKtx` 字符串 URI 小批次证据

## 串行前置条件

前一批 GitHub PR #61 的最终 head 为
`d3d26e49038e7c9895f3b7f2c3c54ef2a2723842`，merge commit 为
`8c1dd1d2915cd5644693b2b3befed52a8ce18930`。PR head 和合并后 `master`
的远端检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，121 个
单元测试 0 失败、1 跳过，Android lint 为 0 error、809 warning、18 hint，
Debug 构建、OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## 可安全转换范围

PR #61 合并后的 lint XML 包含 41 个 `String.toUri` occurrence，映射到 25 个
文件中的 37 个实际 `Uri.parse(String)` 调用。`ContextExtensions`、
`SystemUtils`、`TextActionMenu` 与 `WebViewLoginFragment` 的同一调用分别被 lint
重复登记一次。KTX `String.toUri()` 委托同一 `Uri.parse(String)`，本批保留每个
输入字符串、scheme、path、异常和下游调用不变，只替换调用形式与 import。

覆盖范围包括外部 Intent、content URI、本地书籍、WebDAV、下载、WebView、字体、
RSS、压缩文件和 `FileDoc` 路径。唯一的局部源码整理是将
`AppConfig.defaultBookTreeUri` 先读取为非空局部变量再调用 `toUri()`，避免对可空
属性重复读取；空值仍在同一位置直接返回，不改变导入 URI 或持久化格式。

## 聚焦 RED/GREEN

扩展 `UseKtxContractTest`，要求 25 个目标文件不再包含 `Uri.parse(`，并锁定
38 个显式接收者 `.toUri()` 和 `StringExtensions.parseToUri` 的 1 个隐式接收者
调用。旧生产代码运行该测试类时，既有 5 个测试通过，新增 URI 测试按预期失败；
完成转换后使用同一 JDK 17 命令重跑，6/6 通过，57 个 Gradle 任务实际执行。

## lint 对账

使用 JDK 17 与固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务实际执行并成功。lint XML 为 0 error、768 warning、18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | PENDING |
|---|---:|---:|---:|---:|
| `UseKtx` | 119 | 78 | 41 | 78 |
| **warning 合计** | **809** | **768** | **41** | **725** |

累计状态为 `FIXED=108`、`SUPPRESSED_WITH_REASON=5`、`DEFERRED=43`、
`PENDING_REVIEW=725`。剩余 78 个 `UseKtx` 已按样式属性、颜色、Drawable、
Bitmap、Canvas 和 SharedPreferences 分组，继续保持 `PENDING_REVIEW`；任务
5.4、5.8 暂不完成。

## 全量验证

首次组合执行单元测试、lint 与 Debug 构建时，生产代码和新增 URI 契约已经通过，
但既有 `HighRiskWarningContractTest` 仍把 QQ 群入口写死为 `Uri.parse`，因此该次
运行按失败处理。随后仅更新这条源码契约，使其继续锁定显式 `ACTION_VIEW`、原
`mqqopensdkapi` URI 和 `toUri()` 调用；`HighRiskWarningContractTest` 与
`UseKtxContractTest` 聚焦重跑均成功。

修正契约后，使用 JDK 17.0.17 与固定 Android SDK 执行：

```bash
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug \
  :app:assembleAppDebug --rerun-tasks --console=plain
```

131 个 Gradle 任务全部实际执行并成功。测试 XML 汇总为 28 个测试套件、122 个
测试、0 失败、1 跳过；lint XML 为 0 error、768 warning、18 hint，其中
`UseKtx` 78。Debug APK 构建成功。三份 lint 报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `a2382624beb067360f6b4d941f3db482059091584600871f63ecae808b2f3a78` |
| `lint-results-appDebug.html` | `bfcc4fb69ae7e251e1e25512933cb2df7640fcfd28d0d6d38aa89dfe3a07be5d` |
| `lint-results-appDebug.txt` | `f0496215b8135b1c46b64abb37cb4f94b5aea1d81af9cbe2620fcbab1c2c745d` |

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check`
无输出。当前结果只证明本地提交候选通过；仍须完成 draft PR 最终 head 与合并后
`master` 的远端和本地验证，才可开始下一 warning 批次。当前没有连接设备，本批
未运行设备测试，也不将其描述为通过。
