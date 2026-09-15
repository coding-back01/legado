## Context

见 `proposal.md` 的动机与范围。当前可复现事实源是 `:app:lintAppDebug --rerun-tasks` 生成的 XML：108 个 warning 和 18 个 hint；版本元数据可能使具体数量在 Apply 开始时漂移，因此实现不能只依赖文档中的静态总数。现有维护账本已记录每类风险和重启条件，但仍允许 108 项处于 `DEFERRED`；现有路线图还要求按兼容域拆成多个 OpenSpec 变更，本设计按用户决定改为一个变更的一次 Apply。

仓库最低支持 API 21，目标 SDK 为 36。书源规则、Cookie、URL、备份、导入 URI、Room 数据和 `launcherIcon` 偏好属于兼容敏感边界。当前本机已有 API 21、30、36 AVD，没有 API 23 AVD，也没有连接 Debug 测试设备；这些缺口必须在 Apply 修改生产代码前补齐。

## Goals / Non-Goals

**Goals:**

- 在一个 OpenSpec 变更的一次连续 Apply 中完成全部 warning 与 hint 的处置，并让最终 lint XML 不含任何 issue。
- 以聚焦测试、固定模拟器 golden 和授权 Debug 真机证据区分安全修复与必须保留的兼容行为。
- 让所有抑制精确、可解释、可重新启动，禁止 baseline 和按 ID 全局关闭。
- 建立能同时阻断 error、warning、hint 以及报告缺失的持续 CI 门禁。

**Non-Goals:**

- 不借机升级 Gradle、AGP、Kotlin、KSP、AndroidX、Glide 或其他依赖。
- 不改变最低 SDK、Room schema、JSON、规则格式、导入 URI、备份、包名、签名或发布流程。
- 不引入第三方截图框架，不处理 Web 项目，也不在正式版用户数据空间执行验证。

## Decisions

### 1. 一次 Apply 使用前置预检和内部止损点

Apply 首先只执行只读或环境准备检查：工作树、JDK 17、Android SDK、API 21/23/36 AVD、可授权的 Debug 测试设备、当前 lint 强制报告以及完整 occurrence 哈希。缺少任一硬前置条件时，在修改生产文件前停止。这样既满足“一次执行”，也避免进行到真机批次才留下半成品。

前置条件满足后，在同一次 Apply 中依次执行测试基建、行为与 hint、资源、布局性能、版本提示、CI 门禁和文档收口。每一批都先增加能观察旧行为的 RED/基线证据，再实施、重跑聚焦验证并对账剩余 occurrence；失败即停止，但不创建新的 OpenSpec 变更或并行兼容域。

备选方案是继续遵循旧路线图拆成六个变更。该方案可缩小单次差异，但与用户要求冲突，因此改为同一变更内的小批次与检查点。

### 2. 运行时清单而不是静态数字决定完整范围

Apply 开始与结束都强制运行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
./gradlew :app:lintAppDebug --rerun-tasks --no-daemon \
  --warning-mode all --console=plain
```

开始报告按 severity、lint ID、文件和位置保存机器清单。当前 108/18 只是计划输入；若远端版本元数据或代码状态产生额外 occurrence，它们自动进入同一账本。结束报告必须有 0 个 `<issue>`，且检查范围继续保持 `checkDependencies = true`。

备选方案是只处理文档记录的 126 项。它无法解释用户环境中的 111 warning，也会漏掉 Apply 当天新增项目，因此不采用。

### 3. 处置矩阵固定为真实修复或精确抑制

| lint 范围 | 当前数量 | 设计处置 |
|---|---:|---|
| `AndroidGradlePluginVersion` | 4 | 在 Gradle wrapper 与 AGP 版本声明处记录逐坐标抑制、兼容理由和独立升级重启条件 |
| `GradleDependency` | 14 | 在具体 AndroidX、Material 与测试依赖版本声明处精确抑制，不修改版本 |
| `NewerVersionAvailable` | 25 | 在 Kotlin、Glide、运行库和构建插件的具体版本声明处精确抑制，不影响 Dependabot |
| `DiscouragedApi` | 11 | 九个 `screenOrientation="behind"` 保留并逐位置抑制；两个资源反射改为类型化资源 ID |
| `Overdraw` | 41 | golden 证明等价的重复背景予以删除；承担 ripple、遮罩、主题或内容语义的根背景逐布局抑制 |
| `IconLocation` | 6 | 建立多密度基线后迁入 `drawable-nodpi`，必要视图显式约束尺寸和缩放 |
| `IconDuplicates` | 1 | 保留 RSS fallback 与 xxxhdpi launcher 的不同资源语义，在精确二进制路径抑制 |
| `UnusedResources` | 2 | 删除无生产引用的进度布局和全部 locale 的 `del_all`，同步退役只为死资源存在的旧测试断言 |
| `UnusedAttribute` | 1 | 保留 API 23+ RSS foreground ripple，在该根布局精确抑制 API 21/22 无效提示 |
| `VectorPath` | 1 | 仅在像素差异阈值通过时确定性降精度；否则对 launcher3 精确路径抑制 |
| `UselessParent` | 1 | 仅在漫画菜单位置、背景和点击区域 golden 等价时扁平化；否则局部抑制 |
| `UseCompoundDrawables` | 1 | 仅在两个文件选择器的 View Binding、图标尺寸和点击区域等价时合并；否则局部抑制 |
| `TrimLambda` | 17 hint | 提取一个表达 Java `String.trim` 语义的字符串扩展，只在扩展实现处抑制一次 |
| `ReportShortcutUsage` | 1 hint | 为三个快捷方式 Intent 携带稳定 ID，并只在对应入口实际消费后报告一次 |

每次抑制必须能映射回 lint occurrence。Kotlin 使用函数或语句级注解，XML 使用目标节点的 `tools:ignore`，版本目录使用紧邻版本声明的 `noinspection`。lint 31.13.2 的 wrapper 文本扫描器不读取 `.properties` 中紧邻 `distributionUrl` 的 `noinspection`，因此该唯一例外同时保留声明旁的理由与重启条件，并在 `lint.xml` 只按 `gradle-wrapper.properties` 精确路径忽略；二进制资源也使用带精确路径的 `lint.xml`。不得出现只按 lint ID、没有文件范围的项目级忽略。

### 4. 兼容敏感行为采用明确的数据合同

`TrimLambda` 不能直接替换成 Kotlin 默认 `trim()`，因为后者会裁剪更多 Unicode 空白。新增命名扩展保留 `it <= U+0020`，并测试 NUL、控制字符、普通空格、不换行空格和全角空白边界；17 个调用点只改为使用该扩展。

动态 launcher 图标继续用原字符串作为偏好、entry value 和备份语义。资源数组新增并行的类型化 mipmap ID，运行时与对话框 Bundle 使用 ID 渲染，同时保留字符串用于持久化和恢复；测试覆盖七个 alias、未知旧值 fallback 和配置重建。

三个动态快捷方式分别使用 `bookshelf`、`lastRead`、`readAloud` 稳定 ID。创建 Intent 时附加内部来源与 ID，只有 MainActivity、ReadBookActivity 或 SharedReceiverActivity 确认消费对应快捷方式路径后才调用使用报告；普通启动和 Intent 重放不得误报。

九个 `behind` 在 Android 16 以下仍承担保留平台与厂商方向处理语义的兼容价值，当前不删除。设备验证必须记录平台实际解析结果，不把单个 AVD 的结果外推为所有系统均必然继承；API 21 AVD 已观察到横屏父页启动 `behind` 子页时子页仍解析为竖屏，但返回栈恢复横屏。API 21/36 手机与平板、横竖屏、多窗口和返回栈证据确认局部抑制没有改变运行时属性后，在每个 manifest activity 位置写明 Android 16 大屏会由系统忽略的事实和后续适配重启条件。

### 5. 无第三方依赖的 golden 测试

截图基建沿用现有 Android 测试 runner 与平台能力：测试夹具显式启动目标 Activity 或 Dialog，等待主线程空闲并关闭动画，再使用平台截图能力捕获稳定画面。golden 作为最小 PNG 集合存入 Android 测试资源；普通验证只比较，只有带显式更新参数的本地命令可以输出新 golden，随后由维护者审查并复制回仓库。

比较器同时检查尺寸、关键区域、点击区域与像素差异，并在失败时输出 current、expected 和 diff 三份文件作为本地或 CI artifact。位图还要断言 decode 后像素尺寸，防止迁入 `drawable-nodpi` 后出现非预期缩放或内存膨胀。

矩阵使用 API 21、23、36，覆盖日夜主题；API 36 另覆盖手机、平板、旋转和多窗口；资源覆盖 mdpi 与 xxxhdpi；API 26+ 覆盖 adaptive 和 monochrome launcher。真机只验证 Debug 包的 launcher、通知、快捷方式和厂商旋转，不保存包含个人内容的原始证据。

备选方案是引入 Paparazzi 或其他截图依赖。它会扩大依赖与渲染差异范围，且本次已有设备级行为需要验证，因此不采用。

### 6. CI 以 XML 零节点作为唯一持续门禁

在现有 lint 步骤之后增加 Python 标准库脚本，验证报告路径存在、XML 可解析、根节点合法，并统计所有 `<issue>`。数量非零时按 severity 和 ID 输出摘要并返回失败；数量为零才继续。脚本本身配套仓库测试，覆盖空报告、warning、hint、缺失文件和损坏 XML。

现有 lint 报告上传继续使用 `if: always()`，使失败仍可审计。保留当前三个高风险 fatal ID；XML 门禁补足 warning 与 hint 不会让 Gradle lint 任务失败的问题。

只设置 `warningsAsErrors` 的备选方案无法阻断 hint；只搜索文本容易把 `<issues>` 根标签误认为 occurrence，也无法可靠处理损坏 XML，因此不采用。

### 7. 账本和路线图与单一变更同步收口

`docs/maintenance-baseline.md` 新增 hint 处置状态，并将最终 current、`DEFERRED`、`PENDING_REVIEW` 置零；每类 suppression 保存精确位置、理由、证据和重启条件。`docs/maintenance-risk-reduction-roadmap.md` 删除必须拆成多个 OpenSpec 的约束，改为本变更的一次 Apply 与内部串行批次，且不得把精确抑制描述为依赖已经升级或运行时风险已经消失。

## Risks / Trade-offs

- [单次变更范围大，失败定位困难] → 使用严格顺序、每批聚焦 RED/GREEN、occurrence 守恒和立即停止；不允许跨批混改或批量格式化。
- [当前没有连接 Debug 真机，无法满足一次 Apply 前置条件] → Apply 开始先检查设备；缺失时在生产文件修改前受阻，待设备就绪后再发起同一次完整 Apply。
- [golden 容易受字体、动画和系统差异影响] → 固定 AVD、字体比例、语言、主题、动画和测试数据，只保存必要区域，并对抗锯齿使用经设计批准的窄阈值。
- [精确版本抑制可能降低 lint 中的升级可见性] → Dependabot 普通更新和安全更新继续运行；抑制只覆盖已审坐标，新增坐标仍触发零 issue 门禁。
- [资源迁入 `drawable-nodpi` 会改变密度缩放] → 在修改前锁定各用途像素与截图基线，失败时回退该批并改用精确抑制。
- [Overdraw 通过抑制清零但性能未改善] → 只对承担可证明视觉或交互语义的根背景抑制，账本保留重启条件；可等价删除的背景必须实际删除。
- [快捷方式 Intent 可能被重复消费] → 来源标记和 ID 仅在匹配入口报告，保存消费状态或确保同一 Intent 生命周期只报告一次，并增加重放测试。
- [路线图从多变更改为单变更降低独立回滚粒度] → 保留内部批次证据和精确文件清单；发现回归时使用聚焦反向补丁回退对应批次，不重写 Git 历史。

## Migration Plan

1. 预检环境、设备、工作树和强制 lint 基线；任何硬前置缺失时不修改生产文件。
2. 建立 XML 对账工具、golden 基建和兼容行为测试，保存治理前基线。
3. 处理 `TrimLambda`、快捷方式、动态图标和方向继承，逐类运行聚焦测试与 lint 对账。
4. 处理位图、重复图标、死资源、RSS foreground，并完成密度、通知、快捷方式与 launcher 验证。
5. 处理 Overdraw、漫画菜单、文件路径控件和 launcher3 矢量路径，逐页面完成 golden 或精确抑制。
6. 对全部版本提示按具体坐标增加审计抑制，不升级依赖，并确认 Dependabot 配置未削弱。
7. 加入 XML 零 issue CI 门禁与脚本测试，更新账本、路线图和证据。
8. 强制运行单元测试、lint、Debug 构建、设备矩阵、OpenSpec 严格校验和空白检查；确认最终 XML 为零 issue 后完成变更。

回滚按内部批次执行：使用明确的反向补丁恢复失败批次涉及的代码、资源和测试，再重新生成 lint 清单；不得使用 `git reset --hard`、批量删除或 baseline 掩盖失败。尚未完成全部迁移步骤时不得归档本变更。
