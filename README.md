# Legado / 开源阅读

<!-- markdownlint-disable MD013 MD033 -->

<div align="center">

<img
  src="https://github.com/gedoor/legado/raw/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
  alt="Legado 图标"
  width="125"
  height="125"
/>

<p>一款免费、开源的 Android 小说阅读器</p>

<p>
  <a href="https://gedoor.github.io">gedoor.github.io</a>
  ·
  <a href="https://www.legado.top/">legado.top</a>
</p>

<p>
  <strong>中文</strong>
  ·
  <a href="English.md">英文</a>
</p>

<p>
  <a href="https://play.google.com/store/apps/details?id=io.legado.play.release">
    <img
      src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/icon_android.png"
      alt="从 Google Play 下载"
      width="32"
      height="32"
    />
  </a>
  &nbsp;
  <a href="https://jb.gg/OpenSourceSupport">
    <img
      src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg"
      alt="JetBrains 开源支持计划"
      width="32"
      height="32"
    />
  </a>
</p>

</div>

> [!IMPORTANT]
> 软件不提供任何内容，需要您自行手动添加，例如导入书源。初次使用前请查看
> [官方帮助文档](https://www.yuque.com/legado/wiki)。

## 目录

- [主要功能](#主要功能)
- [交流社区](#交流社区)
- [API](#api)
- [其他资源](#其他资源)
- [致谢](#致谢)
- [界面预览](#界面预览)

## 主要功能

1. 支持自定义书源，可自行设置规则抓取网页数据；软件内提供规则说明。
2. 支持列表书架和网格书架自由切换。
3. 书源规则支持搜索与发现，找书、看书功能均可自定义。
4. 支持订阅自定义内容。
5. 支持替换净化，便于去除广告或替换内容。
6. 支持本地 TXT、EPUB 阅读，可手动浏览或智能扫描。
7. 阅读界面可高度自定义，包括字体、颜色、背景、行距、段距、加粗和简繁转换等。
8. 支持覆盖、仿真、滑动、滚动等多种翻页模式。
9. 软件开源、持续优化且无广告。

## 交流社区

### Telegram

[![Telegram-group](https://img.shields.io/badge/Telegram-%E7%BE%A4%E7%BB%84-blue)](https://t.me/yueduguanfang) [![Telegram-channel](https://img.shields.io/badge/Telegram-%E9%A2%91%E9%81%93-blue)](https://t.me/legado_channels)

### Discord

[![Discord](https://img.shields.io/discord/560731361414086666?color=%235865f2&label=Discord)](https://discord.gg/VtUfRyzRXn)

更多联系方式见[社区列表](https://www.yuque.com/legado/wiki/community)。

## API

- 阅读 3.0 提供网页接口（`Web`）和内容提供器（`Content Provider`）两种 API 调用方式，详见
  [API 文档](api.md)。
- 可通过 URL 唤起阅读并一键导入，格式为
  `legado://import/{path}?src={url}`。
- `path` 支持以下类型：

| `path` | 用途 |
| --- | --- |
| `bookSource` | 书源 |
| `rssSource` | 订阅源 |
| `replaceRule` | 替换规则 |
| `textTocRule` | 本地 TXT 小说目录规则 |
| `httpTTS` | 在线朗读引擎 |
| `theme` | 主题 |
| `readConfig` | 阅读排版 |
| `dictRule` | 字典规则 |
| [`addToBookshelf`](app/src/main/java/io/legado/app/ui/association/AddToBookshelfDialog.kt) | 添加到书架 |

## 其他资源

- [免责声明](https://gedoor.github.io/Disclaimer)
- [书源规则](https://mgz0227.github.io/The-tutorial-of-Legado/)
- [更新日志](app/src/main/assets/updateLog.md)
- [帮助文档](app/src/main/assets/web/help/md/appHelp.md)
- [网页端书架](https://github.com/gedoor/legado_web_bookshelf)
- [网页端源编辑](https://github.com/gedoor/legado_web_source_editor)

## 致谢

- `org.jsoup:jsoup`
- `cn.wanghaomiao:JsoupXpath`
- `com.jayway.jsonpath:json-path`
- `com.github.gedoor:rhino-android`
- `com.squareup.okhttp3:okhttp`
- `com.github.bumptech.glide:glide`
- `org.nanohttpd:nanohttpd`
- `org.nanohttpd:nanohttpd-websocket`
- `cn.bingoogolapple:bga-qrcode-zxing`
- `com.jaredrummler:colorpicker`
- `org.apache.commons:commons-text`
- `io.noties.markwon:core`
- `io.noties.markwon:image-glide`
- `com.hankcs:hanlp`
- `com.positiondev.epublib:epublib-core`

## 界面预览

<p align="center">
  <img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B1.jpg" alt="Legado 界面预览 1" width="270" />
  <img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B2.jpg" alt="Legado 界面预览 2" width="270" />
  <img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B3.jpg" alt="Legado 界面预览 3" width="270" />
</p>

<p align="center">
  <img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B4.jpg" alt="Legado 界面预览 4" width="270" />
  <img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B5.jpg" alt="Legado 界面预览 5" width="270" />
  <img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B6.jpg" alt="Legado 界面预览 6" width="270" />
</p>
