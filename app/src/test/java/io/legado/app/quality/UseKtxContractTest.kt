package io.legado.app.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `Bitmap 像素访问使用等价下标操作符`() {
        val bitmapUtils = source("app/src/main/java/io/legado/app/utils/BitmapUtils.kt")
        val animator = source(
            "app/src/main/java/io/legado/app/ui/widget/anima/explosion_field/ExplosionAnimator.kt"
        )

        assertFalse(bitmapUtils.contains("getPixel("))
        assertFalse(animator.contains("getPixel("))
        assertTrue(bitmapUtils.contains("pixel = this["))
        assertTrue(animator.contains("generateParticle(bitmap["))
    }

    @Test
    fun `Locale 布局方向使用等价 KTX 属性`() {
        val source = source(
            "app/src/main/java/io/legado/app/ui/widget/recycler/DragSelectTouchHelper.kt"
        )

        assertFalse(source.contains("TextUtils.getLayoutDirectionFromLocale"))
        assertTrue(source.contains("Locale.getDefault().layoutDirection"))
    }

    @Test
    fun `ViewGroup 子项判断使用等价 KTX 函数`() {
        val source = source(
            "app/src/main/java/io/legado/app/ui/widget/seekbar/VerticalSeekBarWrapper.kt"
        )

        assertFalse(source.contains("childCount > 0"))
        assertTrue(source.contains("if (isNotEmpty()) getChildAt(0)"))
    }

    @Test
    fun `View 可见性读取使用等价 KTX 属性`() {
        val fastScroller = source(
            "app/src/main/java/io/legado/app/ui/widget/recycler/scroller/FastScroller.kt"
        )
        val rotateLoading = source(
            "app/src/main/java/io/legado/app/ui/widget/anima/RotateLoading.kt"
        )
        val viewExtensions = source(
            "app/src/main/java/io/legado/app/utils/ViewExtensions.kt"
        )

        assertTrue(fastScroller.contains("view?.isVisible == true"))
        assertTrue(rotateLoading.contains("if (isVisible)"))
        assertTrue(viewExtensions.contains("if (visible && !isVisible)"))
        assertTrue(viewExtensions.contains("else if (!visible && isVisible)"))
    }

    private fun source(path: String): String = repoFile(path).readText()

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
