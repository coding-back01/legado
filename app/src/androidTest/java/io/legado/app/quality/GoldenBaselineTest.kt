package io.legado.app.quality

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoldenBaselineTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val profile = requireNotNull(
        InstrumentationRegistry.getArguments().getString(PROFILE_ARGUMENT)
    ) { "必须指定 $PROFILE_ARGUMENT" }

    @Test
    fun capturesGovernanceBaseline() {
        val activity = launchFixture(FIXTURES.first())
        FIXTURES.forEach { fixture ->
            instrumentation.runOnMainSync { activity.showFixture(fixture) }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(500)
            GoldenScreenshot.captureAndCompare(fixture, activity)
        }
        closeFixture()
    }

    private fun launchFixture(fixture: String): GoldenFixtureActivity {
        instrumentation.targetContext.startActivity(
            Intent().apply {
                setClassName(
                    instrumentation.targetContext.packageName,
                    GoldenFixtureActivity::class.java.name,
                )
                putExtra(GoldenFixtureActivity.EXTRA_FIXTURE, fixture)
                putExtra(GoldenFixtureActivity.EXTRA_PROFILE, profile)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        val activity = waitForFixture(fixture)
        assertNotNull("golden fixture 未进入 RESUMED: $fixture", activity)
        SystemClock.sleep(500)
        return requireNotNull(activity)
    }

    private fun closeFixture() {
        val activity = resumedFixture()
        instrumentation.runOnMainSync {
            activity?.finishAndRemoveTask()
        }
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline && resumedFixture() != null) {
            SystemClock.sleep(50)
        }
    }

    private fun waitForFixture(
        fixture: String,
        timeoutMillis: Long = 15_000,
    ): GoldenFixtureActivity? {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            resumedFixture(fixture)?.let { return it }
            SystemClock.sleep(100)
        }
        return null
    }

    private fun resumedFixture(expectedFixture: String? = null): GoldenFixtureActivity? {
        var activity: GoldenFixtureActivity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<GoldenFixtureActivity>()
                .firstOrNull {
                    expectedFixture == null ||
                        it.intent.getStringExtra(GoldenFixtureActivity.EXTRA_FIXTURE) == expectedFixture
                }
        }
        return activity
    }

    companion object {
        private const val PROFILE_ARGUMENT = "legado.golden.profile"
        private val FIXTURES = listOf(
            "direction",
            "icon_catalog",
            "bookshelf",
            "rss",
            "welcome",
            "transparent",
            "dialog",
            "manga_menu",
            "file_path",
        )
    }
}
