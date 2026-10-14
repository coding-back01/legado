# 本地词典依赖说明

本地词典功能不提供词库内容。新增依赖及归属如下：

- libmobi 0.12：作者 Bartek Fabiszewski，采用 LGPL-3.0 或后续版本；源代码固定为 `85dcfe803fc2a21020ddcf15c3eb66b93d388add`。本应用只编译容器、解压、元数据、索引和资源解析，采用可替换的共享库；完整许可证见同目录 `libmobi-LGPL-3.0.txt`，对应源文件及构建方式保留在项目 `app/src/main/cpp/`。
- lzo-core 1.0.6：作者 Shevek，采用 GPL-3.0，用于 MDict 的 LZO 数据块。原仓库为 `https://github.com/shevek/lzo-java`，固定依赖坐标为 `org.anarres.lzo:lzo-core:1.0.6`。本应用使用原始解压器，不依赖其可选日志封装。
- MDict 文件结构参考 mdict4j 的公开实现与格式说明，BGL 文件结构参考 PyGlossary 的公开格式读取说明。应用内适配器采用 Kotlin 实现，不包含桌面程序或 Python 运行时。
- MDict 索引使用独立 RIPEMD-128 摘要，并通过公开标准向量校验；不会注册或替换现有书源加解密提供者。

应用整体采用 GPL-3.0，完整文本保留于应用原有 `LICENSE.md`。依赖升级必须复测相应格式、最低 Android 版本和原生打包，不能只替换二进制文件而丢失来源与归属说明。
