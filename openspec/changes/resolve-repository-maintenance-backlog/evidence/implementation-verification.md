# 替代实现本地总验证

## 可归因检查点

本次替代实现从基线 `997c6037b3810f560a04812d9c326c07547ed802` 连续完成，当前包含三个已经审查的实现检查点：

| 提交 | 内容 |
|---|---|
| `ff1b2cfc1` | 修复 `js-yaml` 高危传递依赖 |
| `331c3f170` | 迁移 Gradle 9.7.1 与 AGP 9.4.0 |
| `1786beb97` | 升级 Web 依赖、兼容配置与 Android 内置 Web 资产 |

实现未直接合并 PR #42/#45/#80/#81，也未提前关闭或删除这些对象；合并后的远端闭环仍受任务 7 和 8 约束。

## 最终依赖图与 Web 验证

- 20 项批准的 Web 目标版本全部达到，完整版本与迁移说明见 `web-dependencies.md`。
- Node.js 22.23.2、pnpm 9.15.9 下的冻结锁文件安装成功。
- `pnpm audit --audit-level low` 为零已知漏洞；`pnpm why js-yaml --depth Infinity` 与锁文件扫描均证明最终依赖图中不存在 `js-yaml`。
- TypeScript 7.0.2 真实编译的章节测试 16/16 通过；静态链接测试 6/6 和 11 个受管链接对账通过；`vue-tsc` 类型检查、ESLint 10 零 error、Vite 8.2.2 生产构建全部通过。
- 本地生产预览验证书架、章节、书源编辑、订阅源编辑和帮助入口均能加载。由于本地没有 Android 后端，书架请求产生预期的后端响应错误；没有发现由 Vue、Pinia、Vue Router 或 Element Plus 升级引起的脚本或路由异常。
- 使用既有同步脚本连续执行两次生产构建，两次 `app/src/main/assets/web/vue/` 均为 16 个文件且逐文件 SHA-1 完全一致；入口引用均存在，没有本机绝对路径、source map 指针或显式密钥模式。

## 最终 Android 验证

所有 Gradle 命令显式使用 JDK 17.0.17、Android SDK 21/23/36、Gradle 9.7.1 与 AGP 9.4.0：

- `:app:testAppDebugUnitTest`：通过。
- `:app:lintAppDebug`：通过；`app/build/reports/lint-results-appDebug.xml` 的 `issue` 节点为 0，报告 SHA-256 为 `1b6e6f54270cf3298b1e965e53ea942f8dcb9f55dcd8f9c34744266238ed457f`。
- `:app:assembleAppDebug`：通过；最终 Debug APK SHA-256 为 `d356b5564cc11764b09176c83b3f847af57bf49d36e9760a7881328d07824e6e`。
- 最终 APK 在 `api21-mdpi-light-phone`、`api23-mdpi-light-phone`、`api36-mdpi-light-phone` 三个可丢弃 AVD 的只读 golden profile 中均返回 `OK (1 test)`；未使用 `--update`，没有改写版本化 golden，也没有接触正式版个人数据。
- Android/Web 组合差异没有涉及 Room schema、书源/订阅源规则、备份与导入格式、正式签名材料或 Release 工作流。

## 仓库与 OpenSpec 检查

- `.github/scripts/test_maintenance_workflow.py`：20/20 通过，覆盖路径分类、Android/Web 门禁、CodeQL 双语言配置和稳定聚合检查合同。
- actionlint 1.7.12：通过；macOS arm64 发布包先按官方 `actionlint_1.7.12_checksums.txt` 校验，归档 SHA-256 为 `aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f`。
- 三份 GitHub Actions workflow 均能被 YAML 解析器读取。
- `openspec validate --all --strict`：8/8 通过。
- `git diff --check`：通过；完整文件清单、锁文件 importer、生成资产入口和三个实现提交均已人工审查。

## 尚未运行或必须等待的检查

- 本机没有执行 GitHub 托管的 CodeQL analyze/upload；这里只完成了同构 Android 编译、workflow 合同和 actionlint 前置检查。替代 Pull Request 创建后必须等待 Android 与 Web CodeQL 实际成功，才能完成任务 7.2。
- 尚未读取合并后的 Dependabot、Code Scanning、Secret Scanning、ruleset、Release 与旧 PR 状态；这些检查只能在替代 Pull Request 进入 `master` 后执行。
- 尚未关闭 PR #42/#45/#80/#81、删除远端分支、移除附加工作树或清理本地历史分支；这是有意保留的合并后阶段，不属于本地实现缺口。

本地替代实现已完成，可以进入单一替代 Pull Request；外部清理前仍必须满足 OpenSpec 第 7 节的合并与门禁条件。

## 替代 Pull Request

- 唯一实现分支：`codex/resolve-repository-maintenance-backlog`。
- 替代 Pull Request：[PR #85](https://github.com/coding-back01/legado/pull/85)。
- PR 说明已关联 Dependabot 告警 #6 以及 PR #42/#45/#80/#81，并明确它们只能在 PR #85 合并及合并后门禁成功后关闭。
