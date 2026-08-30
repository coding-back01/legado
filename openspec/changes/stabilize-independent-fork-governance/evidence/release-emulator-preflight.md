# 合并后 API 21 / API 36 模拟器发布预检证据

本文记录任务 7.5–7.7。预检只使用 PR 5b 已合并后的
`origin/master@92034f3f77efb4c38ca292cfaae838c4890d0267`，本地工作分支为
`codex/release-emulator-preflight`。这些结果只证明下列 AOSP 模拟器和具体页面路径，不能
替代指定真机，也不能扩大为其他设备、厂商系统或全应用语言与 RTL 兼容声明。正式发布在
任务 8 的全部硬门禁完成前继续冻结。

## 环境与产物身份

使用 Android SDK command-line tools 21.0，已安装的镜像与平台为：

| 项目 | 版本 |
|---|---|
| `platforms;android-21` | revision 2 |
| `system-images;android-21;default;arm64-v8a` | revision 4 |
| `system-images;android-36;default;arm64-v8a` | revision 2 |

两个 AVD 均以 `-wipe-data -no-snapshot -no-boot-anim -no-audio -no-window` 启动，并使用
`-gpu swiftshader_indirect`。运行发布 smoke 的命令为：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
.github/scripts/run-release-emulator-smoke.sh emulator-5554
```

脚本在两个 AVD 上都重新核对 `emulator-*` 序列号和 `ro.kernel.qemu=1`，只安装 Debug app
与 androidTest APK，只清空两个 Debug 测试包，并只运行 `@ReleaseSmoke` 两个测试。最终
本地 APK 身份为：

| 项目 | 值 |
|---|---|
| app 包名 | `io.legado.app.debug` |
| test 包名 | `io.legado.app.debug.test` |
| versionCode | `16654` |
| versionName | `3.26.083015debug` |
| minSdk / targetSdk / compileSdk | `21 / 36 / 36` |
| app APK 大小 / SHA-256 | `29,946,788` 字节 / `8aa00f43b4a4ac2c321a1f6b645309b9ab265b257384240b6a291d806584cc05` |
| test APK 大小 / SHA-256 | `1,171,749` 字节 / `63ed323cdb12549b583018836a51c8acb52f8f9ea1bc7cf18b115a7f7cb03044` |
| Debug 证书 SHA-256 | `e5529aeb53e775e4f9647f73d1fad5d39f3b4ce8855cbe562d3eea9fc7265ce7` |
| Debug 签名校验 | v1、v2 通过；这是 Debug 身份，不是正式签名证据 |

## API 21 预检

使用全新 `Legado_API_21`，观测到 API 21、`arm64-v8a`、`ro.kernel.qemu=1`、
`1080x2340@440dpi`。安装后的 Debug 包为 versionCode `16654`、versionName
`3.26.083015debug`。

发布 smoke 实际完成：

- 离线书源预览显示唯一测试源，只点击取消，前后书源数量不变；
- 离线 TXT 经生产导入进入 Debug 数据库，正文 sentinel 可从生产阅读模型读取；
- fixture 产生多页正文，生产翻页成功且进度增加并持久化；
- 返回书架后测试书存在，测试结束按精确 URL/书籍清理一次性数据；
- 结果为 `OK (2 tests)`，耗时 4.968 秒。

随后冷启动 `MainActivity`，`am start -W` 成功，top resumed activity 为生产
`MainActivity`。截图和 UI hierarchy 显示空书架壳；目标 PID 的限定日志没有
`FATAL EXCEPTION`、`ANR in` 或 `am_anr`。API 21 模拟器完成取证后已关闭。

原始证据位于 Git 忽略且权限为 `0700/0600` 的
`build/release-emulator-evidence/api21/`：

| 文件 | SHA-256 |
|---|---|
| `smoke.log` | `87a24827136f31596b368dcca2ae26610bca885637a2a4e697fcc0bbfe302f8b` |
| `device-summary.txt` | `c870be7280064140d6dd7561257b97eb34ecae63ab14a4878e31844ecc7d317f` |
| `package-summary.txt` | `5cb251bff4b12b19231aa120ab56f865a1dfd83b67a665e0538b86d2c94b4970` |
| `startup-command.txt` | `28c128928a296e4370bdcc65e641f84d4be0d18d7d2e4e8373bf2c6759031911` |
| `startup.png` | `49e168231e6df628bbf022aac66aa39c1e7b0113575875f6104ea91fe732849d` |
| `window.xml` | `83206868b14bb5cc532799d0d971115be87b0ec3b644c3cb443626d56119a9a9` |
| `startup-logcat.txt` | `290914a4ed0b9815cd63ae2c0016e3d94177414bcdfe4a9761ee404118c84da3` |
| `dumpsys-activity.txt` | `d34f52af7ef14d799ce07c1d3b2dfb83d41dcaee709976b8c88060f511c35a8a` |

## API 36 核心路径

使用全新 `Legado_API_36`，实际启动命令对应进程为：

```text
qemu-system-aarch64-headless @Legado_API_36 -port 5554 -wipe-data -no-snapshot
-no-boot-anim -no-audio -no-window -gpu swiftshader_indirect
```

观测到 API 36（Android 16）、`arm64-v8a`、`ro.kernel.qemu=1`、
`1080x2340@440dpi`，构建 fingerprint 为
`Android/sdk_phone64_arm64/emu64a:16/BE2A.250530.026.D1/13818094:userdebug/test-keys`。
同一发布 smoke 为 `OK (2 tests)`，耗时 11.914 秒；覆盖的离线书源预览取消、TXT
导入、正文 sentinel、翻页进度与返回书架断言均与 API 21 相同。

## API 36 扩展页面检查

### 启动、导航与公开 RecyclerView

冷启动 `MainActivity` 成功，底部 `Bookshelf`、`Discovery`、`RSS feeds`、`Me` 四个入口
均出现在 UI hierarchy。点击 `Me` 后页面保持在生产 `MainActivity`，设置列表为公开
`androidx.recyclerview.widget.RecyclerView`，可滚动并进入 `Theme settings` 与
`Other settings`。这证明本次实际导航入口可用；公开 RecyclerView 的源码契约仍由
`UiInputNavigationContractTest` 覆盖，不把该页面运行扩大为所有导航容器已验证。

### 输入法

生产 `SearchContentActivity` 未导出。只为启动该应用内部页面，在可丢弃的 AOSP 模拟器上
临时执行 `adb root`；没有修改 APK、Manifest 或仓库。页面启动后先按返回键隐藏输入法，
确认 `mInputShown=false`；再点击生产 `tv_current_search_info`，实际执行
`EditText.showSoftInput()`，随后确认：

- top resumed activity 仍为 `SearchContentActivity`；
- `mCurId=com.android.inputmethod.latin/.LatinIME`；
- `mInputShown=true`、`mIsInputViewShown=true`；
- served view 为生产 `SearchView$SearchAutoComplete`；
- 截图可见完整软键盘，目标 PID 日志无 FATAL/ANR。

检查结束后已执行 `adb unroot`，读回 UID 为 2000 的普通 shell。

### 语言切换

从 `Other settings` 的生产语言列表读取初始值 `Auto`，选择 `Simplified_Chinese`。应用按
生产逻辑重启到 `MainActivity`，主界面显示中文空书架文案；再次进入“其它设置”后，语言
标题和摘要分别为“语言”“简体中文”。取证后通过同一生产列表恢复“跟随系统”，应用重新
回到英文系统语言。此项只证明一次 `Auto -> 简体中文 -> Auto` 的可观察切换，不代表所有
locale 的翻译质量已经人工审阅。

### 动态图标资源

从 `Me -> Theme settings -> Change icon` 打开生产 `IconListPreference`，UI hierarchy 和
截图均显示 `iconMain`、`icon1` 至 `icon6` 共 7 项，每项都有已解析的 `ImageView`。没有
选择其他项，因此没有修改 launcher component 或桌面图标状态。

### 受控 RTL

在应用语言恢复 `Auto` 后，只在该 AOSP 模拟器设置
`settings global debug.force_rtl=1` 与 `debug.force_rtl=true`，强制停止并重启应用。主界面
可观察到搜索/更多按钮、标题和四个底部导航项完整镜像；随后打开 PR 4a 涉及的生产
`dialog_check_source_config.xml`，标题、输入框、检查项和按钮均可见，逻辑起始方向位于
右侧。目标 PID 日志无 FATAL/ANR。

该运行只检查主导航和“书源校验设置”对话框，没有覆盖 PR 4a 的所有布局，更不声明全应用
RTL 通过。取证后已恢复 `debug.force_rtl=false`、全局值 `0`，重新启动并从 UI hierarchy
确认主导航回到 LTR。

## API 36 原始证据哈希

原始文件位于 Git 忽略且权限为 `0700/0600` 的
`build/release-emulator-evidence/api36/`。`my-navigation-logcat.txt` 在限定窗口内没有目标
进程日志行，因此是合法的 0 字节文件，其哈希为标准空文件哈希。

| 检查点 | 截图 SHA-256 | UI hierarchy SHA-256 | 目标 PID 日志 SHA-256 |
|---|---|---|---|
| 冷启动主界面 | `8d7fc8c0d055d3cd1134dc38aec44185a60e0ff53c4a949869f83314e1db5900` | `d941ff0630ad6d0580c50206c64560d9f28593d0debce4b82bb31d606acd3539` | `a5c6cd81e346138655424a26d509bd544f7c33f0ac2aff55e087f695d884c93f` |
| `Me` 导航 | `b33294cd5fc473c069968ea5d426039e2e54ca486cb006bb0e6a3261c4896238` | `3758903ee8fbf9a3ce2ccb8fd4d3063519d252eb1846d1fe09ad1faca8f05234` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| Theme settings | `8943f510d326bd11929c9348f14c7d3640eb6912514db48e5b0e489ac85cf3e4` | `d6ea960ffc6dccc672e8add3100292e0f2adc07a5e6169be57a5e98ab0791a5e` | `f0b57357e6df979d61aa5fddd3102593ed4feb071af2b2fbc168558ca168e5f4` |
| 7 个动态图标 | `21e9a9a52d864daa95e2896c1c588e50dca942cf5fb6b03b2d85ff780c3ed855` | `2a46afada81d87e3221a11e258c659bbf6a3bdfd79a02cbd33535bdd05778d3b` | `de4999be5bc5920fa20ce6722cb0580f3c388f1b0235b4155073838dac6a9441` |
| 英文其它设置 | `72b6efd1c2570b85f337602d915e260fb9021df994fd50812d2f5f492fab8d81` | `38610ee6f46a4c143362e936b35a466af2e8271e2fe1da91dfcb364c09815813` | `617e4c2e53093b3cb9cd346bbfad6c0c0c267d59736d55d00a1c8c172874125c` |
| 中文主界面 | `1458a5326d6c986a9555b0eb6f92bbf858689b100fe7feae9e2afb399b82e82e` | `f7df31548a6dcd5b0b72a719c2ad3390a6f274b8eae0c50d8d6416ae6c3bf627` | `9ba0ea94148742802b5a3da7467d1a8387fe8f43741ba6dbdb51321df9edf4fc` |
| 中文其它设置 | `c3da266ae901fda557eb20397e92611f7a1e05fc930a822932fccadf8040d3c7` | `b91480b92ceedb6c796b2efc20b6ed2aacfc266381229954129dc9a689edb6ad` | `c4902e103772810c984266a29f9bf227b282c3e2ba5eb1daa546a9e5520d9828` |
| 真实软键盘 | `205ce2ab97e6950e60bf12cc8c5de9375b4034178e64a4a3b959411968b04649` | `7d9fbdc378d53636c423eafc5a3c21aaa3fb763f3edc3c5f11d9d49b0487da30` | `bceb5f6c8f092c7e4f3fdb3a45a252bae1d519e7a0eb2a6a4b17719833b28806` |
| RTL 主界面 | `b19757f19595e653de8889e99a400b0e9f30324352bd2423d7ef7916fb3752ae` | `81fbe50b67f8c136c848986618226cfded6dfa7aff2a0064a9e888fa2fcaa365` | `307b57e354f56d4d349374c9d83e7e0b9b8626839b0a6a4fc7525f5f522bac90` |
| RTL 书源校验对话框 | `4d98b1bd68aefa223f6d2f421c8716e1b06a0cfe6060c07e3de2514ee7d4e4c9` | `51e0394cbe1657f62f9328ce790f5c912fb9f2fd296b276d04502d0cc4ebea2d` | `57f0389b4b0451599b0c2fd5fb46a200f98e735b644452d3d084c10ef7bb4c72` |

API 36 `smoke.log` 的 SHA-256 为
`1245f4da4163ed58af631699ac520880124b3c77291c2ff76bd8ddc4c13e292f`。对上述全部目标 PID
日志执行精确搜索，没有发现 `FATAL EXCEPTION`、`ANR in` 或 `am_anr`。

## 结论与边界

API 21 与 API 36 的实际发布 smoke 均为 2/2 通过；API 36 的启动导航、输入法、一次语言
切换、动态图标列表和受控 RTL 检查没有发现实际回归。因此任务 7.5–7.7 的模拟器预检已
完成。模拟器证据仍不能替代任务 8.5–8.10 的指定真机同签名升级和非破坏性数据保持门禁；
在候选、APK 身份、真机与公开回验完成前，正式发布保持冻结。
