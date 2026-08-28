# PR 4l：`UseKtx` Bitmap Drawable 小批次证据

## 串行前置条件

前一批 GitHub PR #64 的最终 head 为
`547fc56ae4b56554201b8618f39810ccce89e4cb`，merge commit 为
`eb9782f8386a94d459a971ee2f26f77bf2dfd70b`。PR head 和合并后 `master`
的远端检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，124 个
单元测试 0 失败、1 跳过，Android lint 为 0 error、732 warning、18 hint，
Debug 构建、OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## 可安全转换与兼容例外范围

PR #64 合并后的 lint XML 包含 10 个 `Bitmap.toDrawable` occurrence，映射到
5 个文件中的 7 个实际 `BitmapDrawable(Resources, Bitmap?)` 构造；
`BaseActivity` 的非空构造被重复登记一次，`WelcomeActivity` 的两个可空构造各被
重复登记一次。

`ACache.bitmap2Drawable` 已在空值分支返回后取得非空 `Bitmap`，
`BaseActivity.upBackgroundImage` 也位于非空 `let` 中；这 2 个实际构造可直接改为
委托同一构造器的 `Bitmap.toDrawable(Resources)`，对应 3 个 `FIXED` occurrence。

其余 5 个实际构造接收可空解码结果。原实现即使取得 null 仍会创建空
`BitmapDrawable`；其中欢迎页还会设置该背景并提前返回。KTX 只接受非空 receiver，
使用安全调用会改变兜底 Drawable 或欢迎页回退控制流。因此在这 5 个精确构造前
使用 `//noinspection UseKtx`，对应 7 个 `SUPPRESSED_WITH_REASON` occurrence；没有
放宽同 ID 的其他位置。

## 聚焦 RED/GREEN

扩展 `UseKtxContractTest`，要求两个非空目标文件共有 2 个 `.toDrawable(resources)`
且不再包含 `BitmapDrawable(`，并锁定三个可空目标文件只保留 5 个构造及 5 个精确
`//noinspection UseKtx`。旧生产代码运行该测试类时，既有 8 个测试通过，新增
Bitmap Drawable 测试在 `UseKtxContractTest.kt:123` 按预期失败；57 个 Gradle
任务实际执行。

第一次实现尝试把可空路径也改为安全调用，并以 `BitmapDrawable(Resources, null)`
保持旧结果；编译器在 49 个任务后因 null 同时匹配 Bitmap、InputStream 与 String
构造器而失败。将 null 显式类型化后聚焦测试 9/9 通过，但 lint 只从 732 降到
729，7 个可空 occurrence 仍然存在，证明该形式没有完成清账。

最终实现恢复 5 个原可空构造并加精确行级抑制；同一聚焦测试 9/9 通过，57 个
Gradle 任务实际执行。随后 103 个 lint 任务全部执行并成功，`Bitmap.toDrawable`
剩余为 0。

## lint 对账

最终 lint XML 为 0 error、722 warning、18 hint：

| lint ID | 修改前 | 修改后 | FIXED | SUPPRESSED | PENDING |
|---|---:|---:|---:|---:|---:|
| `UseKtx` | 42 | 32 | 3 | 7 | 32 |
| **warning 合计** | **732** | **722** | **3** | **7** | **679** |

累计状态为 `FIXED=147`、`SUPPRESSED_WITH_REASON=12`、`DEFERRED=43`、
`PENDING_REVIEW=679`。剩余 32 个 `UseKtx` 精确分为：

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

131 个 Gradle 任务全部实际执行并成功。测试 XML 汇总为 28 个测试套件、125 个
测试、0 失败、1 跳过；lint XML 为 0 error、722 warning、18 hint，其中
`UseKtx` 32，`Bitmap.toDrawable` 0。Debug APK 构建成功。三份 lint 报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `a5a78bec5704d72d1f9d2613459fe760e8d18fa8673afee6e7eadc65cf414a1b` |
| `lint-results-appDebug.html` | `babbd30d83aa0b1c629fb0bb8e6a23a8c1abf0c198ea618d9dcd6ec9230bc424` |
| `lint-results-appDebug.txt` | `2e156bb5f857bbe8bbd89a1e12f465ed2b535a99ab3fc90aaa76cec95fc15087` |

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check`
无输出。draft PR #65 已创建；当前结果只证明本地提交候选通过，仍须完成该 PR
最终 head 与合并后 `master` 的远端和本地验证，才可开始下一 warning 批次。当前
没有连接设备，本批未运行设备测试，也不将其描述为通过。

## Pull Request 与合并后闭环

PR #65 的最终 head 为 `86a018870122db41d90efae2a0ebde813c120114`，merge
commit 为 `35797d31febb7de021f36667cebe0f3de9b6234a`。最终 head 的
`Android Debug 验证` run `33147921852` 成功；合并后同一 merge commit 的
`Test Build` run `33148208737` 也成功。短期 head 分支按治理序列要求保留。

2026-08-28 从干净的合并后 `master` 使用 JDK 17.0.17 与固定 Android SDK
再次强制执行 131 个 Gradle 任务，125 个单元测试 0 失败、1 跳过，Android lint
为 0 error、722 warning、18 hint，Debug 构建、OpenSpec 严格校验和空白检查
均成功。合并后报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `a5a78bec5704d72d1f9d2613459fe760e8d18fa8673afee6e7eadc65cf414a1b` |
| `lint-results-appDebug.html` | `553393fe1105ed373f01a3c041605d080b8127e7b3b16e5f883b72755928d223` |
| `lint-results-appDebug.txt` | `2e156bb5f857bbe8bbd89a1e12f465ed2b535a99ab3fc90aaa76cec95fc15087` |

至此 PR 4l 完成全部串行门禁，可以从该 merge commit 开始下一小批次。
