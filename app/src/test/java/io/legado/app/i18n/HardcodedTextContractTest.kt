package io.legado.app.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HardcodedTextContractTest {

    @Test
    fun `漫画重试和进度使用资源`() {
        val activity = repoFile(
            "app/src/main/res/layout/activity_manga.xml"
        ).readText()
        val page = repoFile(
            "app/src/main/res/layout/item_book_manga_page.xml"
        ).readText()

        assertTrue(activity.contains("android:text=\"@string/retry\""))
        assertTrue(page.contains("android:text=\"@string/retry\""))
        assertTrue(
            page.contains("android:text=\"@string/progress_zero_percent\"")
        )
    }

    @Test
    fun `模拟阅读和主题导入使用资源`() {
        val simulatedReading = repoFile(
            "app/src/main/res/layout/dialog_simulated_reading.xml"
        ).readText()
        val themeMenu = repoFile(
            "app/src/main/res/menu/theme_list.xml"
        ).readText()

        assertTrue(
            simulatedReading.contains("android:hint=\"@string/select_date\"")
        )
        assertTrue(
            simulatedReading.contains(
                "android:hint=\"@string/default_daily_chapters_hint\""
            )
        )
        assertTrue(
            themeMenu.contains(
                "android:title=\"@string/import_from_clipboard\""
            )
        )
    }

    @Test
    fun `搜索菜单的可见文本和无障碍说明使用资源`() {
        val searchMenu = repoFile(
            "app/src/main/res/layout/view_search_menu.xml"
        ).readText()

        assertTrue(
            searchMenu.contains(
                "android:contentDescription=\"@string/previous_search_result\""
            )
        )
        assertTrue(
            searchMenu.contains(
                "android:contentDescription=\"@string/next_search_result\""
            )
        )
        assertTrue(
            count(searchMenu, "@string/search_content_size") == 3
        )
        assertTrue(count(searchMenu, "@string/exit") == 3)
    }

    @Test
    fun `新增文本覆盖所有现有语言且数值常量不可翻译`() {
        val translatableKeys = listOf(
            "select_date",
            "import_from_clipboard",
            "previous_search_result",
            "next_search_result"
        )
        listOf(
            "values",
            "values-es-rES",
            "values-ja-rJP",
            "values-pt-rBR",
            "values-vi",
            "values-zh",
            "values-zh-rHK",
            "values-zh-rTW"
        ).forEach { directory ->
            val strings = repoFile(
                "app/src/main/res/$directory/strings.xml"
            ).readText()
            translatableKeys.forEach { key ->
                assertTrue(
                    "$directory 缺少 $key",
                    strings.contains("name=\"$key\"")
                )
            }
        }

        val nonTranslatable = repoFile(
            "app/src/main/res/values/non_translat.xml"
        ).readText()
        assertTrue(
            nonTranslatable.contains(
                "name=\"default_daily_chapters_hint\" translatable=\"false\""
            )
        )
        assertTrue(
            nonTranslatable.contains(
                "name=\"progress_zero_percent\" translatable=\"false\""
            )
        )
    }

    private fun count(text: String, needle: String): Int =
        needle.toRegex().findAll(text).count()

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
