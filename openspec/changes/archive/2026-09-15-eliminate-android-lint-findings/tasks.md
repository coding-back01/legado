## 1. 一次 Apply 前置预检

- [x] 1.1 重新运行 `git status --short`，确认并记录开始提交、分支和用户既有改动；后续只处理本变更范围。
- [x] 1.2 确认使用 JDK 17 和 `/Users/back/Library/Android/sdk`，核对 API 21、23、36 system image 与 AVD；缺少 API 23 时先完成环境准备，不修改生产文件。
- [x] 1.3 实时读取 `adb devices -l`，核对一台可丢弃或明确授权的 Debug 测试设备、目标包 `io.legado.app.debug` 和无正式版数据边界；未满足时在生产修改前停止。
- [x] 1.4 使用固定 SDK 对开始提交强制运行 `:app:lintAppDebug --rerun-tasks`，保存 XML/HTML/文本报告、SHA-256 和按 severity、ID、文件、位置展开的 occurrence 清单。
- [x] 1.5 将 Apply 当天新出现的 warning 或 hint 加入同一清单，验证每个 occurrence 都有本任务表中的处置批次；不得创建其他 OpenSpec 变更。

## 2. 对账与视觉测试基础设施

- [x] 2.1 为 lint XML 编写只读清单与对账工具，覆盖报告缺失、损坏 XML、空报告以及按 severity/ID 汇总，并先为失败输入观察 RED。
- [x] 2.2 基于现有 Android 测试 runner 和平台截图能力建立无第三方依赖的 golden 捕获、比较和 diff 输出工具。
- [x] 2.3 增加只能通过显式参数启用的 golden 更新入口，确保普通本地运行和 CI 无法自动改写预期图片。
- [x] 2.4 固定测试语言、字体比例、动画、主题、窗口尺寸和无个人内容夹具，建立 API 21、23、36 及 mdpi、xxxhdpi 的设备矩阵配置。
- [x] 2.5 在修改对应生产文件前，保存方向、图标选择、书架/RSS/欢迎页、通知、快捷方式、透明页面、对话框、漫画菜单和文件路径控件的治理前基线。

## 3. 兼容行为与 18 个 hint

- [x] 3.1 为旧式空白裁剪增加聚焦测试，覆盖 U+0000、U+001F、U+0020、不换行空格和全角空白，确认 Kotlin 默认 `trim()` 会产生可观察 RED。
- [x] 3.2 实现保留 `it <= U+0020` 语义的命名字符串扩展，仅在该扩展实现处精确抑制 `TrimLambda`，替换全部 17 个调用点。
- [x] 3.3 为 `bookshelf`、`lastRead`、`readAloud` 快捷方式增加稳定来源标记、实际消费与防重复报告测试，确认普通启动和 Intent 重放不报告使用。
- [x] 3.4 在三个真实入口消费对应 Intent 后调用快捷方式使用报告，消除 `ReportShortcutUsage` 并完成模拟器与授权 Debug 真机烟测。
- [x] 3.5 为七个 launcher alias、旧 `launcherIcon` 字符串、未知值 fallback 和对话框配置重建增加聚焦测试。
- [x] 3.6 增加与字符串 entry value 并行的类型化 mipmap 资源数组，改造图标偏好和 Bundle 恢复逻辑，消除两个 `getIdentifier` occurrence 且不改变持久化值。
- [x] 3.7 为九个 `screenOrientation="behind"` 页面完成 API 21/36、横竖屏、手机/平板、多窗口和返回栈验证，在每个精确 manifest 位置记录保留理由与重启条件。
- [x] 3.8 重跑本批聚焦测试和 lint 对账，确认 `TrimLambda`、`ReportShortcutUsage` 与两个资源反射项目均不可见，九个方向项目只由有证据的局部抑制消除。

## 4. 位图、重复图标与死资源

- [x] 4.1 记录六张生产位图在治理前的源像素、各密度 decode 像素、显示边界和内存占用，并验证通知、快捷方式、书架、RSS、欢迎页与错误图夹具可重复。
- [x] 4.2 将 `icon_read_book`、默认封面、Legado fallback、加载错误图、RSS 源图和 RSS 文章图迁入 `drawable-nodpi`，在确有需要的视图显式保持尺寸与缩放。
- [x] 4.3 在 API 21/36、mdpi/xxxhdpi 和日夜主题下比较迁移前后图片、通知与快捷方式证据；任一未批准差异发生时回退该资源并改用精确路径抑制。
- [x] 4.4 保留字节相同但语义不同的 RSS fallback 与 xxxhdpi launcher 图，在精确二进制资源路径记录 `IconDuplicates` 抑制、理由和重启条件。
- [x] 4.5 删除无生产引用的 `dialog_progressbar_view.xml` 和全部支持 locale 中的 `del_all`，同步退役仅为两个死资源存在的旧测试断言。
- [x] 4.6 在 API 21、23、36 验证 RSS 点击、长按、焦点和 ripple，保留 foreground 并在该根布局精确记录 `UnusedAttribute` 抑制。
- [x] 4.7 重跑资源测试、相关页面 golden 和 lint 对账，确认 `IconLocation`、`IconDuplicates`、`UnusedResources`、`UnusedAttribute` 均不可见且没有新增资源或翻译问题。

## 5. 41 个 Overdraw

- [x] 5.1 从开始报告生成 41 个 Overdraw 精确位置清单，按纯色重复背景、选择器 ripple、透明遮罩、主题兜底和内容背景分类并保持数量守恒。
- [x] 5.2 对 golden 证明与窗口或父容器背景像素等价的根背景逐个删除，不改动内部子视图、主题或点击反馈。
- [x] 5.3 对承担选择器、透明遮罩、主题兜底或内容语义的根背景逐布局添加 `tools:ignore="Overdraw"`，并在相邻中文注释或账本记录保留理由和重启条件。
- [x] 5.4 在 API 21/23/36、日夜主题、手机/平板及透明窗口场景运行全部受影响页面 golden，检查颜色、透明度、ripple、对话框不透明性和点击区域。
- [x] 5.5 重跑 lint 并逐位置对账，确认 41 个原始 occurrence 全部进入 `FIXED` 或 `SUPPRESSED_WITH_REASON`，`Overdraw` 当前数量为零。

## 6. 剩余布局与 launcher 矢量图

- [x] 6.1 为漫画菜单固定夹具增加 SeekBar、前后章按钮位置、背景和点击区域断言；仅在等价时扁平化无用父布局，否则精确抑制 `UselessParent`。
- [x] 6.2 为 FileManageActivity 与 FilePickerDialog 的路径面包屑增加图标尺寸、文字颜色、整行点击和 View Binding 契约；仅在等价时改用 compound drawable，否则精确抑制 `UseCompoundDrawables`。
- [x] 6.3 保存 launcher3 adaptive、monochrome 与多密度 golden，确定像素差异阈值；仅在阈值内确定性简化长 path，否则对该 vector 路径精确抑制 `VectorPath`。
- [x] 6.4 重跑聚焦测试、golden 和 lint 对账，确认三个 lint ID 均不可见且没有 launcher、漫画阅读或文件导航回归。

## 7. 43 个版本提示

- [x] 7.1 对 Gradle wrapper 与 AGP application/library/test 的四个 `AndroidGradlePluginVersion` occurrence，在对应版本声明处增加兼容理由、精确抑制和独立升级重启条件。
- [x] 7.2 对 AppCompat、ConstraintLayout、Core、Fragment、Material、Media、Collection、Annotation 与 AndroidX Test 的 14 个 `GradleDependency` occurrence 逐坐标记录精确抑制。
- [x] 7.3 对 Kotlin、构建插件、解析、网络、协程、二维码和 Rhino 的 `NewerVersionAvailable` occurrence 逐版本声明记录精确抑制，并保留现有最低 Android 固定说明。
- [x] 7.4 对 Glide 核心、编译器、KSP、OkHttp、RecyclerView、AVIF 和 Compose 坐标逐版本声明记录精确抑制，不改变 beta 或运行库版本。
- [x] 7.5 对比 `gradle/libs.versions.toml` 和 wrapper，确认所有版本值完全未变；核对 Dependabot 普通更新、安全更新和固定依赖 ignore 规则未被削弱。
- [x] 7.6 强制重跑 lint，确认三个版本提示 ID 当前均为零，Apply 当天新增坐标也已逐项对账而非全局隐藏。

## 8. 零 issue CI 门禁

- [x] 8.1 为 XML 零 issue 断言脚本增加空报告、warning、hint、缺失文件、错误根节点和损坏 XML 的测试，并观察旧工作流缺少该门禁的 RED。
- [x] 8.2 实现标准库 XML 解析脚本：报告无效或任意 `<issue>` 存在时按 severity/ID 输出摘要并返回失败，零节点时返回成功。
- [x] 8.3 在 Android lint 步骤后接入零 issue 断言，保留 `if: always()` 报告上传和现有三个高风险 fatal ID。
- [x] 8.4 更新仓库工作流契约测试，锁定 lint 命令、报告路径、零 issue 断言、artifact 上传和 `checkDependencies = true`，不得放宽其他必需检查。
- [x] 8.5 本地运行工作流脚本测试并用合成 warning/hint 报告证明门禁失败，再用最终零 issue 报告证明通过。

## 9. 维护账本与单一变更收口

- [x] 9.1 更新 `docs/maintenance-baseline.md`，为 hint 增加处置状态并记录每个 ID 的开始数量、`FIXED`、`SUPPRESSED_WITH_REASON`、最终数量和证据。
- [x] 9.2 将全部目标 ID 的 `DEFERRED`、`PENDING_REVIEW` 和当前可见数量更新为零，验证所有 occurrence 数量守恒；不得把抑制描述为依赖升级或风险消失。
- [x] 9.3 更新 `docs/maintenance-risk-reduction-roadmap.md`，把多个 OpenSpec 兼容域改为本变更的一次 Apply 和内部串行批次，并保留失败即停止与设备数据边界。
- [x] 9.4 创建本次实施证据文档，记录开始/结束报告哈希、测试结果、golden 差异、精确抑制、未接触正式版数据的证明以及任何失败重试。

## 10. 一次 Apply 最终验证

- [x] 10.1 使用 JDK 17 和固定 SDK 强制运行 `:app:testAppDebugUnitTest`、`:app:lintAppDebug`、`:app:assembleAppDebug`，记录任务、测试、APK 和三份 lint 报告结果。
- [x] 10.2 在 API 21、23、36 AVD 完成批准的日夜主题、密度、旋转、手机/平板和多窗口矩阵，保存可审计摘要及 diff artifact。
- [x] 10.3 在授权 Debug 真机完成 launcher alias、通知、三个动态快捷方式和方向烟测，确认没有读取、清理或改变正式版个人数据。
- [x] 10.4 运行仓库脚本测试、`openspec validate --all --strict` 和 `git diff --check`，检查 `git diff --stat` 与工作树没有无关生成文件。
- [x] 10.5 核对最低 SDK、Room schema、规则格式、导入 URI、备份、依赖版本、签名和 Web 产物均未发生范围外变化。
- [x] 10.6 解析最终 lint XML，确认 error、warning、hint 和 `<issue>` 总数全部为零，账本无 `DEFERRED`/`PENDING_REVIEW` 后才将本变更标记为完成。
