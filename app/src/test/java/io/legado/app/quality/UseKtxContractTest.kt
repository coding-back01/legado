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

    @Test
    fun `字符串 URI 解析使用等价 KTX 扩展`() {
        val uriSources = uriSourcePaths.map(::source)

        uriSources.forEach { source ->
            assertFalse(source.contains("Uri.parse("))
        }
        assertEquals(
            38,
            uriSources.sumOf { Regex("""\.toUri\(\)""").findAll(it).count() }
        )
        assertTrue(
            source("app/src/main/java/io/legado/app/utils/StringExtensions.kt")
                .contains("return if (isUri()) toUri()")
        )
    }

    private fun source(path: String): String = repoFile(path).readText()

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }

    private companion object {
        val uriSourcePaths = listOf(
            "app/src/main/java/io/legado/app/help/CrashHandler.kt",
            "app/src/main/java/io/legado/app/help/IntentHelp.kt",
            "app/src/main/java/io/legado/app/help/exoplayer/ExoPlayerHelper.kt",
            "app/src/main/java/io/legado/app/help/glide/ImageLoader.kt",
            "app/src/main/java/io/legado/app/lib/permission/PermissionActivity.kt",
            "app/src/main/java/io/legado/app/model/localBook/LocalBook.kt",
            "app/src/main/java/io/legado/app/model/remote/RemoteBookWebDav.kt",
            "app/src/main/java/io/legado/app/service/DownloadService.kt",
            "app/src/main/java/io/legado/app/ui/about/AboutFragment.kt",
            "app/src/main/java/io/legado/app/ui/about/CrashLogsDialog.kt",
            "app/src/main/java/io/legado/app/ui/association/FileAssociationActivity.kt",
            "app/src/main/java/io/legado/app/ui/association/ImportBookSourceViewModel.kt",
            "app/src/main/java/io/legado/app/ui/book/import/remote/RemoteBookActivity.kt",
            "app/src/main/java/io/legado/app/ui/book/read/TextActionMenu.kt",
            "app/src/main/java/io/legado/app/ui/book/read/page/provider/ChapterProvider.kt",
            "app/src/main/java/io/legado/app/ui/browser/WebViewActivity.kt",
            "app/src/main/java/io/legado/app/ui/font/FontSelectDialog.kt",
            "app/src/main/java/io/legado/app/ui/login/WebViewLoginFragment.kt",
            "app/src/main/java/io/legado/app/ui/rss/read/ReadRssActivity.kt",
            "app/src/main/java/io/legado/app/utils/ArchiveUtils.kt",
            "app/src/main/java/io/legado/app/utils/ContextExtensions.kt",
            "app/src/main/java/io/legado/app/utils/FileDocExtensions.kt",
            "app/src/main/java/io/legado/app/utils/RealPathUtil.kt",
            "app/src/main/java/io/legado/app/utils/StringExtensions.kt",
            "app/src/main/java/io/legado/app/utils/SystemUtils.kt"
        )
    }
}
