# PR 4a RTL warning 清理证据

## 范围与基线

本批从 PR 3 合并后提交 `f8adb3fe0b2100ecdc6412f34fac56bcf672010d` 开始，只处理 lint XML 中 7 个 `RtlHardcoded` 和 2 个 `RtlSymmetry` occurrence，不处理已有的精确局部抑制，也不修改最低 API、Room schema、规则、备份、导入 URI、包名、签名或固定依赖。

精确生产文件为：

- `app/src/main/res/layout/dialog_check_source_config.xml`
- `app/src/main/res/layout/dialog_progressbar_view.xml`
- `app/src/main/res/layout/dialog_simulated_reading.xml`
- `app/src/main/res/layout/item_chapter_list.xml`

修复仅将 `left`/`right` 的物理方向属性替换为等值的 `start`/`end` 逻辑方向属性，并在原布局只有 `paddingEnd="8dp"` 时显式补充 `paddingStart="0dp"`。LTR 下数值保持不变，RTL 下按逻辑方向镜像。

## 聚焦 RED/GREEN

新增 `RtlLayoutContractTest`，以四个测试锁定原有 LTR 数值和逻辑方向契约。测试先在未修改的 XML 上运行，结果为 4 个测试、4 个失败，分别暴露：

- 书源校验标题仍使用 `paddingLeft`；
- 进度视图仍使用两个 `layout_marginRight`；
- 模拟阅读布局仍使用 `right`、`left` 和 `center_horizontal|left`；
- 章节锁图标只有 `paddingEnd`，没有显式的零 `paddingStart`。

完成 XML 修改后运行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:testAppDebugUnitTest \
  --tests io.legado.app.i18n.RtlLayoutContractTest \
  --console=plain
```

结果为 4 个测试全部通过。

## Android lint GREEN

运行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:lintAppDebug --console=plain
```

结果成功，lint XML 为 0 error、860 warning、18 hint。与 PR 3 清单比较：

| lint ID | 修改前 | 修改后 | FIXED | PENDING |
|---|---:|---:|---:|---:|
| `RtlHardcoded` | 7 | 0 | 7 | 0 |
| `RtlSymmetry` | 2 | 0 | 2 | 0 |
| **warning 合计** | **869** | **860** | **21** | **860** |

其中 warning 合计的 `FIXED` 包含 PR 3 已修复的 12 项和本批 9 项。当前没有新增 `SUPPRESSED_WITH_REASON` 或 `DEFERRED`。

本次报告哈希：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `5bdb83597e6882b59eda22c60a77f385bf648ecea0d22f3dd5561a21bdcc2c41` |
| `lint-results-appDebug.html` | `ce6358b4701029aac68b334e95c2f73a025a009d57d9e975f3f984e6e82e5a51` |
| `lint-results-appDebug.txt` | `0c0e30ea074baa84e539d8e7da6da93a421bc62ba93bfda7cc110e365ceb904e` |

完整逐 occurrence 报告仍保存在 Git 忽略的本机构建目录；PR 5 建立持续门禁后由 CI artifact 保存。任务 5.2 继续保持未完成，直到正确性、启动安全、国际化和 RTL 类 warning 全部完成三态处置。

## 本批完整本地验证

2026-08-27 使用同一 JDK 17 和 Android SDK 完成以下验证：

| 验证 | 结果 |
|---|---|
| `:app:testAppDebugUnitTest --rerun-tasks` | 成功；98 个测试、0 失败、0 error、1 跳过，57 个 Gradle 任务实际执行 |
| `:app:lintAppDebug` | 成功；0 error、860 warning、18 hint，两个目标 RTL ID 均为 0 |
| `:app:assembleAppDebug --rerun-tasks` | 成功；75 个 Gradle 任务实际执行 |
| `openspec validate --all --strict` | 成功；3 项通过、0 项失败 |
| `git diff --check` | 成功；没有空白错误 |

上述结果只证明本批本地提交候选通过；Pull Request 与合并后 `master` 的远端检查仍须分别通过后，才可开始下一 warning 批次。
