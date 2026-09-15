package io.legado.app.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FilePathBindingContractTest {

    private val repositoryRoot = requireNotNull(
        File(requireNotNull(System.getProperty("user.dir"))).parentFile
    )

    @Test
    fun bothFileBrowsersKeepWholeRowBindingContract() {
        val layout = repositoryRoot.resolve("app/src/main/res/layout/item_path_picker.xml").readText()
        assertTrue(layout.contains("android:id=\"@+id/text_view\""))
        assertTrue(layout.contains("android:id=\"@+id/image_view\""))
        assertTrue(layout.contains("android:layout_width=\"20dp\""))
        assertTrue(layout.contains("android:clickable=\"true\""))
        assertTrue(layout.contains("android:focusable=\"true\""))

        listOf("FileManageActivity.kt", "FilePickerDialog.kt").forEach { name ->
            val source = repositoryRoot.resolve(
                "app/src/main/java/io/legado/app/ui/file/$name"
            ).readText()
            assertTrue("$name 未使用路径 View Binding", source.contains("ItemPathPickerBinding"))
            assertTrue("$name 未设置路径文字", source.contains("binding.textView.text = item.name"))
            assertTrue("$name 未安装动态箭头", source.contains("imageView.setImageDrawable(arrowIcon)"))
            assertTrue("$name 未保持整行点击", source.contains("binding.root.setOnClickListener"))
        }
    }
}
