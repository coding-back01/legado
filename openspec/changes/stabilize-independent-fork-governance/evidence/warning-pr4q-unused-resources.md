# PR 4q：`UnusedResources` 动态引用审计与清理证据

## 串行前置条件

前一批 GitHub PR #69 的最终 head 为
`828ba3681dce8b08abd239071939cbaf5bdfb6d8`，merge commit 为
`2b08b43634dd2c94d17f3d89ab458fc7ff25d10b`。最终 head 的
`Android Debug 验证` run `33187190988` 和合并后 `master` 的 run
`33187516558` 均成功。合并后使用 JDK 17.0.17 强制执行 131 个 Gradle
任务，129 个单元测试 0 失败、1 跳过，Android lint 为 0 error、690 warning、
18 hint，`UseKtx` 为 0；Debug 构建、OpenSpec 严格校验和空白检查均成功。
满足串行条件后才创建本批分支。

## RED 基线与机器清单

PR #69 合并后的 lint XML 包含 588 个 `UnusedResources` occurrence：

| 资源类型 | occurrence |
|---|---:|
| `color` | 270 |
| `string` | 265 |
| `drawable` | 25 |
| `dimen` | 12 |
| `style` | 7 |
| `layout` | 4 |
| `array` | 3 |
| `anim` | 1 |
| `menu` | 1 |
| **合计** | **588** |

审计脚本按 lint XML 中的 `(资源类型, R 字段名)` 建立精确候选集合，并在写入前
要求 588/588 个候选全部能够解析。dry-run 解析到 2,397 条多语言或限定符
`values` 定义和 32 个独立资源文件，共 2,429 个定义，没有未解析对象。

## 动态引用与死资源闭包审计

删除前对代码、XML、反射、名称拼接、书源相关代码、Web 和 assets 执行只读搜索：

- 生产代码中的 5 个 `Resources.getIdentifier` 调用只读取 Android 系统的
  `status_bar_height`、`navigation_bar_height`，或从本应用读取 `mipmap`；本批
  候选不含 `mipmap`，也没有按名称读取候选 `string`、`color`、`drawable`、
  `layout`、`style`、`dimen`、`array`、`anim` 或 `menu` 的路径；
- 没有通过 `R` 类、`R$<type>`、`getField` 或字段枚举反射应用资源；现有反射仅处理
  Android/库内部字段，与资源表无关；
- 精确的 `@type/name` 与 `R.type.name` 搜索得到 33 条文本命中，其中 10 条是已注释
  的旧书源评论规则编辑代码，1 条实际指向 `android.R.color.black`，其余 22 条全部
  位于同一待删死资源子图，例如待删 selector 引用待删 color、待删 style 引用待删
  drawable/dimen、待删 array 引用待删 string；
- 书源/规则、Web 与 assets 不存在把这些名称转换为 Android 资源 ID 的桥接入口。

因此本批删除的是闭合且不可达的编译资源子图，不修改书源字段、规则语义、Web API、
Room schema、备份格式、导入 URI、最低 API、包名、签名或固定依赖。

## 初次机械删除与静态 GREEN

按 dry-run 的精确目标删除 22 个 `values` 文件中的 2,397 条定义：2,084 条
`string`、277 条 `color`、15 条 `array`、12 条 `dimen` 和 9 条 `style`；同时
删除 25 个 drawable、4 个 layout、1 个 anim、1 个 color selector 和 1 个 menu，
共 32 个独立资源文件。删除均受 Git 跟踪，可以从历史恢复。

使用 JDK 17 与固定 Android SDK 强制执行 `:app:lintAppDebug --rerun-tasks`，
103 个 Gradle 任务全部实际执行并成功。`processAppDebugResources`、Kotlin/Java 编译
与 lint 均通过，结果为 0 error、102 warning、18 hint，`UnusedResources` 为 0。
warning 总数精确减少 588，没有连带新增或消失其他 lint ID。

## 全量合同 RED 与范围收敛

随后运行单元测试、lint 和 Debug 构建的 131 任务全量命令。资源处理、生产编译、
Debug APK 和 lint 均成功，但 129 个单元测试中 2 个失败、1 个跳过，Gradle 在执行
129 个任务后返回失败：

- `BlockingTranslationResourceTest` 要求六个既有阻断键在 8 个支持 locale 中保持
  非空；本批候选中的 `del_all` 被删除后该合同失败；
- `RtlLayoutContractTest` 要求 `dialog_progressbar_view.xml` 保留已验证的 60dp 和
  20dp 逻辑末端间距；删除该布局会撤销 PR 4a 的 RTL 合同。

因此恢复 `del_all` 在 `values`、`values-es-rES`、`values-ja-rJP`、
`values-pt-rBR`、`values-vi`、`values-zh`、`values-zh-rHK`、
`values-zh-rTW` 八个 `strings.xml` 中的原始定义，并恢复
`app/src/main/res/layout/dialog_progressbar_view.xml`。其余五个阻断翻译键从未属于
本批候选，保持原文件与原位置不变。两个合同测试类聚焦重跑 6/6 通过，57 个 Gradle
任务全部实际执行。

最终删除 22 个 `values` 文件中的 2,389 条定义：2,076 条 `string`、277 条
`color`、15 条 `array`、12 条 `dimen` 和 9 条 `style`；同时删除 25 个
drawable、3 个 layout、1 个 anim、1 个 color selector 和 1 个 menu，共 31 个
独立资源文件。相对于本批 588 个 occurrence，586 个安全删除，2 个精确延期。

## 全量验证

使用 JDK 17.0.17 与固定 Android SDK 执行：

```bash
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug \
  :app:assembleAppDebug --rerun-tasks --no-daemon --warning-mode all --console=plain
```

131 个 Gradle 任务全部实际执行并成功。测试 XML 汇总为 28 个测试套件、129 个
测试、0 失败、1 跳过；lint XML 为 0 error、104 warning、18 hint，其中
`UnusedResources` 只剩上述 2 项。Debug APK 构建成功。三份 lint 报告哈希如下：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `0a2edcde943acb4ca3d1084d94fb29a977a666305cd61727137521d342844b2e` |
| `lint-results-appDebug.html` | `b388cff62fd170faf03c032512cf4f97bfc5fdf2f8c2151aac8abcc3dc8df16c` |
| `lint-results-appDebug.txt` | `72c5d488bb734d04575002c4c934c197b2c36071bf3cc1d7665d3c9f69bcb5fa` |

累计状态更新为 `FIXED=765`、`SUPPRESSED_WITH_REASON=12`、`DEFERRED=45`、
`PENDING_REVIEW=59`。`UnusedResources` 自参考 594 项以来累计 `FIXED=592`、
`SUPPRESSED_WITH_REASON=0`、`DEFERRED=2`、`PENDING_REVIEW=0`，已完成该 ID 的
全部三态对账。

## 待完成门禁

`openspec validate --all --strict` 为 3 passed、0 failed，`git diff --check`
无输出。实时 `adb devices -l` 为空，本批未运行设备测试，也不将其描述为通过。
draft PR #70 已创建；仍须完成该 PR 最终 head 与合并后 `master` 的远端和本地
双重复验，才可完成任务 5.5。任务 5.8 继续保持未完成，正式发布继续冻结。
