# PR 4c `HardcodedText` warning 清理证据

## 串行前置条件

PR 4b 为 GitHub PR #55，最终 head 为 `96d3b0165839675224044bc34d538a047f2a26eb`，merge commit 为 `86ee65e7313406839d104774ae518143dac60e05`。PR 与合并后 `master` 的 `Android Debug 验证` 均成功；合并后本地强制执行 131 个 Gradle 任务，102 个单元测试 0 失败、1 跳过，Android lint 为 0 error、852 warning、18 hint，OpenSpec 严格校验 3 项全部通过。满足串行条件后才开始本批。

## 范围与处置

本批只处理合并后 lint XML 中 14 个 `HardcodedText` occurrence：

- 漫画页的两个“重新加载”复用已有 `retry` 资源；
- 搜索菜单的 3 个“结果”和 3 个“退出”分别复用已有 `search_content_size` 和 `exit` 资源；
- 日期选择、剪贴板导入、上个结果和下个结果新增 8 个现有 locale 的文本资源；
- 默认每日章节 `3` 和初始进度 `0%` 作为非语言常量放入精确的 `translatable="false"` 资源；
- 不修改交互 ID、点击处理、运行时进度更新、最低 API、Room schema、规则/备份格式、导入 URI、包名、签名或固定依赖。

## 聚焦 RED/GREEN

新增 `HardcodedTextContractTest`，锁定漫画重试/进度、模拟阅读/主题导入、搜索菜单映射，以及全 locale 与非翻译资源契约。

旧实现运行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:testAppDebugUnitTest \
  --tests io.legado.app.i18n.HardcodedTextContractTest \
  --console=plain
```

结果为 4 个测试、4 个失败。完成精确资源映射后使用同一命令重跑，4/4 全部通过。

## Android lint 对账

实现后运行 `:app:lintAppDebug --console=plain` 成功，lint XML 为 0 error、838 warning、18 hint：

| lint ID | 修改前 | 修改后 | FIXED | SUPPRESSED | PENDING |
|---|---:|---:|---:|---:|---:|
| `HardcodedText` | 14 | 0 | 14 | 0 | 0 |
| **warning 合计** | **852** | **838** | **39** | **4** | **838** |

warning 合计的 `FIXED` 包含前序 25 项和本批 14 项；4 个既有 suppression 仍全部来自 PR 4b 的精确 `SetTextI18n` 输入范围。本批没有新增 suppression 或 `DEFERRED`，`SetTextI18n`、`MissingTranslation`、`RtlHardcoded` 和 `RtlSymmetry` 均保持为 0。

本次报告哈希：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `f499890503a9ebe6a69c747c4096cca6f6646bb2ae80f2e4f4dc7945efb9e3c0` |
| `lint-results-appDebug.html` | `65537885e4c5fc9a0d0647923a9d8215a8d8b0561ef620e3b9a1b5ecd766d935` |
| `lint-results-appDebug.txt` | `5ac03274cc305a71a5fa3830f2f291e1c347f1ba3f9efec91b017fb6f927ebad` |

任务 5.2 继续保持未完成，剩余国际化类 warning 仍须独立审查，且下一批必须等待本 PR 完成全部本地、PR 与合并后验证。

## 本批完整本地验证

2026-08-27 使用 JDK 17 和本机 Android SDK 强制重跑：

```bash
./gradlew :app:testAppDebugUnitTest \
  :app:lintAppDebug \
  :app:assembleAppDebug \
  --rerun-tasks --console=plain
```

131 个 Gradle 任务全部实际执行，命令成功。最终结果为：

- 106 个单元测试、0 失败、0 error、1 跳过；
- Android lint 0 error、838 warning、18 hint；
- Debug APK 构建成功；
- `openspec validate --all --strict` 为 3 项通过、0 项失败；
- `git diff --check` 成功，没有空白错误。

上述结果只证明本批本地提交候选通过；Pull Request 与合并后 `master` 的远端检查仍须分别通过，才可开始下一 warning 批次。
