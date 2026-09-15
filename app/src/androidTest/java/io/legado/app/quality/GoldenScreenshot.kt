package io.legado.app.quality

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File
import java.io.FileNotFoundException

object GoldenScreenshot {

    private const val PROFILE_ARGUMENT = "legado.golden.profile"
    private const val UPDATE_ARGUMENT = "legado.golden.update"
    private const val MAX_DIFFERENT_PIXEL_RATIO = 0.002
    private const val MAX_CHANNEL_DELTA = 2
    private const val DIFF_SCALE = 4
    private val safeName = Regex("[a-z0-9][a-z0-9_-]*")

    fun captureAndCompare(
        name: String,
        activity: Activity,
        maxDifferentPixelRatio: Double = MAX_DIFFERENT_PIXEL_RATIO,
    ) {
        require(name.matches(safeName)) { "非法 golden 名称: $name" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val profile = requireNotNull(arguments.getString(PROFILE_ARGUMENT)) {
            "必须通过 -e $PROFILE_ARGUMENT 指定固定设备配置"
        }
        require(profile.matches(safeName)) { "非法 golden profile: $profile" }

        val current = captureApplicationContent(activity)

        if (arguments.getString(UPDATE_ARGUMENT).toBoolean()) {
            writeBitmap(current, updateDirectory(profile), "$name.png")
            current.recycle()
            return
        }

        val expected = try {
            instrumentation.context.assets.open("golden/$profile/$name.png").use {
                BitmapFactory.decodeStream(it)
            }
        } catch (error: FileNotFoundException) {
            throw AssertionError(
                "缺少 golden/$profile/$name.png；只能通过显式更新流程生成候选图片",
                error,
            )
        }
        assertNotNull("golden 图片无法解码: $profile/$name.png", expected)
        expected ?: return

        val outputDirectory = diffDirectory(profile, name)
        if (expected.width != current.width || expected.height != current.height) {
            writeBitmap(current, outputDirectory, "current.png")
            writeBitmap(expected, outputDirectory, "expected.png")
        }
        assertEquals("截图宽度变化: $profile/$name", expected.width, current.width)
        assertEquals("截图高度变化: $profile/$name", expected.height, current.height)

        val currentRow = IntArray(current.width)
        val expectedRow = IntArray(expected.width)
        val diffWidth = maxOf(1, current.width / DIFF_SCALE)
        val diffHeight = maxOf(1, current.height / DIFF_SCALE)
        val diffPixels = IntArray(diffWidth * diffHeight)
        var differentPixels = 0
        for (y in 0 until current.height) {
            current.getPixels(currentRow, 0, current.width, 0, y, current.width, 1)
            expected.getPixels(expectedRow, 0, expected.width, 0, y, expected.width, 1)
            for (x in 0 until current.width) {
                if (isDifferent(currentRow[x], expectedRow[x])) {
                    differentPixels += 1
                    val diffX = minOf(diffWidth - 1, x / DIFF_SCALE)
                    val diffY = minOf(diffHeight - 1, y / DIFF_SCALE)
                    diffPixels[diffY * diffWidth + diffX] = Color.MAGENTA
                }
            }
        }
        val pixelCount = current.width * current.height
        val differentRatio = differentPixels.toDouble() / pixelCount
        if (differentRatio > maxDifferentPixelRatio) {
            writeBitmap(current, outputDirectory, "current.png")
            writeBitmap(expected, outputDirectory, "expected.png")
            val diff = Bitmap.createBitmap(
                diffPixels,
                diffWidth,
                diffHeight,
                Bitmap.Config.ARGB_8888,
            )
            writeBitmap(diff, outputDirectory, "diff.png")
            diff.recycle()
        }
        current.recycle()
        expected.recycle()

        assertTrue(
            "golden 像素差异超限: $profile/$name, " +
                "$differentPixels/$pixelCount ($differentRatio)，" +
                "阈值 $maxDifferentPixelRatio；差异见 ${outputDirectory.absolutePath}",
            differentRatio <= maxDifferentPixelRatio,
        )
    }

    private fun isDifferent(current: Int, expected: Int): Boolean =
        channelDelta(Color.alpha(current), Color.alpha(expected)) > MAX_CHANNEL_DELTA ||
            channelDelta(Color.red(current), Color.red(expected)) > MAX_CHANNEL_DELTA ||
            channelDelta(Color.green(current), Color.green(expected)) > MAX_CHANNEL_DELTA ||
            channelDelta(Color.blue(current), Color.blue(expected)) > MAX_CHANNEL_DELTA

    private fun channelDelta(first: Int, second: Int): Int = kotlin.math.abs(first - second)

    private fun captureApplicationContent(activity: Activity): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val contentBounds = Rect()
        val visibleWindowBounds = Rect()
        instrumentation.runOnMainSync {
            val content = activity.findViewById<android.view.View>(android.R.id.content)
            assertTrue("应用内容区域不可见", content.getGlobalVisibleRect(contentBounds))
            activity.window.decorView.getWindowVisibleDisplayFrame(visibleWindowBounds)
            assertTrue(
                "应用内容区域与可见窗口不相交: content=$contentBounds, window=$visibleWindowBounds",
                contentBounds.intersect(visibleWindowBounds),
            )
        }
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("平台截图返回空 Bitmap", screenshot)
        screenshot ?: throw AssertionError("平台截图返回空 Bitmap")
        val left = contentBounds.left.coerceIn(0, screenshot.width - 1)
        val top = contentBounds.top.coerceIn(0, screenshot.height - 1)
        val width = contentBounds.width().coerceAtMost(screenshot.width - left)
        val height = contentBounds.height().coerceAtMost(screenshot.height - top)
        assertTrue("应用内容区域宽度无效: $contentBounds", width > 0)
        assertTrue("应用内容区域高度无效: $contentBounds", height > 0)
        val content = Bitmap.createBitmap(screenshot, left, top, width, height)
        if (content !== screenshot) screenshot.recycle()
        return content
    }

    private fun updateDirectory(profile: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, "golden-updates/$profile")
    }

    private fun diffDirectory(profile: String, name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, "golden-diffs/$profile/$name")
    }

    private fun writeBitmap(bitmap: Bitmap, directory: File, name: String) {
        assertTrue("无法创建 golden 输出目录: $directory", directory.mkdirs() || directory.isDirectory)
        File(directory, name).outputStream().use { output ->
            assertTrue("无法写入 $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
