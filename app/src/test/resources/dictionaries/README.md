# 本地词典测试夹具

本目录用于测试，不属于应用内置词库。`generate_fixtures.py` 通过固定输入生成 MDict、StarDict、DSL、DICTD、XDXF 和 BGL 的最小文件；不需要在 Android 应用中运行 Python。自建内容沿用仓库 GPL-3.0 许可证。

`mobi-*` 的原始文件及其派生文件来自 libmobi `v0.12`，固定提交 `85dcfe803fc2a21020ddcf15c3eb66b93d388add`。上游项目为 LGPL-3.0 或后续版本，来源为 `tests/samples/sample-dict-infl2.mobi`、`sample-unicode-huffdic.mobi` 和 `sample-drm-v1.mobi`；源代码及许可证保留于 `app/src/main/cpp/libmobi/`。无压缩和 PalmDOC 派生文件保留同一词形测试索引；HUFF/CDIC 派生文件将小索引接入上游压缩文本，验证解压、混合文件部分选择及索引定位，不作为真实词义词库。

文件大小、摘要、来源和许可记录在 `manifest.json`。主要预期词头为 `go`、`学习`，别名为 `went`；MOBI 词形样本以 `jest` 查询 `być`，以 `mam` 查询 `mieć`。具体正文、资源和错误条件由测试断言固定。

Android 测试使用 `dictionary-fixtures.zip` 保留原始字节，避免 AAPT 特殊处理 `.gz` 文件。该压缩包属于测试 APK，应用 APK 不包含这些词库文件。生成压缩包前应更新并核对摘要。

社区牛津、朗文、汉典等资料用于确定兼容性场景，不把商业词库本体作为夹具提交。

`generate_mobi_compatibility_fixtures.py` 基于上述 libmobi 测试容器，自建中文词条、标签 22 别名及附加检索面板；`mobi-orth-alias`、`mobi-extra-flow` 验证规范词头和正文边界，`mobi-orth-cycle`、`mobi-orth-outside` 验证循环及越界被拒绝。`MobiCompatibilityIntegrationTest` 执行生产导入和查询链；`app/src/test/cpp/mobi_compatibility_test.c` 用小元数据验证 512 MiB 正文上限，不包含用户词库。

主机回归命令为 `python3 app/src/test/resources/dictionaries/verify_mobi_native.py`，需要 C 编译器和 zlib。它编译生产桥接层，检查正文上限、别名目标及输出顺序、附加面板隔离和损坏索引拒绝；构建产物写入临时目录并自动清理。
