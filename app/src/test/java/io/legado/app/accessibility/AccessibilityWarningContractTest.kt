package io.legado.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccessibilityWarningContractTest {

    @Test
    fun `日期选择字段退出自动填充且保留精确输入类型理由`() {
        val layout = repoFile(
            "app/src/main/res/layout/dialog_simulated_reading.xml"
        ).readText()
        val dateField = xmlElement(layout, "start_date")

        assertTrue(dateField.contains("android:importantForAutofill=\"no\""))
        assertTrue(dateField.contains("android:inputType=\"none\""))
        assertTrue(dateField.contains("tools:ignore=\"TextFields\""))
        assertTrue(layout.contains("日期只由 DatePickerDialog 选择"))
    }

    @Test
    fun `日期选择字段可由键盘聚焦且不会弹出软键盘`() {
        val layout = repoFile(
            "app/src/main/res/layout/dialog_simulated_reading.xml"
        ).readText()
        val dateField = xmlElement(layout, "start_date")
        val activity = repoFile(
            "app/src/main/java/io/legado/app/ui/book/read/BaseReadBookActivity.kt"
        ).readText()

        assertTrue(dateField.contains("android:focusable=\"true\""))
        assertTrue(dateField.contains("android:focusableInTouchMode=\"false\""))
        assertFalse(activity.contains("startDate.isFocusable = false"))
        assertTrue(activity.contains("startDate.showSoftInputOnFocus = false"))
        assertTrue(activity.contains("startDate.setOnClickListener"))
    }

    @Test
    fun `锁定章节图标具有所有现有语言的说明`() {
        val layout = repoFile(
            "app/src/main/res/layout/item_chapter_list.xml"
        ).readText()
        val lockIcon = xmlElement(layout, "iv_locked")
        assertTrue(
            lockIcon.contains(
                "android:contentDescription=\"@string/chapter_locked\""
            )
        )

        resourceDirectories.forEach { directory ->
            val strings = repoFile(
                "app/src/main/res/$directory/strings.xml"
            ).readText()
            assertTrue(
                "$directory 缺少 chapter_locked",
                strings.contains("name=\"chapter_locked\"")
            )
        }
    }

    @Test
    fun `装饰性主题图标预览不进入无障碍树`() {
        val layout = repoFile(
            "app/src/main/res/layout/view_icon.xml"
        ).readText()
        assertTrue(
            layout.contains("android:importantForAccessibility=\"no\"")
        )
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

    private companion object {
        val resourceDirectories = listOf(
            "values",
            "values-es-rES",
            "values-ja-rJP",
            "values-pt-rBR",
            "values-vi",
            "values-zh",
            "values-zh-rHK",
            "values-zh-rTW"
        )
    }
}
