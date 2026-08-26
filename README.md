# Legado / 开源阅读（个人稳定 fork）

<!-- markdownlint-disable MD013 MD033 -->

<div align="center">

<img
  src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
  alt="Legado 图标"
  width="125"
  height="125"
/>

<p>一款免费、开源且不内置内容的 Android 阅读器</p>

<p>
  <strong>中文</strong>
  ·
  <a href="English.md">英文</a>
</p>

</div>

> [!IMPORTANT]
> 本仓库是 `coding-back01/legado` 独立维护、独立签名的个人衍生版本，不是原项目的官方继任，也未获得原项目官方背书。应用本身不提供书籍、书源或其他内容，使用者需要自行导入并对第三方内容负责。

## 当前维护状态

- 当前源码：[coding-back01/legado](https://github.com/coding-back01/legado)
- 正式版本：[Releases](https://github.com/coding-back01/legado/releases)
- 正式版问题：[Issues](https://github.com/coding-back01/legado/issues)
- 私密安全报告：[Private vulnerability reporting](https://github.com/coding-back01/legado/security/advisories/new)
- 完整贡献者：[当前 Git 历史贡献者](https://github.com/coding-back01/legado/graphs/contributors)

新正式版目前处于稳定化冻结状态。恢复发布后只提供普通版 `io.legado.app.release`；历史共存版资产和设备数据继续保留，但不再生成新共存版。当前签名由本仓库独立保管，不能直接覆盖其他签名的同包名应用，迁移或安装前请先备份。

这是低维护容量的个人稳定 fork：只接受正式版 bug 和私密安全报告，不提供响应时限，也默认不受理功能请求。兼容性声明只覆盖 Release 说明中明确记录、实际自动验证过的设备与 Android 版本，不对其他设备作推断。

## 主要功能

1. 支持自定义书源，可自行设置规则抓取网页数据；软件内提供规则说明。
2. 支持列表书架和网格书架切换，以及自定义订阅内容。
3. 支持替换净化、本地 TXT 和 EPUB 阅读。
4. 阅读界面可配置字体、颜色、背景、行距、段距、加粗和简繁转换。
5. 支持覆盖、仿真、滑动和滚动等多种翻页模式。
6. 支持本地与 WebDAV 备份、网页端书架和源编辑。

## API

- 应用提供网页接口（`Web`）和内容提供器（`Content Provider`）两种 API，详见 [API 文档](api.md)。
- 可通过 `legado://import/{path}?src={url}` 唤起导入预览。
- `path` 支持 `bookSource`、`rssSource`、`replaceRule`、`textTocRule`、`httpTTS`、`theme`、`readConfig`、`dictRule` 和 [`addToBookshelf`](app/src/main/java/io/legado/app/ui/association/AddToBookshelfDialog.kt)。
- 书源、订阅源、规则、备份和导入 URI 是兼容性敏感接口；本 fork 不随意改变其既有语义。

## 本仓库资料

- [免责声明](app/src/main/assets/disclaimer.md)
- [更新日志](app/src/main/assets/updateLog.md)
- [应用内帮助](app/src/main/assets/web/help/md/appHelp.md)
- [GPL-3.0 许可证](LICENSE)

## 上游项目与社区资源

以下链接属于原项目或其他上游社区，不是本 fork 的维护、下载或支持入口：

- 原项目作者与贡献者：版权和历史贡献保留在许可证及 Git 历史中。
- 上游网站：[gedoor.github.io](https://gedoor.github.io)、[legado.top](https://www.legado.top/)
- 上游帮助：[语雀帮助](https://www.yuque.com/legado/wiki)、[书源规则教程](https://mgz0227.github.io/The-tutorial-of-Legado/)
- 上游分发：[Google Play](https://play.google.com/store/apps/details?id=io.legado.play.release)、[Coolapk](https://www.coolapk.com/apk/io.legado.app.release)、[IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/io.legado.app.release)
- 上游社区：[Telegram 群组](https://t.me/yueduguanfang)、[Telegram 频道](https://t.me/legado_channels)、[Discord](https://discord.gg/VtUfRyzRXn)
- 上游 Web 项目：[网页端书架](https://github.com/gedoor/legado_web_bookshelf)、[网页端源编辑](https://github.com/gedoor/legado_web_source_editor)

## 版权与致谢

本 fork 保留原作者 `gedoor`、原项目贡献者及本分支贡献者的版权和提交归属，并依照 GNU GPL v3 发布。依赖项目的版权与许可证归各自作者所有；主要依赖包括 jsoup、JsoupXpath、JsonPath、Rhino、OkHttp、Glide、NanoHTTPD、Markwon、HanLP 和 epublib。

## 上游历史界面预览

下列图片仅用于展示原项目历史界面，固定到上游资源仓库提交 `3cdf95ece45c85eac9cb7289e3339661373bc4ea`，不表示本 fork 对上游站点提供支持。

<p align="center">
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B1.jpg" alt="上游历史界面 1" width="270" />
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B2.jpg" alt="上游历史界面 2" width="270" />
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B3.jpg" alt="上游历史界面 3" width="270" />
</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B4.jpg" alt="上游历史界面 4" width="270" />
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B5.jpg" alt="上游历史界面 5" width="270" />
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B6.jpg" alt="上游历史界面 6" width="270" />
</p>
