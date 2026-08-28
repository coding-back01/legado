# PR 4r：剩余 Warning 最终三态审计证据

## 串行前置条件

前一批 GitHub PR #70 的最终 head 为
`180fac6ed8916d0450b7041f49b74faba64a8c07`，merge commit 为
`d5c1065ae76898008bea2c6e8df8f45f247e119f`。最终 head 的
`Android Debug 验证` run `33191500939` 和合并后 `master` 的 `Test Build` run
`33191933272` 均成功。合并后本地 131 个 Gradle 任务成功，129 个单元测试
0 失败、1 跳过，Android lint 为 0 error、104 warning、18 hint，Debug 构建、
OpenSpec 严格校验和空白检查均成功。满足串行条件后才从该 merge commit 创建
`codex/warnings-final-audit`。

本批开始提交为 `622de601d65076edbc86ac6e2486996e25907dfa`，基点、当前本地
`master` 与 `origin/master` 均为上述 merge commit，开始时工作区干净。

## 机器清单与数量守恒

事实源为 PR #70 合并后本地强制运行生成的
`app/build/reports/lint-results-appDebug.xml`，SHA-256 为
`0a2edcde943acb4ca3d1084d94fb29a977a666305cd61727137521d342844b2e`。
该报告包含 104 个 warning，没有 error；文本报告另记录 18 个 hint。此前账本已有
765 项 `FIXED`、12 项 `SUPPRESSED_WITH_REASON`、45 项 `DEFERRED`，剩余
59 项 `PENDING_REVIEW` 精确分布如下：

| lint ID | 待审数量 |
|---|---:|
| `AndroidGradlePluginVersion` | 4 |
| `DiscouragedApi` | 11 |
| `GradleDependency` | 14 |
| `IconDuplicates` | 1 |
| `IconLocation` | 6 |
| `NewerVersionAvailable` | 21 |
| `UnusedAttribute` | 1 |
| `VectorPath` | 1 |
| **合计** | **59** |

审计后这 59 项全部进入 `DEFERRED`，没有新增全局或局部抑制，也没有生产源码、
依赖或资源修改。最终账本满足：

```text
765 FIXED + 12 SUPPRESSED_WITH_REASON + 104 DEFERRED = 881 原始 warning
104 当前 warning = 104 DEFERRED
PENDING_REVIEW = 0
```

## 工具链与依赖：39 项

这些 occurrence 只表示有新版本可用，消除它们必须实际升级工具链或依赖。总变更已
明确禁止把工具链大版本、普通依赖和带兼容说明的固定依赖混入 warning 清理，因此不以
版本数字较新为行为等价证据。

| lint ID | 精确坐标与内容 | 数量 | 结论 |
|---|---|---:|---|
| `AndroidGradlePluginVersion` | `gradle/wrapper/gradle-wrapper.properties:4` 的 Gradle 8.13；`gradle/libs.versions.toml:5` 的 AGP 8.13.2 对 application/library/test 各报 1 项 | 4 | Gradle/AGP 升级会改变 JDK、插件和构建输出矩阵；转入独立工具链 OpenSpec 变更 |
| `GradleDependency` | `gradle/libs.versions.toml:6,8,9(2),11(3),22,23,34,80,91,97,149`，对应 AppCompat、ConstraintLayout、Core/Core KTX、Fragment 三模块、Material、Media、Collection、Annotation 与三项 AndroidX Test | 14 | 运行库与测试库逐坐标独立升级，分别完成 API 21、生命周期、主题和设备测试回归 |
| `NewerVersionAvailable` | `gradle/libs.versions.toml:3(6),15(6),16,17,18,19(2),25,36,56,137`，对应 Kotlin 六插件、Glide 六模块、Gson、JSONPath、JsoupXpath、Coroutines 两模块、OkHttp、ZXing、Rhino、Glide Compose | 21 | 包含工具链、主版本、beta 与最低 Android 风险；`rhino:56` 还有 Android 8 以下兼容固定说明，必须拆分升级 |

重新启动条件是按工具链、图片栈或单个运行依赖建立独立变更，逐项核对发行说明、最低
API 和迁移要求，并至少完成 JDK 17 全模块构建、单元测试、lint、Debug APK，以及受
影响的解析、网络、图片、二维码、JavaScript 或设备路径。仅更新版本以消除 lint 不可接受。

## `DiscouragedApi`：11 项

### 九个方向继承

`app/src/main/AndroidManifest.xml:205,210,215,220,225,230,235,240,245` 分别为
关于、书源管理、RSS 源管理、TXT 目录规则、替换规则、书籍管理、书源调试、目录和
正文搜索页面的 `android:screenOrientation="behind"`。Android 16 开始系统在多数
场景忽略固定方向，但直接删除仍会改变 Android 16 以下设备从调用页面继承方向的行为。

本轮没有九个页面在 API 21/36、横竖屏、手机/平板和多窗口下的自动化与截图证据，
因此 9 项延期。重新启动需覆盖这些组合，确认删除后页面创建、配置变化、返回栈和阅读
方向均不回归，不能只以 Android 16 的新行为推断旧系统等价。

### 两个动态图标资源名

`app/src/main/java/io/legado/app/lib/prefs/IconListPreference.kt:45,174` 根据字符串调用
`getIdentifier(..., "mipmap", packageName)`。名称来自
`app/src/main/res/values/array_values.xml:4-12` 的 `icons` array，经
`IconListPreference_icons` styleable 读取并通过 `Bundle` 的 `iconNames` 传入重建后的
对话框。改成资源 ID 会同时改变 XML array、styleable 与 Bundle 恢复合同，不是局部
机械替换，也不是已证明的误报。

2 项延期。重新启动需覆盖旧 `launcherIcon` 偏好、全部七个 launcher alias、配置变化后
对话框恢复和找不到资源时的 fallback，再决定引入 typed array 或显式名称到资源 ID 映射。

## 图标资源：7 项

### `IconLocation` 六项

| 文件 | 像素尺寸 | 已确认生产用途 |
|---|---:|---|
| `app/src/main/res/drawable/icon_read_book.png` | 200×200 | 欢迎页、朗读/音频通知和三个快捷方式 |
| `app/src/main/res/drawable/image_cover_default.jpg` | 600×900 | 书架、书籍详情、搜索和默认封面 |
| `app/src/main/res/drawable/image_legado.png` | 192×192 | RSS 页面图标 fallback |
| `app/src/main/res/drawable/image_loading_error.png` | 512×512 | 图片加载错误 fallback |
| `app/src/main/res/drawable/image_rss.jpg` | 500×500 | RSS 源占位和错误图 |
| `app/src/main/res/drawable/image_rss_article.jpg` | 500×500 | RSS 文章占位图 |

把 densityless drawable 移到 `drawable-nodpi` 会停止现有密度缩放，放入单一密度目录或
自动生成多密度版本也会改变像素大小、内存占用和视觉结果。六项全部延期。重新启动需为
各密度的书架、RSS、欢迎页和错误图建立截图/像素尺寸基线，并覆盖通知和快捷方式图标。

### `IconDuplicates` 一项

`app/src/main/res/drawable/image_legado.png` 与
`app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` 字节相同，SHA-256 均为
`514f0a45caea8ebb96d9f3a5afce92efb669dad89b210b81602c33fc55a681d5`。
前者是 RSS 页面 drawable，后者是 xxxhdpi 应用图标；字节相同不能证明资源类型、密度
和调用语义相同。直接复用任一资源会改变另一处缩放，因此延期。重新启动需覆盖 RSS
fallback、应用图标和 launcher alias 在 API 21/36 与多密度下的打包和截图证据。

## 其余资源：2 项

### `UnusedAttribute`

`app/src/main/res/layout/item_rss.xml:9` 的
`android:foreground="?android:attr/selectableItemBackground"` 只在 API 21/22 被忽略，
在 API 23+ 为 RSS 项提供点击 ripple。直接删除会改变现代设备交互；复制整个布局到
`layout-v23` 会制造两份结构可能漂移的长期风险。该项延期，重新启动需覆盖 API 21、23、
36 的点击、长按、焦点、UI 层级和 ripple 截图，再选择版本化布局或等价兼容实现。

### `VectorPath`

`app/src/main/res/drawable/ic_launcher3.xml:7` 的首个 path 为 6,019 字符，并被
`mipmap-anydpi-v26/launcher3.xml` 同时用于 adaptive foreground 与 monochrome。
降低精度、删细节或栅格化都会改变图标，当前没有可接受误差的原图与像素基线。该项延期，
重新启动需保存可追溯原图，并覆盖 API 26+ adaptive/monochrome 与多密度像素差异。

## 本地全量验证

本批只修改中文维护账本和 OpenSpec 审计证据，不修改最低 API 21、Room schema、规则、
导入 URI、备份、依赖版本、包名、签名、Manifest 行为或任何生产资源。`TrimLambda=17`
和 `ReportShortcutUsage=1` 仍为 hint，不进入 warning 三态。

2026-08-29 使用 JDK 17.0.17 和固定 Android SDK 执行：

```bash
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug \
  :app:assembleAppDebug --rerun-tasks --no-daemon \
  --warning-mode all --console=plain
```

构建在 3 分 6 秒内成功，131 个 Gradle 任务全部实际执行。测试 XML 汇总为 28 个
测试套件、129 个测试、0 失败、0 error、1 跳过；Debug APK
`legado_app_3.26.082901.apk` 构建成功。lint XML 为 0 error、104 warning，文本报告
另含 18 hint。三份报告 SHA-256 为：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `0a2edcde943acb4ca3d1084d94fb29a977a666305cd61727137521d342844b2e` |
| `lint-results-appDebug.html` | `bc53d1aa065c025f3b2b6d0fd884006f47827ece1ba79d770aaf518e08ad09de` |
| `lint-results-appDebug.txt` | `72c5d488bb734d04575002c4c934c197b2c36071bf3cc1d7665d3c9f69bcb5fa` |

只读对账脚本逐 lint ID 比较维护表当前数量与 XML occurrence，并校验每行
`FIXED + SUPPRESSED_WITH_REASON + DEFERRED + PENDING_REVIEW = 原始数量`；26 个 ID
全部通过，总计为 881/104/765/12/104/0，没有 XML 外游离 ID。

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check` 无输出。
实时 `adb devices -l` 为空，设备测试未运行，也不将其描述为通过。

## 待完成 PR 门禁

本地验证已满足 draft PR 前置。仍须提交并创建 PR 4r，等待最终 head 的远端检查成功，
使用 merge commit 合并，并在合并后 `master` 上再次完成远端和本地 131 任务双重复验。
在该闭环完成之前，任务 5.6、5.7、5.8、5.9 保持未完成，正式发布继续冻结。
