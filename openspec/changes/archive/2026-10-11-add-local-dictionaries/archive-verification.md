# 本地词典归档核验报告

核验日期：2026-10-11。变更：`add-local-dictionaries`；流程：`spec-driven`。核验依据为提案、设计、任务清单、三份增量规范、生产代码、自动化测试及本轮已有的构建和设备日志。

## 核验结论

| 维度 | 结果 |
| --- | --- |
| 完整性 | 52 项任务已完成；17 条需求均有实现 |
| 正确性 | 41 个场景有对应代码处理，主要自动化证据见下表；外部浏览器交接的人工验证范围另有说明 |
| 一致性 | 七类适配器、独立索引、手动触发、全局记忆、受控展示及两种备份符合设计 |
| 阻断问题 | 0 项 |
| 未修复警告 | 0 项 |
| 后续建议 | 2 项，见验证边界；不改变本次支持范围 |

完整性、正确性和设计一致性检查均已执行，没有跳过整个核验维度。核验采用生产实现与适用测试的对应关系，不把任务勾选或格式识别当作功能已验证。

## 需求及场景映射

下表的代码路径以仓库为根，行号指向对应实现入口；测试名称对应仓库中的类。

| 需求 | 场景数 | 实现证据 | 测试及实际证据 |
| --- | ---: | --- | --- |
| 首版必须按真实结构支持七类本地词典 | 3 | `app/src/main/java/io/legado/app/model/dictionary/LocalDictionaryStore.kt:199`，七类解析器和 `app/src/main/cpp/dictionary_mobi.c` | `DictionaryParserTest`、`DictionaryVariantTest`、`LocalDictionaryIntegrationTest`、`DictionaryVariantIntegrationTest`、`MobiCompatibilityIntegrationTest` |
| 多文件词库必须整体识别和关联资源 | 3 | `app/src/main/java/io/legado/app/model/dictionary/LocalDictionaryStore.kt:79`、`:199` | `DictionaryStorageTest`、`DictionaryVariantIntegrationTest`；目录授权、缺件和可选资源缺失 |
| 常用归档必须解包后再识别词典 | 2 | `app/src/main/java/io/legado/app/model/dictionary/DictionaryArchive.kt:13`；导入后的多组选择 | `DictionaryArchiveTest`、`DictionaryStorageTest`；四类归档、分卷、加密和损坏输入 |
| 导入必须可取消并原子发布 | 2 | `app/src/main/java/io/legado/app/model/dictionary/LocalDictionaryStore.kt:163`、`:48` | `DictionaryStorageTest`；取消、空间不足、登记失败、进程中断恢复 |
| 导入必须约束文件及资源边界 | 2 | `app/src/main/java/io/legado/app/model/dictionary/DictionaryIO.kt:152`、`DictionaryArchive.kt:13`、`DictionaryIndex.kt:87` | `DictionaryParserTest`、`DictionaryVariantTest`、`DictionaryQueryTest`、`DictionaryArchiveTest`；路径、长度、数量及宽位偏移 |
| 词典管理必须保留原文件并区分身份 | 3 | `app/src/main/java/io/legado/app/model/dictionary/LocalDictionaryStore.kt:163`、`:302`、`:329`；管理列表 | `LocalDictionaryIntegrationTest`、`DictionaryStorageTest`；去重、同名、移动来源、删除及重建回滚 |
| 本地查词必须由现有词典动作触发 | 2 | 原有阅读选词入口继续调用 `DictDialog`；本次未修改 `ReadView` 的分词或选区实现 | `DictionaryReadSelectionTest`；真实长按、拖动、复制和菜单点击 |
| 系统必须全局记住上次本地词典 | 3 | `app/src/main/java/io/legado/app/model/dictionary/LocalDictionaryStore.kt:68`、`app/src/main/java/io/legado/app/ui/dict/DictDialog.kt:120` | `DictionaryCardTest`、`DictionaryReadSelectionTest`、`DictionaryRestartTest`；未命中、失效历史、跨书籍和跨进程 |
| 查询必须离线并隔离并发结果 | 3 | `app/src/main/java/io/legado/app/ui/dict/DictViewModel.kt:33`、`DictionaryIndex.kt:135`；有界查询缓存 | `DictionaryOfflineTest`、`DictionaryCardTest`、`DictionaryPerformanceTest`；断网、取消、迟到结果和百万词头 |
| 语言候选必须保持原词优先且可追溯 | 4 | `app/src/main/java/io/legado/app/model/dictionary/DictionaryIndex.kt:135`、`:209`、`EnglishDictionaryForms.kt:35` | `DictionaryQueryTest`、`DictionaryHtmlTest`；英文变化和反例、繁简多义、古汉语及日韩词库映射 |
| 释义卡片必须完整展示词条及本地资源 | 3 | `app/src/main/java/io/legado/app/ui/dict/DictDialog.kt:193`、`:216`、`:269` | `DictionaryCardTest`、`DictionaryVariantIntegrationTest`；实际绘制、长正文、主题、字号、旋转、导航和播放生命周期 |
| 词库展示必须受本地内容边界约束 | 2 | `app/src/main/java/io/legado/app/model/dictionary/DictionaryHtml.kt:12`、`app/src/main/java/io/legado/app/ui/dict/DictDialog.kt:86`、`:216`、`:269` | `DictionaryHtmlTest`、`DictionaryCardTest`；脚本、协议、网络、资源边界；外部浏览器交接另见验证边界 |
| 现有在线词典和导入行为必须保持兼容 | 2 | `app/src/main/java/io/legado/app/ui/dict/DictViewModel.kt:23`、`:33`；规则管理只新增本地入口，原规则 JSON 与 URI 路径保留 | `LocalDictionaryMigrationTest`、`DictionaryCardTest`；旧在线规则及本地选择保留 |
| 普通备份必须保存清单与偏好 | 2 | `app/src/main/java/io/legado/app/model/dictionary/LocalDictionaryStore.kt:390`、`:413` | `LocalDictionaryIntegrationTest`；清单不带文件或索引、待导入状态及重新关联 |
| 完整备份必须包含查询所需原始资源 | 1 | `app/src/main/java/io/legado/app/model/dictionary/LocalDictionaryStore.kt:390`、`:413`；`Backup.kt`、备份设置 | `LocalDictionaryIntegrationTest`、`DictionaryRestoreTest`；原文件、资源、索引重建和选择映射 |
| 恢复必须校验内容并避免静默覆盖 | 2 | `app/src/main/java/io/legado/app/model/dictionary/LocalDictionaryStore.kt:413`；摘要、身份、路径及原子发布 | `DictionaryRestoreTest`；重复和同名内容、取消、空间不足、摘要及路径错误 |
| 旧备份和在线规则必须保持兼容 | 2 | `app/src/main/java/io/legado/app/model/dictionary/LocalDictionaryStore.kt:413`、`app/src/main/java/io/legado/app/help/storage/Restore.kt:167` | `DictionaryRestoreTest`、`LocalDictionaryIntegrationTest`；清单缺失、未知版本及现有内容保留 |

## 设计及仓库约定

- 格式适配只在导入阶段解析，查询使用独立 SQLite 索引与正文偏移；主 Room 库只保存目录元数据，75→76 自动迁移、结构快照及保留旧在线规则测试齐全。
- 分词、选区及原有词典动作保持不变；不自动按语言切换，不因本地未命中调用在线规则。候选不会改写原词头和正文。
- 文件、数据库和解析重工作位于后台协程；任务绑定现有生命周期。WebView 禁用脚本、通用文件访问和自动网络，仅允许当前词典的受控资源。
- 原文件、资源和索引分离；导入失败不发布半成品，重建有旁路和恢复目录。普通备份只带清单，完整备份保留原文件组，恢复重建索引。
- 用户文案使用字符串资源，相关既有语言目录有对应资源；中文帮助、兼容范围、来源许可及未验证范围均有记录。
- 没有改动固定依赖版本、签名材料或发布工作流。新增 LZO 固定版本的普通更新已加入 Dependabot 忽略清单，独立安全更新渠道保持。

## 归档前修正

1. 新 LZO 依赖带兼容性固定说明，但普通更新忽略规则尚缺失。已补齐 `.github/dependabot.yml`，明确忽略普通 major、minor、patch 更新，并核对安全更新策略未变；维护工作流契约 20 项通过。
2. 新引入的上游 C 源码及头文件有 519 处行尾空白，完整提交范围的空白门禁会检查这些新增文件。已仅清理行尾空白及多余文件末尾空行，保留代码、版权和许可证，并在来源说明记录；主机原生回归和 Android 调试包重新构建通过。
3. 验证记录中“未提交、未归档”的历史状态已经改为归档核验记录，避免归档后保留过期交付描述。

## 验证证据

- 既有完整单元测试：191 项，零失败、零错误，1 项原有忽略；解析、展示、摘要和边界测试 29 项全部通过。
- 既有 Android 静态检查：`lintAppDebug` 零 issue。主调试包和测试包构建成功；清理上游行尾空白后再次执行 `assembleAppDebug` 成功。
- 自建主机原生回归：正文上限、标签 22 别名、先写目标、附加面板隔离、循环及越界拒绝通过。
- 完整模拟器矩阵：API 21、30、36 各 `OK (43 tests)`，各有两次跨进程重启检查；API 36 性能检查 `OK (1 test)`。此矩阵执行于真机兼容补丁前；没有将它描述为补丁后的完整三系统复测。
- 补充真机：API 30 四个用户文件完成生产导入及查词；四本卡片 `OK (1 test)`，新兼容夹具 `OK (3 tests)`。用户词库、截图、日志及临时助手均未进入提交。
- 68 个夹具的大小和摘要与清单一致；夹具来源及许可证有记录。设备导入副本清理后保留已发布词典和下载目录原文件。
- 本轮维护工作流契约：20 项通过。严格规范校验及完整提交范围空白检查通过；同步后的三份主规范逐项与增量规范一致。

详细构建、设备及采样证据位置见 [实施验证记录](verification.md) 和 [性能基线](performance-baseline.md)。本轮没有因归档重复执行用户真机导入或整套设备测试。

## 验证边界与后续建议

1. 大正文 MOBI 导入后半段观测 PSS 为 788,977 KiB，高于合成索引样本的 512 MiB 基线；低内存设备上的真实词库导入尚未验证。后续若扩大设备适用承诺，应先在 `app/src/main/cpp/dictionary_mobi.c` 的原生提取环节评估峰值内存，并用相同词库规模在目标设备实测，不应调高基线来掩盖回归。
2. 外部网页链接交接具备代码及协议过滤单测证据，但本轮没有单独启动真机浏览器验证；本地媒体已检查准备、播放和停止状态，实际扬声器听感未确认。后续媒体或导航改动应为 `app/src/main/java/io/legado/app/ui/dict/DictDialog.kt:216` 增加系统浏览器交接断言，并补充指定真机听感检查。

签名发布包、16 KiB 页系统及其他 ABI 的实际运行未验证，API 23 未单独加入矩阵。以上边界已明确，不将构建成功解释为这些系统运行已通过。

## 归档和推送范围

本次同步三份新主规范并归档到 `openspec/changes/archive/2026-10-11-add-local-dictionaries/`，保留原提案、设计、增量规范、任务和证据记录。代码与归档使用 `codex/add-local-dictionaries` 分支推送至 `origin`，通过受保护 `master` 的评审路径保留待合并状态；不创建发布标签，不触发发布工作流，也不创建 Release。
