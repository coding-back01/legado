package io.legado.app.performance

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LayoutPerformanceContractTest {

    @Test
    fun `模拟阅读开关直接使用剩余宽度而不重复测量`() {
        val layout = repoFile(
            "app/src/main/res/layout/dialog_simulated_reading.xml"
        ).readText()
        val switch = xmlElement(layout, "sr_enabled")

        assertTrue(switch.contains("android:layout_width=\"0dp\""))
        assertTrue(switch.contains("android:layout_weight=\"1\""))
        assertTrue(switch.contains("android:width=\"20dp\""))
    }

    private fun xmlElement(xml: String, id: String): String {
        val idIndex = xml.indexOf("android:id=\"@+id/$id\"")
        require(idIndex >= 0) { "找不到视图 $id" }
        val startIndex = xml.lastIndexOf('<', idIndex)
        val endIndex = xml.indexOf("/>", idIndex)
        require(startIndex >= 0 && endIndex >= 0) { "无法解析视图 $id" }
        return xml.substring(startIndex, endIndex + 2)
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
