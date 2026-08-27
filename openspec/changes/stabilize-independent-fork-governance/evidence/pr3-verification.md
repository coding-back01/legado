# PR 3 阻断错误验证证据

本文记录 `codex/quality-blockers` 对 Android 与网页端阻断错误的 RED/GREEN、构建和自动化验证。只有实际完成的命令才记为通过；尚未修复的 lint error 保持 RED。

## 4.1 AndroidX Startup

修改前的 `app/build/reports/lint-results-appDebug.xml` 包含 1 个 `MissingClass`：

- `app/src/main/AndroidManifest.xml:515`
- `androidx.startup.InitializationProvider`

依赖调查使用 `:app:dependencyInsight --configuration appDebugRuntimeClasspath --dependency androidx.startup:startup-runtime`，确认修改前运行时已经通过传递依赖解析到 `androidx.startup:startup-runtime:1.2.0`。PR 3 只把同一个版本加入 version catalog 和 `app` 的直接 `implementation`，没有升级最终解析版本或其他依赖。

修改后使用 JDK 17 和本机 Android SDK 执行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:lintAppDebug
```

结果为 13 个 error、880 个 warning、18 个 hint；`MissingClass` 从 1 降为 0。命令仍因后续任务对应的 13 个 error 退出 1，不将其描述为 lint 已通过。

随后执行同一固定环境下的 `./gradlew :app:assembleAppDebug`，构建成功。使用 Android build-tools 35.0.0 的 `aapt dump xmltree` 检查 `app/build/outputs/apk/app/debug/legado_app_3.26.082711.apk`，二进制 Manifest 中恰有 1 个 `androidx.startup.InitializationProvider`，authority 为 `io.legado.app.debug.androidx-startup`，没有新增异常 Provider。

## 4.2 并发 key set 的 API 21 接口

新增 `MainViewModelConcurrentSetTest`，先在原始推断类型上运行聚焦测试，2 个测试中 1 个按预期失败。将字段收窄为公开接口：

```kotlin
private val onUpTocBooks: MutableSet<String> = ConcurrentHashMap.newKeySet()
```

实现仍使用并发 key set，8 个线程的重复添加与删除测试通过。修改后 2 个测试全部通过，完整 lint 中 `NewApi` 从 2 降为 0；最低 API 仍为 21。

## 4.3 书籍类型位掩码

新增 `BookTypeTest`，固定验证 8 个既有数值、每个值只占一个二进制位以及组合类型保留成员语义。旧注解契约下 3 个测试中 1 个按预期失败；将 `@IntDef` 改为 `flag = true` 后 3 个测试全部通过。常量使用等价的 `1 shl 3` 至 `1 shl 10` 表达，避免引入新的 `ShiftFlags`，最终 lint 中该 ID 为 0。

## 4.4 RecyclerView 方向契约

新增 `LayoutManagerOrientationTest`。旧的重复 typedef 下 2 个测试中 1 个按预期失败；删除自定义 `@IntDef` 并把三个方向参数改为 `@RecyclerView.Orientation` 后 2 个测试全部通过。测试同时断言横向值为 0、纵向值为 1，以及三个 layout manager 仍原样透传方向和反向布局参数。

## 4.5 输入法和导航列表公共 API

新增 `UiInputNavigationContractTest`。旧实现同时使用结果常量 `RESULT_SHOWN` 和 Material 内部 `NavigationMenuView`，2 个测试全部按预期失败。修改后输入法使用 `SHOW_IMPLICIT`，导航列表只依赖公开 `RecyclerView`，2 个测试全部通过；完整 lint 中 `RestrictedApi` 与对应 `WrongConstant` 均为 0。

## 4.6 缺失翻译与模拟器界面证据

新增 `BlockingTranslationResourceTest`，逐 XML 解析 8 个现有资源集合并要求以下 6 个键均存在且非空：

- `custom_export_section`
- `del_all`
- `system_media_control_compatibility_change`
- `system_media_control_compatibility_change_summary`
- `read_aloud_pause_resume`
- `play_mode`

旧资源下 2 个测试全部按预期失败。补齐西班牙语、日语、巴西葡萄牙语、越南语和三个中文变体后，2 个测试全部通过；默认 `play_mode` 为英文 `Play mode`，简中、港繁和台繁均为“播放模式”。完整 lint 中 `MissingTranslation` 从 6 降为 0。

模拟器使用重新安装的 API 30 Google APIs arm64 镜像，序列号为 `emulator-5554`。首次启动确实被“User Privacy and Agreement”阻断，自动化没有点击 `AGREE`；原始证据位于 Git 忽略目录，哈希如下：

- `launch-en.png`：`42de466e90975e798bf130eb26d0e2f1607139002ab2042a4b53465cc8f8ad23`
- `launch-en.xml`：`d43ffdad4d064ff92e070dd5b9b5e04d660a85ef84828265a8f16347d11637df`

为继续验证资源切换，只在断开 Wi-Fi 和移动数据的可丢弃 Debug 模拟器沙箱中直接写入测试状态；这不是替用户接受协议，也没有作用于真机。随后通过 UI 自动选择简体中文并采集“其它设置”页：

- `config-zh.png`：`f417943517e2ebe4f91ce22fc3b2ff4255dc8956c31f39b614446892b61b02fa`
- `config-zh.xml`：`80c3b139221e6761e0bc44cdffecba202a114c46f8520eade5a57e055c6ca3ec`

截图和 hierarchy 能确认语言切换成功、页面文字可见且未出现明显截断。6 个目标键的完整 locale 覆盖由资源测试验证；本证据不把未逐页展示的纯装饰质量描述为已验证。

## 4.7 三类高风险 warning

新增 `HighRiskWarningContractTest`，先在旧实现上运行并得到 4/4 RED：

- 土耳其语默认 locale 会破坏内部 ASCII 标识的忽略大小写比较；
- 8 处内部大小写转换没有显式 `Locale.ROOT`；
- QQ 群跳转 Intent 没有 action；
- App Bundle 没有禁用语言资源拆分。

修复后使用 `Locale.ROOT` 处理 8 处内部标识，QQ 跳转使用显式 `Intent.ACTION_VIEW`，并设置 `bundle.language.enableSplit = false`。另为后续阻断策略增加契约 RED，再将 `IntentWithNullActionLaunch`、`DefaultLocale` 和 `AppBundleLocaleChanges` 提升为 lint fatal。最终聚焦测试为 5/5 GREEN。

2026-08-27 使用固定 JDK 17 和 Android SDK 再次运行 `:app:lintAppDebug`，退出码为 0，结果为 0 error、869 warning、18 hint；三个高风险 ID 均从 `1 / 8 / 1` 降为 0。QQ Intent 的结构调整同时安全消除了 1 个 `UseKtx`，没有全项目机械转换。

## 4.8 网页端 ESLint

使用 `corepack pnpm` 读回版本 9.15.9 后运行只读 ESLint，旧实现稳定复现 2 个 error、0 个 warning：

- `src/source.d.ts:82:6`：未使用的 `RuleSearch`；
- `src/utils/souce.ts:52:41`：显式 `any`。

删除未使用声明，并让 `normalizeSource` 接受 `object`、在函数内部以 `Record<string, unknown>` 访问可变键，不改变递归清理空值的行为。修改后 `corepack pnpm type-check` 和 `corepack pnpm exec eslint .` 均以退出码 0 完成，ESLint 为 0 error、0 warning。

## 4.10 PR 3 全量验证

2026-08-27 在最终工作区执行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug :app:assembleAppDebug --console=plain

cd modules/web
corepack pnpm install --frozen-lockfile
corepack pnpm type-check
corepack pnpm exec eslint .
corepack pnpm build

openspec validate --all --strict
git diff --check
```

实际结果：

- Android JVM 单元测试成功；翻译内容复核调整后，又使用 `--rerun-tasks` 单独强制执行 `BlockingTranslationResourceTest`，2/2 通过。
- Android lint 成功，0 error、869 warning、18 hint；五类原始阻断 error 和三个提升为 fatal 的高风险 ID 均为 0。
- Debug APK 构建成功。
- `corepack pnpm` 为 9.15.9，冻结安装确认 lockfile 无漂移；类型检查、ESLint 和构建均成功，ESLint 为 0 error、0 warning。
- 本地普通构建按脚本设计不复制 Android Web assets；PR CI 使用 GitHub Actions 环境复制后，必须进一步核对同步产物并提交预期差异，见下节。
- OpenSpec 严格校验为 3 passed、0 failed。
- `git diff --check` 成功。
- 改动未涉及最低 API 21、Room schema、书源/订阅源规则、导入 URI、备份格式、普通正式版包名、正式签名材料或历史 Release 资产。

### PR 首轮 Web 检查修复

PR #52 首轮 `Build Web / build` 在源码、类型检查和 16 个章节 HTML 安全测试全部通过后失败。失败点是 workflow 设置 GitHub Actions 环境并执行同步时，检测到 3 个旧哈希 JavaScript bundle 被 3 个新哈希 bundle 替换，且 `index.html` 入口随之变化；本地普通构建此前按 `sync.js` 的设计跳过了这一步。

使用固定 pnpm 9.15.9 和与 CI 相同的 `GITHUB_ENV` 条件重新构建，得到与失败日志完全一致的文件名：

- 删除 `BookChapter-Cx-OjwJt.js`、`BookShelf-CuH7u1Ed.js`、`index-BnnocMgN.js`；
- 新增 `BookChapter-CdJY7wEM.js`、`BookShelf-CtS426Fi.js`、`index-DjYVT5-l.js`；
- 更新 `app/src/main/assets/web/vue/index.html` 的入口哈希。

这些是固定 lockfile 和现有 Web 源码的生成结果，不包含依赖范围修改。提交同步产物前后再次运行 Web 冻结安装、章节 HTML 测试、类型检查、ESLint、GitHub Actions 等价构建和 Android Debug 构建；只有生成目录不再产生未提交差异后才允许 PR 重新进入合并检查。
