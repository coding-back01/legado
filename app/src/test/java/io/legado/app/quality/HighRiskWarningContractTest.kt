package io.legado.app.quality

import me.ag2s.epublib.domain.GuideReference
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.util.StringUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class HighRiskWarningContractTest {

    @Test
    fun `内部标识的大小写转换不受土耳其语环境影响`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertTrue(StringUtil.startsWithIgnoreCase("TITLE-PAGE", "title"))
            assertTrue(StringUtil.endsWithIgnoreCase("BOOK-TITLE", "title"))
            assertEquals(
                "title-page",
                GuideReference(Resource("chapter.xhtml"), "TITLE-PAGE", "title").type
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `八处内部大小写转换显式使用 Locale ROOT`() {
        val sourcePaths = listOf(
            "modules/book/src/main/java/me/ag2s/epublib/domain/GuideReference.java",
            "modules/book/src/main/java/me/ag2s/epublib/epub/PackageDocumentReader.java",
            "modules/book/src/main/java/me/ag2s/epublib/util/StringUtil.java",
            "modules/book/src/main/java/me/ag2s/umdlib/tool/UmdUtils.java"
        )
        val rootConversions = sourcePaths.sumOf { path ->
            "Locale.ROOT".toRegex().findAll(repoFile(path).readText()).count()
        }

        assertEquals(8, rootConversions)
    }

    @Test
    fun `QQ群跳转使用显式 ACTION VIEW`() {
        val aboutFragment = repoFile(
            "app/src/main/java/io/legado/app/ui/about/AboutFragment.kt"
        ).readText()
        val joinGroup = aboutFragment.substring(
            aboutFragment.indexOf("private fun joinQQGroup"),
            aboutFragment.indexOf("private fun saveLog")
        )

        assertTrue(
            Regex("""Intent\(\s*Intent\.ACTION_VIEW,\s*"mqqopensdkapi:[^"]*"\.toUri\(\)""")
                .containsMatchIn(joinGroup)
        )
    }

    @Test
    fun `App Bundle 不拆分运行时可选语言资源`() {
        val appGradle = repoFile("app/build.gradle").readText()
        val bundleContract = Regex(
            "bundle\\s*\\{[\\s\\S]*?language\\s*\\{[\\s\\S]*?enableSplit\\s*=\\s*false",
            RegexOption.MULTILINE
        )

        assertTrue(bundleContract.containsMatchIn(appGradle))
    }

    @Test
    fun `已修复的高风险 lint ID 提升为 fatal`() {
        val appGradle = repoFile("app/build.gradle").readText()

        listOf(
            "IntentWithNullActionLaunch",
            "DefaultLocale",
            "AppBundleLocaleChanges"
        ).forEach { lintId ->
            assertTrue(appGradle.contains("fatal '$lintId'"))
        }
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
