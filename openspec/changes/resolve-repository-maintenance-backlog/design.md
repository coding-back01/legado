## Context

参见 `proposal.md` 的动机。本设计以当前 `master` `997c6037b3810f560a04812d9c326c07547ed802` 为调查基线：主工作树干净，维护门禁成功，Android lint 为零 issue，OpenSpec 无其他活动变更。

当前欠账具有明确的依赖关系：

- `modules/web/pnpm-lock.yaml` 中的传递开发依赖 `js-yaml 4.3.1` 命中高危 `GHSA-2883-xcg3-v3hh`，最低安全版本为 `4.3.2`。它由 `@eslint/eslintrc 3.3.6` 的 `^4.3.0` 约束引入；包仓库可以读取 `4.3.2`，但 Dependabot run `34949114201` 以 `security_update_not_possible` 失败。
- PR #42 单独更新 Gradle `8.13 → 9.7.1`，因 AGP 8.13.2 使用 Gradle 9.6 已删除的内部 API 而失败；它还生成了不符合 `git diff --check` 的 `gradlew.bat`。
- PR #80 单独更新 AGP `8.13.2 → 9.4.0`，因当前 Gradle 8.13 低于 AGP 要求的 9.6 而失败。
- PR #45 同时更新 12 个 Web 开发依赖，已出现 TypeScript 7 的 `TS5112`；PR #81 同时更新 8 个生产依赖，构建后 Android 内置 Web 资产未同步。两个分支均落后当前 `master`。
- 本地有 38 个已被 `master` 包含且上游跟踪分支已删除的历史分支，一个干净的附加工作树，以及一个内容已被后续归档证据取代但 Git 历史未直接合并的远端证据分支。

## Goals / Non-Goals

**Goals:**

- 使用一个实现分支和一次连续 Apply，以可归因检查点串行消除全部已批准欠账。
- 只采用当前机器人提案已明确的 Android/Web 目标版本，不在实施时追逐后来出现的普通更新。
- 在任何远端或本地清理前，先让替代实现进入 `master` 并验证合并后状态。
- 保留 Android API 21、JDK 17、既有数据/规则格式、静态链接合同和发布审计边界。

**Non-Goals:**

- 不升级 Kotlin 2.3.0、KSP 2.3.4、Node.js 22、pnpm 9.15.9 或带固定兼容说明的其他依赖，除非 AGP 9.4 的硬兼容证据证明仅靠构建脚本迁移无法工作；若必须突破该边界则停止 Apply，而不是扩大范围。
- 不重构业务功能，不改变 Web 页面信息架构、路由语义、持久化状态或 Android 用户流程。
- 不触发发布工作流，不处理新的正式版本，不删除或公开两个历史草稿 Release。
- 不清理执行时新出现、发生漂移或无法证明安全的 PR、分支、工作树和 stash。

## Decisions

### 1. 一个变更、一个替代 PR、多个串行检查点

全部实现从 Apply 开始时重新同步的 `master` 创建单一 `codex/resolve-repository-maintenance-backlog` 分支，按“安全依赖 → Android 工具链 → Web 依赖 → 总验证”形成独立可审查提交。只创建一个承载代码与依赖迁移的替代 Pull Request；内部检查点失败时留在该分支修复，不拆出新的 OpenSpec，也不提前关闭机器人 PR。替代 PR 合并后的外部清理证据和最终 OpenSpec 归档可以进入独立收尾 PR，但仍属于同一 OpenSpec 和同一次连续 Apply，不再承载新的依赖实现。

选择该方案是为了满足一次 Apply，同时用提交边界保留故障归因。替代方案是分别建立安全、Android、Web 和清理变更；它隔离性更高，但会重复修改同一锁文件和治理快照，且不符合本轮单一 OpenSpec 的范围决定。

### 2. 安全修复先在当前依赖图上独立成立

使用固定的 Node.js 22 与 pnpm 9.15.9 从干净安装验证当前依赖图。先尝试只刷新满足 `@eslint/eslintrc` 既有 `^4.3.0` 约束的 `js-yaml` 锁定版本；若 pnpm 仍保留受影响版本，则在 `package.json` 的 `pnpm.overrides` 中加入只约束 `js-yaml` 的精确安全版本。无论使用哪条路径，提交只能包含必要的包清单/锁文件差异，并以 `pnpm why js-yaml` 和锁文件扫描证明不存在 `<4.3.2` 的 4.x 实例。

选择“锁文件优先、精确 override 兜底”是因为安全版本已经存在且父依赖范围允许，不需要把高危修复绑定到 ESLint 10 大版本。直接合并 PR #45 或全量执行不受控的 `pnpm update` 会混入大量普通更新，故不采用。

`.github/dependabot.yml` 只在有受支持配置能够稳定覆盖传递安全依赖时做最小调整；若 GitHub 托管安全更新行为无法由仓库配置改变，则保留现有队列上限，依靠新增的人工兜底合同，不加入无效或未经文档支持的配置键。

### 3. Gradle 9.7.1 与 AGP 9.4.0 作为不可拆分组合

Android 批次固定目标为 Gradle 9.7.1 和 AGP 9.4.0。wrapper 使用 Gradle 官方 wrapper 任务重建并再次运行以确保文件自洽，核对下载 URL、校验值、JAR 和 Unix/Windows 启动脚本；生成后的 `gradlew.bat` 必须经过换行与尾随空格修正并通过 `git diff --check`。

AGP 9 优先迁移到其原生 Kotlin 集成：移除 Android 模块重复应用的 `org.jetbrains.kotlin.android`，保留明确需要的 Parcelize、KSP 和 Kotlin 2.3.0 依赖，并把废弃的 Variant API/DSL 改为受支持接口。不得用长期关闭新 DSL、停用 lint/CodeQL 或回退检查范围换取构建成功。如果 AGP 9.4 与当前 Kotlin/KSP 组合存在无法通过受支持 API 消除的硬冲突，则记录证据并停止，不擅自升级 Kotlin/KSP。

Android 验证按配置阶段、三个模块编译/代码生成、`:app:testAppDebugUnitTest`、`:app:lintAppDebug`、`:app:assembleAppDebug` 和 Android CodeQL 等层次进行。API 21/23/36 只使用既有无个人数据测试矩阵，不触及正式版数据。

### 4. Web 目标版本固定，按兼容域迁移

Apply 以当前 PR 文件差异为版本基线，不直接检出机器人分支，也不追逐实施期间出现的新版本：

| 批次 | 依赖目标 |
|---|---|
| 运行时基础 | `@vueuse/core 14.4.0`、`@vueuse/shared 14.4.0`、`axios 1.20.0`、`element-plus 2.14.5`、`hotkeys-js 4.0.7`、`vue 3.5.42` |
| 状态与路由 | `pinia 4.0.3`、`vue-router 5.3.0` |
| 构建工具 | `@vitejs/plugin-vue 6.0.8`、`npm-run-all2 9.0.3`、`unplugin-auto-import 21.1.0`、`unplugin-icons 23.0.1`、`unplugin-vue-components 32.1.0`、`vite 8.2.2` |
| 类型系统 | `@types/node 26.4.0`、`@vue/tsconfig 0.9.1`、`typescript 7.0.2`、`vue-tsc 3.3.11` |
| lint 工具 | `eslint 10.9.1`、`eslint-plugin-vue 10.10.0` |

每个批次只修改该组依赖及为兼容所必需的源码/配置，并在进入下一批前执行安装、章节 HTML 测试、静态链接测试、类型检查、ESLint 和生产构建。TypeScript 7 的章节测试改用独立 `tsconfig` 或等价受支持调用，继续保留 `strict`、`noEmitOnError` 和真实 Node 测试执行。

TypeScript 7.0.2 不再提供 Vue 3 当前工具链依赖的旧编译器 API，因此采用 TypeScript 官方允许的并行安装方式：将 TypeScript 7.0.2 以 `@typescript/native` 别名安装并让章节测试的 `tsc` 命令实际解析到 7.0.2，同时把 `typescript` 包名映射到 `@typescript/typescript6` 6.0.2，专供 `vue-tsc` 与 `typescript-eslint` 的现有插件 API。该兼容层不得把章节测试退回 TypeScript 6，也不得关闭 Vue 类型检查；待上游支持 TypeScript 7 编译器 API 后再单独移除。

Vite 8 已使用 Rolldown/Oxc 取代原有构建路径。迁移时移除不再接受的 Sass `api` 选项，并把生产环境删除 `console` 与 `debugger` 的既有语义迁移到 `build.rolldownOptions.output.minify` 的 Oxc 压缩选项，不新增 `esbuild` 依赖或放弃原有生产压缩行为。

`pnpm build` 会通过 `modules/web/scripts/sync.js` 写入 `app/src/main/assets/web/vue/`。最终生成资产与同一固定工具环境再次构建结果必须一致；旧哈希资产按同步脚本的确定性结果移除，新资产和入口文件全部提交。禁止手工编辑压缩产物来制造干净状态。

### 5. 安全告警按结果闭环，不把界面状态当作代码事实

安全提交和最终 Web 图完成后，在实现分支读取依赖图并证明 `js-yaml` 高危版本已经实际消失；GitHub Dependabot alerts、Code Scanning 和 Secret Scanning 则在替代 PR 合并到默认分支后读取并闭环。Element Plus 升级后继续运行 11 个固定链接的完整清单、动态绑定禁止和 opener/referrer 隔离测试；若 GitHub 因版本范围关闭两条中危记录，则保存关闭证据；若 advisory 仍无修复版本并保持打开，则更新接受证据，但不得放宽静态链接合同。

选择该方案是因为 GitHub advisory 状态可能晚于锁文件刷新，且 Element Plus advisory 描述的是组件抽象边界。只看告警计数或只看版本号都不足以证明既有静态 URL 安全合同。

### 6. 外部清理采用“合并后再读回”的精确清单

以下仅为提案时快照。Apply 前置阶段必须重新读取对象：若编号、ref 和依赖范围不变而机器人只刷新了 SHA，则先比较新差异，确认仍完全落在本提案范围后，把新 SHA 写入实施证据并锁定为执行清单；ref、依赖范围或对象类型发生实质变化时停止该对象。执行清单锁定后，关闭或删除时必须要求编号、ref 和 SHA 同时匹配，后续任一漂移都保留该对象：

| 对象 | 精确 ref | 提案时 SHA |
|---|---|---|
| PR #42 | `dependabot/gradle/gradle-wrapper-2739417140` | `271896bc1a4c8b99bdfd4ae7f25ded815c2155d9` |
| PR #45 | `dependabot/npm_and_yarn/modules/web/web-development-4abf6744d0` | `7ad3277cb2c8472738861c5f9a6a4e9cff507679` |
| PR #80 | `dependabot/gradle/android-gradle-plugin-792a0080b7` | `b349e42379117535ac92a6de0f83f1576e3d7da6` |
| PR #81 | `dependabot/npm_and_yarn/modules/web/web-production-aa57cd536c` | `6554a8a935d91aba6cc8b352c384fa584008121e` |
| 旧发布证据分支 | `codex/release-verification-evidence` | `182b67140b4eb4d1dbdace589a6c15f67447e61e` |

四个机器人 PR 只在替代 PR 合并且 `master` 合并后门禁成功后关闭，关闭说明关联替代 PR；随后按精确 ref 删除对应远端分支。旧发布证据分支还必须再次证明其唯一内容已被 `openspec/changes/archive/2026-08-31-stabilize-independent-fork-governance/` 中的后续发布验证和归档记录取代。

本地清理先确认无 stash、所有工作树干净，再移除 `/Users/back/legado-reading-time`。以下 38 个提案时已被 `master` 包含的本地分支组成精确候选清单；执行时仍需逐个运行祖先检查，不能按 `codex/*` 批量删除：

```text
codex/add-reading-time-estimation
codex/archive-fork-governance
codex/fork-distribution-identity
codex/fork-distribution-identity-evidence
codex/fork-governance-final-closeout
codex/fork-governance-foundation
codex/fork-governance-stage1-evidence
codex/harden-independent-fork-security
codex/initial-release
codex/maintenance-gates
codex/quality-blockers
codex/quality-blockers-evidence
codex/release-draft-tag-fix
codex/release-emulator-preflight
codex/release-emulator-smoke
codex/release-verification-evidence-v2
codex/stabilize-reading-time-estimation
codex/vite-security-fix
codex/warnings-accessibility
codex/warnings-correctness-i18n
codex/warnings-final-audit
codex/warnings-hardcoded-text
codex/warnings-layout-performance
codex/warnings-plurals-candidate
codex/warnings-settext-i18n
codex/warnings-unused-resources-audit
codex/warnings-use-ktx-accessors
codex/warnings-use-ktx-batch
codex/warnings-use-ktx-bitmap-drawable
codex/warnings-use-ktx-canvas
codex/warnings-use-ktx-colors
codex/warnings-use-ktx-create-bitmap
codex/warnings-use-ktx-int-drawable
codex/warnings-use-ktx-shared-preferences
codex/warnings-use-ktx-styled-attributes
codex/warnings-use-ktx-uri
codex/web-chapter-content-safety
codex/web-chapter-content-safety-evidence
```

`codex/release-verification-evidence` 不在上述“已合并”集合中，只能在远端内容替代证明成立、远端精确 SHA 匹配且远端删除完成后，再按精确名称删除本地分支。

### 7. 发布与仓库保护使用只读复核

清理前后均读取 `master` ruleset、默认分支、安全能力、自动删除已合并分支设置和 Release 清单。`3.26.083020` 与 `3.26.083021` 必须保持草稿，公开 Latest 保持不因本变更改变。实现和验证均不得调用发布 workflow 或读取正式签名 Secrets。

## Risks / Trade-offs

- [AGP 9 原生 Kotlin 或 Variant API 迁移范围可能大于机器人一行版本差异] → 先在独立检查点完成配置和三个模块编译；若必须升级被明确排除的 Kotlin/KSP，则停止并回到设计评审。
- [20 个 Web 依赖跨多个大版本，错误可能相互遮蔽] → 按五个兼容域逐批安装和验证，每批只保留必要修复，禁止一次性全量锁文件刷新。
- [Web 构建哈希资产产生较大差异] → 固定 Node/pnpm，使用现有同步脚本生成，连续构建验证确定性，并人工审查入口与资产集合。
- [GitHub 安全告警关闭存在延迟] → 以锁文件和依赖图作为代码事实，同时记录 API 状态；高危告警未关闭时不执行后续普通依赖和清理。
- [机器人在 Apply 期间刷新 PR head] → 前置阶段只允许在范围未变且差异复核通过时锁定新 SHA；执行清单锁定后的漂移对象保留，不使用 force 或 glob。
- [删除本地工作树或历史分支丢失未提交工作] → 清理前核对所有工作树、未跟踪文件、stash 和祖先关系；任一不干净或不可证明对象均跳过并使闭环未完成。
- [一个替代 PR 评审面较大] → 通过内部可归因提交、每批证据和完整最终门禁降低风险；代价是任一阶段失败会阻止整项合并。

## Migration Plan

1. 从远端重新读取 `master`、安全告警、PR/head SHA、分支、工作树、stash、草稿 Release 和 ruleset，保存实施前证据；任何与本设计冲突的新状态先停止。
2. 在单一实现分支完成 `js-yaml` 最小安全修复并通过全部 Web 检查，确认分支依赖图已脱离受影响版本；默认分支上的 GitHub 告警留待替代 PR 合并后闭环。
3. 联合迁移 Gradle 9.7.1 与 AGP 9.4.0，修复受支持 DSL/Kotlin 集成，完成 Android 分层验证。
4. 按固定五批迁移 Web 生产与开发依赖，逐批验证，最终同步并复现内置 Web 资产。
5. 运行 Android、Web、CodeQL、OpenSpec 和仓库全量门禁，复核安全告警和发布/保护边界；创建一个替代 PR。
6. 替代 PR 合并后读取 `master` 的精确合并提交和全部检查；若出现回归，使用独立 revert PR，不关闭原机器人队列。
7. 只有合并后状态绿色时，按执行时精确快照关闭四个机器人 PR、删除对应远端分支、证明并删除旧证据分支，再移除干净附加工作树和逐个删除批准的本地分支。
8. 再次核对工作树、`master`、OpenSpec、告警、ruleset、草稿 Release 和远端队列，记录归档前证据。

合并前回滚只撤销实现分支中对应的内部检查点，不改写 `master`。合并后若发现代码或兼容性回归，按仓库治理要求建立独立 revert PR；Git 清理位于最后阶段，因此代码回滚不会依赖已经删除的机器人分支。
