# PR 4j：`UseKtx` 样式属性小批次证据

## 串行前置条件

前一批 GitHub PR #62 的最终 head 为
`b27b78577be946bca10cfa67b046acd94dc6ff67`，merge commit 为
`080ddf962b079809c0ab82ba88cfa0137936480c`。PR head 和合并后 `master`
的远端检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，122 个
单元测试 0 失败、1 跳过，Android lint 为 0 error、768 warning、18 hint，
Debug 构建、OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## 可安全转换范围

PR #62 合并后的 lint XML 包含 20 个
`Context.withStyledAttributes` occurrence，精确映射到 20 个自定义 View 或
Preference 文件。KTX 扩展使用同一组 `AttributeSet`、styleable、
`defStyleAttr` 和 `defStyleRes` 参数取得 `TypedArray`，在 receiver 块内执行原有
getter 后自动回收。本批保留全部属性索引、默认值、赋值顺序和后续 UI 初始化，
未转换 lint 没有指向的其他样式读取。

构造期不可变字段不能直接在 receiver lambda 内完成确定赋值，因此先以原默认值
读取到局部变量，再在 lambda 返回后一次赋给原 `val`；没有把字段放宽为可变属性。
`TitleBar` 保留原父 View 作为 inflate 目标，`DividerNoLast` 使用命名参数选择原
`IntArray` 重载。没有改变资源、主题属性或 View 的公开接口。

## 聚焦 RED/GREEN

扩展 `UseKtxContractTest`，要求 20 个目标文件不再包含
`context.obtainStyledAttributes(`，并精确锁定 20 个
`context.withStyledAttributes(` 调用。旧生产代码运行该测试类时，既有 6 个
测试通过，新增样式属性测试按预期失败；57 个 Gradle 任务实际执行。

第一次实现重跑时，编译器如实阻止了 receiver lambda 内对构造期 `val` 的赋值，
同时指出 `DividerNoLast` 需要显式选择 `IntArray` 重载。改为局部解析值并保留字段
不可变性后，同一测试类 7/7 通过，57 个 Gradle 任务实际执行。

## lint 对账

使用 JDK 17 与固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务实际执行并成功。lint XML 为 0 error、748 warning、18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | PENDING |
|---|---:|---:|---:|---:|
| `UseKtx` | 78 | 58 | 20 | 58 |
| **warning 合计** | **768** | **748** | **20** | **705** |

累计状态为 `FIXED=128`、`SUPPRESSED_WITH_REASON=5`、`DEFERRED=43`、
`PENDING_REVIEW=705`。剩余 58 个 `UseKtx` 精确分为：

- `String.toColorInt`：16；
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

131 个 Gradle 任务全部实际执行并成功。测试 XML 汇总为 28 个测试套件、123 个
测试、0 失败、1 跳过；lint XML 为 0 error、748 warning、18 hint，其中
`UseKtx` 58。Debug APK 构建成功。三份 lint 报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `dac6b7ff5778a3764027fb7a0f00176b298a7e59d5d163d3d54d1c8a11eea4e2` |
| `lint-results-appDebug.html` | `0b738fa3def6b635d62928c603a57137e6733bf2ce592f5d084fc24a367fd4da` |
| `lint-results-appDebug.txt` | `d4d49ddded60739b960846582e9702d0315b4ebc7de588414c9fccd210add95f` |

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check`
无输出。draft PR #63 已创建；当前结果只证明本地提交候选通过，仍须完成该 PR
最终 head 与合并后 `master` 的远端和本地验证，才可开始下一 warning 批次。当前
没有连接设备，本批未运行设备测试，也不将其描述为通过。

## Pull Request 与合并后闭环

PR #63 的最终 head 为 `52cbb7509e5b3f6933b86999ce242e769d999d05`，merge
commit 为 `afff26a7ad262b1257728c063e9efa2038d8e5af`。最终 head 的
`Android Debug 验证` run `33140238770` 成功；合并后同一 merge commit 的
`Test Build` run `33142092765` 也成功。短期 head 分支按治理序列要求保留。

2026-08-28 从干净的合并后 `master` 使用 JDK 17.0.17 与固定 Android SDK
再次强制执行 131 个 Gradle 任务，123 个单元测试 0 失败、1 跳过，Android lint
为 0 error、748 warning、18 hint，Debug 构建、OpenSpec 严格校验和空白检查
均成功。合并后报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `dac6b7ff5778a3764027fb7a0f00176b298a7e59d5d163d3d54d1c8a11eea4e2` |
| `lint-results-appDebug.html` | `7972421d4ed45a0ddecf6de8239376211ee03d3377a86cd97800a3c031e545e8` |
| `lint-results-appDebug.txt` | `d4d49ddded60739b960846582e9702d0315b4ebc7de588414c9fccd210add95f` |

至此 PR 4j 完成全部串行门禁，可以从该 merge commit 开始下一小批次。
