## 实现验证记录

### RED：固定 EWMA 轨迹基线

- 命令：`ANDROID_HOME=/Users/back/Library/Android/sdk ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk ./gradlew :app:testAppDebugUnitTest --tests io.legado.app.model.read.ReadingTimeEstimatorReplayTest --no-daemon`
- 结果：`FAILED`，4 项测试中 3 项失败。
- 失败断言：
  - 单个 4 倍慢页使成熟速度变化 `59.99999999999994%`，门禁为不超过 `3%`。
  - 约 10% 慢页污染后的速度误差为 `67.21741496240776%`，门禁为不超过 `5%`。
  - 普通 9/11 秒交替抖动的最大相邻速度变化为 `2.448979591836742%`，门禁为不超过 `2%`。
- 已通过基线：正负 25% 持续变速在约 15/35 分钟轨迹上的最低跟随要求通过。该结果只说明旧 EWMA 响应足够快，不抵消上述稳定性失败。

此记录形成于任何生产实现修改之前。

### RED：新锚点、可见单位与迁移合同

- 迁移命令：`ANDROID_HOME=/Users/back/Library/Android/sdk ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk ./gradlew :app:testAppDebugUnitTest --tests io.legado.app.model.read.ReadingTimeMigrationContractTest --no-daemon`
- 迁移结果：`FAILED`，旧 `version=1` EWMA 被当前实现继续接受，失败于 `ReadingTimeMigrationContractTest.kt:28`。
- 新合同命令：`ANDROID_HOME=/Users/back/Library/Android/sdk ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk ./gradlew :app:testAppDebugUnitTest --tests io.legado.app.model.read.ReadingTimeNewContractTest --no-daemon`
- 新合同结果：编译阶段 `FAILED`，确认生产代码尚无 `VisibleTextUnits`、`ReadingTimeAnchor`、可见长度索引、剩余量置信度、紧凑诊断摘要和旧 sidecar 可见长度迁移能力。
- 锚点规格覆盖：上一可见页内容配对、短事件证据、相邻跨章、跳转重布点、暂停失效。
- 内容与迁移规格覆盖：Unicode 空白与标点、图文输入边界、原始代理校准、完整/部分剩余量置信度、旧 v1 sidecar 原始长度复用、旧 EWMA 失效和新紧凑摘要恢复。

以上 RED 已在生产实现修改前复核，后续实现不得删除或放宽失败断言来取得绿灯。

### GREEN：鲁棒轨迹、锚点、索引与迁移

- 聚焦命令：`ANDROID_HOME=/Users/back/Library/Android/sdk ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk ./gradlew :app:testAppDebugUnitTest --tests io.legado.app.model.read.ReadingTimeNewContractTest --tests io.legado.app.model.read.ReadingTimeEstimatorReplayTest --tests io.legado.app.model.read.ReadingTimeEstimatorTest --tests io.legado.app.model.read.ReadingTimeMigrationContractTest --tests io.legado.app.model.read.ReadingTimeIndexCodecTest --tests io.legado.app.model.read.ReadingTimeReadConfigTest --no-daemon`
- 结果：`BUILD SUCCESSFUL`。
- 已转绿的关键轨迹：成熟模型单个 4 倍慢页不超过 3%、约 10% 异常页误差不超过 5%、一小时 9/11 秒抖动最大相邻变化不超过 2%、正负 25% 阶跃满足 15/35 分钟跟随、异常长停留连续软饱和、重读长期证据降权和固定缓冲边界变化不超过 0.5%。
- 已转绿的结构合同：上一页耗时与上一页可见单位配对、相邻跨章、跳转与暂停重布点、Unicode 可见单位、图文连续可靠性、原始代理校准、双置信度原因、v1 sidecar 复用、v2 sidecar 往返、旧 EWMA 失效和 v2 紧凑摘要恢复。

### GREEN：完整单元测试

- 命令：`ANDROID_HOME=/Users/back/Library/Android/sdk ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk ./gradlew :app:testAppDebugUnitTest --no-daemon`
- 结果：`BUILD SUCCESSFUL`；最终组合验证共执行 52 项测试，0 失败、0 错误、0 跳过。

### 性能回归夹具

- 命令：`ANDROID_HOME=/Users/back/Library/Android/sdk ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk ./gradlew :app:testAppDebugUnitTest --tests io.legado.app.model.read.ReadingTimeEstimatorPerformanceTest --info --no-daemon`
- 本机结果：P50 `24.333 µs`，P95 `75.833 µs`，低于代码级 `0.5 ms` 门禁。
- 完整组合验证中的本机复测：P50 `37.167 µs`，P95 `96.917 µs`，仍低于代码级门禁。
- 证据边界：这是 Apple Silicon 主机 JVM 的可重复回归结果，不代表低端 Android 真机；低端真机延迟与分配另见下节。它不能证明一小时 CPU、I/O 与能耗差异。

### RED/GREEN：低端真机性能与稳态分配

- 设备：海信 `HLTE556N`，Android 11 / API 30，测试开始前电量 98%、电池温度 30.7°C、系统热状态 0；设备序列号为 `7a219732`。
- 安装边界：设备原有 `io.legado.app.debug` 与当前调试 APK 的 SHA-256 证书摘要均为 `e5529aeb53e775e4f9647f73d1fad5d39f3b4ce8855cbe562d3eea9fc7265ce7`，使用 `adb install -r` 原位升级；包 UID 和首次安装时间保持不变，未卸载或清数据。
- 夹具：`app/src/androidTest/java/io/legado/app/model/read/ReadingTimeEstimatorDevicePerformanceTest.kt` 预分配全部锚点和统计数组，预热 1,000 次后测量 2,000 次成熟样本更新；另测 480～560 次更新的固定缓冲跨界窗口，并以 `Debug.getThreadAllocCount()` 检查线程分配。
- RED 命令：`ANDROID_HOME=/Users/back/Library/Android/sdk ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk ./gradlew :app:connectedAppDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.model.read.ReadingTimeEstimatorDevicePerformanceTest --no-daemon`。
- RED 结果：延迟用例已通过，成熟更新 P50 为 `120.104 µs`、P95 为 `131.406 µs`，缓冲边界窗口 P95 为 `228.906 µs`；但 2,000 次更新分配 12,000 个对象，失败于零分配门禁。
- 修复：生产调用改走返回原始 `Boolean` 的 `advance` 采样入口，保留既有 `onForward` 作为兼容包装；进一步定位并移除 `fusedMad()` 对两个原始 `Double` 使用泛型 `takeIf` 导致的每次两次装箱。
- GREEN 命令：`adb shell am instrument -w -r -e class io.legado.app.model.read.ReadingTimeEstimatorDevicePerformanceTest io.legado.app.debug.test/androidx.test.runner.AndroidJUnitRunner`，目标包和测试包均由当前工作树的 `assembleAppDebug`、`assembleAppDebugAndroidTest` 产物原位安装。
- GREEN 结果：3 项测试全部通过；成熟更新 P50 为 `118.489 µs`、P95 为 `126.719 µs`，缓冲边界窗口 P95 为 `185.260 µs`；2,000 次成熟更新、空控制和不训练控制的线程分配对象数均为 0。
- 结论：低端参考设备 P95 低于 `0.5 ms` 门禁，稳态样本更新路径无堆分配，缓冲折叠窗口仍在同一延迟门禁内。该微基准是本次发布的定量性能门禁，但不替代一小时 A/B 的 CPU、I/O 和能耗证据。

### 翻页热路径静态审计

- 新增样本路径只读取当前内存锚点、页面预计算单位、固定 `DoubleArray` 草稿、紧凑状态和不可变索引快照。
- 可见单位在既有排版列遍历中累计；翻页时不扫描 `TextPage.text`，章节完成后只提交内存计数。
- 未新增网络、Room 查询、正文读取、JSON、章节遍历、持续定时器、`AlarmManager`、WorkManager、服务或唤醒锁。
- sidecar 仍使用原有单线程低优先级扫描、约 500 ms 快照防抖和约 30 秒写入防抖；速度状态仍使用约 120 秒批量保存，并在暂停或换书时刷新。

### 构建、lint 与范围校验

- 组合命令：`ANDROID_HOME=/Users/back/Library/Android/sdk ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk ./gradlew :app:testAppDebugUnitTest :app:lintAppDebug :app:assembleAppDebug --no-daemon`。
- 单元测试与 `assembleAppDebug` 已完成；最终复核于 2026-08-22 重新执行组合命令并成功，52 项测试全部通过，生成 `app/build/outputs/apk/app/debug/legado_app_3.26.082216.apk`。
- `lintAppDebug` 未通过：仓库现有基线共有 14 个 error、881 个 warning、18 个 hint；首项为 `AndroidManifest.xml:515` 的 `androidx.startup.InitializationProvider` 缺失，其余 error 位于既有 `MainViewModel.kt`、常量/布局工具和既有翻译资源。本变更新增或修改的阅读时间文件没有 lint error。未修改依赖，也未生成或放宽 lint baseline。
- `openspec validate --all --strict`：3 项通过、0 项失败。
- `git diff --check`：通过。
- 范围审计：未修改 Room schema、Gradle 依赖、权限、Manifest、签名材料、网页端或无关生成文件。

### 真机证据状态

- `/Users/back/Library/Android/sdk/platform-tools/adb devices -l` 已确认 `HLTE556N` 以 `device` 状态连接；低端真机性能与分配门禁已经完成。
- 同机基线为 `io.legado.app.release` `3.26.081119` / versionCode `16568`，书籍配置仍保存 v1 固定 EWMA；新版为 `io.legado.app.debug` `3.26.082215debug` / versionCode `16570`。普通版书架候选长篇共 972 章，目录 `wordCount` 合计 `3,240,898` 字，满足 A/B 文本规模要求。
- 当前设备由外部电源供电，电量 100%、电池温度 30.9°C。`dumpsys batterystats` 的 UID 估算是自既有放电统计窗口以来的累计值：普通版已混入约 1 小时 45 分钟历史，调试版当前窗口没有足够独立能耗数据；设备没有 `powerstats` 服务，`current_now`、`voltage_now` 和 `charge_counter` 对 shell 均返回 `Permission denied`。
- 因此在不重置系统电量历史、不伪造拔电状态且不要求用户提供约 10 小时独占设备的前提下，当前无法完成 5 组旧/新各一小时交叉 A/B，也无法支持 3% 系统估算能耗差异结论。该状态按规范记为“证据噪声不足”，不是功耗通过；未执行 `batterystats --reset`、`dumpsys battery unplug` 或其他会改变系统电量统计的命令。
- 墨水屏人工轨迹中的连续一小时、新旧配对、孤立慢页、持续变速、长停留、重读、图文混合和目录追加尚未形成完整实际显示记录；这些项目已转为发布后单用户观察清单，不作为本次发布前的已验证事实。

### 风险接受与发布判定

- 产品所有者明确拒绝约 10 小时的 5 组交叉 A/B 和整套人工轨迹成本，接受本功能仅供其本人使用时的灰度风险，并要求先验证、再归档、再发布，在日常阅读中持续观察。
- 本次发布门禁以已完成的确定性轨迹、52 项完整单元测试、低端真机 P95、稳态零分配、热路径静态审计、调试包构建、OpenSpec 严格校验和范围审计为准。
- 尚未证明一小时新增进程 CPU 时间不超过 1 秒或估算能耗差异不超过 3%，也未完成整套真机显示轨迹；不得在发布说明中把这些项目表述为已通过。
- 发布后若观察到异常耗电、发热、卡顿、写盘或 ETA 稳定性回归，应停止继续推广并评估回退；具备可靠计量条件时可补做交叉 A/B。
- `lintAppDebug` 仍被仓库既有问题阻塞，但本次范围没有新增 lint error；该既有基线不阻止本次单用户灰度。

## OpenSpec 对照验证报告

| 维度 | 结果 |
| --- | --- |
| 完整性 | 48/48 项任务完成；9/9 项 delta requirement 有实现或流程证据 |
| 正确性 | 40 个场景均已映射；37 个由代码、单元测试、真机微基准或静态审计覆盖，3 个条件性功耗/灰度场景由本节风险边界和发布后处理规则覆盖 |
| 一致性 | 实现遵循稳定锚点、可见 Unicode 单位、对数域鲁棒融合、双置信度、sidecar v2、紧凑状态和事件驱动低功耗设计 |

### Requirement 与证据映射

- **阅读速度模型自然收敛并稳健响应变化**：`ReadingTimeEstimator.kt:367` 实现近期/长期鲁棒融合；`ReadingTimeEstimatorReplayTest.kt:11`、`:25`、`:42`、`:59` 分别覆盖孤立慢页、10% 污染、一小时抖动和正负 25% 阶跃。
- **剩余时间估算不得显著增加功耗**：`ReadingTimeEstimatorDevicePerformanceTest.kt:16`、`:35`、`:54` 覆盖低端真机延迟、缓冲折叠和稳态零分配；`ReadBook.kt:358` 走无分配 `advance` 入口；翻页热路径审计见本文件前文。旧/新一小时 CPU 与 3% 能耗差异未量化，按产品所有者决定进入单用户灰度观察。
- **每本书独立学习有效阅读速度**：`ReadingTimeEstimator.kt:421`～`:512` 实现锚点恢复、重布点和相邻前进结算；`ReadBook.kt:311`～`:370` 接入实际可见页面；`ReadingTimeNewContractTest.kt:21`、`:43`、`:64` 与 `ReadingTimeEstimatorTest.kt:159`、`:175` 覆盖错页配对、微事件、跨章、跳转和遮挡。
- **学习达到最低证据后才显示预计值**：`ReadingTimeEstimator.kt:388`～`:402` 暴露双置信度诊断，`:540`～`:544` 同时检查速度与剩余量置信度；`ReadingTimeEstimatorTest.kt:13`、`:59`、`:103` 和 `ReadingTimeNewContractTest.kt:116`、`:139` 覆盖自然解锁、长期低置信、剩余量不足与成熟摘要恢复。
- **系统自动选择三级剩余量估算**：`ReadingTimeEstimator.kt:240`～`:339` 构建可见量、原始代理稳健比例、退化估计、前缀和与置信度；`ReadingTimeNewContractTest.kt:89`、`:105` 和 `ReadingTimeEstimatorTest.kt:68`、`:85`、`:211` 覆盖代理校准、全量可见量、混合退化与精确索引替换。
- **建立估算信息不得主动获取内容**：`TextLine.kt:82`、`TextPage.kt:103`、`TextChapter.kt:284`、`TextChapterLayout.kt:151` 在既有排版流程机会式累计；`ReadingTimeIndexManager.kt:86` 接收已排版章节计数，既有低优先级索引只读取元数据。静态审计确认未新增 ETA 专属下载、正文扫描、充电或息屏任务。
- **预计时间随阅读行为更新且避免独立刷新**：`ReadBook.kt:339`～`:370` 仅在阅读事件后更新模型，`:506`～`:508` 仅替换索引与内存估算；确定性阶跃和孤立异常测试覆盖速度变化，既有分钟显示去重测试保持绿色。
- **估算状态兼容现有数据和备份**：`ReadingTimeState` 当前版本位于 `ReadingTimeEstimator.kt:15`～`:63`，迁移边界位于该文件末尾；`ReadingTimeIndexCodec.kt` 兼容读取 v1 并写入 v2；`ReadingTimeMigrationContractTest.kt:11`、`ReadingTimeNewContractTest.kt:128`、`:139` 与 `ReadingTimeReadConfigTest.kt:18`、`:27` 覆盖旧 EWMA 失效、旧 sidecar 复用、紧凑摘要恢复和 JSON 往返。
- **翻页热路径保持常数复杂度**：`ReadingTimeEstimator.kt:468` 使用固定容量原始数组和复用草稿；`ReadingTimeEstimatorReplayTest.kt:88` 验证缓冲折叠变化低于 0.5%；真机性能测试验证 P95 和零分配；`ReadingTimeIndexManager.kt` 沿用低优先级分片任务和防抖写入。

### 问题分级

**CRITICAL**：无。

**WARNING**：

1. 未执行 5 组旧版/新版各一小时交叉 A/B，不能证明新增进程 CPU 时间不超过 1 秒或能耗差异不超过 3%。处理：发布说明明确该边界，发布后仅由产品所有者单用户观察，异常时停止推广并回退或修复。
2. `lintAppDebug` 仍因仓库既有 14 个 error 失败。处理：本次修改文件无新增 lint error，调试包构建、52 项单元测试和范围审计均通过；既有 lint 基线单独治理。

**SUGGESTION**：具备可隔离功耗计量设备后，可补做交叉 A/B，将主观灰度观察升级为定量能耗证据。

### 最终判定

无关键问题。上述两项警告已被如实记录，其中高成本人工与定量功耗验证已由产品所有者明确接受为单用户灰度风险；变更可以进入归档。
