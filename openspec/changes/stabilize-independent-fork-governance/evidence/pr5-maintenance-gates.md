# PR 5 持续维护门禁证据

本文记录 `codex/maintenance-gates` 在创建 Pull Request 前的 RED/GREEN、本地质量检查和安全边界。远端范围探针、CodeQL 分析与告警审查、合并后 `master`、ruleset 和自动删分支设置仍按任务 6.7–6.11 单独闭环；本地 workflow 契约通过不代替这些远端证据。

## 验证对象

- 基线：`master@4e1c3474aac3d90f9f02af940dfdfafbb0c07d17`
- 分支：`codex/maintenance-gates`
- 日期：2026-08-29
- 范围：Android、Web、OpenSpec/仓库检查、CodeQL 和稳定聚合门禁

## 维护 workflow 结构

原 `.github/workflows/test.yml` 使用 workflow 级路径过滤，只运行 Android 单元测试和 Debug 构建；原 `.github/workflows/web.yml` 也使用 workflow 级路径过滤，未显式运行类型检查和只读 ESLint。两者不能在全部 Pull Request 和 `master` 提交上提供稳定的必需检查名，仓库也没有持续 OpenSpec 或 CodeQL 检查。

PR 5 将二者收敛为不使用 workflow 级 `paths` 的 `.github/workflows/test.yml`，并提供以下 job：

- `识别变更范围`：按提交范围将 Android、Web 和文档/OpenSpec 改动分类；未知路径 fail-safe 地运行 Android 与 Web；`workflow_dispatch` 提供 `android`、`web`、`docs`、`all` 等价探针。
- `Android 质量检查`：JDK 17、Android lint、JVM 单元测试和 Debug APK；lint 报告即使 lint 失败也尝试上传并保留 30 天，成功的 Debug APK 保留 7 天。
- `Web 质量检查`：Node.js 22、pnpm 9.15.9 冻结安装、章节 HTML 安全测试、类型检查、只读 ESLint、构建，以及 Android Web assets 无未提交差异检查。
- `OpenSpec 与仓库检查`：固定 `@fission-ai/openspec@1.8.0`、固定并校验摘要的 actionlint 1.7.12、维护门禁契约测试，以及真实提交范围的 `git diff --check`。
- `CodeQL（Android）` 与 `CodeQL（Web）`：分别使用 `java-kotlin/manual` Debug 构建和 `javascript-typescript/none`，在每个 Pull Request 与 `master` 提交上实际分析。
- `维护门禁`：使用 `if: always()` 聚合所有 job；Android/Web 只允许在分类结果不适用时为 `skipped`，范围识别、仓库检查、CodeQL 或任何适用子任务不是 `success` 时均失败。

workflow 默认只有 `contents: read`；CodeQL job 额外且仅获得 `security-events: write`，没有 `packages: read`、`continue-on-error`、正式签名 Secrets、正式构建或发布步骤。PR 更新可以取消同一 PR 的旧 run；`master` run 不会因后续提交自动取消，避免跳过某个 `master` 提交的 CodeQL 分析。

## 范围分类与 workflow 契约 RED→GREEN

先只增加 `.github/scripts/test_maintenance_workflow.py`，在旧 workflow 上运行：

```bash
python3 .github/scripts/test_maintenance_workflow.py
```

结果为 12 个契约中的 5 个 error、7 个 failure：5 类范围测试因分类器不存在报错，其余测试分别命中 workflow 级路径过滤、缺少 lint/OpenSpec/CodeQL、旧 Web workflow 仍存在、没有固定聚合 gate 等预期缺口。

随后增加 `.github/scripts/classify-maintenance-scope.sh`，重构维护 workflow 并删除旧 Web workflow。同一命令为 12/12 通过，实际覆盖：

- Android-only：`android=true`、`web=false`
- Web-only：`android=false`、`web=true`
- 文档/OpenSpec-only：`android=false`、`web=false`
- Android/Web 混合与 CI 实现改动：`android=true`、`web=true`

`bash -n .github/scripts/classify-maintenance-scope.sh` 同时通过。

## YAML 与 actionlint

使用 actionlint 1.7.12 的官方 Darwin arm64 Release 压缩包，先按官方 checksums 校验：

```text
aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f
```

第一次 actionlint 真实解析发现两处行内 `run: : > ...` 不是合法 YAML，退出 1；改为 block scalar 后再次运行，全部 workflow 通过。CI 使用同版本 Linux amd64 压缩包，并固定校验官方摘要 `8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8` 后才执行。

## Android 全量验证

第一次强制重跑因当前 shell 没有 `ANDROID_HOME`，在 Gradle 任务解析前以 `SDK location not found` 退出，没有进入测试或代码编译，因此不作为代码 RED。随后不创建 `local.properties`，显式使用现有 Android SDK、ADB 35.0.2 和 JDK 17.0.17：

```bash
ANDROID_HOME=/Users/back/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/back/Library/Android/sdk \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug :app:assembleAppDebug \
  --rerun-tasks --build-cache --no-daemon --warning-mode all
```

结果为 `BUILD SUCCESSFUL`，耗时 3 分 10 秒，131/131 个 Gradle 任务实际执行。测试 XML 汇总为 28 个测试套件、129 个测试、0 failure、0 error、1 skip；lint 为 0 error、104 warning、18 hint。Debug APK `legado_app_3.26.082911.apk` 构建成功，大小为 29,943,675 字节。

lint 报告 SHA-256：

| 报告 | SHA-256 |
|---|---|
| `lint-results-appDebug.xml` | `0a2edcde943acb4ca3d1084d94fb29a977a666305cd61727137521d342844b2e` |
| `lint-results-appDebug.html` | `25b63402e01db26a7aa07fb04502b1463b0edf1eaf4c21d36a1090911e940973` |
| `lint-results-appDebug.txt` | `72c5d488bb734d04575002c4c934c197b2c36071bf3cc1d7665d3c9f69bcb5fa` |

## Web、OpenSpec 与范围边界

在 `modules/web/` 使用 Node.js 22.18.0 和 pnpm 9.15.9 实际运行：

```bash
pnpm install --frozen-lockfile
pnpm test:chapter-html
pnpm type-check
pnpm exec eslint .
pnpm build
```

冻结安装、16 个章节 HTML 安全测试、类型检查、只读 ESLint 和构建全部成功；本地构建按既有脚本不复制 GitHub Actions assets，目标 Android Web assets 无未提交差异。`openspec validate --all --strict` 为 3 passed、0 failed；`git diff --check` 无输出。

本 PR 不修改最低 API 21、Room schema、书源/订阅源规则、导入 URI、备份格式、普通正式版包名、签名材料或依赖版本；验证 workflow 只产生 Debug APK 和报告，不读取或发布正式签名产物。

## 远端待完成门禁

以上本地结果满足任务 6.1–6.6 的实现和本地验证条件。任务 6.7–6.11 仍保持未完成：必须先创建 Draft Pull Request，在精确 PR head 上运行 Android、Web、文档/OpenSpec 三类范围探针和完整 CodeQL，审查 SARIF/可用告警并确认高危/严重项处置后才可转 Ready、合并和验证 `master`；只有稳定检查名被真实观察后才允许最小修改现有 ruleset 和自动删分支设置。正式发布继续冻结。
