# `js-yaml` 安全依赖检查点

## 修复范围

- 检查时间：2026-09-15（Asia/Shanghai）。
- GitHub Dependabot 告警 #6 对应 `GHSA-2883-xcg3-v3hh`，受影响范围为 `>=4.0.0,<4.3.2`，最低安全版本为 `4.3.2`。
- `pnpm why js-yaml --depth Infinity` 证明唯一引入路径是开发依赖链中的 `@eslint/eslintrc 3.3.6`，其版本约束允许 `4.3.2`。
- 包仓库返回 `js-yaml@4.3.2`，完整性值为 `sha512-SFNOvSJ+Dgf/9An904Yx+CgSlIPCkIpao4qo51lpee25TIRejdH3rhR4EZMGoNx3/TP3O+wzWuiTFl4sqbltzA==`。

实施时曾尝试 `pnpm update js-yaml@4.3.2 --lockfile-only --depth Infinity`，pnpm 同时改写了 20 余项顶层依赖声明并重排大量锁文件内容，不符合最小安全补丁边界；该结果在继续前完整撤销。最终只把锁文件的包条目、父依赖引用和快照条目由 `4.3.1` 更新为 `4.3.2`，未加入 `pnpm.overrides`，也未修改 `package.json`。

最终差异为 `modules/web/pnpm-lock.yaml` 4 行新增、4 行删除；没有其他依赖声明或锁文件变化。

## 验证结果

固定使用 Node.js `22.23.2` 和 pnpm `9.15.9`：

| 验证 | 结果 |
|---|---|
| `pnpm install --frozen-lockfile --force` | 通过；按当前锁文件重建依赖目录 |
| `pnpm test:chapter-html` | 通过；16/16 |
| `pnpm test:static-links` | 通过；6/6，11 个受管链接对账成功 |
| `pnpm type-check` | 通过 |
| `pnpm exec eslint .` | 通过；0 error |
| `pnpm build` | 通过；非 GitHub Actions 环境按既有脚本不复制 Android 静态资产 |
| `pnpm why js-yaml --depth Infinity` | 仅解析到 `js-yaml 4.3.2` |
| 锁文件扫描 | 仅有三处 `4.3.2`，不存在受影响的 4.x 实例 |
| `pnpm audit --audit-level high` | 退出码 0；0 个 high/critical，保留 1 个既知 moderate |
| `git diff --check` | 通过 |

因此实现分支的依赖图已脱离 `js-yaml` 高危范围；GitHub 告警基于默认分支，只能在替代 Pull Request 合并后复核其关闭状态。

## Dependabot 人工兜底结论

Dependabot run `34949114201` 对该传递依赖返回 `security_update_not_possible`，但父依赖约束和包仓库事实证明锁文件可以人工最小更新。

检查 GitHub 当前支持的 `dependabot.yml` 选项后，没有可用配置能够要求托管安全更新任务强制刷新某个传递 pnpm 锁文件依赖：

- `open-pull-requests-limit` 管理普通版本更新队列，不解决安全更新解析器失败，也不能在不改变既有跨生态上限的情况下修复本对象。
- `groups`、`allow` 和 `ignore` 不能把一次 `security_update_not_possible` 转换成有效的锁文件更新；误配反而可能扩大更新范围或削弱现有安全更新行为。
- 仓库已单独启用 Dependabot Security Updates，现有配置继续负责普通更新分组和总并发上限。

因此 `.github/dependabot.yml` 保持不变。本仓库对类似失败继续采用“核对 advisory 与父约束 → 最小锁文件更新 → 冻结安装、依赖图扫描和完整项目验证 → 合并后复核告警”的人工兜底流程，不写入无效配置键。
