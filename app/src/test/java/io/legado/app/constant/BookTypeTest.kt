package io.legado.app.constant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookTypeTest {

    private val flags = listOf(
        BookType.text,
        BookType.updateError,
        BookType.audio,
        BookType.image,
        BookType.webFile,
        BookType.local,
        BookType.archive,
        BookType.notShelf
    )

    @Test
    fun `每个书籍类型占用独立二进制位`() {
        assertEquals(listOf(8, 16, 32, 64, 128, 256, 512, 1_024), flags)
        flags.forEach { flag ->
            assertTrue(flag > 0)
            assertEquals(0, flag and (flag - 1))
        }
        assertEquals(flags.size, flags.toSet().size)
    }

    @Test
    fun `组合类型保留每个成员且不引入其他位`() {
        val combined = BookType.text or BookType.image or BookType.local

        assertEquals(BookType.text, combined and BookType.text)
        assertEquals(BookType.image, combined and BookType.image)
        assertEquals(BookType.local, combined and BookType.local)
        assertEquals(0, combined and BookType.audio)
        assertEquals(0, combined and BookType.updateError)
    }

    @Test
    fun `IntDef 明确允许位掩码组合`() {
        val source = repoFile(
            "app/src/main/java/io/legado/app/constant/BookType.kt"
        ).readText()
        val annotationContract = source.substring(
            source.indexOf("@IntDef"),
            source.indexOf("annotation class Type")
        )

        assertTrue(annotationContract.contains("flag = true"))
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
