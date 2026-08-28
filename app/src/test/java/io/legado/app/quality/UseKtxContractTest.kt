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

    @Test
    fun `样式属性读取使用自动回收的 KTX 扩展`() {
        val styledAttributeSources = styledAttributeSourcePaths.map(::source)

        styledAttributeSources.forEach { source ->
            assertFalse(source.contains("context.obtainStyledAttributes("))
        }
        assertEquals(
            20,
            styledAttributeSources.sumOf {
                Regex("""context\.withStyledAttributes\(""").findAll(it).count()
            }
        )
    }

    @Test
    fun `字符串颜色解析使用等价 KTX 扩展`() {
        val colorSources = colorSourcePaths.map(::source)

        colorSources.forEach { source ->
            assertFalse(source.contains("Color.parseColor("))
        }
        assertEquals(
            22,
            colorSources.sumOf { Regex("""\.toColorInt\(\)""").findAll(it).count() }
        )
    }

    @Test
    fun `Bitmap Drawable 转换使用等价 KTX 扩展`() {
        val bitmapDrawableSources = bitmapDrawableSourcePaths.map(::source)
        val nullableBitmapDrawableSources = nullableBitmapDrawableSourcePaths.map(::source)

        assertEquals(
            2,
            bitmapDrawableSources.sumOf {
                Regex("""\.toDrawable\([^)]*resources\)""").findAll(it).count()
            }
        )
        bitmapDrawableSources.forEach { source ->
            assertFalse(source.contains("BitmapDrawable("))
        }
        assertEquals(
            5,
            nullableBitmapDrawableSources.sumOf {
                Regex("""BitmapDrawable\(""").findAll(it).count()
            }
        )
        assertEquals(
            5,
            nullableBitmapDrawableSources.sumOf {
                Regex("""//noinspection UseKtx""").findAll(it).count()
            }
        )
    }

    @Test
    fun `颜色 Drawable 构造使用等价 KTX 扩展`() {
        val colorDrawableSources = colorDrawableSourcePaths.map(::source)

        colorDrawableSources.forEach { source ->
            assertFalse(source.contains("ColorDrawable("))
        }
        assertEquals(
            9,
            colorDrawableSources.sumOf {
                Regex("""\.toDrawable\(\)""").findAll(it).count()
            }
        )
    }

    @Test
    fun `Bitmap 构造使用等价 KTX 函数`() {
        val createBitmapSources = createBitmapSourcePaths.map(::source)

        createBitmapSources.forEach { source ->
            assertFalse(source.contains("Bitmap.createBitmap("))
        }
        assertEquals(
            9,
            createBitmapSources.sumOf {
                Regex("""\bcreateBitmap\(""").findAll(it).count()
            }
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

        val styledAttributeSourcePaths = listOf(
            "app/src/main/java/io/legado/app/lib/prefs/ColorPreference.kt",
            "app/src/main/java/io/legado/app/lib/prefs/NameListPreference.kt",
            "app/src/main/java/io/legado/app/lib/prefs/Preference.kt",
            "app/src/main/java/io/legado/app/lib/prefs/SwitchPreference.kt",
            "app/src/main/java/io/legado/app/lib/theme/view/ThemeRadioNoButton.kt",
            "app/src/main/java/io/legado/app/ui/widget/DetailSeekBar.kt",
            "app/src/main/java/io/legado/app/ui/widget/ShadowLayout.kt",
            "app/src/main/java/io/legado/app/ui/widget/TitleBar.kt",
            "app/src/main/java/io/legado/app/ui/widget/anima/RefreshProgressBar.kt",
            "app/src/main/java/io/legado/app/ui/widget/anima/RotateLoading.kt",
            "app/src/main/java/io/legado/app/ui/widget/dynamiclayout/DynamicFrameLayout.kt",
            "app/src/main/java/io/legado/app/ui/widget/image/ArcView.kt",
            "app/src/main/java/io/legado/app/ui/widget/image/CircleImageView.kt",
            "app/src/main/java/io/legado/app/ui/widget/image/FilletImageView.kt",
            "app/src/main/java/io/legado/app/ui/widget/recycler/DividerNoLast.kt",
            "app/src/main/java/io/legado/app/ui/widget/seekbar/VerticalSeekBar.kt",
            "app/src/main/java/io/legado/app/ui/widget/text/AccentBgTextView.kt",
            "app/src/main/java/io/legado/app/ui/widget/text/AccentStrokeTextView.kt",
            "app/src/main/java/io/legado/app/ui/widget/text/BevelLabelView.kt",
            "app/src/main/java/io/legado/app/ui/widget/text/StrokeTextView.kt"
        )

        val colorSourcePaths = listOf(
            "app/src/main/java/io/legado/app/help/config/ReadBookConfig.kt",
            "app/src/main/java/io/legado/app/help/config/ThemeConfig.kt",
            "app/src/main/java/io/legado/app/lib/theme/ThemeStore.kt",
            "app/src/main/java/io/legado/app/ui/book/read/ReadMenu.kt",
            "app/src/main/java/io/legado/app/ui/widget/anima/RotateLoading.kt",
            "app/src/main/java/io/legado/app/ui/widget/image/ArcView.kt"
        )

        val bitmapDrawableSourcePaths = listOf(
            "app/src/main/java/io/legado/app/base/BaseActivity.kt",
            "app/src/main/java/io/legado/app/utils/ACache.kt"
        )

        val nullableBitmapDrawableSourcePaths = listOf(
            "app/src/main/java/io/legado/app/help/config/ReadBookConfig.kt",
            "app/src/main/java/io/legado/app/model/BookCover.kt",
            "app/src/main/java/io/legado/app/ui/welcome/WelcomeActivity.kt"
        )

        val colorDrawableSourcePaths = listOf(
            "app/src/main/java/io/legado/app/help/config/ReadBookConfig.kt",
            "app/src/main/java/io/legado/app/lib/theme/Selector.kt",
            "app/src/main/java/io/legado/app/lib/theme/ViewUtils.kt",
            "app/src/main/java/io/legado/app/lib/theme/view/ThemeBottomNavigationVIew.kt",
            "app/src/main/java/io/legado/app/utils/DrawableUtils.kt"
        )

        val createBitmapSourcePaths = listOf(
            "app/src/main/java/io/legado/app/ui/widget/anima/explosion_field/Utils.kt",
            "app/src/main/java/io/legado/app/ui/widget/image/CircleImageView.kt",
            "app/src/main/java/io/legado/app/utils/ACache.kt",
            "app/src/main/java/io/legado/app/utils/QRCodeUtils.kt",
            "app/src/main/java/io/legado/app/utils/ViewExtensions.kt"
        )
    }
}
