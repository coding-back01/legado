# PR 2 链接分类审计

## 审计范围

- 仓库首页：`README.md`、`English.md`
- 当前功能入口：更新 API、应用分享、贡献者、内置 Web 首页、根目录与网页端 `package.json`
- 应用内资料：`appHelp.md`、`jsHelp.md`、`ruleHelp.md`、`updateLog.md`、`disclaimer.md`
- 运行时资源：`defaultData/rssSources.json`
- 维护入口：Issue 表单与 Release workflow

审计使用精确文件和链接类别逐项处理，没有执行全局域名替换。

## 当前 fork 入口

以下入口改为 `coding-back01/legado`：

- Latest Release API、仓库主页、Release、Issue、私密安全报告和贡献者列表；
- 8 个现有 locale 的分享文案与贡献者说明；
- 内置 Web 导航页、网页端源码注释和应用内帮助中的当前源码链接；
- 根目录及 `modules/web/package.json` 的 repository、bugs、homepage 和 GPL-3.0-only 许可证标识。

## 本地或不可变替代

- README/English 的应用图标使用仓库内资源；免责声明使用 `app/src/main/assets/disclaimer.md`。
- 两个已失效的默认 RSS jsDelivr 图标改为空值，由既有 `image_rss` placeholder/error 处理。
- QQ 导入帮助截图固定到上游资源仓库提交 `3cdf95ece45c85eac9cb7289e3339661373bc4ea` 的真实路径。
- 2021、2022、2023 更新日志分别固定到当前 fork 可读取的提交 `51c16a1e...`、`1508f698...`、`6697190e...`，不再依赖不存在的 record 分支。
- 原项目“函数共用”Wiki 页面无法恢复，`ruleHelp.md` 已写明缺失事实，不伪造当前仓库 Wiki。

## 保留的上游与历史来源

- README/English 中的原项目网站、帮助、Google Play、社区和 `gedoor/legado_web_*` 明确标为上游资源。
- `jsHelp.md` 的历史 Discussion #3259、`CronetInterceptor.kt` 的 Issue #5025、`EpubFile.kt` 的 Issue #1932、加密实现的 PR #2880 保留真实历史归属。
- LICENSE、依赖坐标、代码注释中的真实来源不改写为本 fork 原创。

## 验证方式

- `ForkLinkContractTest` 固定当前入口、8 个 locale、不可变提交和历史来源白名单。
- PR 交付前另运行 JSON/XML/资源构建、链接可达性抽查、OpenSpec 严格校验和 `git diff --check`；网络不可用与 HTTP 失败分别记录，不将未检查链接描述为可达。
