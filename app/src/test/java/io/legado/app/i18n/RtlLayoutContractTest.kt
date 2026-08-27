package io.legado.app.i18n

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RtlLayoutContractTest {

    @Test
    fun `书源校验标题保留 12dp 起始内边距`() {
        val layout = repoFile(
            "app/src/main/res/layout/dialog_check_source_config.xml"
        ).readText()
        val title = layout.substring(
            layout.indexOf("<io.legado.app.ui.widget.text.AccentTextView"),
            layout.indexOf("<com.google.android.flexbox.FlexboxLayout")
        )

        assertTrue(title.contains("android:paddingStart=\"12dp\""))
        assertTrue(title.contains("android:paddingEnd=\"0dp\""))
        assertFalse(title.contains("android:paddingLeft"))
    }

    @Test
    fun `进度视图的 60dp 和 20dp 间距使用逻辑末端`() {
        val layout = repoFile(
            "app/src/main/res/layout/dialog_progressbar_view.xml"
        ).readText()

        assertTrue(layout.contains("android:layout_marginEnd=\"60dp\""))
        assertTrue(layout.contains("android:layout_marginEnd=\"20dp\""))
        assertFalse(layout.contains("android:layout_marginRight"))
    }

    @Test
    fun `模拟阅读布局保留原数值并使用逻辑方向`() {
        val layout = repoFile(
            "app/src/main/res/layout/dialog_simulated_reading.xml"
        ).readText()

        listOf(
            "android:layout_marginEnd=\"68dp\"",
            "android:gravity=\"center_horizontal|start\"",
            "android:layout_marginStart=\"25dp\"",
            "android:layout_marginEnd=\"5dp\""
        ).forEach { expected -> assertTrue(layout.contains(expected)) }
        listOf(
            "android:layout_marginRight=\"68dp\"",
            "android:gravity=\"center_horizontal|left\"",
            "android:layout_marginLeft=\"25dp\"",
            "android:layout_marginRight=\"5dp\""
        ).forEach { legacy -> assertFalse(layout.contains(legacy)) }
    }

    @Test
    fun `章节锁图标显式保留零起始和 8dp 末端内边距`() {
        val layout = repoFile(
            "app/src/main/res/layout/item_chapter_list.xml"
        ).readText()
        val lockIcon = layout.substring(
            layout.indexOf("android:id=\"@+id/iv_locked\""),
            layout.indexOf("<TextView")
        )

        assertTrue(lockIcon.contains("android:paddingStart=\"0dp\""))
        assertTrue(lockIcon.contains("android:paddingEnd=\"8dp\""))
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
