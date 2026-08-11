package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ReadTipConfigResourceTest {

    @Test
    fun `tip ids are unique and every read tip array has the same size`() {
        val expectedSize = ReadTipConfig.tipValues.size
        assertEquals(expectedSize, ReadTipConfig.tipValues.toSet().size)

        val resourceRoot = requireNotNull(
            sequenceOf(File("src/main/res"), File("app/src/main/res"))
                .firstOrNull(File::isDirectory)
        )
        val overriddenArrays = resourceRoot.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { File(it, "arrays.xml") }
            .filter(File::isFile)
            .mapNotNull { file ->
                val document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(file)
                val arrays = document.getElementsByTagName("string-array")
                (0 until arrays.length)
                    .map { arrays.item(it) as Element }
                    .firstOrNull { it.getAttribute("name") == "read_tip" }
                    ?.let { array ->
                        (file.parentFile?.name ?: "values") to
                                array.getElementsByTagName("item").length
                    }
            }

        assertFalse(overriddenArrays.isEmpty())
        overriddenArrays.forEach { (directory, actualSize) ->
            assertEquals("$directory/read_tip", expectedSize, actualSize)
        }
        assertTrue(overriddenArrays.any { it.first == "values" })
    }
}
