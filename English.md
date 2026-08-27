# Legado (personal stable fork)

<!-- markdownlint-disable MD013 MD033 -->

<div align="center">

<img
  src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
  alt="Legado icon"
  width="125"
  height="125"
/>

<p>A free and open-source Android reader with no bundled content</p>

<p>
  <a href="README.md">中文</a>
  ·
  <strong>English</strong>
</p>

</div>

> [!IMPORTANT]
> This repository is a personally maintained derivative at `coding-back01/legado`, with its own signing identity. It is not an official successor to the original project and is not endorsed by the original project. The app provides no books, sources, or other content; users import and take responsibility for third-party content themselves.

## Maintenance status

- Current source: [coding-back01/legado](https://github.com/coding-back01/legado)
- Releases: [current fork releases](https://github.com/coding-back01/legado/releases)
- Release-build bugs: [current fork issues](https://github.com/coding-back01/legado/issues)
- Private security reports: [private vulnerability reporting](https://github.com/coding-back01/legado/security/advisories/new)
- Contributors: [contributors in the current Git history](https://github.com/coding-back01/legado/graphs/contributors)

New public releases are temporarily frozen while the fork is stabilized. When releases resume, only the ordinary `io.legado.app.release` package will be produced. Historical coexisting-package assets and existing device data remain available, but no new coexisting build will be generated. This repository uses an independent signing key, so an APK cannot update a same-package build signed by somebody else. Back up data before installation or migration.

This is a low-capacity personal stable fork. It accepts release-build bugs and private security reports, makes no response-time commitment, and does not normally accept feature requests. Compatibility claims cover only devices and Android versions explicitly recorded as automatically verified in a Release; they are not generalized to other devices.

## Main features

1. User-defined book-source rules and in-app rule documentation.
2. List and grid bookshelves, plus configurable subscription sources.
3. Content replacement, local TXT reading, and EPUB reading.
4. Configurable fonts, colors, backgrounds, spacing, weight, and script conversion.
5. Cover, simulation, slide, and scroll page transitions.
6. Local and WebDAV backups, a Web bookshelf, and source editing.

## API

- The app exposes a Web API and a Content Provider API; see [the API documentation](api.md).
- `legado://import/{path}?src={url}` opens an import preview.
- Supported paths include `bookSource`, `rssSource`, `replaceRule`, `textTocRule`, `httpTTS`, `theme`, `readConfig`, `dictRule`, and [`addToBookshelf`](app/src/main/java/io/legado/app/ui/association/AddToBookshelfDialog.kt).
- Source rules, subscription rules, backups, and import URIs are compatibility-sensitive interfaces and are not changed casually in this fork.

## Files maintained in this repository

- [Disclaimer](app/src/main/assets/disclaimer.md)
- [Update log](app/src/main/assets/updateLog.md)
- [In-app help](app/src/main/assets/web/help/md/appHelp.md)
- [GPL-3.0 license](LICENSE)

## Upstream projects and community resources

The following links belong to the original project or other upstream communities. They are not maintenance, download, or support channels for this fork:

- Original authorship and contributors remain attributed through the license and Git history.
- Upstream sites: [gedoor.github.io](https://gedoor.github.io), [legado.top](https://www.legado.top/)
- Upstream help: [Yuque help](https://www.yuque.com/legado/wiki), [book-source rule tutorial](https://mgz0227.github.io/The-tutorial-of-Legado/)
- Upstream distribution: [Google Play](https://play.google.com/store/apps/details?id=io.legado.play.release), [Coolapk](https://www.coolapk.com/apk/io.legado.app.release), [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/io.legado.app.release)
- Upstream community: [Telegram group](https://t.me/yueduguanfang), [Telegram channel](https://t.me/legado_channels), [Discord](https://discord.gg/VtUfRyzRXn)
- Upstream Web projects: [Web bookshelf](https://github.com/gedoor/legado_web_bookshelf), [Web source editor](https://github.com/gedoor/legado_web_source_editor)

## Copyright and acknowledgements

This fork preserves the copyright and commit attribution of the original author `gedoor`, the original contributors, and contributors to this branch. It is distributed under GNU GPL v3. Dependencies retain their respective copyright and licenses; major dependencies include jsoup, JsoupXpath, JsonPath, Rhino, OkHttp, Glide, NanoHTTPD, Markwon, HanLP, and epublib.

## Historical upstream interface preview

These images show the historical upstream interface. They are pinned to upstream resource commit `3cdf95ece45c85eac9cb7289e3339661373bc4ea` and do not imply support by this fork for upstream sites.

<p align="center">
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B1.jpg" alt="Historical upstream interface 1" width="270" />
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B2.jpg" alt="Historical upstream interface 2" width="270" />
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B3.jpg" alt="Historical upstream interface 3" width="270" />
</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B4.jpg" alt="Historical upstream interface 4" width="270" />
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B5.jpg" alt="Historical upstream interface 5" width="270" />
  <img src="https://raw.githubusercontent.com/gedoor/gedoor.github.io/3cdf95ece45c85eac9cb7289e3339661373bc4ea/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B6.jpg" alt="Historical upstream interface 6" width="270" />
</p>
