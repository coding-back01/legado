# PR 4f（GitHub 待创建）布局性能 warning 审查证据

## 串行前置条件

PR 4e 为 GitHub PR #58，最终 head 为 `0991a53f5908ea30f0601964fc14828081593ca7`，merge commit 为 `4eff2225d76ec0b425f22d95acc1e4dc26508094`。PR 与合并后 `master` 的 `Android Debug 验证` 均成功；合并后本地强制执行 131 个 Gradle 任务，115 个单元测试 0 失败、1 跳过，Android lint 为 0 error、823 warning、18 hint，OpenSpec 严格校验 3 项全部通过。满足串行条件后才创建本批分支。

## 可安全修复范围

本批修复唯一的 `InefficientWeight` occurrence：`dialog_simulated_reading.xml` 中 `sr_enabled` 位于固定宽度 53dp 的横向 `LinearLayout`，是唯一带 `layout_weight=1` 的子项。将其 `layout_width` 从 `match_parent` 改为 `0dp` 后仍占用同一剩余宽度，并保留 `android:width="20dp"`、开关样式、重力和事件绑定，仅避免第一次无效测量。

## 聚焦 RED/GREEN

新增 `LayoutPerformanceContractTest`，锁定 `sr_enabled` 的 `0dp + weight` 组合和既有 20dp 宽度契约。

旧实现运行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:testAppDebugUnitTest \
  --tests io.legado.app.performance.LayoutPerformanceContractTest \
  --rerun-tasks --console=plain
```

结果为 1 个测试、1 个失败；完成单属性修改后使用同一命令重跑，1/1 通过，57 个相关 Gradle 任务实际执行。

## 精确延期范围

以下 41 个 `Overdraw` occurrence 都指向根布局背景。删除背景可能改变日夜主题、对话框不透明兜底或透明页面渲染；当前没有逐页面截图基线，因此全部保持原代码并记为 `DEFERRED`：

```text
app/src/main/res/layout/activity_audio_play.xml:7
app/src/main/res/layout/activity_source_login.xml:7
app/src/main/res/layout/activity_translucence.xml:6
app/src/main/res/layout/dialog_auto_read.xml:7
app/src/main/res/layout/dialog_book_change_source.xml:7
app/src/main/res/layout/dialog_chapter_change_source.xml:7
app/src/main/res/layout/dialog_click_action_config.xml:8
app/src/main/res/layout/dialog_code_view.xml:6
app/src/main/res/layout/dialog_content_edit.xml:6
app/src/main/res/layout/dialog_file_chooser.xml:7
app/src/main/res/layout/dialog_page_key.xml:7
app/src/main/res/layout/dialog_photo_view.xml:5
app/src/main/res/layout/dialog_read_aloud.xml:8
app/src/main/res/layout/dialog_read_bg_text.xml:8
app/src/main/res/layout/dialog_read_book_style.xml:8
app/src/main/res/layout/dialog_search_scope.xml:7
app/src/main/res/layout/dialog_text_view.xml:6
app/src/main/res/layout/dialog_wait.xml:5
app/src/main/res/layout/item_1line_text.xml:9
app/src/main/res/layout/item_app_log.xml:6
app/src/main/res/layout/item_book_group_manage.xml:6
app/src/main/res/layout/item_book_manga_edge.xml:6
app/src/main/res/layout/item_book_manga_page.xml:8
app/src/main/res/layout/item_book_source.xml:7
app/src/main/res/layout/item_bookmark.xml:7
app/src/main/res/layout/item_change_source.xml:7
app/src/main/res/layout/item_chapter_list.xml:8
app/src/main/res/layout/item_file.xml:9
app/src/main/res/layout/item_font.xml:6
app/src/main/res/layout/item_group_manage.xml:5
app/src/main/res/layout/item_group_select.xml:5
app/src/main/res/layout/item_import_book.xml:7
app/src/main/res/layout/item_read_record.xml:7
app/src/main/res/layout/item_rss_read_record.xml:6
app/src/main/res/layout/item_rss_source.xml:6
app/src/main/res/layout/item_search_list.xml:7
app/src/main/res/layout/item_server_select.xml:6
app/src/main/res/layout/item_text.xml:7
app/src/main/res/layout/item_txt_toc_rule.xml:7
app/src/main/res/layout/popup_keyboard_tool.xml:8
app/src/main/res/layout/view_book_page.xml:8
```

另外两项保持原代码并精确延期：

- `UselessParent`：`app/src/main/res/layout/view_manga_menu.xml:85`。扁平化会把子容器 margin 迁到带背景父容器的 padding，并改变漫画菜单测量与绘制职责；重启条件是建立固定漫画夹具、UI 层级及日夜模式截图，验证 SeekBar、前后章按钮位置和点击区域不变。
- `UseCompoundDrawables`：`app/src/main/res/layout/item_path_picker.xml:2`。改成单 TextView 会同时改变 `FileManageActivity` 与 `FilePickerDialog` 使用的 View Binding 字段、动态箭头 Drawable 尺寸、文本着色和整行点击区域；重启条件是建立两个文件选择路径的面包屑导航、图标/文字截图和点击区域测试。

## Android lint 对账

实现后运行 `:app:lintAppDebug --rerun-tasks --console=plain` 成功，103 个 lint 相关 Gradle 任务实际执行；lint XML 为 0 error、822 warning、18 hint：

| lint ID | 修改前 | 修改后 | FIXED | DEFERRED | PENDING |
|---|---:|---:|---:|---:|---:|
| `InefficientWeight` | 1 | 0 | 1 | 0 | 0 |
| `Overdraw` | 41 | 41 | 0 | 41 | 0 |
| `UselessParent` | 1 | 1 | 0 | 1 | 0 |
| `UseCompoundDrawables` | 1 | 1 | 0 | 1 | 0 |
| **warning 合计** | **823** | **822** | **1** | **43** | **779** |

warning 合计的累计 `FIXED` 为 54，累计 suppression 为 5，累计 `DEFERRED` 为 43。本批没有新增 suppression；此前已清零的无障碍、国际化和 RTL ID 均保持为 0。

本次报告哈希：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `35de7bb88b3bda112e799e75aac63657b5c6e12b9583bffee54acb12078c4af3` |
| `lint-results-appDebug.html` | `4829e954cc4e190e260ce23445f3d1830ee273168a8d8bd72148538849054ca3` |
| `lint-results-appDebug.txt` | `f264d218c5088cb01c51226f10f3e6430b746129b3af19dc1574f811fbbab68e` |

任务 5.3 继续保持未完成，直到本 PR 完成全部本地、PR 与合并后验证；任务 5.7 也不因本批 43 个精确延期项提前完成。

## 本批完整本地验证

2026-08-28 使用 JDK 17 和本机 Android SDK 强制重跑：

```bash
./gradlew :app:testAppDebugUnitTest \
  :app:lintAppDebug \
  :app:assembleAppDebug \
  --rerun-tasks --console=plain
```

131 个 Gradle 任务全部实际执行，命令成功。最终结果为：

- 116 个单元测试、0 失败、0 error、1 跳过；
- Android lint 0 error、822 warning、18 hint；
- Debug APK 构建成功；
- `openspec validate --all --strict` 为 3 项通过、0 项失败；
- `git diff --check` 成功，没有空白错误。

上述结果只证明本批本地提交候选通过；Pull Request 与合并后 `master` 的远端检查仍须分别通过，才可完成任务 5.3 或开始下一 warning 批次。
