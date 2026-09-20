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

## Pull Request 门禁环境差异诊断

- workflow run `35446377850` 的首个 Android 作业 `105905974970` 在配置阶段因 Maven 仓库瞬时未能解析 `com.google.devtools.ksp:2.3.4` 而失败。同一提交的 Android CodeQL 构建随后成功；Maven Central 中的插件标记可以正常读取，并且使用全新 Gradle 用户目录的本地冷解析也成功，因此没有为瞬时网络故障修改插件仓库或 KSP 版本。
- 重跑后的 Android 作业 `105907600140` 完整执行 lint 成功，但 CI 能读取到本地元数据缓存尚未返回的新版本信息，报告新增 1 个 `NewerVersionAvailable`：`me.zhanghai.android.libarchive:library 1.1.6 → 1.1.7`。
- Libarchive 普通依赖升级不在本变更锁定的版本清单内。依据既有 lint 治理方式和本设计“不追逐实施期间新出现的普通更新”的边界，在该版本声明处补充压缩格式、异常处理和 API 21/23/36 独立回归条件，并只对该 occurrence 添加 `NewerVersionAvailable` 精确抑制；依赖版本与依赖图均未改变。
- 修复后重新执行 `:app:lintAppDebug` 和 `scripts/android_lint_report.py assert-zero` 均通过，报告仍为 0 issue，SHA-256 仍为 `1b6e6f54270cf3298b1e965e53ea942f8dcb9f55dcd8f9c34744266238ed457f`。新的 Pull Request 门禁结果仍须实际成功后才能完成任务 7.2。

## Pull Request 最终门禁与差异审查

- PR #85 最终实现 head 为 `8881bd3466484dcc3ae6c2b34341b18d1ef852c8`，workflow run `35447621161` 的 Android、Web、Android CodeQL、Web CodeQL、OpenSpec/仓库、Artifact 完整性和稳定聚合门禁全部成功。
- 完整差异共 50 个文件，`git diff --check origin/master...HEAD` 通过；人工复核确认没有修改 Room schema、业务源码、Android Manifest、正式签名材料或 Release workflow。
- Web 入口的 5 个版本化资产引用均存在，构建资产目录共有 14 个文件；扫描未发现 `sourceMappingURL`、本机绝对路径、GitHub runner 路径、私钥头或 AWS access key 模式。锁文件 importer 与批准的 20 项目标版本、TypeScript 双编译器兼容层一致。
- PR #85 于 2026-09-20 09:50:01（Asia/Shanghai）以 merge commit `791f53cc0598696b20461ba6bbb785952f791da9` 进入 `master`；Git 祖先检查确认实现 head `8881bd3466484dcc3ae6c2b34341b18d1ef852c8` 被该 merge commit 完整包含。
- merge SHA 对应的 `master` workflow run `35482403819` 全部成功：Android 质量检查、Web 质量检查、Android/Web CodeQL、OpenSpec/仓库、Artifact 完整性和稳定聚合门禁均通过；Android 作业中的 lint 零 issue 断言、单元测试和 Debug APK 构建均成功。
- 本地 `master` 与 `origin/master` 的祖先检查为纯快进关系（读取时落后 7 个提交、无本地独立提交），能够安全快进到精确 merge SHA。

## 合并后安全状态

- Dependabot 告警 #6（`GHSA-2883-xcg3-v3hh`，high）在 merge 后于 `2026-09-20T01:50:05Z` 自动变为 `fixed`；当前没有打开的 high 或 critical Dependabot 告警。
- Element Plus 的 Dependabot #1 与 #5（`GHSA-5m5x-9j46-h678`，medium）均随本次依赖升级自动变为 `fixed`，不再需要沿用接受理由；既有 11 个静态链接合同与相关测试保持不变。
- Code Scanning 打开告警为 0，Secret Scanning 打开告警为 0。
