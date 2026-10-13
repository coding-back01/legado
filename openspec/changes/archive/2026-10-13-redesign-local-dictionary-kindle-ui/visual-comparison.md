# 界面对比与设计依据

所有 Legado 图片均为运行应用得到的原始截图。截图内英文是测试设备的应用语言，新增文案已同步仓库现有八组词典字符串。绿色阅读背景、红色选区手柄及原选词工具条沿用设备现有阅读配置，本次没有重写通用阅读器界面。

## 阅读中查词

| 重构前 | Kindle 依据 | 重构后 |
| --- | --- | --- |
| [旧即时面板](evidence/before-reading.png)：来源与词头同一行，正文与标题层级接近；底部三个大写主题色按钮，部分进入导航栏 | [K2 原图](https://www.guidingtech.com/wp-content/uploads/Select-Word-and-Dictionary.jpg)：粗体词头、独立滚动、来源入口，正文仍然可见 | [同夹具新面板](evidence/after-reading.png)：主词头突出、来源独立、细线分隔、按钮避开系统栏 |
| 五行省略，无法在预览内阅读剩余内容 | [Android 历史样本](https://learnoutlive.com/wp-content/uploads/2014/07/2015-02-01-20.17.52.png)：释义面板与全文入口分层 | [多字段夹具](evidence/after-day.png)：音标/词性、义项和例句分层，预览内部滚动，随时可进入全文 |

新多字段夹具用于验证现有可靠字段的排版，不是 Kindle 内容，也不假装与旧 DICTD 夹具内容一致。首屏不强行展示所有例句，滚动后可见；全文保留词库原始顺序。

## 完整词条和来源

- [旧 API 21 富文本卡片](evidence/before-rich-entry-api21.png) 上方三排操作挤占正文，饱和主题色按钮密集。
- [新 API 21 富文本卡片](evidence/after-rich-entry-api21.png) 将操作压缩成词头、来源和更多菜单，正文可用空间增加；原图片、内部链接和音频仍通过真实测试。
- [原生释义详情](evidence/after-detail-day.png) 使用分层文字，例句为独立斜体段落；[夜间完整词条](evidence/after-full-night.png) 使用一致深色表面。
- [来源选择](evidence/after-source-day.png) 和 [夜间来源选择](evidence/after-source-night.png) 标明当前词典，采用原生单选交互。依据 [K2 切换图](https://www.guidingtech.com/wp-content/uploads/Switch-Dictionary-2.png)，不复制截图作者的紫色标注。
- 低频的查询语言、设为首选和管理仍在一个菜单中可达。查看另一词典不改变首选；测试继续验证这个语义。

## 适配和管理

- [夜间阅读](evidence/after-night.png)：阅读页和即时面板都保持低亮度；原通用文字选择菜单不是本变更范围。
- [横屏大字号](evidence/after-landscape-large-font.png)：宽度限制、来源、全文及关闭可见，选区光标保持可操作。
- [平板尺寸阅读](evidence/tablet-day.png)：即时面板限宽 560dp，不横跨整页；[平板详情](evidence/tablet-detail-day.png) 限宽 640dp。尺寸来自 API 30 模拟器调整，不冒称物理平板。
- [管理列表](evidence/after-management-api21.png)：名称、语言、首选/候补、格式和数量分层，导入入口直接可见；完整元数据仍在详情，原操作保留。

## 取舍

采用 Kindle 已验证的内容层级、轻量来源入口及保留上下文方式。48dp 触控、返回键、有界滚动、窗口安全区和宽屏限宽是 Android 适配决策，不声称测量自 Kindle。没有加入没有业务实现的 X-Ray、Wikipedia 标签或轮播指示器。

截图不能证明所有商业词库和硬件组合都正确；实际运行结果及未覆盖范围见 verification.md。
