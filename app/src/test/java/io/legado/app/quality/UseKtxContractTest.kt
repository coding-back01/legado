package io.legado.app.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class UseKtxContractTest {

    @Test
    fun `SparseArray 数量访问使用等价 Kotlin 属性`() {
        val source = repoFile(
            "app/src/main/java/io/legado/app/base/adapter/RecyclerAdapter.kt"
        ).readText()

        assertFalse(source.contains("headerItems.size()"))
        assertFalse(source.contains("footerItems.size()"))
        assertEquals(3, Regex("""headerItems\.size\b""").findAll(source).count())
        assertEquals(3, Regex("""footerItems\.size\b""").findAll(source).count())
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
