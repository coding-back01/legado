# Android lint 清零实施证据

## Apply 前置预检

预检时间：2026-09-02 01:04:48 +0800。

### Git 起点与工作区边界

- 起始分支：`master`
- 起始提交：`826a49d2199b571fc8533b9ae0005fd8e0d70369`
- `git status --short` 仅显示 `?? openspec/changes/eliminate-android-lint-findings/`；这是本变更的 OpenSpec 工件目录，没有发现范围外的用户改动。
- 后续实施只允许修改本变更覆盖的 Android lint、测试、CI 和维护文档范围，不清理或重排无关文件。

### 固定构建环境

- `JAVA_HOME`：`/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`
- Gradle Launcher JVM：Oracle JDK `17.0.17`
- `ANDROID_HOME` 与 `ANDROID_SDK_ROOT`：`/Users/back/Library/Android/sdk`
- system image：API 21、23、36 均为 `default/arm64-v8a`
- AVD：`Legado_API_21`、`Legado_API_23`、`Legado_API_36`
- API 23 镜像与 AVD 在本次预检中补齐；`Legado_API_23` 已无窗口冷启动成功，设备报告 SDK `23`、ABI `arm64-v8a`，随后已正常关闭。

### Debug 真机边界与当前阻塞

- 关闭预检模拟器后实时执行 `/Users/back/Library/Android/sdk/platform-tools/adb devices -l`，设备列表为空。
- 当前没有一台可丢弃或由维护者明确授权的 Debug 真机，无法证明后续验证只针对 `io.legado.app.debug`，也无法证明不会读取、清理或改变正式版个人数据。
- 因硬前置条件未满足，本次 Apply 在修改生产代码、资源、构建配置或工作流前暂停；任务 1.3 及后续任务保持未完成。

### Debug 真机恢复确认

- 2026-09-02 10:22:48 +0800，维护者回复设备已连接；实时 `adb devices -l` 确认一台已授权设备为 USB `device` 状态。设备序列号不写入版本库。
- 设备为 Hisense HLTE556N，API 30，已安装 `io.legado.app.debug` 与测试包，可用于本变更的 Debug 真机验证。
- 设备同时安装 `io.legado.app.release`。该正式包及其数据被明确列为禁止目标：本次实施不得读取、启动、清理、覆盖或卸载它，所有 ADB 安装、启动、停止和测试操作只允许指向 `io.legado.app.debug` 或 `io.legado.app.debug.test`。
- 任务 1.3 的阻塞已解除，继续生成强制 lint 事实基线。

## 强制 lint 起始基线

- 命令使用固定 JDK 17 与 Android SDK，执行 `./gradlew :app:lintAppDebug --rerun-tasks --no-daemon --warning-mode all --console=plain`。
- Gradle 结果：`BUILD SUCCESSFUL`，耗时 3 分 14 秒，103 个任务全部执行。
- XML 根节点为 `<issues>`，共 126 个 issue：108 个 `Warning`、18 个 `Hint`、0 个 `Error`。
- ID 数量：`AndroidGradlePluginVersion` 4、`DiscouragedApi` 11、`GradleDependency` 14、`IconDuplicates` 1、`IconLocation` 6、`NewerVersionAvailable` 25、`Overdraw` 41、`UnusedAttribute` 1、`UnusedResources` 2、`UseCompoundDrawables` 1、`UselessParent` 1、`VectorPath` 1、`ReportShortcutUsage` 1、`TrimLambda` 17。
- 事实基线与 OpenSpec 计划输入的 108 warning/18 hint 一致；用户最初观察到的 111 warning 不作为本次固定命令的事实源，后续仍会把重跑新增项自动纳入。

### 起始证据 SHA-256

| 文件 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `0dc576afa9b90210b51830304a8b83c3d8d1cc1fb77349c1f575713ea0ddb6f6` |
| `lint-results-appDebug.html` | `030c142c9c7ecccd6bec7c90a01d22e480d648d4245f79017240e0f61a60e4eb` |
| `lint-results-appDebug.txt` | `9455cbec63425f41170ac63bddf058e0cb7ce6708084443fc269b0807afbd164` |
| `lint-gradle-console.txt` | `02c5151cce9cb06f71c0fcf9210e53cec435607453c1e6edc80d4823b9ec2b23` |
| `lint-occurrences.tsv` | `d2a0b76b36fa7eb14a7a761dc0c63e4620e72c62efcc5b51fa21777b0b707eed` |

完整 XML、HTML、文本、控制台日志和展开位置的 TSV 清单保存在 `evidence/baseline/`。

### occurrence 批次覆盖

- `lint-disposition-plan.tsv` 将 14 个起始 lint ID 映射到任务 3.1—7.4 的具体处置批次，起始数量合计 126。
- 基线没有计划输入之外的新 ID；所有 occurrence 均可经 lint ID 映射到任务批次，没有另建 OpenSpec 变更。
- 后续每次重跑若出现新 ID 或数量漂移，必须先更新同一清单与映射再继续。

## XML 清单工具 RED/GREEN

- 在 `scripts/android_lint_report.py` 不存在时先运行 5 个契约测试，观察到 5 个失败，证明旧工作区没有清单工具。
- 实现只读 XML 解析和 TSV 输出后，同一组 5 个测试全部通过。
- 覆盖范围包括报告缺失、损坏 XML、错误根节点、空 `<issues>`、多个 severity/ID 汇总以及一个 issue 的多个位置展开。

## Golden 基础设施 RED/GREEN

- 使用现有 `AndroidJUnitRunner` 与 `UiAutomation.takeScreenshot()` 捕获平台渲染结果；比较器按通道容许 2 级差异、像素差异比例阈值为 `0.002`，失败时输出 current、expected 和四分之一尺寸的 magenta diff，不新增第三方依赖。
- 普通模式只从 `app/src/androidTest/assets/golden/<profile>/` 读取预期图片；只有脚本显式传入 `--update` 时才设置 `legado.golden.update=true`，候选仅输出到 `app/build/golden-updates/`，脚本和测试代码均不包含写回版本化 assets 的路径。
- `scripts/android-golden-matrix.tsv` 固定 API 21、23、36、light/dark、mdpi/xxxhdpi、phone/tablet/multiwindow 配置；runner 使用精确 framebuffer、窗口和密度校验，固定简体中文、字体比例、动画与无个人内容 Debug 夹具，并只允许在可丢弃模拟器上清理 Debug 包。
- 聚焦契约测试 `GoldenInfrastructureContractTest` 于 2026-09-07 通过；`api23-mdpi-light-phone --update` 随后运行 `OK (1 test)` 并导出 9 张候选图，复制接受后普通模式日志 `app/build/golden-run/api23-mdpi-light-phone/instrumentation.txt` 再次为 `OK (1 test)`。
- 稳定化过程中保留并处理了以下失败：逻辑窗口与 AVD framebuffer 不一致造成黑边和 1px 漂移；API 23 的 `sys.boot_completed` 空值造成错误超时；WindowManager 尚未就绪造成 `wm` 设置失败及一次图标解码 OOM；Activity 重建造成导航栏 inset 漂移；首次沉浸式系统提示造成整图 dim 差异。最终分别通过精确 `-skin`、服务与显示参数校验、单 Activity 夹具序列、焦点恢复和预确认沉浸式提示消除环境噪声，未放宽 `0.002` 阈值。

## 治理前视觉基线

- 在修改任何生产代码、资源、构建配置或工作流前，依次捕获 `direction`、`icon_catalog`、`bookshelf`、`rss`、`welcome`、`transparent`、`dialog`、`manga_menu` 与 `file_path` 九个无个人内容夹具。
- API 21 的 mdpi/xxxhdpi 与日夜主题、API 23 的 mdpi 与日夜主题，以及 API 36 的 mdpi/xxxhdpi 与日夜主题、mdpi 平板和 xxxhdpi 多窗口共 12 个 profile 均保存了每组 9 张版本化 PNG；每个 profile 都在显式 `--update` 之后再次以普通只读模式运行并得到 `OK (1 test)`。
- API 23 的 `api23-xxxhdpi-light-phone` 与 `api23-xxxhdpi-dark-phone` 无法生成 PNG：两者都在 inflate `item_bookshelf_list` 的 `CoverImageView` 时解码生产资源 `image_cover_default`，尝试分配 `34,560,012` 字节并触发 `OutOfMemoryError`。这不是被接受的测试基础设施差异，而是待任务 4.2 修复的治理前 RED 行为；资源迁移后必须为这两个 profile 生成 golden 并以普通模式复验，才能完成资源批次。
- 两份 API 23 xxxhdpi 失败日志分别为 `app/build/golden-run/api23-xxxhdpi-light-phone/instrumentation.txt` 与 `app/build/golden-run/api23-xxxhdpi-dark-phone/instrumentation.txt`，SHA-256 分别为 `69f9fd196778a3678586f3bb9bda5bd4b785e8a38552e919261548b8f56320be` 和 `e635dbe68248dc98b5e13e29697b5619fd09b7653ff838b436ce6d240624e455`。这些构建产物不进入 Git，摘要作为可审计证据保留。
- 任务 2.5 的治理前事实由 108 张通过的 PNG 与两份高密度 OOM 失败基线共同组成；后者清楚记录了生产资源在旧系统有限 heap 下的不可渲染状态，没有伪造缺失截图或放宽比较阈值。

## 旧式空白裁剪 RED

- 新增 `LegacyWhitespaceTrimTest`，以同一契约覆盖 U+0000、U+001F、U+0020、不换行空格 U+00A0 和全角空白 U+3000；旧式期望只裁剪 `<= U+0020` 的边界字符。
- 先让候选实现直接调用 Kotlin 默认 `trim()`，聚焦运行 `:app:testAppDebugUnitTest --tests io.legado.app.utils.LegacyWhitespaceTrimTest`，得到 1 个测试、1 个失败。汇总失败明确列出 U+0000、U+00A0 与 U+3000 三处语义差异，证明不能按 lint 建议直接删除 lambda。
- RED 控制台日志保存在未提交构建目录 `app/build/lint-cleanup-evidence/task-3.1-default-trim-red.txt`，SHA-256 为 `ab44f8cebb59d6e99c145c43810df029274b1da1882d5c6515f03dcbd0df9fda`。

## 旧式空白裁剪 GREEN

- 在 `StringExtensions.kt` 增加 `trimAsciiControlAndSpace()`，实现仍为 `trim { it <= ' ' }`，仅在该函数上精确 `@Suppress("TrimLambda")`；函数名直接表达 U+0000—U+0020 的兼容边界。
- 起始报告中的 17 个调用点已全部改用命名扩展；生产源码中原始 lambda 只剩扩展实现本身一处，调用点清单 SHA-256 为 `9498b3d909ae6181d77f781b6f59e2b5a5536c9ae6cdd9afdcdc5431eaf59897`。
- 同一聚焦测试转绿，`:app:testAppDebugUnitTest --tests io.legado.app.utils.LegacyWhitespaceTrimTest` 为 `BUILD SUCCESSFUL`；控制台日志 SHA-256 为 `2e072240c9c0a604f36ac4b53d611e185f6c4b039843ddbafb699f00cf617fa7`。

## 动态快捷方式消费契约

- 将既有 `bookshelf`、`lastRead`、`readAloud` 三个 ID 收口为稳定常量，并在三个真正到达目标入口的 Intent 上同时写入内部来源、shortcut ID 和未消费标记；`lastRead` 的父栈 MainActivity Intent 不携带标记，避免误报 `bookshelf`。
- `ShortcutLaunch` 将来源匹配、入口 ID 匹配和已消费状态建模为可独立测试的状态转换；只有来源和入口同时匹配且尚未消费时才返回一次 ID，随后状态变为已消费。
- 聚焦测试覆盖既有 ID 不变、普通启动不消费、错误入口不消费以及同一 Intent 状态重放不重复消费；连同旧式裁剪回归测试共 5 个测试通过，控制台日志 SHA-256 为 `df554555841d0aaf37f77fea375e913ce9d5e8a61cbb97c9dbc8ca7b0844f053`。

## 动态快捷方式入口实现与验证

- MainActivity 仅在消费 `bookshelf` 标记后切回书架并上报；ReadBookActivity 仅在 `initData` 入口完成后消费并上报 `lastRead`，同时在 `onNewIntent` 更新 Activity Intent；SharedReceiverActivity 仅在触发朗读动作后上报已匹配的 `readAloud`。三处均先完成对应动作，再调用统一的 `ShortcutLaunch.report`。
- JVM 入口顺序和状态测试以及 Android 测试 APK 编译成功，控制台日志 SHA-256 为 `51e312e2bc956bfc26383229ead8a64be3c3a35313e8fd102aae5aa2de81ebe5`。
- API 36 可丢弃 AVD 实际调用 `ShortCuts.buildShortCuts()` 后读回系统动态快捷方式列表，确认三个 ID 齐全且每个目标 Intent 的来源标记与自身 ID 匹配；普通 Intent、不重复消费和系统登记共 3 个设备测试全部通过，instrumentation 日志 SHA-256 为 `ad6c07126111aa4d34ad2798a664e6f29c26d01b481586582e75bc63d297c4bd`。
- 授权真机恢复连接后，在 Hisense HLTE556N（API 30）只安装并测试 `io.legado.app.debug` 与 `io.legado.app.debug.test`；同一组 3 个设备测试全部通过，instrumentation 日志 SHA-256 为 `632a327d3e22aa32dfa4ea196902a218ef2502e518924b3253edbafcbb074b98`。
- 真机分别通过带稳定来源标记的 Debug Intent 启动 MainActivity、ReadBookActivity 和 SharedReceiverActivity，三个入口均成功到达且保持 Debug 进程；入口烟测日志 SHA-256 为 `3f0a71af728dce0d13e85186ad0b515e205f1479f80aca360de6a2e4288a958d`。
- MainActivity 的厂商 `am start -W` 没有正常返回。诊断时曾执行一次 `dumpsys activity activities io.legado.app.debug`，厂商系统输出意外包含正式包组件名称与任务栈元数据；本次未读取正式包文件、数据库或个人内容，也未启动、停止、清理、覆盖或卸载正式包。发现后不再使用可能暴露全局任务栈的查询，原始设备日志仅保留在未提交的 `app/build/` 中。
- 强制重跑 lint 后 `ReportShortcutUsage` 与 `TrimLambda` 均为 0。Apply 当天由 Debug golden 夹具引入的一个 `UseKtx` 和一个 `DiscouragedApi` occurrence 已分别通过 KTX `createBitmap` 与删除等价的 `screenOrientation="unspecified"` 修复；处置计划同步纳入了两者。
- 本批最新 lint XML 共 107 个 `Warning`、0 个 `Hint`、0 个 `Error`，SHA-256 为 `8f9212de709ca11951ea363a0de04120cc334fb443412b5dc249281b3f3210d4`；控制台日志 SHA-256 为 `d2d03a49d6b86d5b9a5801818268feeb36dff863915bbc21e608bcee314b01a0`。`ReportShortcutUsage`、`TrimLambda`、`UseKtx` 均不可见，`DiscouragedApi` 剩余 11 个原始 occurrence，按任务 3.6—3.8 继续处理。

## Launcher 图标兼容契约 RED

- 新增聚焦契约测试，锁定默认 WelcomeActivity 与 Launcher1—Launcher6 共七个入口、`ic_launcher` 与 `launcher1`—`launcher6` 七个旧字符串值及其顺序、默认值和 `entryValues`，这些治理前契约均通过。
- 同一测试要求运行时使用平行的类型化 mipmap 数组、对话框 Bundle 保存 `IntArray`、未知旧值回退默认图标且源码不再调用 `getIdentifier`；治理前运行共 3 个测试，其中上述两个迁移要求如预期失败，为任务 3.6 提供 RED。

## Launcher 图标类型化资源 GREEN

- 保留 `@array/icons` 中七个旧字符串及 `launcherIcon` 默认值，新增顺序一一对应的 `@array/launcher_icon_resources`；偏好运行时从 typed array 读取编译期 mipmap ID，不再按名称反射资源。
- 图标选择对话框通过 Fragment arguments 保存和恢复 `IntArray`，配置重建不依赖资源名称；已知旧值保持原索引，未知值和空值仅在显示层回退默认图标，不主动改写持久化字符串。
- launcher 图标契约与选择逻辑共 6 个聚焦测试全部通过，控制台日志 SHA-256 为 `80ec330cfa3224549486a5b13af7fc446723f50780d6e1954d4b5681e4d54664`；生产源码中两个 `getIdentifier` 调用均已删除，最终 lint occurrence 由任务 3.8 强制对账确认。

## 九个 behind 页面方向证据

- JVM manifest 契约锁定九个页面仍为 `screenOrientation="behind"`，且每个 activity 节点都有精确 `tools:ignore="DiscouragedApi"`；相邻中文注释逐页记录 Android 16 以下兼容理由和“九页完成自适应布局后重新评估”的重启条件。契约测试与 Android 测试 APK 构建通过，日志 SHA-256 为 `9c950acdbcb089fb84e7dba3a99e926cc9a52c146c2a005ded9d321fdb9737a5`。
- 设备测试读取已安装 Debug 包的九个 `ActivityInfo.screenOrientation`，并使用空数据 BookshelfManageActivity 代表真实 `behind` 页面，分别从横、竖方向 Debug 父页启动，记录子页实际窗口方向并验证返回栈恢复；局部 lint 元数据不改变运行时属性。
- API 21 手机、API 36 手机、API 36 平板及 API 36 xxxhdpi freeform 多窗口四个 profile 均为 `OK (3 tests)`，日志 SHA-256 依次为 `e09f43d7f9999f4f1d026edb5a1e2ff5d7c1bbd9e2922e803466c7a950116754`、`a195d5863481110b03f48b8d3ff6e373cb5d5f7b2992c6b2b8165de732ee98c4`、`34219d76c829eb6151adb46f43e89c740346d240d69eb0e646159b25e89aade2`、`94925665dd96383d57b68b43b58ce0fe3b8227f1e7ac5b7f5f7494dc75a5e2ac`。多窗口 profile 在重启前启用 freeform 与强制可调整，并实际断言 `isInMultiWindowMode`，不再以窄窗口尺寸冒充多窗口。
- 首次 Android 测试编译误把 `ParcelFileDescriptor` 当作直接可读流，改用 `AutoCloseInputStream` 后通过。API 21 初始断言假定子页窗口必然继承父页，连续两次都观察到横屏父页启动后子页仍为竖屏、返回后父页恢复横屏；测试因此改为如实审计平台实际解析结果，不把单个 AVD 外推为所有 ROM。多窗口首次因 freeform 未启用失败，补齐系统能力并重启后转绿；这些失败没有被跳过或通过放宽 lint 范围隐藏。

## 行为与 hint 批次 lint 对账

- 旧式裁剪、快捷方式、launcher 图标选择和方向 manifest 共 11 个 JVM 聚焦测试全部通过，日志 SHA-256 为 `74be5075532afa5dd6f624c9ef48a235673a599688de6b48c77d5b2af95d4816`。
- 使用固定 JDK 17 与 SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，103 个任务全部执行并 `BUILD SUCCESSFUL`；控制台日志 SHA-256 为 `c5521bb32d76cdc8ce38b5a355f23f61dc82b54886bc7651289afc3a782f9ce7`。
- 最新 XML SHA-256 为 `0e1ad18b787b0389c634019930fa6bc1f7e020153e7c0ea5256da2d5b7b755a1`，共 96 个 `Warning`、0 个 `Hint`、0 个 `Error`。`TrimLambda`、`ReportShortcutUsage`、`DiscouragedApi` 均为 0，`IconListPreference.kt` 不再包含 `getIdentifier`；展开 occurrence 清单 SHA-256 为 `4fff61814c0c13d1734d4354ae7979485ab92204d5553f913139fd0f246090e2`。

## 六张生产位图治理前事实

六张文件均位于未限定密度的 `res/drawable`，Android 将其按 mdpi 基准缩放。设备测试逐张解码、断言像素与 `byteCount` 后立即回收；API 21 mdpi 与 API 36 xxxhdpi 均为 `OK (1 test)`。mdpi TSV SHA-256 为 `184e6bfb194c7d37f192abb27236f15139aaaa0c4697b910b2de0d2efc086450`。

| 资源 | 源像素 | mdpi 解码/内存 | xxxhdpi 解码/内存 | 生产显示边界 |
|---|---:|---:|---:|---|
| `icon_read_book` | 200×200 | 200×200 / 160,000 B | 800×800 / 2,560,000 B | 欢迎页 120×120 dp；通知与动态快捷方式由系统约束 |
| `image_cover_default` | 600×900 | 600×900 / 2,160,000 B | 2400×3600 / 34,560,000 B | 书架列表 66×90 dp、网格与详情封面按各自固定视图裁剪 |
| `image_legado` | 192×192 | 192×192 / 147,456 B | 768×768 / 2,359,296 B | RSS 源图标 50×50 dp |
| `image_loading_error` | 512×512 | 512×512 / 1,048,576 B | 2048×2048 / 16,777,216 B | 图片加载错误占位按目标 ImageView 约束 |
| `image_rss` | 500×500 | 500×500 / 1,000,000 B | 2000×2000 / 16,000,000 B | RSS 源图标 50×50 dp |
| `image_rss_article` | 500×500 | 500×500 / 1,000,000 B | 2000×2000 / 16,000,000 B | RSS 文章缩略图 110×68 dp、`centerCrop` |

- xxxhdpi TSV SHA-256 为 `63a33b4ae3139f75c4d7aeef8235e65350a054a98276ea0015f70c633a9419b8`。默认封面的 34,560,000 B 设备测量与 API 23 两个 xxxhdpi profile 先前的 34,560,012 B 分配失败（含运行时分配开销）吻合。
- 文件 SHA-256 依次为 `47e93cb2a4668cc1dc969f762e17a334e22be635fabb8759a0d3b87290132e1c`、`fa103f538daf551421956f6286ed4411cdfedaa679f45e17e291c5445bb12734`、`514f0a45caea8ebb96d9f3a5afce92efb669dad89b210b81602c33fc55a681d5`、`b0e325db18a5102e75f54c335d66ed355bb9905d4cfe3ec9fec4a66988010157`、`c8b06fb53c5b0bb1cc2071a6bafb0278994a2e8525a507adfdcd4887e2bd5dc5`、`4e34d74635c619605602f21d4bfcbb0686277fe7d2b0b9e9a8c210a2580e8ccf`。通知、快捷方式、书架、RSS、欢迎页和错误图均已包含在治理前 `icon_catalog`、`bookshelf`、`rss`、`welcome` golden 夹具中。

## 六张位图迁入 drawable-nodpi

- 六张原文件逐一从 `res/drawable` 移到 `res/drawable-nodpi`，资源名、文件字节和所有调用点均保持不变；生产视图已有 120×120 dp、66×90 dp、50×50 dp、110×68 dp 及相应 `fitCenter`、`centerCrop` 约束，本批未引入额外布局尺寸变化。
- API 23 xxxhdpi 设备测试确认六张资源的 `TypedValue.density` 均为 `DENSITY_NONE`，全部按源像素解码；默认封面从治理前申请约 34.56 MB 降到 2.16 MB，测试为 `OK (1 test)`。TSV SHA-256 为 `41733bca75d506f4c4da6c0b1f93056cfde86a1c45139fb4f9b5539a9bd6e32e`，instrumentation 日志 SHA-256 为 `220e1a09de68c8c681d51e4c6929afd4ee5aa78f457bfe717bf42e344cbb3fff`；先前两个 API 23 xxxhdpi OOM 的资源根因已转绿。

## RSS 根项交互与 foreground

- 新增设备测试在真实 `item_rss` 根布局上安装点击、长按监听并各触发一次，断言根项可聚焦；API 23 与 API 36 另外断言 foreground 为可响应状态的 `RippleDrawable`，按下与释放时 `state_pressed` 正确进入和退出。
- API 21 初次运行在模拟器默认 touch mode 下使用普通 `requestFocus()` 失败；测试改用平台 `requestFocusFromTouch()` 进入真实非触摸焦点路径后，API 21、23、36 三个 mdpi profile 均为 `OK (1 test)`。最终日志 SHA-256 依次为 `b6a6659f3a73429a02086cae32dc57d742b195a5afa99c92b4246af32521d0b4`、`ce36aa85f217f53ff95875fef91caa246ff3c871f623e2e3642415f50297b062`、`c5d15f481a44243abd88f4ac400e349c78baff876c6ee8cc56749c945bcdbb69`。
- `item_rss.xml` 根节点保留 `android:foreground="?android:attr/selectableItemBackground"`，并只在该节点添加 `tools:ignore="UnusedAttribute"`；相邻中文注释说明 API 23+ 依赖整项 ripple、API 21/22 使用可聚焦背景，并以最低版本升至 23 或拆分版本布局为重新评估条件。

## 死资源删除

- 删除无任何生产引用的 `res/layout/dialog_progressbar_view.xml`，并从 `RtlLayoutContractTest` 退役仅为该死布局存在的间距断言；其他 RTL 合同保持不变。
- 删除 base、简中、繁中、日语、葡萄牙语、西班牙语和越南语共 8 个 locale 的未使用 `del_all` 字符串，并从 `BlockingTranslationResourceTest` 的阻断键集合移除该键；实际 RSS 菜单继续使用独立资源 ID `menu_del_all` 和既有删除逻辑。
- `BlockingTranslationResourceTest` 与 `RtlLayoutContractTest` 聚焦执行后 `:app:testAppDebugUnitTest` 为 `BUILD SUCCESSFUL`；静态复查确认 `app/` 中不再存在 `dialog_progressbar_view`、`R.string.del_all`、`@string/del_all` 或 `name="del_all"`。

## 重复图标的精确路径抑制

- `drawable-nodpi/image_legado.png` 是 RSS 无封面 fallback，`mipmap-xxxhdpi/ic_launcher.png` 是旧系统 xxxhdpi launcher 图标；两者 SHA-256 虽同为 `514f0a45caea8ebb96d9f3a5afce92efb669dad89b210b81602c33fc55a681d5`，但资源类型、调用入口和密度语义不同，互相复用会破坏相应合同。
- `app/lint.xml` 的 `IconDuplicates` 条目只忽略上述两个精确二进制路径，中文注释记录保留理由，并以 RSS fallback 或 launcher 资源体系重构为重新评估条件；没有按 ID 全局关闭其他文件的重复检测。
- 固定 JDK 17 与 SDK 强制重跑 `:app:lintAppDebug --rerun-tasks` 后 `BUILD SUCCESSFUL`，XML SHA-256 为 `8dc255e443000b81956b8705da19a5c8d32784797552fff009fb120408de7c8c`，清单共 89 个 warning、0 个 hint、0 个 error，`IconLocation` 与 `IconDuplicates` 均为 0；展开清单 SHA-256 为 `a4e5b6a55003b7e480c73106c4800f55295e8e9956ce30efdf01f7eb4b2b703f`。

## 位图迁移后的视觉与解码验证

- API 21 与 API 36 的 mdpi、xxxhdpi、日夜主题共 8 个必需 profile，以及用于补齐治理前 OOM 缺口的 API 23 xxxhdpi 日夜 2 个 profile，均以普通只读模式完成 9 个夹具比较并返回 `OK (1 test)`。10 份最终 instrumentation 日志 SHA-256 依次为 `9493a546b800b25e339dba380e635737dbadc8329d810fdaac4089a201ce7333`、`e039d7cf9f81c4b08181b4b094035b3800874833520d64e1a7f8beade7fe3d21`、`1521bbbe3f517e738345191807737c7fb8d83fcb408c20b26375c41d7529bb35`、`e0ebec66f4879f9654edd7be3179c2c8795941525474f525bf43e898be961097`、`4fa4d4150427f026ec1bb54be7bc5874ec9c6ffa28e4b6e4f543204c5eacca4a`、`e4bff0727e6d807e087f31d7a18c464e68f318cc0d1a710bb177897da3ac5126`、`d766587d6b5ba2e187e31a19f25aaf4933c3d181119bbed5db80797b6871299b`、`26bddccb619d1ef599fb985ee1525d8af7c757919aed6ce3dc93d290990eb771`、`495db0f8d44efaee39cbaa988ddc3ac44e41a2071e2deb34db27ae135d1ecf85`、`66b684c306d30fbfb994055c5eecdf290b4d553d6a5ec503f8e45f288a82fe3f`。
- 初次只读比较在 API 21 xxxhdpi 与 API 36 的 mdpi、xxxhdpi `icon_catalog` 上观察到 2.227% 至 4.316% 的差异；像素审查确认差异只来自 Debug 夹具不再把小于 240 px 的位图强制放大，通知、快捷方式和 fallback 图形内容不变，边缘反而避免了旧式四倍解码后的二次缩放。候选图只能通过显式 `--update` 输出，审查后仅复制 6 个受影响的 `icon_catalog.png`，未覆盖同 profile 的其他预期图。API 21 xxxhdpi 的 `bookshelf` 与 `welcome` 差异分别不超过 0.056% 和 0.155%，均低于既定 0.2% 阈值；其余受检页面像素相同。API 21 内部存储回退曾使失败 diff 无法拉回，测试脚本增加只读 `run-as` 回退后可取得 current、expected、diff，未改变测试阈值或生产行为。
- API 23 xxxhdpi 两个 profile 在显式更新目录中生成此前因 34.56 MB 默认封面解码而缺失的 18 张候选图；逐页审查日夜主题、书架封面、图标目录、RSS、欢迎页、透明页、对话框、漫画菜单和文件路径夹具后，才最小复制到版本化测试 assets，随后两个 profile 的普通只读比较均通过。
- API 21 mdpi 与 API 36 xxxhdpi 的 nodpi 解码测试均为 `OK (1 test)`，TSV SHA-256 分别为 `d9c41b19e1a84f5deb261496e70d8155e63398f9db9323310811ac50763dba79`、`41733bca75d506f4c4da6c0b1f93056cfde86a1c45139fb4f9b5539a9bd6e32e`，instrumentation 日志 SHA-256 分别为 `816452b344df81678b49c46d18c555ead9d1c376314b884d253d0d7a1f335089`、`8763b876ae31b28e3ba403efe6b54feb3b57a81723ffde1eb00e94e6a8b4f754`。两端六张资源均为 `DENSITY_NONE`，源像素、解码像素和内存占用一致，确认不会随设备密度膨胀。

## 资源批次最终对账

- `BlockingTranslationResourceTest`、`RtlLayoutContractTest`、`LauncherIconContractTest` 与 `GoldenInfrastructureContractTest` 聚焦执行后 `:app:testAppDebugUnitTest` 为 `BUILD SUCCESSFUL`，日志 SHA-256 为 `cf35855752422efc494744fcc31df91bcdfec9d0c5c4ea2ab1a323a7edcb968e`。
- API 21、23、36 的 mdpi light phone profile 均以普通只读模式完成 9 个夹具比较并返回 `OK (1 test)`，最终 instrumentation 日志 SHA-256 依次为 `c560f99d13dfa8f4c8b3c99180896bd9558b764fa427be3d28cfaa94c0f538c8`、`d53a538714efeff1ba6b69c61b3aa5db354e9bb1500a06440418283c0025e0d0`、`9faf49db7ab26458db8c78de8e00d9a910ccbe8cf0047904ab892f5b5301cef6`。
- 固定 JDK 17 与 SDK 强制重跑 `:app:lintAppDebug --rerun-tasks`，103 个任务全部执行并 `BUILD SUCCESSFUL`。XML、HTML、文本报告 SHA-256 分别为 `e130f93825cf0fa8a2f87be8cf49a0041786079012373305f5ae08316f9f6780`、`7b56da8d5577c5c9d32aeb66675be8e70952d2ee06e0b34a4a928c007e4b27f5`、`b8e5d98db0985a6bf0e52f795a655e27d575f74933d8e0b432bc15b531c23ae2`；展开 occurrence 清单 SHA-256 为 `e3463849045bd8995a5e04406846edd57a5fb87e742a0e1fa91dafe91d99a8b7`。
- 最新报告共 86 个 `Warning`、0 个 `Hint`、0 个 `Error`；仅剩 4 个 `AndroidGradlePluginVersion`、14 个 `GradleDependency`、24 个 `NewerVersionAvailable`、41 个 `Overdraw`、1 个 `UseCompoundDrawables`、1 个 `UselessParent` 和 1 个 `VectorPath`。`IconLocation`、`IconDuplicates`、`UnusedResources`、`UnusedAttribute` 均为 0，且没有新增资源或翻译问题。

## Overdraw 精确分类

- 治理开始报告的 occurrence 75—115 共 41 个 `Overdraw` 已逐位置写入 `evidence/overdraw-disposition.tsv`；当前报告仍保留相同的 41 个文件与背景值，没有遗漏或新增位置。
- 初始分类数量为：纯色重复背景 5、选择器 ripple 15、透明遮罩 3、主题兜底 12、内容背景 6，合计 41。删除验证发现 `activity_audio_play` 的根背景并非重复绘制：API 21 light 的 `overdraw_solid_activity` 有 2,000,645/2,073,600（96.48%）像素变化，失败输出及数值已记录。因此该位置回退并改分为内容背景，最终分类为纯色重复背景 4、选择器 ripple 15、透明遮罩 3、主题兜底 12、内容背景 7，仍合计 41；其余四个列表根背景继续执行等价删除验证。

## Overdraw 纯色背景删除

- 新增 `OverdrawSolidGoldenInstrumentedTest` 和只能显式调用的 `--overdraw-solid`、`--overdraw-solid-update` 入口；候选仍只写入 `app/build/golden-updates/`。组合 fixture 分别覆盖音频页全屏根布局，以及 `item_book_group_manage`、`item_group_manage`、`item_group_select`、`item_server_select` 四个列表根布局。
- 删除前在 API 21 mdpi 与 API 36 xxxhdpi 的日夜主题生成并审查 8 张最小 golden。删除音频页背景后的 API 21 light 比较产生 96.48% 差异，证明其底色会透过 `#50000000` 遮罩参与最终画面；该删除立即回退，并转入精确抑制。
- 四个列表根布局的 `@color/background` 已删除；恢复音频页后，四个 profile 的普通只读比较均为 `OK (1 test)`，最终日志 SHA-256 依次为 `f7af2dc33c891b588f8c042b76a3f26c19728fc2359dd07cfb19b09bf621cc38`、`d75dfb55086fa05147aa8ffb2afc3479b9553812a5c07d1c9d4eb9db63c065c7`、`1de60e7d2b50d54c0bfb42066b112c03ec3f0f92cc5d4e1c1c62fddc065672e4`、`d8a55432a4d2b9b20cc6b17c8e6cb3edf049359399c9dd1aa11eb0e24d316d02`。

## Overdraw 语义背景精确抑制

- 最终分类中的 37 个语义背景均只在对应布局根节点添加 `tools:ignore="Overdraw"`；脚本对照 `overdraw-disposition.tsv` 后得到 `planned_suppressed=37`、`actual_overdraw_ignores=37`，文件集合完全一致。
- 15 个选择器 ripple、3 个透明遮罩、12 个 Dialog 主题兜底和 7 个内容背景的保留理由与重新评估条件逐条记录在账本；没有使用 lint baseline、项目级 ID 关闭或无文件范围的忽略。四个真实删除文件不包含 `Overdraw` 抑制。
- `:app:processAppDebugResources` 与 `GoldenInfrastructureContractTest` 聚焦执行均成功，确认新增 tools 命名空间、原有 `tools:ignore` 合并和显式 golden 入口合同有效。

## Overdraw 全矩阵与交互验证

- API 21、23、36 的日夜主题、mdpi/xxxhdpi、phone/tablet/multiwindow 共 14 个普通 profile 均完成 9 个 fixture 的只读 golden 比较并返回 `OK (1 test)`。日志按 profile 排序后的 SHA-256 为：`6c466ef4f92db112d2f277950eb91ff2219a32bc5a32ab0b7b4330a6ece17b44`、`3eb25a281a82656e40913227783af4df7f4ba2836d982bc8cd5eeaee632997ff`、`49b52b394f0cc16738032604ed370f32a22806daf88ae453d73ae078ba315485`、`bf7c5f4b759623282e5780103496ab0aa753d6cd189cb0c4b3c22e626ae63213`、`e97a8d2ac4d8228582486284ca633672c42f64c8e4ebfe1552bc739bae0e8be8`、`d6b81ee3d8eb388c4f927359e050f5b9cc4d20886b706c9f396721d9f5d9964c`、`5fe014dbe5bacf83863ff766c35585fda4ea0aa704000f85f4585bc9a4268bb5`、`f19e574680780909103d668f47ab893cba45fa84a92179cac587847d5181619e`、`102d87bd71c959046b787ea06767e69eda2f1465b9feabe48e8ef892010ee401`、`538d02307926abfab04c870f15b205fee5799208b912c199238d900e145a6d1b`、`6f4478b94f78f1171bbcdd3b20eb2ad981ba3b9da7ee2635d6c3763202a4c493`、`e7390af574bb4dbb7a783a4039b796886c38567e79faab3984ba011ed3278882`、`e313e9ff484d05f5819c7cee6f715c11998d3c90a73f32b31b3221024b8d641d`、`4756020b19deaf90e0613a64cb3dd142d50763e8caf60a41256ace7f707cdca0`。
- 新增 `OverdrawInteractionInstrumentedTest` 与显式 `--overdraw-interaction` 入口，逐一覆盖 37 个保留背景：15 个选择器验证点击、长按、pressed 状态及 stateful 背景，3 个透明遮罩验证 alpha 为 1..254，19 个主题兜底或内容背景验证 alpha 为 255。API 21、23、36 mdpi light profile 均返回 `OK (1 test)`，日志 SHA-256 分别为 `1f03b59ff9ddb97d66ac04d51ae6c9fbd739bf379cb23251dbaf4f5e770785cb`、`be4dc4ea45f9242b34ba966bed54b3e31f1ece8eb5fe91664ace738af0671087`、`6c8df9da9933ab4e0090733850c325441033101b5cc98a370ced945183086452`。
- API 21 首次安装测试 APK 时 `dex2oat` 持续等待，终止后使用同一干净 profile 重试成功。随后初版交互测试直接 inflate `view_book_page.xml`，因 `ContentTextView` 必须由实现阅读回调的真实宿主创建而失败；测试改为只解析该布局根节点的主题背景，页面整体仍由普通 golden 覆盖，重编译后在三套 API 全部通过。生产阅读代码未为测试伪造回调。

## Overdraw 最终对账

- 固定 JDK 17 与 SDK 强制重跑 `:app:lintAppDebug --rerun-tasks`，103 个任务全部执行并 `BUILD SUCCESSFUL`。XML、HTML、文本报告 SHA-256 分别为 `08db4bdf9c6c9d66e648d1278ee204ef9e4c779a32e0281edf5d6ae73de50397`、`ccda5762dac73c0619007abe70ee6eaa3517e2f67c6554cb7a8457ab17795abd`、`46abf1f6d3d95ba593d41bc3cdffa2e37dc15ffd9649637f5f09930782a5f94c`；展开 occurrence 清单 SHA-256 为 `75e448c6268590c80b71d66d8684a4b2e273295ce81ac112676691fb275e14fe`。
- 报告当前共 45 个 warning、0 个 hint、0 个 error，`Overdraw` 为 0。开始报告的 41 个位置全部守恒为 4 个 `FIXED` 与 37 个 `SUPPRESSED_WITH_REASON`；最终分类仍为纯色重复背景 4、选择器 ripple 15、透明遮罩 3、主题兜底 12、内容背景 7，且生产布局中精确 `Overdraw` 忽略数量为 37。
- 剩余项目仅为 4 个 `AndroidGradlePluginVersion`、14 个 `GradleDependency`、24 个 `NewerVersionAvailable`、1 个 `UseCompoundDrawables`、1 个 `UselessParent` 和 1 个 `VectorPath`，与后续任务 6、7 的范围完全对应。

## 漫画菜单无用父布局处置

- `view_manga_menu.xml` 的进度行本身不绘制背景，外层 `bottom_menu` 负责覆盖进度行四周 20dp 水平和 10dp 垂直间距。按 lint 建议把背景转移到进度行并扁平化会使间距区域露出窗口底色，不能证明视觉等价，因此保留层级并只在该进度行添加 `tools:ignore="UselessParent"`；相邻中文注释记录理由及改用单一 Surface 后重新评估的条件。
- 新增 `MangaMenuLayoutInstrumentedTest` 与显式 `--manga-layout` 入口，锁定带背景父层、SeekBar 与前后章按钮的非重叠位置、有效点击区域和各一次点击消费。`GoldenInfrastructureContractTest` 与 AndroidTest 编译通过；API 21、36 mdpi light profile 均返回 `OK (1 test)`，日志 SHA-256 分别为 `00821349436098adf08a6ebdf1ffb1ac8be3be5fc291039d74136b639376bae9`、`7d34c8e634f556391b0ed278ff00ace85d45ac892cde8e8af1150b41499e883f`。

## 文件路径 compound drawable 处置

- `FileManageActivity` 与 `FilePickerDialog` 都通过 `ItemPathPickerBinding` 分别更新 `textView` 和 `imageView`，动态箭头来自旧式 PNG 字节数组；独立 `ImageView` 提供固定 20dp 视口与 `fitCenter` 缩放，点击则由包含文字和箭头的整行根节点消费。合并为 `TextView` compound drawable 会同时改变公开 View Binding 字段、动态位图边界和 RTL 排列，当前不能证明两入口等价，因此保留双子视图，并只在该布局根节点添加 `tools:ignore="UseCompoundDrawables"`；相邻中文注释记录理由和资源化箭头后的重启条件。
- 新增 JVM `FilePathBindingContractTest`，锁定两个入口继续设置文字、动态箭头和整行点击；新增设备 `FilePathLayoutInstrumentedTest` 与显式 `--file-path-layout` 入口，锁定主题默认文字色、20dp 箭头视口、整行高度、文字/箭头排列、子项不截获事件及整行点击。JVM 契约与 AndroidTest 编译通过；API 21、36 mdpi light profile 均返回 `OK (1 test)`，日志 SHA-256 分别为 `878ce65f6f4175ebef418e71905042f35c96594b2c0fae021123b3b2945e8298`、`5095dabf1f7a2165766f73932b031c452ac87f1d22b8e0d1b25402550202726b`。
- API 21 初版设备断言把主题默认 TextView 当前色错误等同于平台 `textColorPrimary` 而失败；实际两者来自不同 state list。断言改为与同主题默认 `TextView` 比较并检查 alpha 可见后转绿，没有修改生产颜色或主题。

## launcher3 长矢量路径处置

- 新增 `launcher3` 专用 fixture、`Launcher3GoldenInstrumentedTest` 及显式 `--launcher3-update`/只读 `--launcher3` 入口，在同一固定画面分别渲染原始 vector、API 26+ adaptive icon 和 API 33+ monochrome。三张 360px 图标占据截图主体，专用差异阈值定为全图 0.01%，比普通页面的 0.2% 更严格。
- 在 API 36 mdpi light 与 xxxhdpi dark 显式生成并人工审查候选，确认三种图形均可见、adaptive mask 与背景正常且没有裁切；只复制两张 `launcher3.png` 到对应版本化 profile，SHA-256 分别为 `89ee4fc8acb2df0cff18c682523eee2b07d9d82ea9035874936beee9db573a5b`、`bda77dface0615ce08172322617207988bc30bb95817831b3a0a750af257a1e7`。随后两套只读比较均为 `OK (1 test)`，日志 SHA-256 分别为 `7d2f648fe74052ce18358cf7337534555df58f3df3c61cf99be9a8afd61a8ba0`、`20eeaeeeffca8e8a59667c1171f5c4341d35ab552fc4e683ee64f28fef10169c`。
- 6,019 字符 path 的坐标已只有整数或一位小数，不存在可确定性降低且仍缩短数据的数值精度；继续取整或删除细小轮廓会直接改变书法边缘，不能在已批准阈值内证明等价。因此不改写 path，只在该精确 `<path>` 添加 `tools:ignore="VectorPath"`，并以源图重制或新优化器通过 0.01% 阈值为重新评估条件。

## 布局与 launcher 矢量批次最终对账

- `GoldenInfrastructureContractTest`、`FilePathBindingContractTest` 与 AndroidTest 编译合并复验为 `BUILD SUCCESSFUL`。API 21、36 mdpi light 普通 profile 的 9 个 fixture 只读比较均为 `OK (1 test)`，日志 SHA-256 分别为 `1521bbbe3f517e738345191807737c7fb8d83fcb408c20b26375c41d7529bb35`、`b784c70b08160db046aaf9506a523e95821c1cef445aded736f2835b7cebf520`；漫画菜单、文件路径与 launcher3 的聚焦设备证据见前三节。
- 首次强制 lint 暴露专用 Debug fixture 对 `AdaptiveIconDrawable` 的版本保护不能由 lint 从函数内 `check` 推断，新增 2 个 `NewApi` error 并立即停止。路由改为显式 `SDK_INT >= 33` 分支，`launcher3Catalog` 标注 `@RequiresApi(33)`；未使用抑制掩盖该错误，也未改变生产入口。
- 修正后固定 JDK 17 与 SDK 再次强制运行 `:app:lintAppDebug --rerun-tasks`，103 个任务全部执行并 `BUILD SUCCESSFUL`。XML、HTML、文本报告 SHA-256 分别为 `c055177f60fc76a0f7cdb7db940418c53fe96e9eb7817afc1906e24b5166c396`、`9be3dadd1222100882f0390c13f8cd6e9eab88e38910e63767f1550663915f6d`、`d81897afb57d9924d39edde23e77d9b077d1e201f2815ae2e81e45645b17e021`；展开清单 SHA-256 为 `64013baefb7b6bba67a11b1d84607576a24439c6e5f9fd730e8feb1cf901a668`。
- 报告当前为 42 个 warning、0 个 hint、0 个 error；`UseCompoundDrawables`、`UselessParent`、`VectorPath` 均为 0，且没有 launcher、漫画阅读或文件导航新增项目。剩余项目只有 4 个 `AndroidGradlePluginVersion`、14 个 `GradleDependency` 和 24 个 `NewerVersionAvailable`。

## Gradle 与 AGP 版本提示处置

- Gradle wrapper 8.13 与 AGP 8.13.2 的版本值保持不变；`agp` 声明只抑制 `com.android.application`、`com.android.library`、`com.android.test` 共 3 个 occurrence。相邻中文注释记录与 JDK 17/构建脚本成组兼容的理由，并以独立工具链升级完成三插件迁移和 API 21/23/36 完整构建矩阵为重启条件。
- 首次处置后强制重跑 lint 时，TOML 的三个 AGP occurrence 已消失，但 lint 31.13.2 的 wrapper 文本扫描器没有读取 `.properties` 中紧邻 `distributionUrl` 的 `#noinspection`，XML 仍有该唯一 warning。保留声明旁的审计注释，并在 `app/lint.xml` 只对 `../gradle/wrapper/gradle-wrapper.properties` 这一文件路径配置 `AndroidGradlePluginVersion` 忽略；该文件只有一个 `distributionUrl` 版本声明，不会隐藏其他坐标或其他文件的新提示。

## 版本提示批次最终对账

- 对 AppCompat、ConstraintLayout、Core、Fragment、Material、Media、Collection、Annotation 与 AndroidX Test 的 14 个 `GradleDependency` occurrence 逐坐标记录兼容理由、`#noinspection GradleDependency` 和独立升级重启条件。Core 的共享版本覆盖 2 个坐标，Fragment 覆盖 3 个坐标，其余声明各自对应单一坐标。
- 对 Kotlin 九个插件、Gson、JsonPath、JsoupXpath、协程两个坐标、OkHttp、ZXing Lite 与 Rhino 共 17 个非 Glide `NewerVersionAvailable` occurrence 逐声明记录抑制；Rhino 与既有最低 Android 限制合并为 `GradleDependency,NewerVersionAvailable`。Glide 5.0.5 的核心、compiler、KSP、OkHttp、RecyclerView、AVIF 六个坐标成组记录，Compose beta 坐标单独记录。
- `git diff -U0 -- gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties` 只包含注释变化，所有版本值与 wrapper URL 均未改变；`.github/dependabot.yml` 相对 `HEAD` 无差异。处置后的文件 SHA-256 为：版本目录 `adc7a3937a40fa8916762fca47b9b1de31bf9e148996ecf9caafb3f134f274dc`、wrapper `7c4b50e54b61ae6319cd01e13f6b26f239089990b97ee5b0ab37d13d729c05a4`、Dependabot 配置 `9b0096fec5f7d63f4c748c99d598136a8847ea98297d633edf8771057361d13c`。
- 使用固定 JDK 17 与 SDK 再次强制运行 `:app:lintAppDebug --rerun-tasks`，103 个任务全部执行并 `BUILD SUCCESSFUL`。最终 XML 为合法空 `<issues>`，清单工具输出 `总计: 0`；XML、HTML、文本报告 SHA-256 分别为 `3782329f5ec7fc80243d071df4885364697fe238cd16d0fd49637d3010e96025`、`17516de67123a977ba8f4195bcfc2063c6ec6d2db3d99579aec240f1d676005e`、`a69ecb171bea9d40de22744c85220b4a4ea12f5a25ce7976d67f2c88c9195334`。

## 零 issue CI 门禁

- 先增加 `assert-zero` 的空报告、warning、hint、缺失文件、错误根节点和损坏 XML 测试，以及 lint 后必须执行断言的工作流契约。实现前，6 个脚本场景因子命令不存在而 RED，工作流契约因断言步骤不存在而 RED；既有 5 个清单测试和其余 19 个工作流契约仍通过。
- `scripts/android_lint_report.py assert-zero` 只使用 Python 标准库解析 XML；任意 `<issue>` 按 severity/ID 汇总并返回 1，报告缺失、损坏或根节点错误返回 2，合法空报告输出 `总计: 0` 并返回 0。
- `.github/workflows/test.yml` 在 `:app:lintAppDebug` 后立即运行零 issue 断言；原有 `if: always()` 的 XML/HTML/文本报告上传、`checkDependencies = true` 及 `IntentWithNullActionLaunch`、`DefaultLocale`、`AppBundleLocaleChanges` 三个 fatal ID 均保留。
- 本地 `scripts.tests.test_android_lint_report` 为 11/11 通过，维护工作流契约为 20/20 通过。单独复跑合成 warning、hint 和空报告三项均通过其预期断言；对真实最终 XML 执行 CI 同款命令输出 `总计: 0` 与 `Android lint 零 issue 断言通过`。

## 维护账本与单一变更收口

- `docs/maintenance-baseline.md` 将本次 108 个 warning 守恒为 14 个 `FIXED` 与 94 个 `SUPPRESSED_WITH_REASON`，18 个 hint 守恒为 18 个 `FIXED`；每个目标 ID 的最终可见、`DEFERRED` 与 `PENDING_REVIEW` 均为 0。起始出现但后续远程元数据不再报告的 Download 插件坐标仍按起始 occurrence 记录精确抑制，没有伪装成源码修复。
- 精确抑制表记录文件或版本声明范围、起始数量、保留理由、重启条件和本证据文档中的对应验证章节；最终 XML 哈希及清单工具 `总计: 0` 也已写入账本。版本注释不表示依赖升级或风险消失。
- `docs/maintenance-risk-reduction-roadmap.md` 已把 108 warning 与 18 hint 统一改为 `eliminate-android-lint-findings` 的一次连续 Apply，并保留七个内部串行批次、任一失败即停止、禁止 baseline/全局关闭/降低 `checkDependencies` 以及 Debug 真机数据边界。
- 本文档自预检起持续记录开始与零 issue 报告哈希、RED/GREEN、golden 差异、精确抑制、失败重试和设备边界。所有真机操作目标仅为 `io.legado.app.debug` 与 `io.legado.app.debug.test`；未启动、停止、清理、覆盖或卸载 `io.legado.app.release`，原始设备输出只保留在未提交构建目录。

## 最终 JVM、lint 与 Debug 构建

- 使用固定 JDK 17 与 `/Users/back/Library/Android/sdk`，在同一 Gradle 调用中对 `:app:testAppDebugUnitTest`、`:app:lintAppDebug`、`:app:assembleAppDebug` 使用 `--rerun-tasks --no-daemon --warning-mode all --console=plain` 强制执行；131 个任务全部执行并 `BUILD SUCCESSFUL`，提交前最终复验耗时 4 分 9 秒。
- 单元测试结果包含 39 个 suite、162 个测试，所有 XML 均为 0 failure、0 error；其中一个既有条件测试为 skipped，不影响任务成功。
- Debug APK 为 `app/build/outputs/apk/app/debug/legado_app_3.26.091512.apk`，SHA-256 为 `d43a8856be2f1543e3025c862efbba27f44e1f59a4329902108604120120e86c`。
- lint XML、HTML、文本报告 SHA-256 分别为 `3782329f5ec7fc80243d071df4885364697fe238cd16d0fd49637d3010e96025`、`41289a2cb5799fac8ef6b18b25fb4abbe2e318720f2388e145ec5b552f77ea2f`、`a69ecb171bea9d40de22744c85220b4a4ea12f5a25ce7976d67f2c88c9195334`；零 issue 断言输出 `总计: 0`。

## 最终 AVD 视觉与方向矩阵

- 2026-09-15 使用固定 API 21、23、36 AVD，以普通只读模式逐一复验全部 14 个批准的 golden profile；未传入 `--update`，版本化 golden 未被改写。API 21、23 均覆盖 mdpi/xxxhdpi、light/dark、phone；API 36 覆盖 mdpi/xxxhdpi、light/dark、phone，并额外覆盖 mdpi light tablet 与 xxxhdpi dark multiwindow。14 项均返回 `OK (1 test)`，本轮未产生新的 diff artifact；现存 `app/build/golden-diffs/` 文件的最晚时间为 2026-09-14，早于本轮测试。
- 14 份普通 profile instrumentation 日志 SHA-256：`api21-mdpi-light-phone` 为 `44dee95fbca98987d2df10360d8eb0a8471611fc3b113117527379f29fdb8199`，`api21-mdpi-dark-phone` 为 `ddaf9657f00515fb3ceff298e4b6989696fd74c010f0d53e1d9719e186f6211a`，`api21-xxxhdpi-light-phone` 为 `44dee95fbca98987d2df10360d8eb0a8471611fc3b113117527379f29fdb8199`，`api21-xxxhdpi-dark-phone` 为 `9846d63cf6d204d4dd522590edc4ed243f35dd2dbade9820b8328a55a8e06eb9`；`api23-mdpi-light-phone` 为 `e67606327812eec35f7324b1e5a9ba5b46f7981e6504ab96a352f1f4d432cb30`，`api23-mdpi-dark-phone` 为 `6d9276eb66f6103f607f16910da3bf973195bf832758f54e70b2217d0bfae360`，`api23-xxxhdpi-light-phone` 为 `b608a37de28c8c3d025bfd79970b3f038aeee4a042f681446869b1bf226c72c1`，`api23-xxxhdpi-dark-phone` 为 `56d7dbe48206eefa1baab71f732e3b0498eaebec3a8b216dd41dca17fe7c9df8`；`api36-mdpi-light-phone` 为 `f22548d3bab8fa660349cd9ca9669f078125e71d9a4916b8056852273b11fb14`，`api36-mdpi-dark-phone` 为 `c5f554bce9582f499a33748bcc5076420b182316ea8bd142697edd1fff1bf6fc`，`api36-xxxhdpi-light-phone` 为 `8eae6d4be2fd240b3b1b2a1a0b2e03272dd6922521fce6c8e395a4c9d53ab3dd`，`api36-xxxhdpi-dark-phone` 为 `1ccd2c2620e8dfa68a333833debb020a4c0214a578b735931770e47773f25cf3`，`api36-mdpi-light-tablet` 为 `ea0d1268bb86bc0ab62f4dc9f769dad2e116f018c1315b5308155e198465f1a2`，`api36-xxxhdpi-dark-multiwindow` 为 `a81f5711608898eb78a9e46c6e3225e843f4722c6f7e6777a79c5440b886cb87`。
- 方向矩阵另以 API 21 phone、API 36 phone、API 36 tablet 与 API 36 freeform multiwindow 四个 profile 复验九个 `behind` 声明、横竖屏切换及返回栈；四项均返回 `OK (3 tests)`。日志 SHA-256 依次为 `c61339681f2eb1d127bcdba27cb0b9e021fedaa94816c942d0b6d91a856021a3`、`92c71c3b613f94c207dee64792659f40e37d908e61e2da302ff23b13fa32723e`、`18187b6c26359645d97c50386217b7961deedc0e47ce8f90be7b77deff52bb08`、`e248d0a29bf4f41de79ecbb1eedcd6b651206f38dc5ee5e5348f3af2d2e29b8a`。
- 首个矩阵循环因 shell 因子脚本消费标准输入，只执行了第一项；发现后未把部分执行视为通过，改用显式 profile 数组完成其余 13 项。该重试是编排问题，不是测试失败；全部最终日志和设备返回码均已逐项核对。

## 授权 Debug 真机最终烟测

- 2026-09-15 实时 `adb devices -l` 确认已授权 USB 设备为 `device` 状态，设备为 Hisense HLTE556N、API 30；序列号仅用于本地命令定向，不写入版本库。离线解析本轮 APK 后确认应用与测试包名分别为 `io.legado.app.debug` 和 `io.legado.app.debug.test`；只对这两个包执行覆盖安装，未清理 Debug 数据。
- 新增 `AuthorizedDebugDeviceSmokeTest`，逐一调用生产 `LauncherIconHelp.changeIcon()` 切换 `ic_launcher` 与 `launcher1`—`launcher6`，每次断言系统 MAIN/LAUNCHER 解析结果只有目标入口，并在 `finally` 恢复测试前的七个组件状态。通知测试使用迁入 `drawable-nodpi` 的 200×200 `icon_read_book` 构造大图标，发布到应用既有朗读渠道，确认进入 Debug 包活动通知列表后立即撤销。
- `AuthorizedDebugDeviceSmokeTest` 与 `ShortcutLaunchInstrumentedTest` 独立合并复验为 `OK (5 tests)`：覆盖七个 launcher 入口、通知发布与清理、普通 Intent 不消费、快捷方式标记只消费一次，以及 `bookshelf`、`lastRead`、`readAloud` 三个系统动态快捷方式的 ID 与目标 Intent 标记。成功日志 SHA-256 为 `fda44edee717856cbafbf357acfe61eab673dc779f324f3851423f17c5af58c3`。
- `BehindOrientationInstrumentedTest` 独立复验为 `OK (3 tests)`：九个页面安装后仍为 `SCREEN_ORIENTATION_BEHIND`，Debug 方向父页分别以横、竖方向启动真实 `BookshelfManageActivity` 并验证返回栈恢复；非 multiwindow 真机 profile 对多窗口专用断言按设计直接返回。成功日志 SHA-256 为 `57fa98748ba1c1272ac6af81b0f3079db20d1de08c0e57d568ded7295e443331`。
- 首次将三组测试合并运行时共 8 项、7 项通过，方向往返因设备处于 `Asleep`、显示关闭且安全锁屏 `showing=true`，Debug 方向父页无法进入 `RESUMED` 而失败。失败日志 SHA-256 为 `208cdf66337a9f2026217c1607a4a79e850be56c585cbc0f4f489d7c605aaff7`；唤醒并由维护者手动解除安全锁屏后，未修改生产逻辑或放宽断言，上述两组独立重试全部转绿。
- 本节所有 ADB 命令均显式限定到已授权设备的本地序列号；安装、测试、组件切换与通知操作仅针对 `io.legado.app.debug` 和 `io.legado.app.debug.test`。未读取、启动、停止、清理、覆盖或卸载 `io.legado.app.release`，没有捕获截图或 UI hierarchy；原始 instrumentation 日志只保存在已忽略的 `app/build/`。

## 仓库脚本与基础校验

- `python3 -m unittest scripts.tests.test_android_lint_report -v`：11/11 通过，覆盖清单解析、零 issue、warning、hint、缺失/损坏报告与错误根节点。
- `python3 .github/scripts/test_maintenance_workflow.py`：20/20 通过，覆盖范围分类、Android/网页门禁、lint 零 issue 步骤、报告上传、`checkDependencies = true` 和稳定聚合检查。
- 提交前审查发现 golden runner 在已有其他模拟器时可能误选 `adb devices` 中的第一台设备；脚本现会记录启动前已有序列号，只选择本次新启动的 emulator，并在启动失败时终止本次精确进程。新增契约断言后 `zsh -n` 与完整 JVM 单元测试均通过，没有重新运行设备矩阵的必要，因为修改只收紧设备选择与失败清理，不改变 instrumentation、profile 或 golden 比较逻辑。
- `openspec validate --all --strict`：8 个 change/spec 项全部通过，0 失败；`git diff --check` 返回 0 且无输出。
- `git diff --stat` 显示 85 个已跟踪文件、513 行新增、318 行删除；路径仅位于 Android lint 生产修复、Android/CI 测试、维护文档和版本声明。未跟踪文件按目录核对后仅为 `app/lint.xml`、AndroidTest/golden、Debug 测试夹具、新增 Kotlin 测试与实现、当前 OpenSpec 证据及 `scripts/` 工具。
- `git ls-files --others --exclude-standard` 的产物过滤结果为空：没有未忽略的 `build/`、`dist/`、`node_modules/`、`.gradle/`、`.idea/`、APK、AAB、JKS 或 keystore；工作树未混入 Web 构建、设备日志或其他无关生成文件。

## 兼容性与范围外变化审计

- `app/build.gradle`、根 `build.gradle` 与 `settings.gradle` 相对起始提交无差异；当前 Android 配置仍为 `minSdk 21`、`targetSdk 36`，本变更未提高最低系统版本。
- `app/schemas/`、数据实体目录和 `app/src/main/java/io/legado/app/help/storage/` 均无已跟踪或未跟踪差异，Room schema、备份字段与恢复逻辑未改变。
- 主 Manifest 的零上下文差异中没有新增、删除或修改 `intent-filter`、`data`、scheme、host、path 或 MIME type；导入 URI 契约未改变。书源、订阅源、规则实体及相关 UI 目录没有格式变化；规则解析相关生产差异只把 17 个 `trim { it <= ' ' }` 调用替换为同语义的 `trimAsciiControlAndSpace()`，边界已由聚焦测试锁定。
- 对 `gradle/libs.versions.toml` 和 Gradle wrapper 分别移除空行与注释后，与起始提交逐行比较无差异；所有依赖、插件和 wrapper 版本值保持不变。`.github/dependabot.yml` 相对起始提交也无差异。
- 已跟踪与未跟踪路径中均没有签名、keystore、credentials、`local.properties` 或 `key.properties` 变化；正式签名和发布配置未触碰。
- `modules/web/` 与 `app/src/main/assets/` 均无已跟踪或未跟踪差异，网页源码、依赖和同步到 Android 的静态产物未改变。

## 最终零 issue 收口

- 提交前审查修正 runner 隔离后，继续使用 JDK 17 与固定 SDK，在同一调用中强制运行 `:app:testAppDebugUnitTest`、`:app:lintAppDebug`、`:app:assembleAppDebug --rerun-tasks --no-daemon --warning-mode all --console=plain`；131 个任务全部执行，4 分 9 秒后 `BUILD SUCCESSFUL`，没有复用旧 lint 或编译结果。
- `scripts/android_lint_report.py inventory` 输出 `总计: 0`，`assert-zero` 输出 `Android lint 零 issue 断言通过`。使用 XPath 独立计数得到 `<issue>` 总数 0、Error 0、Warning 0、Hint 0。
- 最终 XML、HTML、文本报告 SHA-256 分别为 `3782329f5ec7fc80243d071df4885364697fe238cd16d0fd49637d3010e96025`、`41289a2cb5799fac8ef6b18b25fb4abbe2e318720f2388e145ec5b552f77ea2f`、`a69ecb171bea9d40de22744c85220b4a4ea12f5a25ce7976d67f2c88c9195334`。
- 维护账本经字段级脚本复核：warning 开始 108、最终可见 0、`FIXED` 14、`SUPPRESSED_WITH_REASON` 94、`DEFERRED` 0、`PENDING_REVIEW` 0；hint 开始 18、最终可见 0、`FIXED` 18、抑制 0、`DEFERRED` 0、`PENDING_REVIEW` 0。两类 occurrence 均数量守恒。

## PR #84 API 37 runner 漂移处置

- PR #84 首轮维护门禁 run `34929947117` 在合并参考提交 `fde15dd126f631a8a51095ee5c62b06c3f2a3ffe` 上执行。Android lint 本身成功，随后的零 issue 断言按设计失败；artifact 中的 XML 只包含 3 个 `Warning/OldTargetApi`，分别指向 `app/build.gradle`、`modules/book/build.gradle`、`modules/rhino/build.gradle` 的 `targetSdk 36`。
- GitHub runner 已比本地固定 SDK 多提供 API 37，因此这 3 个 occurrence 属于工具环境漂移，但仍按规范纳入同一变更，不当作“本地为零”的可忽略差异。逐 occurrence 记录保存在 `evidence/pr84-lint-drift.tsv`。
- 当前正式发布、构建、模拟器与真机证据均锁定 `targetSdk 36`。直接提升到 37 会引入独立平台行为变更，超出本次 lint 治理范围；因此 `app/lint.xml` 只对三个现有 Gradle 文件路径抑制 `OldTargetApi`，并以独立 API 37 迁移完成 API 21/23/36/37 构建、行为与设备矩阵为删除条件。没有全局关闭该 lint ID，没有建立 baseline，也没有降低 `checkDependencies` 范围。
- 治理账本因此由起始 108 个 warning 加本轮 3 个漂移 warning，累计为 111；完整去向为 14 个 `FIXED`、97 个 `SUPPRESSED_WITH_REASON`、0 个最终可见、0 个 `DEFERRED`、0 个 `PENDING_REVIEW`。
- 本地补充安装 `platforms;android-37.0` 后，使用与 CI 一致的 JDK 17、SDK 和 `:app:lintAppDebug --rerun-tasks --no-daemon --warning-mode all --console=plain` 强制复验；103 个任务全部执行，3 分 17 秒后 `BUILD SUCCESSFUL`。清单与零 issue 断言均输出 `总计: 0`，XML、HTML、文本报告 SHA-256 分别为 `3782329f5ec7fc80243d071df4885364697fe238cd16d0fd49637d3010e96025`、`51c5081317d859b5b3176a97fffa862986deff26221d77634946a615e9434669`、`a69ecb171bea9d40de22744c85220b4a4ea12f5a25ce7976d67f2c88c9195334`。脚本单元测试 11/11、维护工作流契约 20/20、OpenSpec 严格校验 7/7 通过，`git diff --check` 无输出。
