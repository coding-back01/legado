## 范围与插入理由

第一阶段安全告警处置期间确认了新的发布阻断数据流：不可信书源正文经 Web API 进入
`ChapterContent.vue`，旧实现再通过 `v-html` 交给浏览器解析。`design.md` 允许阻断当前
发布的紧急安全修复插入串行序列，因此在 PR 2 前增加任务 2.13 和 PR 1b；普通分发身份、
更新器和链接任务仍保持未开始。

修复仅收紧网页章节正文的浏览器边界，不修改最低 API 21、Room schema、书源或订阅源
规则、导入 URI、备份格式、普通正式版包名与签名。APK 实际打包
`app/src/main/assets/web/vue/`，因此源码、确定性构建和生成资产必须作为同一个安全闭环。

## RED 与实现契约

旧实现上的新增测试实际得到以下 RED：

- 未闭合外层属性 `<a title="...` 会把其中的小写图片标记提升为真实 `<img>`；
- 12,000 个畸形普通标记的解析耗时约 1,840.2 毫秒；
- 12,000 个畸形 raw-text 结束标记的解析耗时约 2,092.8 毫秒。

最终实现采用以下 fail-closed 契约：

- 普通正文、comment、raw-text 标签和全部非图片标签均按文本转义；
- 只接受后端 canonical 小写 `<img src="...">`，并从零重建唯一、非空、带引号的
  `src`；其他属性不会进入输出；
- uppercase、`img-foo`、`data-src`、`srcset`、重复、空值和未加引号 `src` 全部按文本
  处理；
- 保留 Legado `URL,{JSON options}` 请求参数及单双外层引号形式；
- 未闭合标记与畸形 raw-text 关闭候选停止继续寻找内层图片，消除请求激活和后缀重复回扫；
- 独立图片、内嵌图片和字数计算共用同一 tokenizer；同一图片元素最多尝试一次不同代理
  地址。

最终 `pnpm test:chapter-html` 为 16/16；两项复杂度回归约为 2.0 毫秒和 0.5 毫秒。
独立只读复审又覆盖未闭合单双引号属性、comment、script、textarea、title、xmp、
noembed 与 noframes，没有观察到图片 resolver 调用或新的重复回扫。

## 锁文件、生成资产与本地验证

安全修复要求把最终代码进入 APK，因此提前完整执行任务 4.9：

- `packageManager` 固定为 `pnpm@9.15.9`，新增 `pnpm-lock.yaml`，依赖版本范围未改变；
- 锁文件与 `node_modules/.pnpm/lock.yaml` 的 SHA-256 均为
  `99ed1ab8f828093b30b55255af76ffd4cf1aba874303381874b0d61e6db4f42e`；
- 当前锁定 Vue 3.5.41；旧生成 bundle 使用 3.5.32，这一差异来自首次锁定既有版本范围；
- 许可证只有 MIT、Apache、BSD、ISC、Python-2.0、BlueOak 和兼容组合，未发现 GPL、
  AGPL 或 UNKNOWN；
- `pnpm audit` 为 0 critical、0 high、1 moderate；唯一中危仍是已记录接受的
  Element Plus `el-link` 告警；
- 两次最终构建的 `dist` 与 Android Web assets 目录总哈希均为
  `8ca528796ef4f10471948b3eeedea1003e0bdf39cbac000b3e9b5a477f2aed66`，目录比较无差异；
- Web workflow 同时检查新增、删除和修改的生成资产，workflow、源码或 Android Web
  assets 任一单独变化都会触发检查；同步脚本任一步失败均返回失败。

实际通过的本地检查：固定 pnpm 冻结安装、16 项章节安全测试、类型检查、改动范围 ESLint、
改动源码 Prettier 检查、两次 Web 构建、`actionlint` 1.7.12、YAML 解析、
`openspec validate --all --strict`、`git diff --check` 和 `:app:assembleAppDebug`。最终 Debug
APK 内含 `BookChapter-Cx-OjwJt.js`。第一次未设置 Android SDK 环境变量的 Gradle 命令在
依赖解析前失败；显式使用既有 `/Users/back/Library/Android/sdk` 后构建成功，不将第一次
环境失败描述为代码检查通过。全量 Web ESLint 仍精确为既有 2 errors、0 warnings，留待
任务 4.8 清零。

## Pull Request 与合并后证据

| 项目 | 精确结果 |
|---|---|
| 基线 `master` | `47649e562d16bc5c5604d22799a1ef4595fb0883` |
| PR | `#48`，`https://github.com/coding-back01/legado/pull/48` |
| head ref / SHA | `codex/web-chapter-content-safety` / `b46d770039727f43e6c33b1dce64a296944791fc` |
| PR Web | run `32745108375`，成功 |
| PR Android | run `32745108304`，成功 |
| merge commit | `538a58156eb601d19488509868be885e715782cb` |
| `master` Web | run `32920993138`，成功 |
| `master` Android | push run `32920993055` 被同一并发组中新到达的 `workflow_run` 自动取消；替代 run `32921025411` 对同一 merge SHA 成功 |

PR 合并后立即读回安全状态：Dependabot 没有 high 或 critical，Secret Scanning 打开告警
为 0。`GHSA-5m5x-9j46-h678` 因新锁文件在 `modules/web/package.json` 和
`modules/web/pnpm-lock.yaml` 分别显示为告警 `#1` 与 `#5`，二者是同一 Element Plus
中危且没有修复版本；现有 11 个 `el-link` 的 `href` 均为源码固定值，原接受理由继续适用。
短期安全分支仍保留，等待总变更最终按精确 ref/SHA 白名单清理。
