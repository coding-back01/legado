package io.legado.app.quality

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Launcher3GoldenInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val profile = requireNotNull(
        InstrumentationRegistry.getArguments().getString(PROFILE_ARGUMENT)
    ) { "必须指定 $PROFILE_ARGUMENT" }

    @Test
    fun vectorAdaptiveAndMonochromeStayWithinApprovedThreshold() {
        val activity = launchFixture()
        // 三张 360px 图标占据固定截图主体；0.01% 全图阈值约束 path 细节变化。
        GoldenScreenshot.captureAndCompare(FIXTURE, activity, MAX_DIFFERENT_PIXEL_RATIO)
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
        error("launcher3 fixture 未进入 RESUMED")
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
        private const val FIXTURE = "launcher3"
        private const val MAX_DIFFERENT_PIXEL_RATIO = 0.0001
    }
}
