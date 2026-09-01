## Context

参见 [proposal.md](proposal.md) 的动机。2026-09-01 实时复核时，当前 `master` 为
`c009929fb`，#73 的 head 为 `cdc989ee5`，相对 `master` 落后 2 个提交、领先 1 个提交；
其 Android、Web 和 CodeQL
检查通过，但 `OpenSpec 与仓库检查` 及稳定聚合 `维护门禁` 失败。直接失败点是维护契约仍
精确要求 `actions/setup-java@v5` 和 `pnpm/action-setup@v5`，但现有契约没有完整覆盖
`release.yml`、`stale.yml` 和其余 Action 版本。

目标 Action 均运行于 Node 24，官方给出的最低兼容 Actions Runner 为 `2.327.1`；仓库当前
只使用 GitHub-hosted `ubuntu-latest`，没有 self-hosted runner。三个工作流分别承载必需质量
门禁、正式签名草稿发布和 issue 自动关闭，不能用一次普通构建成功推断另外两个工作流安全。
前置 `harden-element-plus-static-links` 已通过独立 Pull Request #79 合并为 `c009929fb`，
其临时分支也已清理；本变更工作区只保留自身 OpenSpec 工件，不会把两批改动混成一个
Pull Request。

## Goals / Non-Goals

**Goals:**

- 让每个目标 Action 大版本都有可追溯的官方行为判断、精确配置和聚焦合同。
- 先让新合同对旧工作流产生可解释 RED，再以最小工作流改动得到 GREEN。
- 在普通 CI 中真实执行 v7 上传与 v8 下载闭环，同时保持正式发布 workflow 未触发。
- 收紧 checkout 凭据、缓存 provider、artifact 解压与摘要、pnpm 缓存所有权等隐式默认值。
- 从最新 `master` 创建替代 Pull Request，完整 CI 通过后再关闭 #73。

**Non-Goals:**

- 不把本变更扩展为依赖、runner 镜像、CodeQL、构建工具、pnpm 或 Node.js 版本迁移。
- 不通过正式签名构建、tag、草稿 Release 或设备测试验证 Action 升级。
- 不改变 stale 的标签、时限、提示文案或业务权限，不改变正式发布授权和候选公开流程。
- 不在本变更中引入 YAML 解析库、自动合并、SHA pinning 策略或新的自托管 runner。

## Decisions

### 1. 从合并后的最新 master 重建替代变更，不复用 #73 的提交

实现前先完成当前 Element Plus 静态链接变更的独立提交、Pull Request、合并和归档读回，随后
基于新的 `origin/master` 创建本变更分支。#73 只提供目标版本、release notes 和真实 RED
证据，不 cherry-pick、不 rebase 后直接合并。这样可以避免其落后分支覆盖最新的工作流合同和
OpenSpec 产物，也能让替代 Pull Request 的差异只包含本变更。

备选方案是更新 #73 分支并修正合同；该方案保留机器人提交但把后续治理变化与一次
跨 8 个 Action 的自动更新混在一起，难以建立逐项责任链，因此不采用。

### 2. 每个 Action 显式保留或收紧关键默认值

| Action | 目标 | 关键变化 | 本仓库决策 |
|---|---|---|---|
| `actions/checkout` | v7 | Node 24；v6 将持久化凭据移到独立文件；v7 阻止受信任事件默认检出 fork PR | 保留既有 `fetch-depth` 和 `clean` 语义；所有不执行 push 的 checkout 显式 `persist-credentials: false`，只有创建候选 tag 的发布作业显式保留凭据 |
| `actions/setup-java` | v6 | Node 24、ESM；Zulu 元数据源变化；输入兼容别名 | 继续固定 Temurin JDK 17，不启用其依赖缓存，由 `setup-gradle` 单独管理 Gradle 缓存 |
| `gradle/actions/setup-gradle` | v6 | Node 24；默认 enhanced 缓存受独立条款约束；缓存协议变化会导致首次 miss | 每处显式设置 `cache-provider: basic`，使用 100% MIT provider；正常 Android/Release 构建继续允许 Gradle build cache，CodeQL 命令继续 `--no-build-cache` |
| `actions/upload-artifact` | v7 | Node 24、ESM；新增单文件 direct upload | 显式 `archive: true`，继续使用命名压缩 artifact；保留现有 `if-no-files-found`、保留期和唯一名称，不启用会忽略 `name` 的 direct upload |
| `actions/download-artifact` | v8 | Node 24、ESM；支持 direct download；摘要不一致默认失败 | 继续按名称下载，显式 `skip-decompress: false` 与 `digest-mismatch: error`；不使用受 v5 路径变化影响的单 artifact ID 下载 |
| `actions/stale` | v11 | Node 24、ESM、安全依赖更新 | 保留 issue-only 参数、豁免标签和 `issues: write`/`contents: read`，不增加 Pull Request 写权限 |
| `pnpm/action-setup` | v6 | Node 24；仍支持 pnpm 10 及以下，pnpm 11+ 才建议迁移到 `pnpm/setup` | 继续固定 pnpm 9.15.9 与 `run_install: false`，显式 `cache: false`，避免与 `setup-node` 双重缓存 |
| `actions/setup-node` | v7 | Node 24、ESM；pnpm 不自动缓存，npm 自动缓存规则变化 | 继续固定 Node.js 22，显式 `cache: pnpm`、锁文件路径和 `package-manager-cache: false`，使缓存所有权唯一 |

不采用 `pnpm/setup`，因为它要求 pnpm 11+，会把 Action 更新扩展为包管理器迁移；不采用
Gradle enhanced provider，因为本仓库不需要为当前 CI 引入商业缓存组件和额外条款；不采用
artifact direct upload，因为现有跨作业合同依赖稳定的 artifact 名称与解压后路径。

2026-09-01 实时复核时，8 个 major ref 分别解析为：checkout v7
`3d3c42e5a`、setup-java v6 `dd06d9cba`、gradle/actions v6 `4733eaac7`、
upload-artifact v7 `043fb46d1`、download-artifact v8 `3e5f45b2c`、stale v11
`4391f3da6`、pnpm/action-setup v6 `f520eceda`、setup-node v7 `820762786`。
对应 `action.yml` 均声明 `node24`；官方迁移说明要求最低 runner `2.327.1`。修改前清点结果为：
`test.yml` 含 checkout 5 处、setup-java/setup-gradle/upload-artifact/setup-node 各 2 处、
pnpm/action-setup 1 处，保留 CodeQL v4；`release.yml` 含 checkout 3 处及 JDK、Gradle、上传、
下载各 1 处，只有草稿作业执行 tag push；`stale.yml` 含 stale 1 处。三个工作流分别只由
`pull_request`/`master` push/手动探针、手动发布、定时/手动 stale 触发，均使用
GitHub-hosted `ubuntu-latest`，没有 self-hosted runner。

### 3. 合同先行，并分别覆盖三个工作流

先扩展 `.github/scripts/test_maintenance_workflow.py`，让它分别读取 `test.yml`、
`release.yml` 和 `stale.yml`，按工作流断言目标版本和关键输入；不新增 PyYAML 等运行时依赖。
继续使用现有 Kotlin `ReleaseWorkflowContractTest` 锁定正式发布顺序，并补充发布 upload/download
版本、显式压缩/解压、摘要失败、唯一名称和操作先后关系。

合同修改后、工作流修改前必须运行 Python 契约和聚焦 Kotlin 测试，记录预期失败的旧版本
token；只允许这些版本/缺失配置断言失败。随后修改三个工作流并重跑得到 GREEN。版本断言按
工作流分段，禁止以“文件中某处出现目标 token”代替正确作业中的配置；同时保留以下既有
合同：

- CodeQL `init/analyze@v4` 和 Android `--no-build-cache`；
- JDK 17、Node.js 22、pnpm 9.15.9 与冻结安装；
- 正式签名 Secrets 不进入 Pull Request 验证；
- stale 只处理 issue；
- 稳定聚合门禁区分成功、失败和合法跳过。

备选方案是只替换现有两个失败字符串；该方案不能发现 release/stale 漂移，也不能证明新默认值
没有扩大权限或改变 artifact 路径，因此不采用。

### 4. 用无签名夹具建立真实 artifact 上传下载闭环

在 `test.yml` 增加两个轻量作业：上传作业生成一个确定性文本夹具和对应 SHA-256 文件，使用
`upload-artifact@v7` 以唯一运行名称、`archive: true`、`if-no-files-found: error` 和 1 天保留期
上传；下载作业只依赖上传作业，使用 `download-artifact@v8` 按名称下载，并显式设置
`skip-decompress: false` 与 `digest-mismatch: error`。下载后必须核对文件集合恰好为夹具和摘要
文件，再执行 `sha256sum --check --strict`。

两个作业不 checkout 仓库、不读取 Secrets、只使用 `contents: read`，并在 Pull Request、
`master` push 和人工 scope probe 中执行。稳定 `维护门禁` 将下载核验作业加入 `needs` 并要求
成功。相比增加新的路径分类维度，始终运行两个极小作业更简单，也避免“恰好在工作流升级
Pull Request 中被合法跳过”的漏洞；代价是每次 CI 多一次很小的 artifact 传输。

该闭环只证明 v7/v8 服务互操作、压缩解压和内容保持，不证明正式 APK、签名或 Release 流程；
正式 `release.yml` 仍只做静态合同和 actionlint 检查，不执行 `workflow_dispatch`。

### 5. 验证顺序保持可归因且失败即停止

本地顺序为：Python 维护合同、聚焦 `ReleaseWorkflowContractTest`、固定版本 actionlint、
OpenSpec 全量严格校验、`git diff --check`。Pull Request 必须实际产生范围识别、Android、Web、
CodeQL Android/Web、OpenSpec/仓库、artifact 闭环和稳定聚合门禁结果；因为工作流文件属于 CI
实现范围，Android 与 Web 均必须实际运行而不是合法跳过。

不触发 `Release Build` 是设计约束，不是“未完成验证”：发布授权、签名、tag、Release 和设备
门禁没有变化，且其无副作用合同由静态检查覆盖。若 actionlint、合同、任一构建、CodeQL 或
artifact 服务失败，替代 Pull Request 保持未合并，#73 保持打开。

## Risks / Trade-offs

- [Node 24 Action 需要 runner `2.327.1`] → 当前只使用 GitHub-hosted `ubuntu-latest`；合同记录
  下限。未来引入 self-hosted runner 必须另行验证，不允许静默复用本结论。
- [checkout v7 对 `pull_request_target`/`workflow_run` 的 fork 检出更严格] → 当前工作流不使用
  这两个事件；合同锁住实际触发器，不添加绕过开关。
- [显式关闭多数 checkout 的凭据持久化可能暴露隐含 push 依赖] → 当前只有发布候选 tag 作业
  需要 push，并在该作业单独保留；若其他作业因此失败，应修复错误依赖而非全局恢复凭据。
- [Basic Gradle 缓存首次 miss 且可能慢于 enhanced provider] → 把首次冷启动作为预期，仍要求
  真实构建成功；用开放许可和较小数据边界换取有限的构建耗时。
- [artifact 闭环增加外部服务依赖和少量 CI 成本] → 夹具极小、保留 1 天；服务异常保持门禁
  失败并可在服务恢复后重跑，不把故障降级为成功。
- [Major tag 可变] → 本变更沿用仓库现有 Dependabot major-tag 策略并用精确合同监测漂移；完整
  commit SHA pinning 会改变更新治理模型，留待独立安全变更。
- [文本合同可能受无关排版影响] → 按工作流和作业片段缩小断言范围；有意把安全关键默认值写成
  显式 YAML，以可审计的少量脆性换取 fail-closed。
- [dirty worktree 混入无关变更] → 前一变更已独立合并并清理；本次只暂存本变更的精确路径，
  不使用 `git add -A`。

## Migration Plan

1. 完成 `harden-element-plus-static-links` 的独立提交、Pull Request、合并、归档后读回和分支
   清理，确认本地基于最新 `origin/master` 且没有混合未提交改动。
2. 重新读取 #73 的 head SHA、ahead/behind、检查结果和目标版本；若目标或官方兼容事实变化，
   先更新本变更工件并重新评审。
3. 先修改 Python/Kotlin 合同并运行，保存只由旧 Action 版本和缺失显式边界造成的 RED。
4. 修改三个工作流和稳定聚合门禁，运行聚焦合同、actionlint、OpenSpec 和差异检查至 GREEN。
5. 创建替代 Pull Request，等待 Android、Web、CodeQL、仓库、artifact 和聚合门禁全部绿色；
   不触发 Release workflow。
6. 人工评审后合并，重新读取 `origin/master` 和合并提交的全部检查。只有读回证据完整时才用
   替代 Pull Request 链接关闭 #73，随后验证、归档本 OpenSpec 变更并清理已合并临时分支。

回滚使用独立 revert Pull Request，不重写 `master`。若合并后发现 runner、缓存、artifact、
stale 或发布合同回归，保持正式发布冻结；尚未关闭的 #73 继续保留，已关闭则重新打开并说明
回滚原因，直到新的兼容方案通过同一门禁。
