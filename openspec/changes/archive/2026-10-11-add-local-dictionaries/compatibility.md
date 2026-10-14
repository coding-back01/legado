# 词典兼容性与解析方案记录

## 解析方案

MOBI/PRC 使用固定版本 libmobi 0.12，提交为 `85dcfe803fc2a21020ddcf15c3eb66b93d388add`。现有 Kotlin 解析器保留普通图书职责；没有完整的词形、旧词典索引和词条边界接口，不直接初始化普通图书对象查词。原生接口只在导入时提取词头、词形及正文，阅读时查询应用独立索引。读取混合文件前固定选择 MOBI 部分，避免先读取 KF8 再切换而误用索引。

其余六类使用应用内 Kotlin 适配器。MDict 结构参考 mdict4j，BGL 结构参考 PyGlossary 的公开格式说明，没有引入桌面框架或 Python 运行时。MDict 的 LZO 块使用 `org.anarres.lzo:lzo-core:1.0.6`，排除未使用的日志与注解依赖。索引混淆采用独立 RIPEMD-128 摘要，七个公开测试向量通过，不注册通用加密提供者，保留既有 Hutool 的提供者选择环境。

## 兼容性矩阵

以下范围有对应自动化断言。自建内容采用 GPL-3.0；MOBI 上游样本及派生文件采用 LGPL-3.0 或后续版本。每个文件的来源、字节数、SHA-256 和预期断言位置记录在 `app/src/test/resources/dictionaries/manifest.json`；生成脚本与来源说明在同目录。社区牛津、朗文和汉典资料仅用于确定测试方向，没有提交商业词库或将同名词典全部版本宣称为兼容。

| 格式 | 版本、编码、压缩 | 词条、资源和代表夹具 | 正反验证 |
| --- | --- | --- | --- |
| MDict | 1.x/2.x；UTF-8、UTF-16、GBK；无压缩、zlib、LZO；索引混淆 | `sample`、`legacy`、`utf16`、`gbk`、`lzo`、`obfuscated`、`styled`、`cycle`、`media`；原词 `go`、`学习`，别名 `went`；跨正文块、数字样式、MDD 与 `.1.mdd` 分卷、外置 CSS、图片及 WAV | JVM 解析与摘要测试；API 21/36 导入查询；循环别名、缺资源、分配长度超限 |
| StarDict | 2.4.2/3.0.0 文件集；UTF-8；IDX/IDX.GZ、DICT/DICT.DZ、SYN；32/64 位偏移 | `sample`、`wide`、`text`、`xmlbody`、`binary`；`go`、`学习`、`went`；文本、HTML、XDXF、内嵌声音及资源目录/RES.ZIP | JVM 正文与偏移测试；API 21/36 导入查询；缺文件、无效偏移；独立文件读取验证超过 4 GiB 的偏移 |
| MOBI/PRC/兼容 AZW | MOBI 词典索引、UTF-8/Windows-1252；无压缩、PalmDOC、HUFF/CDIC；混合文件的 MOBI 部分 | `mobi-inflections`、`mobi-dictionary-huff`、`mobi-resource`、`mobi-cross-record`、`mobi-no-length`、`mobi-languages`、`mobi-latin`；`jest`→`być`、`mam`→`mieć`；词形、旧词典索引、图片、跨记录及缺长度边界 | 主机原生接口及 API 21/36；普通无词典索引电子书、受保护文件、损坏索引被拒绝；缺长度词条不串入下一条 |
| DSL | UTF-16（含字节序标记）；DSL/DSL.DZ；可选词头和注释 | `sample.dsl`、`optional.dsl`、`.files.zip`；`go`、`went`、`word/words`、`学习`；排版、内部链接、图片、声音 | JVM 标记和词头测试；API 21/36 资源包导入查询；括号异常和缺资源 |
| DICTD | UTF-8；INDEX 中 Base64 偏移与长度；DICT/DICT.DZ | `dictd.index`、`dictd.dict.dz`；`学习`→获得知识 | JVM/API 21/36；非法数字及偏移超出正文被拒绝 |
| XDXF | UTF-8 XML；原始或 gzip/dictzip | `sample.xdxf`、`sample.xdxf.dz`；`go`、`went`、`学习`；正文标记、音标、资源引用及词条跳转 | JVM/API 21/36；畸形 XML、外部实体被拒绝；复用 StarDict 的 XDXF 转换 |
| BGL | 已知 BGL 头与块结构；UTF-8、Windows-1252；gzip 及零 CRC 变体 | `sample.bgl`、`latin.bgl`；`go`、`went`、`学习`、`café`；别名和内嵌图片 | JVM/API 21/36；错误长度、未知头被拒绝，编码保留重音字符 |

解析、展示、摘要与扩展合计 29 项 JVM 测试通过；API 21/36 的基础导入、恢复、迁移各 6 项，扩展格式、归档和查询各 15 项通过。Android 资源夹具由固定 ZIP 保留原始压缩字节，避免 AAPT 自动处理 `.gz`；这些词库只进入测试 APK。

## 归档与语言查询

ZIP、普通单卷 RAR4、7z、tar.gz 自建归档在 API 21/36 上解包并成功查询。RAR 夹具采用标准未压缩块；不据此宣称所有 RAR 变体通过。分卷、密码、越界路径、符号链接和声明超限的归档均有拒绝断言。新词典导入器使用原生文件描述符读取归档，以避开 API 21 的 Java ByteBuffer 回调差异；既有通用解压默认行为保持。

精确词头优先于别名，保留同词多个义项；离线英文候选覆盖不规则词、短语、大小写与 `news/counter` 等负例。繁简索引保留 `乾/幹` 两个词头及正文，精确 `学习/學習` 不相互覆盖，不推断未声明的 `说/悦` 通假关系。日韩原词及词库映射有效，不应用英文后缀规则。候选数量、别名深度和取消均有设备断言。

## 构建、许可与维护

GitHub 仓库元数据于 2026-10-08 核对：libmobi 未归档，最近推送为 2024-10-29；lzo-java 未归档，最近推送为 2020-01-05。两者不是持续快速迭代的依赖，因此固定版本并保留格式回归样本，升级前重新核验。

libmobi 为 LGPL-3.0 或后续版本，LZO 为 GPL-3.0，与应用现有 GPL-3.0 许可相容。完整原生源、版权及许可证保留在 `app/src/main/cpp/libmobi/`，应用内资产保留依赖说明和 libmobi 许可证。共享库没有内置解密入口，不注册或更改书源加密提供者。

NDK 固定为 27.2.12479018，CMake 固定为 3.22.1，最低 API 21，链接系统 zlib；四种架构 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 均构建成功，LOAD 段对齐为 `0x4000`。API 21/36 已执行原生词典及 LZO 查询，调试 APK 包含四种共享库。已有固定依赖、签名和 CI 配置未升级或改写；未执行签名发布构建，不将调试打包视为发布签名验证。

## 明确边界

不支持 MDict 3.x、纯 KF8/KFX、DRM 及账号绑定加密词库、分卷或带密码的外层归档；未知版本、编码和必要正文类型给出错误。BGL 与文本 GLS 分开处理。词库脚本不执行，互动排版以可读静态内容降级，远程资源不自动加载。

MOBI 的 UTF-16/ORDT 索引由 libmobi 转换为 UTF-8 标签，正文保留其 UTF-8 或 Windows-1252 编码单独读取；语言元数据保留输入与输出字段。追加样本验证 `en/zh` 元数据、`byé` 词头与 `café` 正文。

2026-10-11 用户指定词库补测：支持标签 22 的词头别名及最多 16 层的目标解析，先写规范词条再写别名，保留规范词头；循环与越界目标拒绝导入。附加检索面板按独立本地资源保存，不计入主词条正文或执行其中脚本。原文件上限为 256 MiB，正文上限为 512 MiB，编译配置与 Kotlin 接口使用一致限额。四个自建 `mobi-orth-*`、`mobi-extra-flow` 夹具在主机及 API 30 真机通过，正文上限由小元数据回归检查；用户《现代汉语词典》《汉语大词典》《汉典》及牛津第七版修复词典的实际结果见验证记录，不提交用户词库。

当前矩阵是结构和代表变体的证据，不是所有社区词典文件的兼容保证。新文件出现问题时应在获得可分发样本或最小复现后补充断言。
