# PR 5b 模拟器发布烟测证据

本文记录 `codex/release-emulator-smoke` 的离线夹具、聚焦 instrumentation、模拟器
安全边界和本地验证。这里的运行用于证明 PR 5b 测试实现可用，不属于任务 7.5–7.7 的
合并后发布预检；只有本 PR 合并且 `master` 全部聚合门禁绿色后，才能把同一套测试代码
用于发布证据。

## 验证对象

- 基线：`master@580f1913882e8c24f7a3b9a56dbe9d99791edd61`
- 分支：`codex/release-emulator-smoke`
- 日期：2026-08-30
- Debug 包名：`io.legado.app.debug`
- test APK 包名：`io.legado.app.debug.test`
- 范围：离线 TXT 完整路径、离线书源导入预览取消、既有 ReaderProvider 设备测试修正

## 离线 TXT 完整路径

`release-smoke-sentinel.txt` 是 4,985 字节的版本化测试文本，包含唯一标记
`LEGADO_RELEASE_SMOKE_SENTINEL_20260830` 和足以在常见模拟器页面生成多页正文的稳定
内容。测试只把它复制到目标 Debug 应用自己的缓存目录，不访问公网或用户文件，并执行：

1. 通过生产 `LocalBook.importFile` 导入测试 TXT，断言书籍进入 Debug 数据库；
2. 打开生产 `ReadBookActivity`，等待正文分页完成且 `pageSize > 1`；
3. 从生产阅读模型读取正文并断言唯一 sentinel，不使用 OCR 或截图代替正文判断；
4. 在主线程调用生产 `ReadBook.moveToNextPage()`，断言页内位置增加；
5. 返回后重新读取数据库，断言翻页进度已经持久化；
6. 打开 `MainActivity`，断言书架容器可见且测试书仍然存在；
7. 测试结束删除测试书、章节和缓存夹具。

烟测开始前会把隐私确认和首次使用状态设置为测试值，并关闭自动刷新与进度同步。上述写入
只发生在脚本清空后的一次性 Debug 模拟器数据空间，不能在用户真机或正式包上运行。

## 书源导入预览取消路径

书源夹具 `release-smoke-source.json` 为 342 字节，包含唯一 URL
`release-smoke://20260830` 和名称 `烟测源-20260830`。长度大于 256 字节是为了适配生产
`InputStream.isJson()` 对首尾各 128 字节的既有探测逻辑；本 PR 没有借测试修改生产解析
语义。

test APK 自身声明一个不可导出的只读 `ContentProvider`。纯 Java dispatcher 在 test APK
的独立 UID 中生成 URI，只向 `io.legado.app.debug` 的 `FileAssociationActivity` 授予单个
URI 的读取权限；Provider 拒绝写模式和全部 insert/update/delete。目标应用显示生产
`ImportBookSourceDialog` 后，测试通过 AndroidX Test 生命周期监视器定位 resumed Activity
和 DialogFragment，确认唯一名称可见，只点击 `tv_cancel`，最后断言：

- 导入页已经关闭；
- 书源总数与预览前完全相同；
- 唯一测试 URL 不存在。

测试不会点击确认按钮。`finally` 中的精确 URL 删除只用于失败后的 Debug 沙箱兜底，不会
扩大到其他书源。

## 设备测试隔离与 ReaderProvider 修正

新增运行时 annotation `io.legado.app.release.ReleaseSmoke`，脚本使用
`am instrument -e annotation ...` 只执行两个发布烟测类，不调用
`connectedAppDebugAndroidTest`，因此既有联网、迁移、性能和其他可能写数据的完整
instrumentation 套件不会被默认纳入。

既有 `ExampleInstrumentedTest` 原先使用不存在的 authority
`io.legado.app.api.ReaderProvider` 和路径 `sources/query`。当前测试改为运行时包名
`${appContext.packageName}.readerProvider` 与生产注册的复数路径 `bookSources/query`，只
断言返回行和 `result` 非空，不再把完整 JSON 写入日志。该测试没有被标记为
`@ReleaseSmoke`，不会混入聚焦发布集合。

## 实现收敛记录

- test APK 的组件在独立 UID/进程运行，不能假设目标 APK 的 Kotlin、AndroidX 或应用类在
  该进程可用；因此 URI dispatcher 和 Provider 只使用 Java 与 Android 框架 API。
- 直接依赖短 JSON 会触及生产首尾 128 字节探测边界；最终夹具固定为 342 字节，使预览
  路径可重复而不修改兼容敏感的生产解析器。
- Espresso 不能稳定枚举透明跨进程 Activity 上 DialogFragment 的 root；最终使用已有
  AndroidX Test `ActivityLifecycleMonitor` 在主线程读取生产视图并点击取消。现有依赖已
  足够，`app/build.gradle` 和版本目录均未增加 UIAutomator。

## 脚本安全边界

`.github/scripts/run-release-emulator-smoke.sh` 在构建、安装或清数据前先要求：

- 精确提供一个序列号；
- 序列号以 `emulator-` 开头；
- `ro.kernel.qemu=1`。

构建完成后，脚本在安装前要求 Debug app APK 与 androidTest APK 各精确一份。它只安装
这两个测试产物，只清空 `io.legado.app.debug` 和 `io.legado.app.debug.test`，再从已安装
instrumentation 中精确选择 target 为 `io.legado.app.debug` 的 AndroidJUnitRunner，随后
只运行 `@ReleaseSmoke`。脚本文件模式为 `0755`，`bash -n` 通过。真机序列号会在构建、
安装或清数据之前被拒绝。

## API 30 聚焦运行

实际在线环境：

| 项目 | 值 |
|---|---|
| AVD | `Pixel_5_API_30` |
| serial | `emulator-5554` |
| API | 30 |
| ABI | `arm64-v8a` |
| `ro.kernel.qemu` | `1` |
| app | `io.legado.app.debug`，versionCode `16652`，versionName `3.26.083010debug` |
| SDK | minSdk 21，targetSdk 36，compileSdk 36 |

当前 shell 第一次直接调用脚本时没有 `ANDROID_HOME`，Gradle 在任务解析前以
`SDK location not found` 退出；尚未进入构建后的安装或清数据步骤，不计为代码测试
失败。没有创建 `local.properties`，随后使用仓库现有 JDK 17 和 Android SDK 显式重跑：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
.github/scripts/run-release-emulator-smoke.sh emulator-5554
```

结果：Gradle `BUILD SUCCESSFUL`；Debug APK 和 test APK 均安装成功；只运行
`BookSourceImportPreviewSmokeTest` 与 `LocalTxtReleaseSmokeTest`，耗时 7.412 秒，
`OK (2 tests)`。

## 本地质量门禁

使用同一 JDK/SDK 对 PR 范围强制重跑：

```bash
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug \
  :app:assembleAppDebug :app:assembleAppDebugAndroidTest \
  --rerun-tasks --build-cache --no-daemon --warning-mode all
```

结果为 163/163 个 Gradle task 实际执行并 `BUILD SUCCESSFUL`；JVM XML 汇总为 141 个
测试、0 failure、0 error、1 skip；lint 为 0 error、108 warning、18 hint；Debug 和
androidTest APK 均构建成功。另有：

- `ReleaseEmulatorSmokeContractTest` 覆盖 annotation 过滤、模拟器限制、精确清数据包名、
  ReaderProvider authority/path、离线核心断言、只读 Provider 和无 UIAutomator 依赖；
- `openspec validate --all --strict`：3 passed、0 failed；
- `git diff --check`：通过。

lint 相对 PR 5 合并时的 104 warning 新增 4 项，全部属于未修改
`gradle/libs.versions.toml` 上的 `NewerVersionAvailable` 远端元数据漂移：Kotlin 同一版本
行从 6 个增为 9 个插件坐标提示，并新识别 `de.undercouch.download` 更新。它们已经按
精确位置补入 `docs/maintenance-baseline.md` 的 `DEFERRED`，原始 881 项参考基线未被
改写，当前没有 `PENDING_REVIEW`。

## 范围结论与剩余门禁

PR 5b 没有修改生产源码、最低 API 21、Room schema、书源/订阅源规则、导入 URI、备份
格式、普通正式版包名、签名材料或依赖版本，也没有生成正式 APK 或访问发布 Secrets。

以上证据完成任务 7.1–7.3，并覆盖 7.4 的本地验证部分。7.4 仍必须等待本 PR 创建、全部
远端聚合检查绿色、使用 merge commit 合并，并确认合并后 `master` 全部聚合门禁绿色；
在此之前，当前 API 30 运行不能作为任务 7.5–7.7 的合并后发布预检证据，正式发布继续
冻结。
