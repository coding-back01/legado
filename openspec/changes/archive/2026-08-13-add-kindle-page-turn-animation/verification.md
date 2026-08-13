# 验证记录

## 刷新模式证据更正与前置门槛

### 本地构建与隔离检查

- 日期：2026-08-11
- 分支：`codex/add-kindle-page-turn-animation`
- 历史聚焦测试：旧版 `KindlePageTurnTimelineTest` 曾通过，但其“硬裁剪 + 独立灰条”视觉模型已被否定，不再作为当前任务 1.1 的完成证据。
- 调试构建：`:app:assembleAppDebug` 成功。
- 当前 APK：`app/build/outputs/apk/app/debug/legado_app_3.26.081200.apk`
- APK manifest 包名：`io.legado.app.debug`。
- 历史调试入口：`io.legado.app.ui.debug.KindlePageTurnDebugActivity` 曾仅由 `app/src/debug` 提供；完成候选测试后已从当前源码和生产调试包删除。
- 安装结果：旧视觉原型曾以 `io.legado.app.debug` 全新安装；第二轮非线性时间缓动版和第三轮非线性内容混合版均通过不带降级参数的 `adb install -r` 更新成功，专用验证 Activity 启动成功且系统状态为 `Resumed`，未发现启动崩溃。
- 正式版保护：安装后 `io.legado.app.debug` 与 `io.legado.app.release` 同时存在；未对正式版执行安装覆盖、卸载、清数据或降级操作。

### 目标设备

- 设备：海信 Hi Reader Pro，ADB 型号实测为 `HLTE556N`，序列号为 `7a219732`。
- 系统：实测为 Android 11，API 30。
- 刷新模式更正：用户于 2026-08-12 复核确认，首轮、第二轮、第三轮和 `IMG_1005.MOV` 实际均在流畅模式下完成；此前“清晰模式”的记录无效。
- ADB 状态：2026-08-12 已连接，第三轮调试入口已启动；设备显示为唤醒状态，Activity 为当前 `ResumedActivity`，等待用户观察物理屏幕。

### 第一轮候选时长观察：线性扫动与线性内容混合

以下项目只能由物理屏幕观察和外部录像判定，ADB 截图或录屏不能替代：

| 时长 | 前进方向肉眼可辨 | 后退方向肉眼可辨 | 外部录像可见移动边界 | 最终文字稳定 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 120ms | 基本不可辨 | 基本不可辨 | 待验证 | 是 | 肉眼基本看不出动画 |
| 160ms | 可辨 | 可辨 | 待验证 | 是 | 能看出动画，但肉眼感觉有掉帧 |
| 200ms | 完整可辨 | 完整可辨 | 待验证 | 是 | 动画较完整，但仍有轻微掉帧感；首轮优先候选 |
| 220ms | 待验证 | 待验证 | 待验证 | 待验证 | 新增非线性缓动精调候选，等待第二轮实机观察 |
| 250ms | 完整可辨 | 完整可辨 | 待验证 | 是 | 动画完整，但主观速度偏慢 |

用户对修订视觉的当轮观察：不再出现独立灰条；下一页从右向左、上一页从左向右出现宽幅文字叠印；旧字逐渐变浅、新字逐渐变深；纸张背景基本稳定；动画结束后文字内容稳定。与 Kindle 参考视频最明显的剩余差异是当前线性速度带来的跳帧感，而参考视频呈现非线性节奏，主观观感更流畅。随后四模式复核确认流畅模式仍有面板残影，因此本段早期“没有异常残影”的主观记录不再作为最终清晰度证据。

### 第二轮候选时长观察：非线性时间扫动

第二轮把扫动位置改为对称 `smoothstep` 时间缓动。用户在同一目标设备的流畅模式下观察后确认，五个选项都只是一闪而过，已无法辨认动画：

| 时长 | 前进方向肉眼可辨 | 后退方向肉眼可辨 | 最终文字稳定 | 备注 |
| --- | --- | --- | --- | --- |
| 120ms | 不可辨 | 不可辨 | 待确认 | 一闪而过 |
| 160ms | 不可辨 | 不可辨 | 待确认 | 一闪而过 |
| 200ms | 不可辨 | 不可辨 | 待确认 | 一闪而过 |
| 220ms | 不可辨 | 不可辨 | 待确认 | 一闪而过 |
| 250ms | 不可辨 | 不可辨 | 待确认 | 一闪而过 |

结论：`INVALID_EASING_MODEL`。对扫动位置直接应用对称 `smoothstep` 后，当轮流畅模式的五个候选都失去首轮已经可见的方向感；现有证据不能说明清晰模式如何处理或调度中间帧。停止第二轮外部录像，不将其结果作为 GO 证据。

### 第三轮候选时长观察：线性扫动与非线性内容混合

第三轮恢复扫动位置的线性时间进度，仅在宽幅交叉区内部使用 `smoothstep` 灰阶混合，默认回到首轮最优的 200ms。用户在同一目标设备的流畅模式下重点对比 200、220、250ms，结果如下：

| 时长 | 横向扫动肉眼可辨 | 无独立灰条 | 最终文字稳定 | 综合观感 |
| --- | --- | --- | --- | --- |
| 200ms | 是 | 是 | 是 | 可用，但综合效果不如 220ms |
| 220ms | 是 | 是 | 是 | 三个速度中综合效果最好 |
| 250ms | 是 | 是 | 是 | 可用，但综合效果不如 220ms |

用户确认本轮询问的四项均达到要求，前进和后退方向仍符合既定模型，纸张背景保持基本稳定。120ms 与 160ms 本轮未作新的重点比较，继续沿用第一轮的肉眼结论，不据此新增证据。第三轮总体观感与第一轮没有明显区别，因此只能确认线性扫动位置恢复了可见方向，不能证明仅对内容混合使用 `smoothstep` 带来了可感知的额外顺滑收益。

### 门槛结论

旧结论 `GO` 已撤回。上述三轮只能证明流畅模式能够显示软件中间帧，不能证明清晰模式可行，也不能证明结束后无面板残影。当前门槛结论为 `NO_GO_CLEAR_AND_ANIMATED`；任务 5.2 保持未完成。

最新四模式复核如下：

| 海信模式 | 动画表现 | 卡顿 | 残影/清晰度 | 结论 |
| --- | --- | --- | --- | --- |
| 极速 | 动画效果最好，200ms 主观优于 220ms | 较少 | 有残影 | 仅保留 200ms 软件候选 |
| 流畅 | 动画可见 | 有卡顿 | 有残影 | 不满足核心目标 |
| 均衡 | 动画可见性不足 | 很卡顿 | 有残影 | 不满足核心目标 |
| 清晰 | 基本看不到动画 | 不适用 | 最终清晰 | 不满足核心目标 |

### 第三轮外部录像逐帧复核

- 录像：用户提供的 `IMG_1005.MOV`，HEVC、1080×1920、约 5.652 秒，视频轨名义帧率约 59.982fps；录像文件仅作本地验证证据，不纳入仓库。
- 测试条件：画面显示 Hi Reader Pro 的独立调试入口、220ms 被选中，依次执行两次下一页和两次上一页；刷新模式现已更正为流畅模式。
- 第一次下一页：约 1.67–1.90 秒的连续帧可见目标页文字先在右侧出现并向左扩展，新旧文字在移动区内重叠，随后稳定在固定测试页 2/3。
- 第二次下一页：约 2.62–2.90 秒的连续帧再次捕获右侧先变化、随后向左覆盖的文字叠印，最终稳定在固定测试页 3/3。
- 第一次上一页：约 3.72–3.98 秒的连续帧可见左侧内容先恢复并向右扩展，最终稳定在固定测试页 2/3。
- 第二次上一页：约 4.65–4.98 秒的连续帧可见相反方向的内容恢复，最终稳定在固定测试页 1/3。
- 独立灰条：四段过渡均未发现贯穿页面的纯色灰条、阴影幕布或整页平移；可见灰阶来自新旧文字的重叠与面板响应。
- 背景与结束状态：纸张背景在相机曝光允许范围内保持基本稳定，每次过渡后页面均清晰稳定，没有白屏、崩溃或异常持续残影。
- 与参考视频比较：两者都表现为固定坐标上的新旧文字灰阶叠印和定向扫过；当前录像能看到更明显的离散推进，而 Kindle 参考的变化更集中、更连贯，因此不能声称视觉完全一致或还原其专有波形。

### 海信自动组合刷新审计与有界实验

- 只读反汇编确认，Hi Reader Pro 的 `android.app.Activity.refreshEpdDisplayMode`、`setEnablePageScroll` 等方法属于非 SDK framework 能力；`IEpdManager` 没有 Kindle 那种方向、步数或 swipe 波形参数。
- framework 的自动组合刷新只在纵向位移越过阈值且明显大于横向位移时进入；真实横向滑动没有触发，`sys.hmct.dynamic_refreshing_flag` 保持 `0`。
- 纵向对照实验证明系统流程本身有效：动态标志可从 `0` 变为 `1`，framework 临时切换快速模式，并在真实 View 滚动结束后恢复原模式、执行清残影收尾并把标志恢复为 `0`。
- 仅使用标准 `MotionEvent` 和 View 滚动把纵向流程桥接到横向 200ms 动画时，事件递归进入应用手势处理并反复触发刷新状态，不能可靠完成单次 `0→1→0`，结论为 `NO_GO_UNSAFE_EVENT_BRIDGE`。
- 实验只安装 `io.legado.app.debug`，没有覆盖、卸载或清理 `io.legado.app.release`。实验代码随后已从工作区删除。2026-08-13 继续工作时先通过真实系统列表纵向滚动让 framework 将遗留动态标志恢复为 `0`；切回 `io.legado.app.release` 阅读页后只读确认当前应用刷新配置为清晰模式 `3`、`sys.hmct.epd_mode=515`。整个恢复过程未写系统属性、未调用私有 EPD 服务，正式版版本和安装时间未变化。
- 因自动组合刷新仅适用于纵向滚动，且安全桥接失败，任务 3.6 记为 `NOT_RUN_PROTOCOL_STOP`。在当前不调用私有接口、无 root、无系统签名的范围内没有可继续实施的硬件路径。

### 参考视频复核与旧原型结论

- 参考：用户提供的 Kindle Paperwhite 5 实拍视频，H.264、1080×1920、30fps、约 7.47 秒。
- 逐帧观察：前进翻页时目标页墨迹按最终位置从右向左显现，返回翻页时从左向右显现；起始页墨迹在扫过区域滞后变浅，新旧内容短暂以不同灰阶叠印，纸张背景基本稳定。
- 逐帧观察：未见独立灰色条、阴影幕布、页面卷曲或整页平移；可见过渡约为 0.20–0.25 秒，但录像时间不直接作为内部固定时长。
- 用户确认：2026-08-11，用户确认上述描述与参考视频观感一致。
- 旧原型结论：`INVALID_VISUAL_MODEL`。此前调试包中的独立灰色条与 Kindle 参考不一致，任务 1.1 撤回完成状态，门槛继续保持 `PENDING`，不得开始第 2 节。
- 修订实现：旧版硬裁剪和独立灰条已替换为约 35% 视口宽度的空间交叉淡变遮罩；新旧页面内容通过连续透明度混合产生叠印，不再绘制额外颜色。
- 第一轮修订验证：`KindlePageTurnTimelineTest` 通过，覆盖前后方向、交叉区位置、区内 0.25/0.5/0.75 内容混合比例和线性时间进度；`appDebug` 源码编译成功，`openspec validate --all --strict` 通过。
- 第二轮节奏修订：参考视频 30fps 关键帧与用户实机观感均支持线性速度是主要剩余差异，但受面板响应和相机采样影响，不能据此声称还原 Kindle 私有精确曲线。实现曾对扫动位置使用纯 Kotlin `smoothstep`（`3t² - 2t³`）非线性缓动并新增 220ms 候选，随后被实机结果判定为 `INVALID_EASING_MODEL`。
- 第二轮本地与安装验证：聚焦 `KindlePageTurnTimelineTest` 通过，`:app:assembleAppDebug` 成功，`openspec validate --all --strict` 与 `git diff --check` 通过；APK manifest 确认为 `io.legado.app.debug`，设备上的调试版和正式版安装后仍同时存在。用户已完成第二轮物理屏幕观察；因全部候选不可辨，外部录像按门槛协议停止。
- 第二轮实机结论：用户确认 120、160、200、220、250ms 均一闪而过、看不出动画；外部录像按协议停止，第二轮不得用于 GO。
- 第三轮修订：扫动位置恢复线性时间进度，`smoothstep` 只用于交叉区内部的目标页透明度；2026-08-12 聚焦测试、`:app:assembleAppDebug`、`openspec validate --all --strict` 和 `git diff --check` 通过，APK 包名再次确认为 `io.legado.app.debug`，通过 `adb install -r` 更新并启动成功。
- 第三轮实机结论：用户确认 200、220、250ms 的横向扫动、无独立灰条、背景与最终文字稳定等四项均达到要求，220ms 综合效果最好；但这些结果及随后提供的 59.98fps 外部录像现已更正为流畅模式证据，不能用于清晰模式门槛，旧 `GO` 已撤回。

## 生产实现聚焦验证

- 协议与设置：`PageAnim` 在既有 `0..4` 后追加值 `5`，未知值显式归一为无动画；普通阅读、墨水屏阅读和单本书覆盖设置均只在末尾追加新选项，八套现有语言资源已补齐名称。
- 持久化：全局 `ReadBookConfig.Config` 的 `pageAnim`、`pageAnimEInk` 与单本书 `Book.ReadConfig.pageAnim` 继续沿用现有 JSON 字段；新旧 JSON 聚焦测试通过，未修改 Room 实体字段、数据库版本或 schema 快照。
- 生产动画：新增 `KindlePageDelegate`，软件时间线根据最新极速模式反馈改为 200ms；手势移动阶段只确认方向，松手后才播放。渲染范围仍是 `ReadView` 的完整阅读内容画布，系统栏、菜单与弹窗不在录制范围内。200ms 不是清晰模式通过证据。
- 连续输入：独立状态控制器覆盖 `Idle`、`Animating`、`Returning`、`Committing`，使用净待处理页数和 generation 保护；测试确认连续二十次同向输入提交二十页、反向输入净抵消、旧回调无效、目标缺失或提交失败清空意图。
- 按键：仅新 Delegate 绕过既有 600ms 丢弃式防抖；独立按下直接进入状态机，关闭长按翻页时仍忽略 repeat，启用时以 200ms 为最短接受间隔。鼠标滚轮和其他动画路径保持既有逻辑。
- 主动选择与降级：用户主动选择新模式后不受设备系统动画时长比例为 `0` 的影响；视口无效、录制器不可用或绘制异常时直接提交正确目标页，返回阶段失败稳定在原页，生命周期销毁清除未提交动画，尺寸变化时完成当前有效目标；没有 500ms 延迟补刷。
- 调试清理：前置门槛使用的 `KindlePageTurnDebugActivity`、debug manifest 声明、候选时长控件和专用字符串已删除，未进入生产 APK。
- 聚焦命令：`./gradlew :app:testAppDebugUnitTest --tests io.legado.app.constant.PageAnimTest --tests io.legado.app.help.config.PageAnimationConfigJsonTest --tests 'io.legado.app.ui.book.read.page.animation.*'`，2026-08-12 执行成功。
- 基础检查：2026-08-12 `git diff --check` 与 `openspec validate --all --strict` 执行成功。普通 Android 人工验证、最终 Hi Reader Pro 生产路径验收和完整项目检查仍待执行。

## 生产 APK 与最终项目检查

### 完整单元测试

- 命令：`./gradlew :app:testAppDebugUnitTest`。
- 结果：2026-08-12 执行成功，完整 `appDebug` JVM 单元测试没有失败；在最终复核中使用一次性 `ANDROID_HOME=/Users/back/Library/Android/sdk` 和 `ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk` 再次执行，57 个任务均为最新状态且构建成功。
- 环境说明：未设置 SDK 环境变量的一次调用在确定任务依赖前因找不到 SDK 位置而停止，不属于测试用例失败；没有创建或提交 `local.properties`。任务 6.1 完成。

### 静态检查与调试构建

- 命令：`./gradlew :app:testAppDebugUnitTest :app:lintAppDebug :app:assembleAppDebug`。
- 单元测试与构建结果：`:app:testAppDebugUnitTest` 成功，`:app:assembleAppDebug` 成功。
- lint 结果：`:app:lintAppDebug` 失败，报告为 14 个错误、880 个警告、18 个提示，因此不得描述为 lint 通过。
- 错误归属：14 个错误位于既有 `AndroidManifest.xml`、`MainViewModel.kt`、`BookType.kt`、`LayoutManager.kt`、`ViewExtensions.kt`、`NavigationViewUtils.kt` 和既有缺失翻译项；没有错误指向本变更新增的 Delegate、时间线、状态控制器、渲染器或新增字符串。变更触及的既有文件只报告了原有 `dialog_read_book_style.xml` 根布局 overdraw 和 `BaseReadBookActivity.kt` 数字格式警告。
- 处理边界：本变更没有顺手修改这些项目基线问题。由于 lint 命令本身失败，任务 6.2 保持未完成。

### 生产调试包审计

- APK：`app/build/outputs/apk/app/debug/legado_app_3.26.081201.apk`，大小约 29MB，SHA-256 为 `ea5769807377613c1b6c7d53a50a0717327d127c951cbb87707bc623a28a45cf`。
- manifest：包名 `io.legado.app.debug`，`versionCode` 为 `16566`，`versionName` 为 `3.26.081201debug`，最低 API 21、目标 API 36。
- 签名：`apksigner verify --verbose --print-certs` 验证 v1、v2 签名成功，签名者为 Android Debug 证书；这不是系统签名。
- 源码差异审计：生产变更中没有 `com.hmct.epd`、`IEpdManager`、`EpdManager`、`ServiceManager`、`setEpdDisplayMode`、`detectBeginByDirection`、sysfs 路径、root 命令、全局刷新模式切换或 500ms 延迟补刷调用。
- APK 审计：压缩包清单中没有提取的 `framework.jar`、`services.jar`、海信/HMCT 文件或已移除的 Kindle 调试入口；解压 `classes*.dex` 后也未匹配到上述海信 EPD 接口和 sysfs 字符串。
- 工作区审计：变更列表中没有签名材料、设备私有文件、`local.properties` 或构建产物；任务 5.4 完成。

### 仍待实机覆盖

- `IMG_1005.MOV` 只覆盖前置专用调试入口的 220ms 前后翻页，不是生产阅读路径，不能替代任务 5.1–5.3。
- 2026-08-12 最终检查时 `adb devices -l` 没有列出设备，因此尚未安装当前生产调试包，也没有执行横屏双页、浅色/深色主题、连续二十页、反向抵消和实体键验收。
- 普通 Android 人工验证尚无可用普通设备；本机 `Pixel_5_API_30` AVD 的系统镜像缺少 `system.img` 和 `userdata.img`，未使用 `-wipe-data`，任务 4.3 保持未完成。

### Hi Reader Pro 生产调试包隔离安装

- 日期：2026-08-12；设备序列号 `7a219732`，型号/设备标识 `HLTE556N`，Android 11、API 30。
- 安装前本地 APK 为 `io.legado.app.debug`、`versionCode=16566`、`versionName=3.26.081201debug`，签名为 Android Debug 证书，证书 SHA-256 为 `e5529aeb53e775e4f9647f73d1fad5d39f3b4ce8855cbe562d3eea9fc7265ce7`。
- 安装前设备已有 `io.legado.app.debug` `3.26.081200debug`，使用相同 Android Debug 证书；正式版为独立包 `io.legado.app.release` `3.25`，使用 `CN=gedoor` 证书。
- 更新命令仅对本地生产 debug APK 执行 `adb install -r`，返回 `Success`；没有使用 `-d`，没有执行卸载或清数据。更新后 debug 版本为 `3.26.081201debug`，设备回拉 APK 的 SHA-256 为 `ea5769807377613c1b6c7d53a50a0717327d127c951cbb87707bc623a28a45cf`，与本地 APK 完全一致。
- 正式版保护复核：安装前后 `io.legado.app.release` 的 APK 路径相同，`versionCode=15604`、`versionName=3.25`、首次安装时间 `2024-05-10 11:59:42`、最后更新时间 `2025-01-03 11:37:11` 均未变化；安装前后 APK SHA-256 均为 `1e3a7aab498ffc4d7939c8be2f6ca1ff8c3c6e4847637e8080e4e4a463e0174e`。
- 安装后只在 debug 应用自己的沙箱中导入仓库自带的公开 AGPL 许可证 `LICENSE`，共享测试文件为 `/sdcard/Download/codex_kindle_animation_test.txt`；安装前 debug 数据库为 0 本书，未从正式版复制书籍、配置、数据库或凭据。任务 5.1 完成。

### 生产阅读路径首次验收失败与修复

- 用户在生产阅读设置中确认“Kindle翻页动画”为选中状态，但前后翻页的视觉效果与“无翻页动画”一致，因此任务 5.2 本轮结论为 `PRODUCTION_PATH_NO_ANIMATION`，保持未完成。
- 只读设备设置显示 `window_animation_scale=0.0`、`transition_animation_scale=0.0`、`animator_duration_scale=0.0`。生产 `KindlePageDelegate` 在启动前调用 `ValueAnimator.areAnimatorsEnabled()`，得到关闭状态后通过 `forceFinish` 直接提交目标页；前置专用调试入口没有读取该开关，所以此前 220ms 原型仍可见。
- 修复移除生产 Delegate 对系统动画时长比例的依赖。用户主动选择该阅读模式视为明确要求播放；视口无效、页面录制未准备或绘制异常仍按原方案安全降级，且不修改设备全局动画设置。
- 用户同时反馈第六个“Kindle翻页动画”按钮会把选项行撑高。布局已从最多两行改为单行尾部省略，完整本地化名称仍作为控件文本保留。
- 修复后完整运行 `:app:testAppDebugUnitTest :app:assembleAppDebug`，82 个任务中 25 个执行、57 个为最新状态，构建成功且单元测试没有失败。新 APK 为 `legado_app_3.26.081210.apk`，包名 `io.legado.app.debug`、`versionCode=16566`、`versionName=3.26.081210debug`，SHA-256 为 `203d72a0b370122a818777cae29376188bfaf909d49d17bd8e3872485d8adfaf`，继续使用相同 Android Debug 证书。
- 仅通过 `adb install -r` 更新上述 debug APK，返回 `Success`，没有降级、卸载或清数据。更新后正式版的包路径、版本、首次安装时间和最后更新时间均未变化，APK SHA-256 仍为 `1e3a7aab498ffc4d7939c8be2f6ca1ff8c3c6e4847637e8080e4e4a463e0174e`；设备三项系统动画比例继续保持 `0.0`，未为测试修改系统设置。
- 修复后 `openspec validate --all --strict` 通过。`:app:lintAppDebug` 仍被项目既有的 14 个错误阻断，错误位置与修复前一致，没有错误指向本轮 Delegate、状态控制器、测试或按钮布局；本变更没有修改 lint 基线或顺手修复无关问题。
- 修复后的生产阅读 Activity 已启动到锁屏后方，等待设备解锁和物理屏幕复验；以上自动化与安装结果不得作为任务 5.2 通过证据。

### 差异与 OpenSpec 校验

- `git diff --check` 通过；另对全部未跟踪的新文件逐一执行等价的 `git diff --no-index --check`，没有空白错误。
- 已检查 `git diff --stat`、全部未跟踪文件和 `git status --short`。变更只涉及 Android 阅读动画实现、对应资源与测试以及本 OpenSpec 变更，没有 Room schema、Gradle 依赖、构建产物、设备私有文件、签名材料、`local.properties` 或无关格式化差异；任务 6.3 完成。
- `openspec validate --all --strict` 通过，`change/add-kindle-page-turn-animation` 与既有 `spec/fork-build-security` 共 2 项通过、0 项失败；提案、规范、设计、任务和当前 `GO`/待验收状态一致，任务 6.4 完成。

## 2026-08-13 继续验证与停止边界

- 设备恢复：开始继续工作时只读发现 `sys.hmct.dynamic_refreshing_flag=1`。先唤醒设备并让系统设置中的真实可滚动列表完成纵向滚动，framework 将动态标志恢复为 `0`；随后切回正式版阅读页，只读确认 `current_epd_mode=3`、`sys.hmct.epd_mode=515`。整个过程未写系统属性、未调用私有 EPD 服务。
- 正式版保护：恢复后正式版仍为 `io.legado.app.release` `3.25`，`versionCode=15604`，首次安装时间 `2024-05-10 11:59:42`、最后更新时间 `2025-01-03 11:37:11`，没有覆盖、卸载、清数据或降级。
- 事实更正：提案、规范、设计和本记录不再把自动组合刷新描述为待验证可行路线，也不再用“清晰模式合并中间帧”解释现象。现有证据只证明清晰模式基本看不到普通 Canvas 动画，不能证明厂商内部如何调度中间帧。
- 当前核心结论：普通 Canvas 路线保持 `NO_GO_CLEAR_AND_ANIMATED`，标准事件组合刷新桥接保持 `NO_GO_UNSAFE_EVENT_BRIDGE`。200ms 只保留为普通屏幕或极速模式软件候选；在当前权限边界内，任务 3.6、5.2 和 5.3 不得标记完成。
- 聚焦测试与构建：使用一次性 `ANDROID_HOME=/Users/back/Library/Android/sdk` 和 `ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk` 运行聚焦 JVM 测试及 `:app:assembleAppDebug`，82 个任务中 25 个执行、57 个为最新状态，构建成功。随后运行完整 `:app:testAppDebugUnitTest` 成功；将两处遗留的 220ms 测试名称和限速夹具更正为 200ms 后再次运行完整单测，57 个任务中 14 个执行、43 个为最新状态，构建成功。
- lint：单独运行 `:app:lintAppDebug`，结果仍为 14 个错误、884 个警告、18 个提示并以失败退出。14 个错误全部位于既有 `AndroidManifest.xml`、`MainViewModel.kt`、`BookType.kt`、`LayoutManager.kt`、`ViewExtensions.kt`、`NavigationViewUtils.kt` 和既有缺失翻译；没有错误指向本变更新增的 Delegate、时间线、状态控制器、渲染器、测试或新字符串。任务 6.2 继续保持未完成。
- 干净 APK：构建产物为 `app/build/outputs/apk/app/debug/legado_app_3.26.081316.apk`，SHA-256 为 `29d879aa13abf0299e1584d890c898e5f8d6cb062073e0b7701969067d6029a2`。`aapt` 确认为 `io.legado.app.debug`、`versionName=3.26.081316debug`、最低 API 21、目标 API 36；`apksigner` 验证 v1、v2 签名通过，签名者为 Android Debug 证书。
- 私有接口审计：APK 的 `classes*.dex` 与压缩包清单均未匹配已删除的 `KindlePageTurnDebugActivity`、`com.hmct.epd`、`IEpdManager`、动态刷新属性、framework/services 提取物或海信 EPD 控制入口。
- 安装：只对上述 `io.legado.app.debug` APK 执行 `adb install -r`，返回 `Success`。调试版更新为 `3.26.081316debug`；正式版路径、版本和安装时间前后不变，安装后设备仍为动态标志 `0`、清晰配置 `3`。
- 最终基础检查：`openspec validate --all --strict` 通过，2 项通过、0 项失败；`git diff --check` 以及对全部未跟踪文件逐一执行的等价空白检查均通过。任务 6.4 完成；任务 3.6、4.3、5.2、5.3 和 6.2 保持未完成。

## NO-GO 归档收口

- 归档日期：2026-08-13；归档目录为 `openspec/changes/archive/2026-08-13-add-kindle-page-turn-animation/`，使用 `--skip-specs`，没有创建或修改 `openspec/specs/kindle-page-turn-animation/`。
- 未推广实现快照：实验分支 `codex/add-kindle-page-turn-animation` 的提交 `5ef423314`。该提交只用于保留软件候选、状态机、测试和复现上下文，不得整体合入主分支，不得作为功能完成或发布依据。
- 主线允许回流范围仅为本归档目录中的事实记录；Android 实现、资源、测试、调试 APK 和增量能力规范均不推广。
- 最终结论：Hi Reader Pro 普通应用权限下的 Canvas 路线为 `NO_GO_CLEAR_AND_ANIMATED`，标准事件组合刷新桥接为 `NO_GO_UNSAFE_EVENT_BRIDGE`。如需重启该方向，必须先明确批准海信私有接口、厂商 SDK、系统签名或 root 等新的权限边界，并建立新的 OpenSpec 变更。
