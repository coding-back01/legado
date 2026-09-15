## 1. Apply 前置与事实快照

- [x] 1.1 从远端获取最新 `origin/master`，确认主工作树除本 OpenSpec 计划工件外没有用户改动，并在 `codex/resolve-repository-maintenance-backlog` 分支开始同一次连续 Apply；记录基线 SHA，禁止在陈旧提交上实现。
- [x] 1.2 读取 Dependabot、Code Scanning、Secret Scanning、开放 Issue、PR #42/#45/#80/#81 的状态、head ref/SHA、文件差异和失败检查；若机器人只刷新 SHA，则复核范围后在实施证据中锁定新 SHA，若 ref 或依赖范围变化则停止该对象。
- [x] 1.3 读取全部远端分支、本地分支及祖先关系、`git worktree list --porcelain`、每个工作树状态和 `git stash list`，核对设计中的 38 个已合并本地分支、`legado-reading-time` 工作树和旧证据分支候选清单。
- [x] 1.4 读取默认分支、ruleset、安全能力、自动删除已合并分支设置，以及 `3.26.083020`、`3.26.083021` 和当前 Latest Release，记录不得改变的实施前快照。
- [x] 1.5 核对 JDK 17、Android SDK/API 21/23/36、可丢弃模拟器或获授权 Debug 测试设备、Node.js 22 和 pnpm 9.15.9；运行当前 Android、Web、OpenSpec 和仓库基线检查，并将通过、失败、环境受阻分别写入 `evidence/preflight.md`，必需前置缺失时在修改生产代码前停止。

## 2. `js-yaml` 高危安全修复

- [x] 2.1 重新核对 `GHSA-2883-xcg3-v3hh`、包仓库中的最低安全版本、`@eslint/eslintrc` 版本约束和 `pnpm why js-yaml` 依赖路径，证明安全补丁可以脱离普通 Web 大版本升级独立实施。
- [x] 2.2 使用 pnpm 9.15.9 先尝试仅将锁文件中的 4.x `js-yaml` 更新到 `4.3.2`；若解析器仍保留受影响版本，则加入只约束 `js-yaml` 的精确 `pnpm.overrides`，不得执行不受控的全依赖更新。
- [x] 2.3 对安全补丁执行干净 `pnpm install --frozen-lockfile`、`pnpm test:chapter-html`、`pnpm test:static-links`、`pnpm type-check`、`pnpm exec eslint .` 和 `pnpm build`，确认当前 Web 行为及 Android 内置资产同步流程没有回归。
- [x] 2.4 扫描 `pnpm-lock.yaml` 并重新执行 `pnpm why js-yaml`，确认不存在受影响的 4.x 实例且差异只包含必要的依赖声明/锁文件变化；把 Dependabot run `34949114201` 的失败与人工兜底结果写入 `evidence/security-dependency.md`。
- [x] 2.5 调查 `.github/dependabot.yml` 是否存在受支持且不突破队列上限的传递安全更新配置：有可靠配置时做最小修改并补充契约测试，否则保持配置不变并在证据中明确以后沿用人工兜底，不写入无效键。
- [x] 2.6 在进入普通依赖升级前完成安全检查点，确认实现分支依赖图已安全；默认分支上的 GitHub 告警保持待合并后复核，不把界面延迟误报为分支仍受影响。

## 3. Android Gradle 与 AGP 联合迁移

- [ ] 3.1 使用官方 wrapper 任务把 Gradle 固定到 `9.7.1` 并连续生成两次，核对下载 URL、校验值、wrapper JAR 和启动脚本；修正 `gradlew.bat` 换行/尾随空格并通过 `git diff --check`。
- [ ] 3.2 把 application、library、test 三个插件共用的 AGP 版本更新到 `9.4.0`，迁移到 AGP 9 受支持的原生 Kotlin 集成，同时保持 Kotlin `2.3.0`、KSP `2.3.4`、Parcelize、JDK 17 和显式 stdlib 版本不变。
- [ ] 3.3 迁移 AGP 9 不再支持或已废弃的 Variant API、DSL 和任务访问方式，保持 APK 命名、product flavor、application ID、release/debug 签名边界、Room schema 输出和三个模块命名空间不变。
- [ ] 3.4 分别执行根工程配置、`app`、`modules/book`、`modules/rhino` 的 Kotlin/Java 编译及 KSP/Room 代码生成，确认没有通过关闭插件、旧 DSL 兼容开关或升级范围外依赖绕过错误；若当前 Kotlin/KSP 与 AGP 9.4 存在硬冲突则停止 Apply 并记录证据。
- [ ] 3.5 执行 `./gradlew :app:testAppDebugUnitTest`、`./gradlew :app:lintAppDebug` 和 `./gradlew :app:assembleAppDebug`，解析 lint XML 确认 issue 节点为零，并核对 Debug APK 身份及最低/目标 API 未变化。
- [ ] 3.6 在既有无个人数据矩阵中完成适用的 API 21/23/36 构建或设备测试，验证启动、资源、Room、launcher/shortcut 和受影响兼容路径；原始设备证据不得进入 Git。
- [ ] 3.7 把版本组合、构建脚本迁移、wrapper 核验及全部 Android 结果写入 `evidence/android-toolchain.md`，形成可独立审查的 Android 检查点。

## 4. Web 运行时依赖迁移

- [ ] 4.1 将运行时基础组更新到 `@vueuse/core 14.4.0`、`@vueuse/shared 14.4.0`、`axios 1.20.0`、`element-plus 2.14.5`、`hotkeys-js 4.0.7`、`vue 3.5.42`，只修复该组引起的兼容问题并执行完整 Web 检查后再继续。
- [ ] 4.2 将状态与路由组更新到 `pinia 4.0.3`、`vue-router 5.3.0`，验证书架、书源编辑、帮助入口和既有路由/持久化状态语义，并执行完整 Web 检查。
- [ ] 4.3 对运行时迁移重新运行 11 个固定链接清单、动态 `href` 禁止和 opener/referrer 隔离测试，确认 Element Plus 升级没有放宽 `web-link-security` 合同。

## 5. Web 构建、类型与 lint 工具迁移

- [ ] 5.1 将构建工具组更新到 `@vitejs/plugin-vue 6.0.8`、`npm-run-all2 9.0.3`、`unplugin-auto-import 21.1.0`、`unplugin-icons 23.0.1`、`unplugin-vue-components 32.1.0`、`vite 8.2.2`，解决真实兼容错误并执行完整 Web 检查。
- [ ] 5.2 将类型系统组更新到 `@types/node 26.4.0`、`@vue/tsconfig 0.9.1`、`typescript 7.0.2`、`vue-tsc 3.3.11`；用专用 `tsconfig` 或等价受支持方式消除 `TS5112`，保留 `strict`、`noEmitOnError` 和章节测试真实执行。
- [ ] 5.3 将 lint 工具组更新到 `eslint 10.9.1`、`eslint-plugin-vue 10.10.0`，只做配置 API 和规则语义所需迁移，保持 ESLint 零 error 且不禁用既有安全/质量规则。
- [ ] 5.4 每个工具批次均执行固定版本安装、章节 HTML 测试、静态链接测试、类型检查、ESLint 和生产构建；任一批次失败时停止后续批次并记录精确失败，不通过全量锁文件刷新掩盖来源。
- [ ] 5.5 最终对照 PR #45/#81 的批准版本清单，确认 20 项目标全部达到指定版本，未顺带升级 Kotlin/KSP、Node/pnpm、固定 Android 依赖或清单外 Web 依赖。

## 6. Web 静态资产与本地总验证

- [ ] 6.1 使用 `modules/web/scripts/sync.js` 的既有路径同步 `app/src/main/assets/web/vue/`，审查删除的旧哈希文件、新入口、脚本和样式，确认没有手工编辑生成产物或混入无关资产。
- [ ] 6.2 在相同 Node.js 22 与 pnpm 9.15.9 环境连续执行两次生产构建，确认第二次构建后版本化 Web 资产无新增、删除或内容漂移。
- [ ] 6.3 从干净依赖安装再次运行 `pnpm test:chapter-html`、`pnpm test:static-links`、`pnpm type-check`、`pnpm exec eslint .` 和 `pnpm build`，记录 Web 最终证据。
- [ ] 6.4 再次运行 Android 单元测试、零 issue lint、Debug 构建及适用设备矩阵，确认 Web 资产变化和最终依赖图没有破坏 Android 打包或运行时入口。
- [ ] 6.5 执行仓库契约测试、适用工作流语法/CodeQL 前置检查、`openspec validate --all --strict` 和 `git diff --check`，检查 `git diff --stat` 与完整差异，确认没有 Room schema、规则/备份/导入格式、正式签名或 Release 工作流副作用。
- [ ] 6.6 将最终依赖图、资产确定性、Android/Web 结果和未运行项写入 `evidence/implementation-verification.md`，完成替代实现的人工审查。

## 7. 替代 Pull Request 与合并验证

- [ ] 7.1 将安全、Android、Web 和总验证保留为可归因提交，推送唯一实现分支并创建一个替代 Pull Request，在说明中关联告警 #6 以及 PR #42/#45/#80/#81，但不提前关闭它们。
- [ ] 7.2 等待替代 Pull Request 的 Android、Web、CodeQL、OpenSpec/仓库和稳定聚合门禁全部实际成功，审查完整 diff 和生成资产；失败时只在同一实现分支修复并重新验证。
- [ ] 7.3 全部门禁和人工审查通过后合并替代 Pull Request，记录精确 merge SHA；若不能合并或合并后出现回归，则停止外部清理并按独立 revert PR 处理。
- [ ] 7.4 读取 merge SHA 对应的 `master` 维护门禁和 CodeQL 结果，确认本地 `master` 可快进到同一 SHA，且合并后 Android lint 仍为零 issue。

## 8. 合并后安全与远端队列闭环

- [ ] 8.1 重新读取 Dependabot alerts、Code Scanning 和 Secret Scanning，确认 `js-yaml` 高危告警关闭且 high/critical 为零；记录 Element Plus 两条中危是随升级关闭，还是在静态链接合同不变前提下继续保留当前接受理由。
- [ ] 8.2 重新读取 PR #42/#45/#80/#81 的状态、head ref、head SHA 和差异，要求与 Apply 前锁定的执行清单一致，并证明它们的批准依赖范围已由替代 merge SHA 完整覆盖。
- [ ] 8.3 逐个在 PR #42/#45/#80/#81 留下关联替代 Pull Request 和 merge SHA 的关闭说明，再关闭 PR 并按精确 ref/SHA 删除对应 Dependabot 远端分支；任一执行时漂移的对象停止操作且不得标记完成。
- [ ] 8.4 比较 `codex/release-verification-evidence` 的唯一提交与归档发布证据，确认内容已被后续提交完整取代且远端 SHA 仍匹配执行清单后，按精确 ref 删除该远端分支。
- [ ] 8.5 重新读取开放 PR、开放 Issue和远端分支，确认四个机器人对象及旧证据分支已经消失，同时保留执行期间新出现且未列入清单的对象。

## 9. 本地工作树与历史分支清理

- [ ] 9.1 切换主工作树到已验证的最新 `master`，再次确认主/附加工作树状态、未跟踪文件和 stash 均为空；任一内容存在时停止对应清理，不执行 reset、force 或批量 glob 删除。
- [ ] 9.2 确认 `/Users/back/legado-reading-time` 仍干净且 `codex/add-reading-time-estimation` 被当前 `master` 包含，先移除该工作树，再按精确名称删除对应本地分支。
- [ ] 9.3 对设计中其余 37 个已合并本地分支逐一执行祖先检查并按精确名称删除；对象缺失视为已清理，出现独立提交则保留并停止该对象。
- [ ] 9.4 在远端旧证据分支已精确删除且内容替代证明仍成立后，按精确名称删除本地 `codex/release-verification-evidence`；不得把它混入已合并分支批次。
- [ ] 9.5 执行远端引用 prune 并重新读取本地分支和工作树，确认只保留 `master`、当前仍必需的 OpenSpec 收尾分支以及执行时明确未获清理授权的对象。

## 10. 最终复核与归档准备

- [ ] 10.1 读回默认分支、active ruleset、自动删除已合并分支、Dependabot、CodeQL、Secret Scanning 和私有漏洞报告状态，确认不弱于前置快照。
- [ ] 10.2 读回 `3.26.083020`、`3.26.083021` 和 Latest Release，确认两个候选仍为草稿、没有新 Release 或 tag、Latest 未因本变更改变。
- [ ] 10.3 将替代 PR、merge SHA、合并后检查、安全告警、四个旧 PR、远端/本地分支、工作树及仓库设置的最终状态写入 `evidence/final-closeout.md`，只把实际完成的任务标为完成。
- [ ] 10.4 最后运行 `openspec validate --all --strict`、`git diff --check`、`git status --short --branch` 和本地/远端 `master` SHA 对账，确认所有任务与证据一致后将本变更交给独立 OpenSpec 归档流程。
