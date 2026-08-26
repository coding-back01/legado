# PR 2 本地验证证据

## 验证对象

- 分支：`codex/fork-distribution-identity`
- 基线提交：`2d61c97cfdf39297407d62372d89765c714b40bd`
- 验证日期：2026-08-26
- 范围：更新器、fork 身份、链接分类、未来 `releaseA` 生成链退役和 Release workflow

本文件只记录 PR 创建前实际完成的本地验证；GitHub PR 与合并后 `master` 的检查结果在任务 3.13 完成时另行补记。

## RED→GREEN 补充证据

发布 workflow 新增“候选必须等于当前远端 `master`”契约后，先只运行
`ReleaseWorkflowContractTest`：旧 workflow 内容共 8 条测试，其中新增契约 1 条失败。
加入分支、检出 SHA、远端 `master`、构建前和草稿前漂移检查后，同一测试类 8/8 通过。

第一次尝试运行该 RED 命令时因 shell 未设置 Android SDK 而在任务解析阶段失败，没有进入测试，
因此不计作 RED 证据；后续命令显式使用现有 `/Users/back/Library/Android/sdk`，没有写入
`local.properties`。

## Android 与 JVM 验证

执行：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
./gradlew :app:testAppDebugUnitTest \
  :app:processAppDebugResources \
  :app:assembleAppDebug
```

结果：成功。PR 2 相关测试报告如下：

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `ForkLinkContractTest` | 4 | 0 | 0 | 0 |
| `ReleaseWorkflowContractTest` | 8 | 0 | 0 | 0 |
| `StableReleaseParserTest` | 9 | 0 | 0 | 1 |
| `StableUpdateChannelTest` | 1 | 0 | 0 | 0 |
| `UpdateCheckExecutorTest` | 4 | 0 | 0 | 0 |

`StableReleaseParserTest` 中唯一 skip 是必须显式提供 `LEGADO_LATEST_RELEASE_JSON` 的联网烟测；
该烟测已按下节独立运行并通过。原先依赖撤空上游和固定资产数量的设备 `UpdateTest.kt` 已删除，
对应行为由确定性 JVM 测试覆盖，本阶段没有运行会接触设备数据的 instrumentation 套件。

## Latest Release 真实烟测

从 `https://api.github.com/repos/coding-back01/legado/releases/latest` 下载当前 JSON 到临时目录，
先用 `jq` 核对普通正式版、版本格式、MIME、上传状态和唯一资产，再通过环境变量将同一文件交给
`StableReleaseParserTest`。实际结果：

- tag：`3.26.082216`
- 精确普通资产 `legado_app_3.26.082216_release.apk`：1 个
- 同一解析器联网烟测：通过

## Release workflow 与资源

- `actionlint 1.7.12`：`.github/workflows/release.yml` 通过。
- workflow 只允许 `workflow_dispatch`，同时核对仓库、`actor`、`triggering_actor`、
  `expected_sha`、`github.sha` 和当前远端 `master`；构建前及草稿阶段发现 SHA 漂移会失败。
- workflow 仅整理、上传一个普通 APK，签名 Secrets 缺失时在构建前失败，Release 保持草稿，
  tag 和 target 均核对到锁定 SHA。
- `jq empty`：根目录 `package.json`、`modules/web/package.json`、`rssSources.json` 通过。
- `xmllint --noout`：相对基线发生变化的全部 XML 通过。
- Android `processAppDebugResources` 和 `assembleAppDebug` 通过。

`modules/web/package.json` 的 `packageManager: "pnpm@9.15.9"` 是对紧急安全 PR 已合并 lockfile
及已勾选任务 4.9 的补齐；本 PR 没有修改 `pnpm-lock.yaml` 或依赖版本范围。

## Web 复核

使用固定 `pnpm@9.15.9` 执行冻结安装、类型检查、只读 ESLint 和构建：

- `install --frozen-lockfile`：通过，lockfile 无漂移。
- `type-check`：通过。
- `build`：通过；非 GitHub Actions 环境按脚本设计没有同步生成资产，工作区无额外产物差异。
- `eslint .`：仍为参考基线的 2 个 error、0 warning：
  - `modules/web/src/source.d.ts:82`：未使用的 `RuleSearch`；
  - `modules/web/src/utils/souce.ts:52`：`no-explicit-any`。

这两个错误属于任务 4.8，PR 2 没有新增、移动或掩盖它们；因此不得把 Web ESLint 描述为通过，
也不得据此提前开始 warning 批次。

## 链接、OpenSpec 与范围边界

- `ForkLinkContractTest`：当前 fork 入口、8 个 locale、不可变提交与历史来源契约 4/4 通过。
- 远程抽查：当前 Release、Issue、贡献者，两个仍有效上游 Web 项目，固定帮助截图和 3 个历史更新日志链接共 9 个目标，均返回 HTTP 200。
- `openspec validate --all --strict`：3 项通过、0 项失败。
- `git diff --check`：通过。
- 计划纳入 PR 的版本化变更共 39 个文件；没有 `app/schemas/`、迁移、备份、
  `local.properties`、签名二进制或密钥扩展名路径，也没有二进制 diff。
- `modules/web/src/book.d.ts` 与 `source.d.ts` 只修改当前源码注释链接；`ruleHelp.md` 只治理帮助链接。
  未修改 Room schema、书源/订阅源规则语义、导入 URI、备份格式、普通包名、签名材料或历史 Release 资产。
