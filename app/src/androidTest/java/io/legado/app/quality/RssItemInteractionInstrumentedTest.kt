package io.legado.app.quality

import android.content.Intent
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssItemInteractionInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val profile = requireNotNull(
        InstrumentationRegistry.getArguments().getString(PROFILE_ARGUMENT)
    ) { "必须指定 $PROFILE_ARGUMENT" }

    @Test
    fun rssItemPreservesClickLongClickFocusAndRipple() {
        val activity = launchFixture()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val rssItem = content.getChildAt(0)
        assertNotNull("RSS 根布局不存在", rssItem)
        rssItem ?: return

        var clickCount = 0
        var longClickCount = 0
        instrumentation.runOnMainSync {
            rssItem.setOnClickListener { clickCount += 1 }
            rssItem.setOnLongClickListener {
                longClickCount += 1
                true
            }
            assertTrue("RSS 根布局必须可聚焦", rssItem.isFocusable)
            assertTrue("RSS 根布局无法从 touch mode 获得焦点", rssItem.requestFocusFromTouch())
            assertTrue("RSS 根布局未保持焦点", rssItem.isFocused)
            assertTrue("RSS 根布局未消费点击", rssItem.performClick())
            assertTrue("RSS 根布局未消费长按", rssItem.performLongClick())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val foreground = rssItem.foreground
                assertTrue("API 23+ RSS foreground 必须是 ripple", foreground is RippleDrawable)
                assertTrue("RSS ripple 必须响应状态变化", requireNotNull(foreground).isStateful)
                rssItem.isPressed = true
                rssItem.refreshDrawableState()
                rssItem.jumpDrawablesToCurrentState()
                assertTrue(
                    "RSS ripple 未接收到 pressed 状态",
                    foreground.state.contains(android.R.attr.state_pressed),
                )
                rssItem.isPressed = false
                rssItem.refreshDrawableState()
                assertFalse(
                    "RSS ripple 释放后仍保留 pressed 状态",
                    foreground.state.contains(android.R.attr.state_pressed),
                )
            }
        }

        assertEquals("RSS 点击回调次数变化", 1, clickCount)
        assertEquals("RSS 长按回调次数变化", 1, longClickCount)
        closeFixture(activity)
    }

    private fun launchFixture(): GoldenFixtureActivity {
        instrumentation.targetContext.startActivity(
            Intent().apply {
                setClassName(
                    instrumentation.targetContext.packageName,
                    GoldenFixtureActivity::class.java.name,
                )
                putExtra(GoldenFixtureActivity.EXTRA_FIXTURE, "rss")
                putExtra(GoldenFixtureActivity.EXTRA_PROFILE, profile)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            resumedFixture()?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("RSS golden fixture 未进入 RESUMED")
    }

    private fun closeFixture(activity: GoldenFixtureActivity) {
        instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
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

    private companion object {
        const val PROFILE_ARGUMENT = "legado.golden.profile"
    }
}
