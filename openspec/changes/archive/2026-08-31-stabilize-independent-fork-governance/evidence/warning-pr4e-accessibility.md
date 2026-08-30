# PR 4e（GitHub #58）无障碍与输入可用性 warning 清理证据

## 串行前置条件

PR 4d 为 GitHub PR #57，最终 head 为 `054b9391cb96640787ee38c126ea137b435d87a9`，merge commit 为 `033dbe5db1c202ebaed97e31ad052f4dcd774873`。PR 与合并后 `master` 的 `Android Debug 验证` 均成功；合并后本地强制执行 131 个 Gradle 任务，111 个单元测试 0 失败、1 跳过，Android lint 为 0 error、828 warning、18 hint，OpenSpec 严格校验 3 项全部通过。满足串行条件后才创建本批分支。

## 范围与处置

本批只处理合并后 lint XML 中的 5 个 occurrence：

- `Autofill` 1 项：模拟阅读的日期字段只由 `DatePickerDialog` 赋值，明确设置 `importantForAutofill="no"`；
- `ContentDescription` 2 项：锁定章节图标使用 8 个现有 locale 均具备的 `chapter_locked` 语义；主题设置中的当前图标预览由父 Preference 的标题表达用途，作为纯装饰退出无障碍树；
- `KeyboardInaccessibleWidget` 1 项：日期字段保留键盘焦点和点击入口，移除运行时强制不可聚焦设置，并通过 `showSoftInputOnFocus=false` 保持不弹出软键盘；
- `TextFields` 1 项：日期字段不接受直接文本编辑，继续使用 `inputType="none"`，只在该视图添加精确 `tools:ignore` 和中文原因注释，记为 `SUPPRESSED_WITH_REASON`。

本批没有修改日期格式、保存逻辑、章节付费判断、图标选择行为、最低 API 21、Room schema、规则/备份格式、导入 URI、正式包名、签名或固定依赖。

## 聚焦 RED/GREEN

新增 `AccessibilityWarningContractTest`，覆盖日期字段的自动填充与输入类型契约、键盘焦点和软键盘边界、锁定章节语义的全 locale 完整性，以及装饰性主题图标预览的无障碍树边界。

旧实现运行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:testAppDebugUnitTest \
  --tests io.legado.app.accessibility.AccessibilityWarningContractTest \
  --rerun-tasks --console=plain
```

结果为 4 个测试、4 个失败；完成精确布局、运行时焦点和资源调整后使用同一命令重跑，4/4 全部通过，57 个相关 Gradle 任务实际执行。

## Android lint 对账

实现后运行 `:app:lintAppDebug --rerun-tasks --console=plain` 成功，103 个 lint 相关 Gradle 任务实际执行；lint XML 为 0 error、823 warning、18 hint：

| lint ID | 修改前 | 修改后 | FIXED | SUPPRESSED | PENDING |
|---|---:|---:|---:|---:|---:|
| `Autofill` | 1 | 0 | 1 | 0 | 0 |
| `ContentDescription` | 2 | 0 | 2 | 0 | 0 |
| `KeyboardInaccessibleWidget` | 1 | 0 | 1 | 0 | 0 |
| `TextFields` | 1 | 0 | 0 | 1 | 0 |
| **warning 合计** | **828** | **823** | **4** | **1** | **823** |

warning 合计的累计 `FIXED` 为 53，累计 suppression 为 5。本批没有新增 `DEFERRED`，`MissingTranslation`、`PluralsCandidate`、`HardcodedText`、`SetTextI18n`、`RtlHardcoded` 和 `RtlSymmetry` 均保持为 0。

本次报告哈希：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `6dd392aff8cd3308feb5045cc3c4775b456846b0821f508e856132972c7a6c6b` |
| `lint-results-appDebug.html` | `76bdff5e8c59ca39566110fdf4ee0cb86763153d767d3c88199c4519a99ed176` |
| `lint-results-appDebug.txt` | `753f2560b2d9276095133e04cc3e7f2cdea3e2522fa9baf153ff0881ecb24b56` |

任务 5.3 继续保持未完成，后续仍须独立处理有可靠证据的性能 warning；任务 5.6 也不因本批 1 个精确 suppression 提前完成。

## 本批完整本地验证

2026-08-28 使用 JDK 17 和本机 Android SDK 强制重跑：

```bash
./gradlew :app:testAppDebugUnitTest \
  :app:lintAppDebug \
  :app:assembleAppDebug \
  --rerun-tasks --console=plain
```

131 个 Gradle 任务全部实际执行，命令成功。最终结果为：

- 115 个单元测试、0 失败、0 error、1 跳过；
- Android lint 0 error、823 warning、18 hint；
- Debug APK 构建成功；
- `openspec validate --all --strict` 为 3 项通过、0 项失败；
- `git diff --check` 成功，没有空白错误。

本批的 `FIXED` 只覆盖可由布局、生产代码和 lint 共同证明的结构契约：日期字段明确可聚焦、运行时不再覆盖为不可聚焦、点击入口仍存在且软键盘被禁止。当前没有已连接设备，已有 `Pixel_5_API_30` AVD 未启动；实际模拟器交互仍按任务 7.6 复验，若发现回归将阻止发布。本批不把未运行的设备交互描述为通过。

上述结果只证明本批本地提交候选通过；Pull Request 与合并后 `master` 的远端检查仍须分别通过，才可开始下一 warning 批次。
