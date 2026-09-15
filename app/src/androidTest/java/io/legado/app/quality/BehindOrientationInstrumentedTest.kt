package io.legado.app.quality

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.legado.app.ui.book.manage.BookshelfManageActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BehindOrientationInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val profile = InstrumentationRegistry.getArguments()
        .getString(PROFILE_ARGUMENT).orEmpty()

    @Test
    fun nineActivitiesKeepBehindManifestContract() {
        BEHIND_ACTIVITIES.forEach { className ->
            @Suppress("DEPRECATION")
            val info = context.packageManager.getActivityInfo(
                ComponentName(context.packageName, className),
                0,
            )
            assertEquals(className, ActivityInfo.SCREEN_ORIENTATION_BEHIND, info.screenOrientation)
        }
    }

    @Test
    fun behindActivityCoversBothParentOrientationsAndBackStack() {
        verifyOrientationRoundTrip(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        verifyOrientationRoundTrip(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    }

    @Test
    fun multiWindowProfileUsesPlatformMultiWindowMode() {
        if (!profile.contains("multiwindow")) return
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)

        val component = "${context.packageName}/${DirectionParentActivity::class.java.name}"
        val commandOutput = instrumentation.uiAutomation.executeShellCommand(
            "am start -W --windowingMode 5 -n $component",
        )
        ParcelFileDescriptor.AutoCloseInputStream(commandOutput)
            .bufferedReader()
            .use { it.readText() }
        val activity = waitForResumed<DirectionParentActivity>()
        assertNotNull("多窗口 profile 未启动方向父页", activity)
        assertTrue("多窗口 profile 未进入平台多窗口模式", requireNotNull(activity).isInMultiWindowMode)
        finish(activity)
    }

    private fun verifyOrientationRoundTrip(requestedOrientation: Int) {
        val parent = launchParent(requestedOrientation)
        waitForStableOrientation(parent)
        val parentOrientation = windowOrientation(parent)
        assertTrue(
            "父页方向无效: $parentOrientation",
            parentOrientation == Configuration.ORIENTATION_PORTRAIT ||
                    parentOrientation == Configuration.ORIENTATION_LANDSCAPE,
        )

        instrumentation.runOnMainSync {
            parent.startActivity(Intent(parent, BookshelfManageActivity::class.java))
        }
        val child = requireNotNull(waitForResumed<BookshelfManageActivity>()) {
            "BookshelfManageActivity 未进入 RESUMED"
        }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_BEHIND, child.requestedOrientation)
        waitForStableOrientation(child)
        val childOrientation = windowOrientation(child)
        assertTrue(
            "behind 子页方向无效",
            childOrientation == Configuration.ORIENTATION_PORTRAIT ||
                    childOrientation == Configuration.ORIENTATION_LANDSCAPE,
        )
        finish(child)

        val resumedParent = requireNotNull(waitForResumed<DirectionParentActivity>()) {
            "返回栈未恢复方向父页"
        }
        waitForWindowOrientation(resumedParent, parentOrientation)
        assertEquals(parentOrientation, windowOrientation(resumedParent))
        println(
            "方向验证 profile=$profile requested=$requestedOrientation " +
                    "parent=$parentOrientation child=$childOrientation " +
                    "sdk=${Build.VERSION.SDK_INT}",
        )
        finish(resumedParent)
    }

    private fun launchParent(requestedOrientation: Int): DirectionParentActivity {
        context.startActivity(
            Intent(context, DirectionParentActivity::class.java).apply {
                putExtra(DirectionParentActivity.EXTRA_REQUESTED_ORIENTATION, requestedOrientation)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        return requireNotNull(waitForResumed<DirectionParentActivity>()) {
            "方向父页未进入 RESUMED"
        }
    }

    private fun waitForStableOrientation(activity: Activity) {
        var previous = Configuration.ORIENTATION_UNDEFINED
        var stableCount = 0
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline && stableCount < 5) {
            val current = activity.resources.configuration.orientation
            if (current == previous && current != Configuration.ORIENTATION_UNDEFINED) {
                stableCount++
            } else {
                previous = current
                stableCount = 0
            }
            SystemClock.sleep(100)
        }
        assertTrue("方向未稳定", stableCount >= 5)
    }

    private fun waitForWindowOrientation(activity: Activity, expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (
            SystemClock.uptimeMillis() < deadline &&
            windowOrientation(activity) != expected
        ) {
            SystemClock.sleep(100)
        }
    }

    private fun windowOrientation(activity: Activity): Int {
        var width = 0
        var height = 0
        instrumentation.runOnMainSync {
            width = activity.window.decorView.width
            height = activity.window.decorView.height
        }
        return when {
            width <= 0 || height <= 0 -> Configuration.ORIENTATION_UNDEFINED
            width > height -> Configuration.ORIENTATION_LANDSCAPE
            else -> Configuration.ORIENTATION_PORTRAIT
        }
    }

    private inline fun <reified T : Activity> waitForResumed(
        timeoutMillis: Long = 15_000,
    ): T? {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            var activity: T? = null
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<T>()
                    .firstOrNull()
            }
            activity?.let { return it }
            SystemClock.sleep(100)
        }
        return null
    }

    private fun finish(activity: Activity) {
        instrumentation.runOnMainSync { activity.finish() }
        instrumentation.waitForIdleSync()
    }

    private companion object {
        const val PROFILE_ARGUMENT = "legado.golden.profile"
        val BEHIND_ACTIVITIES = listOf(
            "io.legado.app.ui.about.AboutActivity",
            "io.legado.app.ui.book.source.manage.BookSourceActivity",
            "io.legado.app.ui.rss.source.manage.RssSourceActivity",
            "io.legado.app.ui.book.toc.rule.TxtTocRuleActivity",
            "io.legado.app.ui.replace.ReplaceRuleActivity",
            "io.legado.app.ui.book.manage.BookshelfManageActivity",
            "io.legado.app.ui.book.source.debug.BookSourceDebugActivity",
            "io.legado.app.ui.book.toc.TocActivity",
            "io.legado.app.ui.book.searchContent.SearchContentActivity",
        )
    }
}
