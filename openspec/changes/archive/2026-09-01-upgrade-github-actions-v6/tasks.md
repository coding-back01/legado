## 1. 隔离前置变更并刷新事实

- [x] 1.1 完成 `harden-element-plus-static-links` 的独立提交、Pull Request、合并、归档读回和
  已合并临时分支清理，确认本变更将基于合并后的最新 `origin/master`，且不会混入前一变更的
  未提交文件。
- [x] 1.2 重新读取 #73 的 head SHA、ahead/behind、目标版本和全部检查；确认它仍因精确版本
  合同失败并继续保持打开，若事实漂移则先更新并重新评审本 OpenSpec 工件。
- [x] 1.3 对照官方 release/action metadata 复核 8 个目标 Action 的 Node 24、runner
  `2.327.1` 下限、输入和大版本差异，记录 `checkout` fork/凭据、Gradle provider、artifact
  摘要、pnpm 缓存和 stale 权限结论。
- [x] 1.4 清点三个工作流的 Action 出现位置、事件、权限、缓存、push 和 artifact 路径，确认
  CodeQL v4、JDK 17、Node.js 22、pnpm 9.15.9、Gradle/AGP、应用依赖及正式发布授权均不在
  升级范围。

## 2. 建立精确合同并观察 RED

- [x] 2.1 扩展 `.github/scripts/test_maintenance_workflow.py`，分别读取 `test.yml`、
  `release.yml` 和 `stale.yml`，按工作流片段断言 8 个批准的大版本，并拒绝目标 Action 的旧
  major 或未批准 major。
- [x] 2.2 为测试工作流补充合同：非 push checkout 不持久化凭据、所有 `setup-gradle@v6`
  使用 `cache-provider: basic`、pnpm 与 setup-node 缓存所有权唯一、upload 使用压缩 artifact，
  且稳定聚合门禁要求真实 artifact 下载核验成功。
- [x] 2.3 为 stale 工作流补充合同：仅有 `issues: write` 和 `contents: read`，只处理
  `needs-info` issue，豁免 `crash,data-loss,security`，并保持两个 Pull Request 时限为 `-1`。
- [x] 2.4 扩展 `ReleaseWorkflowContractTest`，精确断言 release checkout 凭据边界、JDK/Gradle
  目标、唯一 `release-apk` 的 v7 压缩上传、v8 按名解压下载、`digest-mismatch: error` 以及
  下载核验先于 tag/Release 创建。
- [x] 2.5 在修改工作流前运行 Python 合同和聚焦 Kotlin 合同，保存只由旧 Action major 和
  缺失显式安全配置造成的预期 RED；出现其他失败时停止，不进入工作流修改。

## 3. 升级测试与安全分析工作流

- [x] 3.1 将 `.github/workflows/test.yml` 的 checkout 升至 v7、setup-java 和 setup-gradle
  升至 v6、upload-artifact 和 pnpm/action-setup 升至 v7/v6、setup-node 升至 v7，并保持
  CodeQL `init/analyze@v4` 不变。
- [x] 3.2 为所有不执行 push 的 checkout 显式设置 `persist-credentials: false`，为每个
  setup-gradle 显式设置 `cache-provider: basic`，同时保持 Android CodeQL 命令只有
  `--no-build-cache` 而没有 `--build-cache`。
- [x] 3.3 为 pnpm/action-setup 保持 `version: 9.15.9`、`run_install: false` 并显式关闭其
  cache；由 setup-node 继续用 Node.js 22、固定锁文件和 pnpm cache，并显式关闭 npm 自动缓存。
- [x] 3.4 为现有 lint/Debug artifact 上传显式设置 `archive: true`，保持原名称、缺失文件策略
  和保留期，不启用 direct upload、overwrite 或隐藏文件上传。
- [x] 3.5 增加无签名 artifact 上传与下载作业：上传确定性夹具及 SHA-256 文件并保留 1 天，
  下载时按唯一名称使用 `skip-decompress: false` 和 `digest-mismatch: error`，核对精确文件集合
  后执行 `sha256sum --check --strict`。
- [x] 3.6 将 artifact 下载核验加入稳定 `维护门禁` 的 `needs` 和成功判定，确保其失败、取消
  或跳过均不能被聚合为绿色。

## 4. 升级发布与 stale 工作流

- [x] 4.1 将 `.github/workflows/release.yml` 的 checkout 升至 v7、setup-java 和 setup-gradle
  升至 v6、upload-artifact 升至 v7、download-artifact 升至 v8，不改变触发器、授权、签名、
  唯一普通 APK、tag、草稿 Release 或目标 SHA 逻辑。
- [x] 4.2 在 release 的 authorize/build checkout 中关闭凭据持久化，只在需要 push 候选 tag 的
  draft-release checkout 中显式保留；所有 setup-gradle 使用 `cache-provider: basic`。
- [x] 4.3 为 release upload 显式设置 `archive: true`，为 download 显式设置
  `skip-decompress: false` 和 `digest-mismatch: error`，保持 `release-apk`、`dist` 路径、30 天
  保留期与下载后唯一 APK 核验。
- [x] 4.4 将 `.github/workflows/stale.yml` 升至 `actions/stale@v11`，逐项对账触发器、最小权限、
  issue-only 时限、标签、豁免项和中英文关闭说明均无语义变化。

## 5. 本地 GREEN 与差异审计

- [x] 5.1 运行 `python3 .github/scripts/test_maintenance_workflow.py`，确认版本矩阵、权限、缓存、
  artifact、stale、CodeQL 和聚合合同全部通过。
- [x] 5.2 运行聚焦 `ReleaseWorkflowContractTest` 及 `:app:testAppDebugUnitTest`，确认发布合同和
  既有 Android 单元测试通过；不得把未运行的正式签名构建或设备测试描述为通过。
- [x] 5.3 使用仓库固定的 actionlint `1.7.12` 和已记录 SHA-256 校验三个工作流，确认 YAML、
  表达式、作业依赖和新增 artifact 闭环无静态错误。
- [x] 5.4 运行 `openspec validate --all --strict`、`git diff --check`、精确 `git diff --stat` 和
  `git status --short`，确认没有依赖、锁文件、应用源码、Room schema、签名材料、Release 对象
  或无关生成文件变化。

## 6. 替代 Pull Request 与远端闭环

- [x] 6.1 只暂存本变更的精确路径，提交并推送独立 `codex/` 分支，创建明确说明“替代 #73、
  不触发正式 Release”的 Pull Request；不得使用 `git add -A`。
- [x] 6.2 等待范围识别、Android、Web、CodeQL Android/Web、OpenSpec/仓库、artifact 上传下载
  和稳定 `维护门禁` 全部实际绿色，核对 Gradle 首次缓存 miss 没有隐藏构建失败，且 CodeQL
  Android 确实使用 `--no-build-cache` 编译源码。
- [x] 6.3 核对替代 Pull Request 没有触发 `Release Build`，没有读取正式签名 Secrets、推送
  tag、创建草稿 Release 或改变 stale 实际对象；任何失败均保持 #73 打开并停止合并。
- [x] 6.4 人工评审通过后合并替代 Pull Request，重新读取 `origin/master` SHA、合并提交检查、
  三个工作流和 OpenSpec 状态；只有读回结果完整时才以替代 Pull Request 链接关闭 #73。
- [x] 6.5 按验证与归档流程同步主规范、归档 `upgrade-github-actions-v6`，在归档提交和合并后的
  `master` 上再次严格校验，并只按精确 ref/SHA 清理已合并临时分支；不触发正式发布。
