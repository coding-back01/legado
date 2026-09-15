package io.legado.app.quality

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaMenuLayoutInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val profile = requireNotNull(
        InstrumentationRegistry.getArguments().getString(PROFILE_ARGUMENT)
    ) { "必须指定 $PROFILE_ARGUMENT" }

    @Test
    fun progressRowPreservesPositionBackgroundAndClickAreas() {
        val activity = launchFixture()
        instrumentation.runOnMainSync {
            val bottomMenu = activity.findViewById<View>(R.id.bottom_menu)
            val previous = activity.findViewById<View>(R.id.tv_pre)
            val seekBar = activity.findViewById<View>(R.id.seek_read_page)
            val next = activity.findViewById<View>(R.id.tv_next)
            assertNotNull("漫画菜单底部背景缺失", bottomMenu.background)

            val progressRow = seekBar.parent as View
            assertSame("进度行必须直接位于带背景的底部菜单内", bottomMenu, progressRow.parent)
            assertTrue("进度行上边距未形成背景留白", progressRow.top > 0)
            assertTrue("进度行下边距未形成背景留白", progressRow.bottom < bottomMenu.height)

            val previousArea = Rect().also(previous::getHitRect)
            val seekArea = Rect().also(seekBar::getHitRect)
            val nextArea = Rect().also(next::getHitRect)
            assertTrue("上一章点击区域无效: $previousArea", !previousArea.isEmpty)
            assertTrue("进度条点击区域无效: $seekArea", !seekArea.isEmpty)
            assertTrue("下一章点击区域无效: $nextArea", !nextArea.isEmpty)
            assertTrue("上一章与进度条发生重叠", previousArea.right <= seekArea.left)
            assertTrue("进度条与下一章发生重叠", seekArea.right <= nextArea.left)

            var previousClicks = 0
            var nextClicks = 0
            previous.setOnClickListener { previousClicks += 1 }
            next.setOnClickListener { nextClicks += 1 }
            assertTrue("上一章区域不可点击", previous.performClick())
            assertTrue("下一章区域不可点击", next.performClick())
            assertEquals("上一章点击次数错误", 1, previousClicks)
            assertEquals("下一章点击次数错误", 1, nextClicks)
        }
        closeFixture()
    }

    private fun launchFixture(): GoldenFixtureActivity {
        instrumentation.targetContext.startActivity(
            Intent().apply {
                setClassName(
                    instrumentation.targetContext.packageName,
                    GoldenFixtureActivity::class.java.name,
                )
                putExtra(GoldenFixtureActivity.EXTRA_FIXTURE, FIXTURE)
                putExtra(GoldenFixtureActivity.EXTRA_PROFILE, profile)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            resumedFixture()?.let {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(500)
                return it
            }
            SystemClock.sleep(100)
        }
        error("漫画菜单 fixture 未进入 RESUMED")
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
        private const val FIXTURE = "manga_menu"
    }
}
