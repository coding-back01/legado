package io.legado.app.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BehindOrientationContractTest {

    @Test
    fun `九个页面精确保留 behind 并记录局部抑制`() {
        val root = requireNotNull(
            sequenceOf(File(".."), File(".")).firstOrNull {
                it.resolve("app/src/main/AndroidManifest.xml").isFile
            }
        ) { "找不到仓库根目录" }
        val manifest = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(root.resolve("app/src/main/AndroidManifest.xml"))
        val activities = manifest.getElementsByTagName("activity")
        val actual = (0 until activities.length)
            .map { activities.item(it) as Element }
            .filter { it.getAttribute("android:screenOrientation") == "behind" }

        assertEquals(EXPECTED_ACTIVITIES, actual.map { it.getAttribute("android:name") })
        actual.forEach { activity ->
            assertEquals("DiscouragedApi", activity.getAttribute("tools:ignore"))
        }

        val source = root.resolve("app/src/main/AndroidManifest.xml").readText()
        assertEquals(9, "九页完成自适应布局后重新评估".toRegex().findAll(source).count())
        assertTrue(source.contains("Android 16 以下的前页方向继承"))
    }

    private companion object {
        val EXPECTED_ACTIVITIES = listOf(
            ".ui.about.AboutActivity",
            ".ui.book.source.manage.BookSourceActivity",
            ".ui.rss.source.manage.RssSourceActivity",
            ".ui.book.toc.rule.TxtTocRuleActivity",
            ".ui.replace.ReplaceRuleActivity",
            ".ui.book.manage.BookshelfManageActivity",
            ".ui.book.source.debug.BookSourceDebugActivity",
            ".ui.book.toc.TocActivity",
            ".ui.book.searchContent.SearchContentActivity",
        )
    }
}
