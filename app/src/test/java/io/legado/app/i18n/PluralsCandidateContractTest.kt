package io.legado.app.i18n

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class PluralsCandidateContractTest {

    @Test
    fun `移除未使用的章节总数资源`() {
        assertResourceAbsent("all_chapter_num")
    }

    @Test
    fun `移除未使用的剩余下载章节资源`() {
        assertResourceAbsent("un_download")
    }

    @Test
    fun `移除未使用的搜索书源数量资源`() {
        assertResourceAbsent("search_book_source_num")
    }

    @Test
    fun `移除未使用的文件子项数量资源`() {
        assertResourceAbsent("nb_file_sub_count")
    }

    @Test
    fun `移除未使用的批量加书结果资源`() {
        assertResourceAbsent("nb_file_add_succeed")
    }

    private fun assertResourceAbsent(resourceName: String) {
        resourceDirectories.forEach { directory ->
            val strings = repoFile(
                "app/src/main/res/$directory/strings.xml"
            ).readText()
            assertFalse(
                "$directory 仍声明未使用资源 $resourceName",
                strings.contains("name=\"$resourceName\"")
            )
        }
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
