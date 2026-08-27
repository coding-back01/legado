# PR 4b `SetTextI18n` warning 清理证据

## 串行前置条件

PR 4a 为 GitHub PR #54，最终 head 为 `2a57817fce243ca813c3ded2bb4c4b4182f99ad4`，merge commit 为 `a8b0eb2d6f5e54a737381a78d905b1c4c3634385`。PR 与合并后 `master` 的 `Android Debug 验证` 均成功；合并后本地强制执行 131 个 Gradle 任务，98 个单元测试 0 失败、1 跳过，Android lint 为 0 error、860 warning、18 hint，OpenSpec 严格校验 3 项全部通过。满足串行条件后才开始本批。

## 范围与处置

本批只审查合并后 lint XML 中的 8 个 `SetTextI18n` occurrence：

- `DetailSeekBar` 的默认整数值和 `ThemeConfigFragment` 的两个模糊度值使用当前界面 locale 的整数格式器；
- `OpenUrlConfirmDialog` 的来源名称和确认提示改为 8 个现有 locale 都具备 `%1$s` 占位符的字符串资源；
- 4 个会直接由 `toInt()` 或 `toLong()` 回读的数值输入继续使用 ASCII 整数，并在 3 个精确函数范围添加 `SetTextI18n` 抑制与原因注释；
- 不修改数字字段的持久化语义、最低 API、Room schema、规则格式、备份、导入 URI、包名、签名或固定依赖。

## 聚焦 RED/GREEN

新增 `SetTextI18nContractTest`，覆盖 locale 分组格式、3 处显示数字调用、ASCII 数字输入的精确抑制理由，以及 8 个 locale 的跳转提示资源。

旧实现运行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:testAppDebugUnitTest \
  --tests io.legado.app.i18n.SetTextI18nContractTest \
  --console=plain
```

结果为 4 个测试、4 个失败：缺少本地化整数格式器、显示调用仍使用 `toString()`、ASCII 输入没有精确兼容说明、跳转提示仍为中文拼接。

实现后首次运行有 3/4 通过；唯一失败是测试把格式正确的多行 `getString` 调用误写为单行源码匹配。只将该断言改为保留资源名、参数和调用边界的空白不敏感正则后，4/4 全部通过，没有放宽产品契约。

## Android lint 对账

实现后运行 `:app:lintAppDebug --console=plain` 成功，lint XML 为 0 error、852 warning、18 hint：

| lint ID | 修改前 | 修改后 | FIXED | SUPPRESSED | PENDING |
|---|---:|---:|---:|---:|---:|
| `SetTextI18n` | 8 | 0 | 4 | 4 | 0 |
| **warning 合计** | **860** | **852** | **25** | **4** | **852** |

warning 合计的 `FIXED` 包含前序 21 项和本批 4 项；4 个 suppression 已在 `docs/maintenance-baseline.md` 按文件、函数、理由和重启条件精确登记。当前没有新增 `DEFERRED`，`RtlHardcoded`、`RtlSymmetry` 和 `MissingTranslation` 均保持为 0。

本次报告哈希：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `8756d567f4d4bc363e0eb0ea95c12e52ee47c0afd32dc4700b94f03f77c0fd0d` |
| `lint-results-appDebug.html` | `573574fa9b25594dfd232d24325fc87570d58b0fac23f5e3234662b500317d70` |
| `lint-results-appDebug.txt` | `28767ad10010c53ca31891aeab7537fb5929748cce86630ba6c7f7d321447b02` |

任务 5.2 继续保持未完成，下一批仍须处理 `HardcodedText` 等国际化 warning，并等待本 PR 完成全部本地、PR 与合并后验证。

## 本批完整本地验证

2026-08-27 使用 JDK 17 和本机 Android SDK 强制重跑：

```bash
./gradlew :app:testAppDebugUnitTest \
  :app:lintAppDebug \
  :app:assembleAppDebug \
  --rerun-tasks --console=plain
```

131 个 Gradle 任务全部实际执行，命令成功。最终结果为：

- 102 个单元测试、0 失败、0 error、1 跳过；
- Android lint 0 error、852 warning、18 hint；
- Debug APK 构建成功；
- `openspec validate --all --strict` 为 3 项通过、0 项失败；
- `git diff --check` 成功，没有空白错误。

上述结果只证明本批本地提交候选通过；Pull Request 与合并后 `master` 的远端检查仍须分别通过，才可开始下一 warning 批次。
