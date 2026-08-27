package io.legado.app.help

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LayoutManagerOrientationTest {

    private val source = repoFile(
        "app/src/main/java/io/legado/app/help/LayoutManager.kt"
    ).readText()

    @Test
    fun `方向参数复用 RecyclerView 公共契约`() {
        assertFalse(source.contains("annotation class Orientation"))
        assertFalse(source.contains("@IntDef"))
        assertEquals(3, "@RecyclerView.Orientation".toRegex().findAll(source).count())
    }

    @Test
    fun `横向和纵向常量保持 RecyclerView 语义`() {
        assertEquals(0, RecyclerView.HORIZONTAL)
        assertEquals(1, RecyclerView.VERTICAL)
        assertTrue(source.contains("LinearLayoutManager(recyclerView.context, orientation, reverseLayout)"))
        assertTrue(source.contains("orientation,\n                    reverseLayout"))
        assertTrue(source.contains("StaggeredGridLayoutManager(spanCount, orientation)"))
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
