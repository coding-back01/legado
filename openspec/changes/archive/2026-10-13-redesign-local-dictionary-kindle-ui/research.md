# Kindle 词典界面调研

调研日期：2026-10-13。先完成网络搜索、原始图片观察和官方手册核对，随后检查仓库。以下区分图片事实、文字依据与 Android 适配决策；不声称使用过真实 Kindle 硬件。

## 可追溯证据

| 编号 | 产品与来源 | 实际观察及限制 |
| --- | --- | --- |
| K1 | [Paperwhite 第一、二代对比，2016 年更新](https://www.dummies.com/article/technology/electronics/tablets-e-readers/amazon-fire-tablets/how-to-use-the-dictionary-on-your-kindle-paperwhite-157147/)，[原图](https://cdn.prod.website-files.com/6634a8f8dd9b2a63c9e6be83/669aa10c09aec614e8a9cae9_412421.image0.jpeg) | 已打开原图。一代上方矩形面板、词典名称和关闭、分割线、完整释义；二代在所选词下方显示 Dictionary/X-Ray/Wikipedia 标签、内部滚动条、底部操作。早期反白选词。未确认固件号。 |
| K2 | [2024 年查词教程](https://www.guidingtech.com/how-to-use-dictionary-in-kindle/)，[阅读图](https://www.guidingtech.com/wp-content/uploads/Select-Word-and-Dictionary.jpg)，[切换图](https://www.guidingtech.com/wp-content/uploads/Switch-Dictionary-2.png) | 两张原图均已查看，具体机型/固件不明，不冒称最新 Paperwhite。Dictionary 标题、细横线、加粗词头、斜体词性/例句、义项编号、内部滚动条、右下词典名称；正文仍可见，选词有下划线和拖动柄；切换列表勾选当前来源。紫色框和箭头是教程标注，不是产品配色。 |
| K3 | [Paperwhite 第十一代官方手册](https://d1ergij2b6wmg5.cloudfront.net/kug/kindle_paperwhite_11th/v1/en-US/html/kug.html) | 已读查词章节：长按选词、点击当前词典名切换、横向切换查阅卡片、菜单打开词典。用于交互依据，不能从文字推导精确像素尺寸。 |
| K4 | [Android Kindle 实机教程](https://learnoutlive.com/add-german-english-dictionary-android-kindle-app/)，[查词原图](https://learnoutlive.com/wp-content/uploads/2014/07/2015-02-01-20.17.52.png) | 已查看原图：蓝色选择及拖柄、独立文字操作条、底部浅色释义面板、轻阴影、词头加粗、词性斜体、右下 Full Definition。文章日期 2023，但图片文件标明 2015，App 版本无法确认，只作历史 Android 适配样本。 |
| K5 | [Oasis 第三代 2019 实机评测与视频入口](https://blog.the-ebook-reader.com/2019/08/10/kindle-oasis-3-review-and-video-walkthrough/) | 已阅读作者实测说明：释义可滚动、右下选择词典；与 K2 一致。找到演示入口但未播放视频，不声称观察视频帧。另查阅 [Oasis 第二代评测](https://www.gsmarena.com/amazon_kindle_oasis_second_generation_review-news-28752.php)，只用作辅助交互依据。 |
| K6 | [Amazon Paperwhite 使用说明](https://www.aboutamazon.com/news/devices/how-to-use-kindle-paperwhite) | 官方确认长按与查词、保留阅读上下文。用户给定手册目录可访问；read.amazon.com 抓取失败，内置浏览器两次超时，不作为截图证据。 |

## 结论与选择

采用 K1/K2 的内容层级与 K4 的 Android 操作可达性：清晰词头、克制细线、来源独立于释义、正文优先、保留全文。K1 标签与 K2 卡片并不一致；Legado 无对应 X-Ray/Wikipedia 功能，不加入虚假标签或轮播圆点。不复制教程标注、电子墨水刷新限制、过小关闭按钮，也不擅自替换 Legado 选区手柄。

截图证明面板可位于选词上/下方，但不足以证明 Kindle 的精确定位算法。Legado 采用已有选区空间判断并限制宽高，属于 Android 适配。48dp 触控、sp 缩放、系统返回、横屏有限空间降级同样是适配要求，不归因于 Kindle。

## 当前实现调查

| 界面/调用链 | 当前实现及差距 | 修改与保留边界 |
| --- | --- | --- |
| `ReadView` 稳定选区 → `ReadBookActivity.onStableDictionarySelection` → `DictViewModel.lookupLocal` | XML 内嵌 `view_instant_dictionary`，标题一行塞来源，正文五行省略；三个并列按钮 | 标题、滚动正文、来源/全文分层；保留请求代次、取消、按书开关、选区和分页 |
| `DictDialog` + `dialog_dict.xml` | 三排按钮在正文上方；摘要通过普通换行拼接，全文为受限 WebView | 合并低频操作到单层菜单；原生摘要排版共享；保留展开、内部返回、音频和外部链接策略 |
| `LocalDictionaryActivity` + 管理布局 | 单列列表，名称下每本四五行技术资料，导入藏在菜单 | 名称/角色/语言优先，详情继续提供完整元数据；保留全部管理动作和 ViewModel |
| `DictionaryDisplayProcessor`、`DictionarySummaries`、`DictionaryHtml` | 后台处理可靠字段，复杂原文回退，已有缓存和安全策略 | 仅展示样式可改，摘要提取、缓存键、查词数据结构和净化边界不改 |
| 主题 | 原生用主题色，HTML 固定明暗色；阅读背景由 `ReadBookConfig` 管理 | 即时面板使用阅读背景的低饱和表面和足够对比文字；详情使用应用主题 |

查询规划、英文词形、繁简、首选/候补、导入/存储/备份均已实现。相关 UI/模型目录未发现独立生词本界面，本次不新增。无更深层 AGENTS.md。开始时工作区干净，无活动变更；既有词典规格与两次归档保持不动。已确认 API 36/21/30 模拟器，可运行实际测试；基线实际运行原即时阅读测试 3 项通过，截图捕捉结果见验证记录。
