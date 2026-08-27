package io.legado.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class SetTextI18nContractTest {

    @Test
    fun `显示整数遵循界面语言的分组格式`() {
        val formatter = Class.forName(
            "io.legado.app.utils.LocalizedNumberFormatter"
        ).getMethod("formatInteger", Int::class.javaPrimitiveType, Locale::class.java)

        assertEquals("1,234", formatter.invoke(null, 1234, Locale.US))
        assertEquals("1.234", formatter.invoke(null, 1234, Locale.GERMANY))
    }

    @Test
    fun `显示型数字调用本地化格式器`() {
        val seekBar = repoFile(
            "app/src/main/java/io/legado/app/ui/widget/DetailSeekBar.kt"
        ).readText()
        val themeConfig = repoFile(
            "app/src/main/java/io/legado/app/ui/config/ThemeConfigFragment.kt"
        ).readText()

        assertTrue(
            seekBar.contains(
                "LocalizedNumberFormatter.formatInteger(context, progress)"
            )
        )
        assertEquals(
            2,
            "LocalizedNumberFormatter.formatInteger".toRegex()
                .findAll(themeConfig)
                .count()
        )
    }

    @Test
    fun `机器数值输入保留 ASCII 且有精确理由`() {
        val readActivity = repoFile(
            "app/src/main/java/io/legado/app/ui/book/read/BaseReadBookActivity.kt"
        ).readText()
        val checkSource = repoFile(
            "app/src/main/java/io/legado/app/ui/config/CheckSourceConfig.kt"
        ).readText()
        val replaceEdit = repoFile(
            "app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditActivity.kt"
        ).readText()

        assertTrue(readActivity.contains("ASCII 数字输入会由 toInt 回读"))
        assertTrue(
            readActivity.contains(
                "@SuppressLint(\"SetTextI18n\")\n    fun showSimulatedReading()"
            )
        )
        assertTrue(checkSource.contains("ASCII 数字输入会由 toLong 回读"))
        assertTrue(
            checkSource.contains(
                "@SuppressLint(\"SetTextI18n\")\n    override fun onFragmentCreated"
            )
        )
        assertTrue(replaceEdit.contains("ASCII 数字输入会由 toLong 回读"))
        assertTrue(
            replaceEdit.contains(
                "@SuppressLint(\"SetTextI18n\")\n    private fun upReplaceView"
            )
        )
    }

    @Test
    fun `外部跳转确认使用所有语言资源占位符`() {
        val dialog = repoFile(
            "app/src/main/java/io/legado/app/ui/association/OpenUrlConfirmDialog.kt"
        ).readText()
        assertTrue(
            Regex(
                """getString\(\s*R\.string\.open_url_confirm_message,\s*""" +
                    """viewModel\.sourceName\s*\)"""
            ).containsMatchIn(dialog)
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
            assertTrue(
                "$directory 缺少 open_url_confirm_message",
                strings.contains("name=\"open_url_confirm_message\"")
            )
            assertTrue(
                "$directory 缺少来源名称占位符",
                strings.substringAfter("name=\"open_url_confirm_message\"")
                    .substringBefore("</string>")
                    .contains("%1\$s")
            )
        }
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
