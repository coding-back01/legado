# 实施与验证记录

## 环境与边界

2026-10-13，保持当前分支，开始时工作区干净。使用已有 JDK 17、Android SDK 和离线 Gradle 缓存，不创建 local.properties，不更新依赖或签名配置。只在可丢弃的模拟器运行测试，测试导入自建夹具并清理自己的记录，未使用商业词库。

## 调研与规格先行

先访问官方手册、真实截图和评测，再检查当前代码；证据见 research.md。独立创建 `redesign-local-dictionary-kindle-ui`，完成 proposal、specs、design、tasks 并严格校验后才修改应用 UI。原词典规格和归档未改，变更不归档。

## 构建命令

以下命令使用同一环境前缀：

```bash
/usr/bin/env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
ANDROID_HOME=/Users/back/Library/Android/sdk \
./gradlew --offline --max-workers=2 \
'-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' \
:app:assembleAppDebug :app:assembleAppDebugAndroidTest :app:testAppDebugUnitTest
```

构建与单元测试实际成功；单元测试 XML 汇总 251 项，失败 0、错误 0、跳过 1，其中词典测试 89 项全部通过。过程日志在忽略目录 `app/build/reports/dictionary-kindle-ui/`，最终单元检查日志为 `final-verification.log`，最终打包与静态检查日志为 `delivery-final.log`，单元结果在 `app/build/test-results/testAppDebugUnitTest/`。

## 旧版基线

- 原 API 36 即时阅读测试 3 项通过；增加截图时机后，旧应用即时阅读及详情测试合计 9 项通过。
- API 21 旧应用实际词条图片、内部链接与音频测试 1 项通过，保存富文本截图。
- 连续轮询截图错过短暂弹窗，因此改为测试明确达到状态后截图。旧长正文滚动截图出现未完成绘制的空白帧，不纳入交付证据；不能据此宣称旧词典正文为空。
- 有效基线：`evidence/before-reading.png`、`before-landscape.png`、`before-rich-entry-api21.png`。

## 发现并修复

1. 旧即时面板按钮进入导航栏：定位改用窗口可见区域，并增加全文入口完全落在可见窗口的断言。
2. 首轮夜间详情标题栏仍为浅色：详情表面以实际夜间配置为准，与全文深色一致。
3. 旋转测试在 Fragment 恢复前访问空列表：等待实际视图恢复，再执行原文字、滚动和展开状态断言。
4. 低频操作迁移到菜单后，首选策略测试仍选择无焦点的宿主窗口：改为明确匹配弹出菜单窗口，保留首选写入断言。
5. 来源选择夜间仍为白底：增加词典专用明暗对话框主题，保持选择标识中性色。WebView 初始背景与表面同步，截图等待绘制提交，避免把中间白帧当作最终内容。
6. 首次编译发现 Kotlin 接收者中局部 text 与属性同名，已明确使用 this.text；后续构建通过。

首轮设备组合测试 30 项中 28 项通过，上述两处自动化定位/同步问题修复后另行复测；不将首轮失败计为成功。静态检查前两次遇到 lint 的 AsyncExecutionService 内部异常，另一次独立进程检查在发现新的视觉问题后主动取消；取消结果不计通过。最终重新执行完整静态检查。

## 最终结果

- API 36：六组词典回归共 30 项通过，日志 `final-api36.log`。
- API 21：同一组 30 项通过，日志 `final-api21.log`。
- API 30 平板逻辑尺寸（1600×2560、240dpi）：实际阅读与长正文旋转 2 项通过，日志 `tablet-api30.log`；已恢复原尺寸与密度。
- 最后增加局部弹窗主题、真实滑动和 WebView 绘制等待后，API 36 的阅读/详情/查询策略 14 项全部通过（`api36-ui.log`），API 21 的阅读/查询策略 8 项全部通过（`api21-ui.log`）。真实注入滑动后预览发生滚动，阅读位置未改变。
- 静态检查已成功运行；过程警告包括数量复数、废弃拼接资源、局部透明背景写法及容器焦点属性，已逐项处理。面板容器采用子控件优先的键盘焦点策略，屏幕阅读器跳过没有动作的容器本身，保留其子按钮与正文。最终 `:app:assembleAppDebug :app:lintAppDebug` 成功（`delivery-final.log`），lint 报告无问题。

## 适用范围

截图来自真实运行的 Android 应用，不是概念图。新英文词条为自建 XDXF 夹具，通过实际导入、查词和渲染路径展示；原有 DICTD、MDict 等夹具继续用于兼容回归。API 30 的平板验证通过临时调整模拟器逻辑尺寸与密度完成，结束恢复原值，不代表物理平板实测。

未运行 ARM 真机、TalkBack 人工朗读、全部第三方商业词库与所有 OEM 分屏/输入法矩阵。不声称复刻当前所有 Kindle 固件或当前 Android Kindle 版本；参考版本和不可访问资料的限制见调研记录。


## 验收与代码映射

| 验收项 | 实现 | 自动化证据 |
| --- | --- | --- |
| 稳定选词、关闭、复制、按书开关 | ReadBookActivity 与即时面板 XML | DictionaryInstantReadingTest、DictionaryReadSelectionTest |
| 词头/字段/例句层级、滚动、全文 | DictionaryPresentation、DictDialog、DictionaryHtml | 新阅读排版场景、DictionaryCardTest 的可靠摘要与长正文用例 |
| 匹配来源、首选/候补语义、错误 | 独立匹配文字与原查询入口 | DictionaryCardQueryPolicyTest、DictionaryQueryTest |
| 词典切换、内部返回、图片、音频、取消 | 原查询/资源处理保留，菜单和单选入口重组 | DictionaryCardTest 的来源切换和资源导航用例 |
| 管理语言、角色、资源关联 | LocalDictionaryActivity 与两份管理 XML | DictionaryManagementTest（新增角色可见断言及截图） |
| 明暗、横屏、大字号、宽屏 | 专用展示样式、窗口安全区和限宽 | 即时横屏字体 2.0、详情字体 1.4/旋转、平板两项 |

构建、设备测试和静态检查日志保存在 `app/build/reports/dictionary-kindle-ui/`，不把构建产物或日志混入提交。长期保留的调研、设计、验证结论和有效截图在本变更目录。


## 核心修改文件

- `app/src/main/java/io/legado/app/ui/dict/DictionaryPresentation.kt`：共享摘要排版、表面配色及局部明暗对话框主题。
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`：即时面板字段绑定、安全区与限宽、滚动区域尺寸、来源/全文入口及返回收起。
- `app/src/main/java/io/legado/app/ui/dict/DictDialog.kt`：词头和匹配说明分离、单选来源、低频菜单、窗口自适应及深色初始背景；资源安全逻辑保持。
- `app/src/main/java/io/legado/app/ui/dict/local/LocalDictionaryActivity.kt`：可见导入入口、语言与首选/候补展示、数量复数资源。
- `app/src/main/java/io/legado/app/model/dictionary/DictionaryHtml.kt`：仅调整正文与可靠语义类的显示样式、表格溢出；净化和 CSP 不变。
- `app/src/main/res/layout/view_instant_dictionary.xml`、`dialog_dict.xml`、`activity_local_dictionary.xml`、`item_local_dictionary.xml` 与 `values/styles_dictionary.xml`：布局和视觉规范。
- 八组 `strings_local_dictionary.xml` 和 `localDictionaryHelp.md`：本地化与使用说明；移除已废弃的来源拼接资源。
- 四个词典设备测试文件：截图、真实滚动、窗口安全区、角色显示断言及菜单/旋转同步。

解析器、索引、查找算法、查询数据结构、Room、存储、备份、依赖和通用阅读选择样式未改动。没有切换分支、提交、发布或归档。


## 最终工件检查

`openspec validate --all --strict` 共 11 项通过；`git diff --check` 及新增文本空白检查通过。已检查全部修改路径与差异统计，无数据库、解析/索引、依赖、签名、CI 或网页构建产物改动。全部 13 项实施任务完成，保留变更供最终审查，不归档。
