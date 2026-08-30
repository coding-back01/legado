# PR 4o：`UseKtx` SharedPreferences 编辑小批次证据

## 串行前置条件

前一批 GitHub PR #67 的最终 head 为
`873f43aadefb60fb3691e0da0d1d1728a8484cf7`，merge commit 为
`595174680f4206b6b64996d45e003d18adee6104`。PR head 和合并后 `master`
的远端检查均成功；合并后使用 JDK 17 强制执行 131 个 Gradle 任务，127 个
单元测试 0 失败、1 跳过，Android lint 为 0 error、703 warning、18 hint，
Debug 构建、OpenSpec 严格校验和空白检查均成功。满足串行条件后才创建本批分支。

## 可安全转换范围

PR #67 合并后的 lint XML 包含 3 个 `SharedPreferences.edit` occurrence：

- `Backup` 1 个：原实现最后同步调用 `commit()` 且忽略 Boolean 返回值，改为
  `edit(commit = true)`，仍在继续打包前同步提交完整偏好快照；
- `Restore` 1 个：原实现最后调用 `apply()`，改为默认 `edit` KTX，仍在动作完成后
  异步提交恢复值；
- `ThemeStore.isConfigured` 1 个：原实现调用 `apply()`，改为默认 `edit` KTX，
  仍异步保存配置版本后返回 false。

`ThemeStore` 用于链式主题配置的长生命周期 `mEditor` 不在 lint 目标中，本批没有
修改。偏好 key、值类型、WebDAV 密码加解密、备份格式、恢复过滤规则和主题判断
均保持不变。

## 聚焦 RED/GREEN

扩展 `UseKtxContractTest`，分别锁定备份的同步 `commit = true`、恢复与主题版本的
默认异步编辑，并禁止三个原始调用形式。旧生产代码运行该测试类时，新增测试在
`UseKtxContractTest.kt:183` 按预期失败；12 个测试中 1 个失败，57 个 Gradle
任务实际执行。

完成转换后使用同一 JDK 17 命令重跑，12/12 通过，57 个 Gradle 任务实际执行。

## lint 对账

使用 JDK 17 与固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务实际执行并成功。lint XML 为 0 error、700 warning、18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | PENDING |
|---|---:|---:|---:|---:|
| `UseKtx` | 13 | 10 | 3 | 10 |
| **warning 合计** | **703** | **700** | **3** | **657** |

累计状态为 `FIXED=169`、`SUPPRESSED_WITH_REASON=12`、`DEFERRED=43`、
`PENDING_REVIEW=657`。剩余 10 个 `UseKtx` 精确分为：

- `Canvas.withSave`：6；
- `Canvas.withClip`：2；
- `Canvas.withTranslation`：2。

这些 Canvas 项继续保持 `PENDING_REVIEW`；任务 5.4、5.8 暂不完成。

## 全量验证

使用 JDK 17.0.17 与固定 Android SDK 执行：

```bash
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug \
  :app:assembleAppDebug --rerun-tasks --no-daemon --warning-mode all --console=plain
```

131 个 Gradle 任务全部实际执行并成功。测试 XML 汇总为 28 个测试套件、128 个
测试、0 失败、1 跳过；lint XML 为 0 error、700 warning、18 hint，其中
`UseKtx` 10，`SharedPreferences.edit` 0。Debug APK 构建成功。三份 lint 报告
哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `1b096ef8b77ea1585338b1391f41f330602a57b95d9c26b1885e1b1562b62e1a` |
| `lint-results-appDebug.html` | `4eb90b6bbb59720a489cc902f69bff0f7358e410f6c898332ba515e96d893cec` |
| `lint-results-appDebug.txt` | `2dfc974fb1536c57c90cf7a8afc9c826bca3c6cb1070825e2c18b302abde56d0` |

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check`
无输出。draft PR #68 已创建；当前结果只证明本地候选通过，仍须完成该 PR 最终 head
与合并后 `master` 的远端和本地验证，才可开始 Canvas 批次。实时
`adb devices -l` 为空，本批未运行设备测试，也不将其描述为通过。

## Pull Request 与合并后闭环

PR #68 的最终 head 为 `25d04d1b53e0dd1ce4f4963d1dca382b2644b985`，merge
commit 为 `15956395a4389efc108569877bb4465ed26ad797`。最终 head 的
`Android Debug 验证` run `33167967205` 成功；合并后同一 merge commit 的
`Test Build` run `33168293139` 也成功。短期 head 分支按治理序列要求保留。

2026-08-28 从干净的合并后 `master` 使用 JDK 17.0.17 与固定 Android SDK
再次强制执行 131 个 Gradle 任务，128 个单元测试 0 失败、1 跳过，Android lint
为 0 error、700 warning、18 hint，Debug 构建、OpenSpec 严格校验和空白检查
均成功。合并后报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `1b096ef8b77ea1585338b1391f41f330602a57b95d9c26b1885e1b1562b62e1a` |
| `lint-results-appDebug.html` | `31a28c284ecf7c832c42f146a47d63622df51f23f02b0f4298e1ffd24de9aebb` |
| `lint-results-appDebug.txt` | `2dfc974fb1536c57c90cf7a8afc9c826bca3c6cb1070825e2c18b302abde56d0` |

至此 PR 4o 完成全部串行门禁，可以从该 merge commit 开始 Canvas 小批次。
