package io.legado.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BlockingTranslationResourceTest {

    private val requiredKeys = setOf(
        "custom_export_section",
        "del_all",
        "system_media_control_compatibility_change",
        "system_media_control_compatibility_change_summary",
        "read_aloud_pause_resume",
        "play_mode"
    )

    private val localeDirectories = listOf(
        "values",
        "values-es-rES",
        "values-ja-rJP",
        "values-pt-rBR",
        "values-vi",
        "values-zh",
        "values-zh-rHK",
        "values-zh-rTW"
    )

    @Test
    fun `六个阻断键在每个支持的 locale 都有非空资源`() {
        localeDirectories.forEach { directory ->
            val strings = readStrings(directory)
            requiredKeys.forEach { key ->
                assertTrue("$directory 缺少 $key", strings[key].orEmpty().isNotBlank())
            }
        }
    }

    @Test
    fun `默认资源使用英文且中文 locale 保留播放模式`() {
        assertEquals("Play mode", readStrings("values").getValue("play_mode"))
        listOf("values-zh", "values-zh-rHK", "values-zh-rTW").forEach { directory ->
            assertEquals("播放模式", readStrings(directory).getValue("play_mode"))
        }
    }

    private fun readStrings(directory: String): Map<String, String> {
        val file = repoFile("app/src/main/res/$directory/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return document.getElementsByTagName("string").let { nodes ->
            buildMap {
                repeat(nodes.length) { index ->
                    val element = nodes.item(index) as Element
                    put(element.getAttribute("name"), element.textContent)
                }
            }
        }
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
