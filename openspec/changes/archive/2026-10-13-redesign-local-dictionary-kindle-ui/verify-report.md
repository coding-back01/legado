# 归档前核验报告

日期：2026-10-13。核验对象：redesign-local-dictionary-kindle-ui，流程为 spec-driven。本次用户在 Debug 安装验证阶段之后明确授权验证、归档、提交及正式发布，替代此前仅保留待审查的交付边界。

## 核验结论

| 维度 | 结果 |
| --- | --- |
| 完整性 | 13/13 任务完成；5 条需求、9 个场景均有实现或已有兼容路径 |
| 正确性 | 5/5 需求对应代码、设备回归和截图；未发现阻断问题 |
| 一致性 | XML/View、受限 WebView、共享排版、限宽与主题设计一致；未新增依赖或改写业务数据模型 |

## 需求与场景证据

| 需求及场景 | 实现位置 | 验证 |
| --- | --- | --- |
| 即时面板：长按关闭、长内容与受限空间 | ReadBookActivity.kt 的 onStableDictionarySelection、positionInstantDictionary、返回回调；view_instant_dictionary.xml | DictionaryInstantReadingTest：按书开关、关闭不重弹、真实滑动、全文与来源、安全区、横屏大字号 |
| 内容层级：字段/多候选、全文导航 | DictionaryPresentation.kt:21；DictDialog.kt 的 display/bindDisplay/navigate；DictionaryHtml.kt | 可靠摘要、缺失字段、词形说明、长词条、内部链接、图片与音频回归；明暗截图 |
| 来源语义：切换、加载与错误 | DictDialog.kt:154、264；ReadBookActivity.kt 的 queryInstantDictionary | DictionaryCardQueryPolicyTest 与 DictionaryCardTest：首选保持、失败与未收录区分、迟到结果隔离 |
| 管理：已有/空词库 | LocalDictionaryActivity.kt:46、194；activity_local_dictionary.xml | DictionaryManagementTest：角色可见、分类、首选/候补、关联和资源确认；空状态沿用原逻辑 |
| Android 适配：主题字号、功能兼容 | 窗口可见区域、560/640dp 限宽、局部明暗对话框、48dp 操作 | API 21/36 各 30 项回归；最后 14/8 项 UI 聚焦复测；API 30 平板 2 项；250 项单元通过、1 项跳过 |

以上代码路径均相对 app/src/main/java/io/legado/app/，布局相对 app/src/main/res/layout/。完整命令、过程问题及修复见 verification.md，图片与依据见 visual-comparison.md、research.md。

## 问题分级

- 阻断问题：无。
- 需修正的规格/实现偏差：无。
- 已记录的验证边界：未覆盖全部商业词库、ARM 完整测试矩阵和 TalkBack 人工朗读。这些不被冒称通过；正式 APK 将另按仓库发布规范完成签名、候选完整性和指定真机非破坏性验证。

已复核最终 Debug 构建成功、lint 无问题、既有测试日志与截图存在。归档只同步新增的 local-dictionary-presentation，不覆盖查询、导入、备份规格。可以归档；正式发布资格仍由独立发布门禁决定。
