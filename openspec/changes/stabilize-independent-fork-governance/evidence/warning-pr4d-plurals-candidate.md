# PR 4d（GitHub #57）`PluralsCandidate` warning 清理证据

## 串行前置条件

PR 4c 为 GitHub PR #56，最终 head 为 `e79236490072015a9be7999102b3311f2bb68210`，merge commit 为 `755abede96b539fc6d2f4de2c6da3943efad5518`。PR 与合并后 `master` 的 `Android Debug 验证` 均成功；合并后本地强制执行 131 个 Gradle 任务，106 个单元测试 0 失败、1 跳过，Android lint 为 0 error、838 warning、18 hint，OpenSpec 严格校验 3 项全部通过。满足串行条件后才创建本批分支。

## 范围与动态引用审计

本批只处理合并后 lint XML 中的 5 个 `PluralsCandidate` occurrence：

- `all_chapter_num`；
- `un_download`；
- `search_book_source_num`；
- `nb_file_sub_count`；
- `nb_file_add_succeed`。

这 5 个名称同时各有 1 个 `UnusedResources` occurrence。删除前使用精确名称搜索代码、XML、书源/规则、Web、assets 和仓库其他文本，除 8 组 locale 的 `strings.xml` 声明外没有引用；生产代码中的 `Resources.getIdentifier` 只动态读取系统尺寸或启动图标，不存在按名称读取 `string` 的路径。因此本批不把死资源机械改写为 `plurals`，而是删除 8 组 locale 中对应的 40 条声明，同时消除两类冗余 warning。

本批没有删除其他资源，没有修改生产调用、用户可见行为、最低 API 21、Room schema、规则/备份格式、导入 URI、正式包名、签名或固定依赖。

## 聚焦 RED/GREEN

新增 `PluralsCandidateContractTest`，为 5 个资源分别断言所有现有 locale 均不再声明该死资源。

旧实现运行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:testAppDebugUnitTest \
  --tests io.legado.app.i18n.PluralsCandidateContractTest \
  --rerun-tasks --console=plain
```

结果为 5 个测试、5 个失败；删除精确声明后使用同一命令重跑，5/5 全部通过，57 个相关 Gradle 任务实际执行。

## Android lint 对账

实现后运行 `:app:lintAppDebug --rerun-tasks --console=plain` 成功，103 个 lint 相关 Gradle 任务实际执行；lint XML 为 0 error、828 warning、18 hint：

| lint ID | 修改前 | 修改后 | 本批 FIXED | SUPPRESSED | PENDING |
|---|---:|---:|---:|---:|---:|
| `PluralsCandidate` | 5 | 0 | 5 | 0 | 0 |
| `UnusedResources` | 593 | 588 | 5 | 0 | 588 |
| **warning 合计** | **838** | **828** | **10** | **0** | **828** |

warning 合计的累计 `FIXED` 为 49，4 个既有 suppression 仍全部来自 PR 4b 的精确 `SetTextI18n` 输入范围。本批没有新增 suppression 或 `DEFERRED`，`HardcodedText`、`SetTextI18n`、`MissingTranslation`、`RtlHardcoded` 和 `RtlSymmetry` 均保持为 0。

本次报告哈希：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `7d25ec8f0022b1a315e0348ccb9130a659e324a2fac9310ee8cd103371f1beab` |
| `lint-results-appDebug.html` | `402d1ed08d1f9b1c75cb50a7a50302d12b761a2c3a620dface38b1f184256307` |
| `lint-results-appDebug.txt` | `59abab56215250cf2c2771e29490aa7db7e3a7572f1d24cb0134d92d00679b5e` |

任务 5.2 继续保持未完成，剩余正确性与国际化类 warning 仍须独立审查，且下一批必须等待本 PR 完成全部本地、PR 与合并后验证。

## 本批完整本地验证

2026-08-27 使用 JDK 17 和本机 Android SDK 强制重跑：

```bash
./gradlew :app:testAppDebugUnitTest \
  :app:lintAppDebug \
  :app:assembleAppDebug \
  --rerun-tasks --console=plain
```

131 个 Gradle 任务全部实际执行，命令成功。最终结果为：

- 111 个单元测试、0 失败、0 error、1 跳过；
- Android lint 0 error、828 warning、18 hint；
- Debug APK 构建成功；
- `openspec validate --all --strict` 为 3 项通过、0 项失败；
- `git diff --check` 成功，没有空白错误。

上述结果只证明本批本地提交候选通过；Pull Request 与合并后 `master` 的远端检查仍须分别通过，才可开始下一 warning 批次。
