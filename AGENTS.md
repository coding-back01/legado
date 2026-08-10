# AGENTS.md

## 适用范围

- 本文件适用于整个仓库；子目录若有更具体的 `AGENTS.md`，以更深层文件为准。
- 修改前先查看 `git status --short`，保留用户已有的未提交改动，不要顺手重排或清理无关文件。
- 变更应小而聚焦；除非任务明确要求，不要同时升级依赖、重构代码或改写生成文件。

## 项目概览

Legado（开源阅读）是一款不内置内容的 Android 阅读器。主工程是使用 Kotlin、Java 17
和 Gradle 构建的多模块 Android 项目，最低支持 Android 5.0（API 21）。仓库还包含一个
独立的 Vue 3 Web 子项目。

## 文档语言

- 仓库内新建或维护的项目文档一律使用简体中文，包括 `README`、`AGENTS.md`、开发说明、
  设计记录以及 OpenSpec 的提案、设计、规范和任务文件。
- 文档中的说明、标题、验收条件和提交给维护者的总结必须使用中文，不要中英文混写。
- 代码标识符、命令、文件路径、协议字段、依赖坐标和无法准确翻译的专有名词可以保留原文，
  但相关解释必须使用中文。
- `English.md` 等明确面向其他语言用户的翻译文件可以保留对应语言；中文文档是内容基准，
  中文内容发生实质变化时应同步维护翻译版本。

## 目录与职责

- `app/`：Android 应用、资源、Room 数据库、单元测试和设备测试。
- `modules/book/`：书籍解析与阅读相关的 Android 库。
- `modules/rhino/`：Rhino JavaScript 执行支持。
- `modules/web/`：Vue 3、TypeScript、Vite 和 pnpm 管理的 Web 书架与源编辑器。
- `gradle/libs.versions.toml`：Gradle 依赖与插件版本目录。
- `app/schemas/`：Room 导出的数据库结构快照，数据库结构变化时必须同步更新。
- `app/src/main/assets/`：内置帮助、默认数据和 Web 静态资源；修改时注意格式与向后兼容。
- `openspec/`：OpenSpec 的规范、变更提案及归档。

## 开发约定

### Android

- 沿用相邻代码的 Kotlin 风格：4 空格缩进、官方 Kotlin 代码风格、清晰的空安全处理，避免无必要的 `!!`。
- UI 文案放在字符串资源中，并同步考虑已有的多语言资源；不要在业务代码中新增可见硬编码文案。
- 网络、数据库和文件操作不得阻塞主线程；协程应绑定合适的生命周期和调度器。
- 修改 Room 实体、DAO 或数据库版本时，同步迁移逻辑、`app/schemas/` 和相关迁移测试。
- 书源、订阅源、规则解析、备份恢复和导入 URI 属于兼容性敏感接口。除非提案明确要求，不要破坏已有 JSON 字段、规则语义或 URI path。
- 遵守 `gradle/libs.versions.toml` 中的固定版本说明。带有“不要更新版本”或兼容性注释的依赖不得顺手升级。
- 不要提交本地签名文件、凭据、`local.properties`、构建产物或 IDE 状态。不要改动 CI 中的签名配置，除非任务明确要求且已确认安全边界。

### 网页端

- `modules/web/` 使用 Node.js 20+、pnpm 9+、Vue 3 和 TypeScript。
- 遵循 `modules/web/.editorconfig`、ESLint 和 Prettier 配置；优先延续现有 Composition API 与目录组织。
- `pnpm lint:fix` 和 `pnpm format` 会写文件。仅检查时使用 `pnpm exec eslint .`，不要在无关任务中批量格式化。
- 网页端构建脚本会把产物同步到 Android 静态资源；提交前检查同步结果，避免混入无关生成差异。

## OpenSpec 工作流

- 新功能、用户可见行为变化、数据格式或兼容性变化，以及跨模块重构，应先在 `openspec/changes/` 建立变更并通过评审，再实现代码。
- 纯文档、注释和机械格式修复通常无需新建变更；如果它们改变了对外承诺或行为规范，仍应走 OpenSpec。
- 开始工作前运行 `openspec list` 并查看相关现有规范或变更，避免重复或冲突。
- 提案必须写明范围、非目标、受影响模块、兼容性与迁移风险，以及可验证的验收条件。
- 实现过程中保持提案、设计、规范和任务与代码一致；不要把未验证事项标记为完成。
- 完成后运行 `openspec validate --all --strict`。只有实现与验证均完成后才能归档变更。

## 常用验证命令

按改动范围选择最小充分验证；不要为了文档变更启动完整 Android 构建。

```bash
# Android 单元测试
./gradlew :app:testAppDebugUnitTest

# Android 静态检查与可安装调试包
./gradlew :app:lintAppDebug
./gradlew :app:assembleAppDebug

# 网页端类型检查、静态检查与构建
cd modules/web
pnpm install
pnpm type-check
pnpm exec eslint .
pnpm build

# OpenSpec 与补丁基础检查
openspec validate --all --strict
git diff --check
```

设备测试位于 `app/src/androidTest/`，需要可用的模拟器或真机；若未运行，交付时明确说明。

## 提交与交付

- 提交信息应简短说明意图；可参考仓库现有风格，关联 issue 时保留编号。
- 交付前检查 `git diff --stat` 和 `git diff --check`，确认没有无关改动、尾随空格或意外生成文件。
- 汇报实际执行过的验证及结果；没有运行的构建、测试或设备验证不得描述为已通过。
