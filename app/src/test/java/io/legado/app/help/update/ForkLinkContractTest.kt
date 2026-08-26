package io.legado.app.help.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ForkLinkContractTest {

    @Test
    fun `中英文首页区分当前 fork 与上游资源`() {
        val readme = repoFile("README.md").readText()
        val english = repoFile("English.md").readText()

        listOf(readme, english).forEach { home ->
            assertTrue(home.contains("https://github.com/coding-back01/legado/releases"))
            assertTrue(home.contains("https://github.com/coding-back01/legado/issues"))
            assertTrue(home.contains("https://github.com/coding-back01/legado/graphs/contributors"))
            assertTrue(home.contains("app/src/main/assets/disclaimer.md"))
            assertTrue(home.contains("gedoor/legado_web_bookshelf"))
            assertTrue(home.contains("3cdf95ece45c85eac9cb7289e3339661373bc4ea"))
        }
        assertTrue(readme.contains("独立签名"))
        assertTrue(readme.contains("不提供书籍、书源或其他内容"))
        assertTrue(english.contains("independent signing key"))
        assertTrue(english.contains("provides no books, sources, or other content"))
    }

    @Test
    fun `分享贡献者和仓库元数据全部指向当前 fork`() {
        val resourceRoot = repoFile("app/src/main/res")
        val localizedStrings = resourceRoot.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { File(it, "strings.xml") }
            .filter(File::isFile)
            .map(File::readText)
            .filter { it.contains("name=\"app_share_description\"") }

        assertEquals(8, localizedStrings.size)
        localizedStrings.forEach { strings ->
            assertTrue(strings.contains("https://github.com/coding-back01/legado/releases"))
            assertTrue(strings.contains("name=\"contributors_summary\""))
            assertFalse(strings.contains("https://github.com/gedoor/legado/releases"))
        }

        val nonTranslatable = repoFile(
            "app/src/main/res/values/non_translat.xml"
        ).readText()
        assertTrue(
            nonTranslatable.contains(
                "https://github.com/coding-back01/legado/graphs/contributors"
            )
        )

        listOf("package.json", "modules/web/package.json").forEach { path ->
            val metadata = repoFile(path).readText()
            assertTrue(metadata.contains("git+https://github.com/coding-back01/legado.git"))
            assertTrue(metadata.contains("https://github.com/coding-back01/legado/issues"))
            assertTrue(metadata.contains("\"license\": \"GPL-3.0-only\""))
            assertFalse(metadata.contains("github.com/gedoor/legado"))
        }
    }

    @Test
    fun `失效运行时和历史资料使用内置降级或不可变引用`() {
        val rssSources = repoFile(
            "app/src/main/assets/defaultData/rssSources.json"
        ).readText()
        val appHelp = repoFile(
            "app/src/main/assets/web/help/md/appHelp.md"
        ).readText()
        val updateLog = repoFile("app/src/main/assets/updateLog.md").readText()

        assertFalse(rssSources.contains("cdn.jsdelivr.net/gh/gedoor/legado"))
        assertTrue(appHelp.contains("3cdf95ece45c85eac9cb7289e3339661373bc4ea"))
        assertFalse(appHelp.contains("@master/images/importSource.jpg"))
        assertFalse(updateLog.contains("record2023"))
        assertFalse(updateLog.contains("record2022"))
        assertFalse(updateLog.contains("record2021"))
        listOf(
            "6697190e182252dcdbd91d73cdcb7a810f9ff58d",
            "1508f69830f854804b2fb2c692eabd2d7239b03b",
            "51c16a1efcb036cc18e9cdb6d592e691b3da5816"
        ).forEach { sha -> assertTrue(updateLog.contains(sha)) }
    }

    @Test
    fun `当前源码链接已迁移且历史来源仍保留`() {
        val jsHelp = repoFile("app/src/main/assets/web/help/md/jsHelp.md").readText()
        val ruleHelp = repoFile("app/src/main/assets/web/help/md/ruleHelp.md").readText()
        val webHome = repoFile("app/src/main/assets/web/index.html").readText()

        assertTrue(jsHelp.contains("coding-back01/legado/blob/master"))
        assertTrue(ruleHelp.contains("coding-back01/legado/blob/master"))
        assertTrue(webHome.contains("https://github.com/coding-back01/legado"))
        assertFalse(webHome.contains("https://github.com/gedoor/legado\""))

        assertTrue(jsHelp.contains("gedoor/legado/discussions/3259"))
        assertTrue(
            repoFile("app/src/main/java/io/legado/app/help/crypto/README.md")
                .readText().contains("gedoor/legado/pull/2880")
        )
        assertTrue(
            repoFile("app/src/main/java/io/legado/app/lib/cronet/CronetInterceptor.kt")
                .readText().contains("gedoor/legado/issues/5025")
        )
        assertTrue(
            repoFile("app/src/main/java/io/legado/app/model/localBook/EpubFile.kt")
                .readText().contains("gedoor/legado/issues/1932")
        )
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::exists)
    ) { "找不到 $path" }
}
