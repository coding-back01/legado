# Apply 前置证据

## 基线与实现分支

- 读取时间：2026-09-15（Asia/Shanghai）。
- `master`、`origin/master` 与实施基线均为 `997c6037b3810f560a04812d9c326c07547ed802`。
- 唯一实现分支为 `codex/resolve-repository-maintenance-backlog`。
- 建立分支前主工作树只有尚未提交的本 OpenSpec 目录，没有其他用户改动；stash 为空。

## 安全与远端队列

| 对象 | 状态 | 锁定 ref / SHA |
|---|---|---|
| Dependabot #6 `js-yaml` | open / high；受影响 `>=4.0.0,<4.3.2`；最低安全版本 `4.3.2` | `modules/web/pnpm-lock.yaml` |
| Dependabot #1、#5 `element-plus` | open / medium；同一 `GHSA-5m5x-9j46-h678`；无 `first_patched_version` | `package.json` 与 `pnpm-lock.yaml` |
| PR #42 | open / blocked | `dependabot/gradle/gradle-wrapper-2739417140` / `271896bc1a4c8b99bdfd4ae7f25ded815c2155d9` |
| PR #45 | open / blocked | `dependabot/npm_and_yarn/modules/web/web-development-4abf6744d0` / `7ad3277cb2c8472738861c5f9a6a4e9cff507679` |
| PR #80 | open / blocked | `dependabot/gradle/android-gradle-plugin-792a0080b7` / `b349e42379117535ac92a6de0f83f1576e3d7da6` |
| PR #81 | open / blocked | `dependabot/npm_and_yarn/modules/web/web-production-aa57cd536c` / `6554a8a935d91aba6cc8b352c384fa584008121e` |
| 旧发布证据分支 | 未保护 | `codex/release-verification-evidence` / `182b67140b4eb4d1dbdace589a6c15f67447e61e` |

- 四个机器人 PR 的编号、ref、SHA、依赖范围和失败类型与设计快照一致，以上值锁定为本次 Apply 的执行清单。
- Code Scanning 打开告警为 0，Secret Scanning 打开告警为 0，开放 Issue 为 0。

## 本地 Git 对象

- `/Users/back/legado-reading-time` 工作树干净，分支 `codex/add-reading-time-estimation` 已被当前 `master` 包含。
- 设计列出的 38 个本地历史分支均被 `master` 包含且上游状态为 gone。
- 本地 `codex/release-verification-evidence` 含一个 `master` 不可达提交；远端比较为 ahead 1 / behind 26，只有旧候选证据与任务 8.1 勾选，需在合并后按归档替代证明单独处理。

## 仓库与发布保护

- 默认分支为 `master`，仓库公开、未归档、未禁用，`delete_branch_on_merge=true`。
- ruleset `20653588`（`保护 master 分支`）为 active，无 bypass actor；要求 Pull Request、稳定检查 `维护门禁` 和 CodeQL high-or-higher 安全门禁，并禁止删除和非快进。
- Dependabot Security Updates、Secret Scanning 与 push protection 为 enabled。
- Latest 为正式版 `3.26.083101`。
- `3.26.083020` 与 `3.26.083021` 均为 draft；后者明确标记设备验证失败，二者不得由本变更删除或公开。

## 工具与基线验证

- JDK 17.0.17 位于 `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`；交互 shell 默认 JDK 21，因此所有 Android 命令显式指定 JDK 17。
- Android SDK 位于 `/Users/back/Library/Android/sdk`，已安装 API 21、23、36 平台及对应 arm64 system image；已有 `Legado_API_21`、`Legado_API_23`、`Legado_API_36` 三个可丢弃 AVD。前置时没有已启动 adb 设备，不影响后续按矩阵启动 AVD。
- Node.js 22.23.2 可通过固定 `node@22` 环境执行；项目内 pnpm 固定为 9.15.9。
- 首次 Android 基线因交互 shell 未设置 `ANDROID_HOME` 在任务依赖解析前失败；显式设置 JDK 17、`ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 后重跑成功，不属于源码失败。
- Android 基线：`:app:testAppDebugUnitTest`、`:app:lintAppDebug`、`:app:assembleAppDebug` 成功；`app/build/reports/lint-results-appDebug.xml` issue 数为 0。
- Web 基线：冻结锁文件安装成功；章节 HTML 16 项测试、静态链接 6 项测试及 11 个链接对账、类型检查、ESLint、生产构建全部成功。
- 仓库契约 20 项测试成功；`openspec validate --all --strict` 为 8/8 成功；`git diff --check` 成功。

以上前置满足开始生产代码修改的条件。
