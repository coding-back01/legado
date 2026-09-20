# Web 依赖迁移证据

## 固定环境与目标版本

- 验证日期：2026-09-19（Asia/Shanghai）。
- 所有最终安装、测试与构建均显式使用 Node.js 22.23.2 和 pnpm 9.15.9；未使用交互 shell 默认的 Node.js 24 或 pnpm 10 更新锁文件。
- `pnpm install --frozen-lockfile` 从不完整依赖目录之外重新安装成功，锁文件未发生二次解析。
- 20 项批准目标的最终解析版本如下：

| 批次 | 最终版本 |
|---|---|
| 运行时基础 | `@vueuse/core 14.4.0`、`@vueuse/shared 14.4.0`、`axios 1.20.0`、`element-plus 2.14.5`、`hotkeys-js 4.0.7`、`vue 3.5.42` |
| 状态与路由 | `pinia 4.0.3`、`vue-router 5.3.0` |
| 构建工具 | `@vitejs/plugin-vue 6.0.8`、`npm-run-all2 9.0.3`、`unplugin-auto-import 21.1.0`、`unplugin-icons 23.0.1`、`unplugin-vue-components 32.1.0`、`vite 8.2.2` |
| 类型系统 | `@types/node 26.4.0`、`@vue/tsconfig 0.9.1`、TypeScript CLI `7.0.2`、`vue-tsc 3.3.11` |
| lint 工具 | `eslint 10.9.1`、`eslint-plugin-vue 10.10.0` |

- `package.json` 没有修改清单外的既有顶层 Web 依赖版本；Kotlin 2.3.0、KSP 2.3.4、Node.js 22、pnpm 9.15.9 和固定 Android 依赖均未随 Web 批次升级。

## TypeScript 7 与 Vite 8 兼容迁移

- TypeScript 7.0.2 不再提供 Vue 3 当前工具链使用的旧编译器 API。按并行安装方案，将 `typescript@7.0.2` 以 `@typescript/native` 别名安装，使 `pnpm exec tsc --version` 实际输出 `Version 7.0.2`；`typescript` 包名映射到 `@typescript/typescript6@6.0.2`，供 `vue-tsc` 和 `typescript-eslint` 使用现有 API。
- 新增 `tsconfig.chapter-test.json`，章节 HTML 测试通过 TypeScript 7 的 `tsc --project` 真实编译，继续启用 `strict` 与 `noEmitOnError`；16 项 Node 测试随后真实执行。移除了 TypeScript 6 已弃用的 `baseUrl`。
- Vite 8 不再接受原 Sass `api: modern-compiler` 配置，已移除该选项。
- Vite 8 使用 Rolldown/Oxc 后，原 `esbuild.drop` 不能继续承担生产构建语义；现使用 `build.rolldownOptions.output.minify` 的 `dropConsole` 与 `dropDebugger`，并保留压缩、变量改名和代码生成，不新增 `esbuild` 依赖。
- `unplugin-auto-import` 与 `unplugin-vue-components` 重新生成了声明文件；没有手工编辑生成声明或生产压缩资产。

## 聚焦检查与生产预览

每个迁移批次完成后均执行章节 HTML、静态链接、类型检查、ESLint 和生产构建；最终从冻结锁文件安装后再次完整执行，结果如下：

- `pnpm test:chapter-html`：16/16 通过。
- `pnpm test:static-links`：6/6 通过；11 个受管链接与固定清单一致，动态 `href`、危险协议、重复链接以及缺失 opener/referrer 隔离仍会被拒绝。
- `pnpm type-check`：通过。
- `pnpm exec eslint .`：零 error。
- `pnpm build`：通过，Vite 8.2.2 共转换 1792 个模块。
- 本地生产预览在无真实用户数据、无 Android 后端的环境中验证了书架、书源编辑、订阅源编辑、章节页和帮助页路由；书源与订阅源表单、帮助页 11 个受管链接均正常渲染。控制台只有因本地预览没有 Android 后端而产生的既有请求失败，不属于依赖或路由回归。

## 安全依赖与生成资产

- `pnpm audit --audit-level low` 返回 `No known vulnerabilities found`。
- `pnpm why js-yaml --depth Infinity` 无输出，`pnpm-lock.yaml` 扫描也不存在 `js-yaml`；最终 ESLint 10 图已经完全移除该传递依赖。
- 使用既有 `scripts/sync.js` 并设置其 GitHub Actions 环境开关，把生产输出同步到 `app/src/main/assets/web/vue/`；旧 Vite 6 哈希资产由脚本删除，新 Vite 8 输出新增 `rolldown-runtime` 以及对应脚本、样式和入口引用。
- 相同 Node.js 22.23.2、pnpm 9.15.9 环境连续执行两次完整生产构建与同步，两次 Android Web 资产目录均为 16 个文件，逐文件 SHA-1 清单完全一致，第二次构建没有新增、删除或内容漂移。

以上结果满足任务 4.1 至 6.3；Android 打包与设备矩阵在最终资产进入应用后由后续总验证单独记录。
