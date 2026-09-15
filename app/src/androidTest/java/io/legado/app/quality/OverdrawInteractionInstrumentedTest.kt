package io.legado.app.quality

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class OverdrawInteractionInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val profile = requireNotNull(
        InstrumentationRegistry.getArguments().getString(PROFILE_ARGUMENT)
    ) { "必须指定 $PROFILE_ARGUMENT" }

    @Test
    fun retainedBackgroundsPreserveInteractionAndVisualSemantics() {
        val activity = launchFixture()
        instrumentation.runOnMainSync {
            val inflater = LayoutInflater.from(activity)
            SELECTOR_LAYOUTS.forEach { layout ->
                val root = inflater.inflate(layout, null, false)
                val background = requireNotNull(root.background) { "选择器根背景缺失: $layout" }
                var clicks = 0
                var longClicks = 0
                root.setOnClickListener { clicks += 1 }
                root.setOnLongClickListener {
                    longClicks += 1
                    true
                }
                assertTrue("选择器根项不可点击: $layout", root.performClick())
                assertTrue("选择器根项不可长按: $layout", root.performLongClick())
                assertEquals("选择器根项点击次数错误: $layout", 1, clicks)
                assertEquals("选择器根项长按次数错误: $layout", 1, longClicks)
                root.isPressed = true
                assertTrue(
                    "选择器未进入 pressed 状态: $layout",
                    root.drawableState.contains(android.R.attr.state_pressed),
                )
                assertTrue("选择器背景不是 stateful: $layout", background.isStateful)
                root.isPressed = false
            }
            TRANSLUCENT_LAYOUTS.forEach { layout ->
                val alpha = renderedAlpha(inflater.inflate(layout, null, false).background)
                assertTrue("透明遮罩 alpha 无效: $layout alpha=$alpha", alpha in 1..254)
            }
            OPAQUE_LAYOUTS.forEach { layout ->
                val alpha = renderedAlpha(rootBackground(activity, inflater, layout))
                assertEquals("内容或兜底背景必须不透明: $layout", 255, alpha)
            }
            assertEquals(37, SELECTOR_LAYOUTS.size + TRANSLUCENT_LAYOUTS.size + OPAQUE_LAYOUTS.size)
        }
        closeFixture()
    }

    private fun renderedAlpha(drawable: Drawable?): Int {
        assertNotNull("语义根背景缺失", drawable)
        drawable ?: return 0
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 1, 1)
        drawable.draw(Canvas(bitmap))
        val alpha = Color.alpha(bitmap.getPixel(0, 0))
        bitmap.recycle()
        return alpha
    }

    private fun rootBackground(
        activity: GoldenFixtureActivity,
        inflater: LayoutInflater,
        @LayoutRes layout: Int,
    ): Drawable? {
        if (layout != R.layout.view_book_page) {
            return inflater.inflate(layout, null, false).background
        }
        // 阅读页的 ContentTextView 要求真实 ReadBookActivity 回调；这里只解析根节点背景，
        // 页面整体视觉继续由固定 golden 覆盖，避免为 lint 测试伪造阅读运行时。
        val parser = activity.resources.getLayout(layout)
        try {
            while (parser.eventType != XmlPullParser.START_TAG) {
                parser.next()
            }
            val attributes = activity.obtainStyledAttributes(
                parser,
                intArrayOf(android.R.attr.background),
            )
            return try {
                attributes.getDrawable(0)
            } finally {
                attributes.recycle()
            }
        } finally {
            parser.close()
        }
    }

    private fun launchFixture(): GoldenFixtureActivity {
        instrumentation.targetContext.startActivity(
            Intent().apply {
                setClassName(
                    instrumentation.targetContext.packageName,
                    GoldenFixtureActivity::class.java.name,
                )
                putExtra(GoldenFixtureActivity.EXTRA_FIXTURE, "transparent")
                putExtra(GoldenFixtureActivity.EXTRA_PROFILE, profile)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            resumedFixture()?.let { return it }
            SystemClock.sleep(100)
        }
        error("Overdraw 交互 fixture 未进入 RESUMED")
    }

    private fun closeFixture() {
        val activity = resumedFixture()
        instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
    }

    private fun resumedFixture(): GoldenFixtureActivity? {
        var activity: GoldenFixtureActivity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<GoldenFixtureActivity>()
                .firstOrNull()
        }
        return activity
    }

    companion object {
        private const val PROFILE_ARGUMENT = "legado.golden.profile"
        private val SELECTOR_LAYOUTS = intArrayOf(
            R.layout.item_1line_text,
            R.layout.item_app_log,
            R.layout.item_book_source,
            R.layout.item_bookmark,
            R.layout.item_change_source,
            R.layout.item_chapter_list,
            R.layout.item_file,
            R.layout.item_font,
            R.layout.item_import_book,
            R.layout.item_read_record,
            R.layout.item_rss_read_record,
            R.layout.item_rss_source,
            R.layout.item_search_list,
            R.layout.item_text,
            R.layout.item_txt_toc_rule,
        )
        private val TRANSLUCENT_LAYOUTS = intArrayOf(
            R.layout.activity_source_login,
            R.layout.activity_translucence,
            R.layout.dialog_click_action_config,
        )
        private val OPAQUE_LAYOUTS = intArrayOf(
            R.layout.activity_audio_play,
            R.layout.dialog_auto_read,
            R.layout.dialog_book_change_source,
            R.layout.dialog_chapter_change_source,
            R.layout.dialog_code_view,
            R.layout.dialog_content_edit,
            R.layout.dialog_file_chooser,
            R.layout.dialog_page_key,
            R.layout.dialog_photo_view,
            R.layout.dialog_read_aloud,
            R.layout.dialog_read_bg_text,
            R.layout.dialog_read_book_style,
            R.layout.dialog_search_scope,
            R.layout.dialog_text_view,
            R.layout.dialog_wait,
            R.layout.item_book_manga_edge,
            R.layout.item_book_manga_page,
            R.layout.popup_keyboard_tool,
            R.layout.view_book_page,
        )
    }
}
