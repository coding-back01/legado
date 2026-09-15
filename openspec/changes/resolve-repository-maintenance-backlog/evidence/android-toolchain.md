# Android 工具链迁移检查点

## 固定版本与 Wrapper

- 检查时间：2026-09-15（Asia/Shanghai）。
- Gradle Wrapper：`8.13 → 9.7.1`。
- Android Gradle Plugin：application、library、test 三个插件共用版本 `8.13.2 → 9.4.0`。
- JDK 固定为 Oracle JDK `17.0.17`；所有 Gradle 验证显式设置 `JAVA_HOME`、`ANDROID_HOME` 与 `ANDROID_SDK_ROOT`。
- Kotlin 版本目录、显式 stdlib 与 Parcelize 保持 `2.3.0`，KSP 保持 `2.3.4`，没有修改其他 Android 依赖版本。

使用 Gradle 官方 `wrapper` 任务连续生成两次，并提供官方发布的二进制校验值：

```text
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
```

第二次生成后的 Wrapper 文件摘要：

| 文件 | SHA-256 |
|---|---|
| `gradle/wrapper/gradle-wrapper.jar` | `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d` |
| `gradlew` | `a5a5c199ba02189ae8c46a334223371a20599d9c298ef65e7540ede4a3f72d59` |

上述两个摘要与锁定的 PR #42 中 Gradle 9.7.1 生成结果完全一致。`gradlew.bat` 已在生成后规范为仓库既有的 LF 文本，未保留 CRLF 转换警告或尾随空格；`git diff --check` 通过。Wrapper properties 保留 URL 验证、超时和重试字段。

## 构建脚本迁移

- Android 模块不再应用 `org.jetbrains.kotlin.android`；版本目录中也不再暴露该未使用插件别名，改用 AGP 9 默认的原生 Kotlin 集成。
- 保留 application 模块所需的 Parcelize、KSP 与 Room 插件；没有设置 `android.builtInKotlin=false`、旧 DSL 兼容或关闭检查的逃生开关。
- `compileSdk` 改为显式读取 `rootProject.ext.compile_sdk_version`，消除 Gradle 9 对父项目属性隐式查找的弃用。
- 删除 AGP 9 已移除且不再生效的全局 `android.defaults.buildfeatures.*` 选项；应用模块仍显式启用 `buildConfig` 与 `viewBinding`，其余功能继续采用默认关闭状态。
- APK 重命名从已删除的 `applicationVariants` 迁移到公开的 `androidComponents.onVariants` 与 `VariantOutput.outputFileName` API。
- application ID、debug/release suffix、签名条件、product flavor、Room schema 目录、三个命名空间及 Java 17 编译目标保持不变。

迁移后 `./gradlew help --warning-mode all` 成功，配置阶段没有 AGP/Gradle 弃用或兼容警告。

## 编译、代码生成与本地门禁

以下任务在 Gradle 9.7.1、AGP 9.4.0、JDK 17 下通过：

- `:app:compileAppDebugKotlin`
- `:app:compileAppDebugJavaWithJavac`
- `:app:kspAppDebugKotlin`
- `:app:copyRoomSchemas`
- `:modules:book:compileDebugKotlin`
- `:modules:book:compileDebugJavaWithJavac`
- `:modules:rhino:compileDebugKotlin`
- `:modules:rhino:compileDebugJavaWithJavac`
- `:app:testAppDebugUnitTest`
- `:app:lintAppDebug`
- `:app:assembleAppDebug`

lint XML 的 `issue` 节点为 0。生成的 Debug APK 元数据保持：application ID `io.legado.app.debug`、minSdk `21`、targetSdk `36`、compileSdk `36`；APK 名称仍为 `legado_app_<版本>.apk`。Room schema 没有产生版本化差异。

Android 测试代码编译仍报告 `MigrationTest` 中既有 Room 测试构造器弃用和可空类型提示；它们位于本次未修改的测试代码，不影响生产编译、lint 或设备矩阵，未通过关闭编译检查处理。

## API 21/23/36 可丢弃模拟器矩阵

所有设备验证均使用 `-wipe-data` 的既有 AVD，原始 emulator 与 instrumentation 日志只保存在忽略的 `app/build/`，未进入 Git：

| AVD / profile | 结果 |
|---|---|
| `Legado_API_21` / `api21-mdpi-light-phone` | golden 启动、资源与页面矩阵 `OK (1 test)`；离线 TXT 导入、Room 写入、阅读翻页及书源预览 `OK (2 tests)` |
| `Legado_API_23` / `api23-mdpi-light-phone` | golden 启动、资源与页面矩阵 `OK (1 test)` |
| `Legado_API_36` / `api36-mdpi-light-phone` | golden 启动、资源与页面矩阵 `OK (1 test)`；离线 TXT/Room/书源预览 `OK (2 tests)` |
| `Legado_API_36` launcher/shortcut 聚焦检查 | 七个 launcher alias 与三个 shortcut 断言共 4 项连续两次通过 |

同一设备烟测还包含一个与本工具链无关的通知渠道断言。它在全新数据环境中因生产通知渠道尚未由对应媒体流程创建而失败，即使先启动 WelcomeActivity 仍可复现；本次没有把该失败误记为通过，也没有扩展范围修改既有测试或通知业务。任务要求的启动、资源、Room、launcher 与 shortcut 路径均有独立通过证据。
