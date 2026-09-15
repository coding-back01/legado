package io.legado.app.quality

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.help.LauncherIconHelp
import io.legado.app.ui.welcome.Launcher1
import io.legado.app.ui.welcome.Launcher2
import io.legado.app.ui.welcome.Launcher3
import io.legado.app.ui.welcome.Launcher4
import io.legado.app.ui.welcome.Launcher5
import io.legado.app.ui.welcome.Launcher6
import io.legado.app.ui.welcome.WelcomeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthorizedDebugDeviceSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageManager = context.packageManager

    @Test
    fun allLauncherEntriesResolveAndOriginalStateIsRestored() {
        assumeTrue("launcher 动态切换需要 API 26+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assertEquals(DEBUG_PACKAGE, context.packageName)

        val originalStates = LAUNCHER_CHOICES.associate { choice ->
            choice.component to packageManager.getComponentEnabledSetting(choice.component)
        }
        try {
            LAUNCHER_CHOICES.forEach { choice ->
                LauncherIconHelp.changeIcon(choice.preferenceValue)
                instrumentation.waitForIdleSync()

                LAUNCHER_CHOICES.forEach { candidate ->
                    assertEquals(
                        "launcher 状态不符合选择值 ${choice.preferenceValue}: ${candidate.component}",
                        if (candidate == choice) {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        } else {
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        },
                        packageManager.getComponentEnabledSetting(candidate.component),
                    )
                }

                val resolved = waitForLauncherResolution(choice.component.className)
                assertEquals(setOf(choice.component.className), resolved)
                assertEquals(
                    choice.component.className,
                    packageManager.getLaunchIntentForPackage(DEBUG_PACKAGE)?.component?.className,
                )
            }
        } finally {
            originalStates.forEach { (component, state) ->
                packageManager.setComponentEnabledSetting(
                    component,
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun notificationUsingReadBookIconIsPostedAndRemoved() {
        assumeTrue("活动通知查询需要 API 23+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        assertEquals(DEBUG_PACKAGE, context.packageName)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(
            "朗读通知渠道应由 Debug Application 创建",
            notificationManager.getNotificationChannel(AppConst.channelIdReadAloud),
        )
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.icon_read_book)
        assertNotNull("icon_read_book 无法解码", largeIcon)
        assertEquals(200, largeIcon.width)
        assertEquals(200, largeIcon.height)

        val notification = NotificationCompat.Builder(context, AppConst.channelIdReadAloud)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setLargeIcon(largeIcon)
            .setContentTitle("Android lint Debug 真机烟测")
            .setContentText("通知会在断言结束后立即撤销")
            .setOnlyAlertOnce(true)
            .build()
        assertNotNull("通知未携带大图标", notification.getLargeIcon())

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (
                SystemClock.uptimeMillis() < deadline &&
                notificationManager.activeNotifications.none { it.id == NOTIFICATION_ID }
            ) {
                SystemClock.sleep(100)
            }
            assertTrue(
                "Debug 通知未进入活动通知列表",
                notificationManager.activeNotifications.any { it.id == NOTIFICATION_ID },
            )
        } finally {
            notificationManager.cancel(NOTIFICATION_ID)
            largeIcon.recycle()
        }
    }

    private fun waitForLauncherResolution(expectedClassName: String): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(DEBUG_PACKAGE)
        val deadline = SystemClock.uptimeMillis() + 5_000
        var resolved = emptySet<String>()
        do {
            @Suppress("DEPRECATION")
            resolved = packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.name }
                .toSet()
            if (resolved == setOf(expectedClassName)) return resolved
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        return resolved
    }

    private data class LauncherChoice(
        val preferenceValue: String,
        val component: ComponentName,
    )

    private companion object {
        const val DEBUG_PACKAGE = "io.legado.app.debug"
        const val NOTIFICATION_ID = 0x1E6AD0

        val LAUNCHER_CHOICES = listOf(
            LauncherChoice(
                "ic_launcher",
                ComponentName(DEBUG_PACKAGE, WelcomeActivity::class.java.name),
            ),
            LauncherChoice("launcher1", ComponentName(DEBUG_PACKAGE, Launcher1::class.java.name)),
            LauncherChoice("launcher2", ComponentName(DEBUG_PACKAGE, Launcher2::class.java.name)),
            LauncherChoice("launcher3", ComponentName(DEBUG_PACKAGE, Launcher3::class.java.name)),
            LauncherChoice("launcher4", ComponentName(DEBUG_PACKAGE, Launcher4::class.java.name)),
            LauncherChoice("launcher5", ComponentName(DEBUG_PACKAGE, Launcher5::class.java.name)),
            LauncherChoice("launcher6", ComponentName(DEBUG_PACKAGE, Launcher6::class.java.name)),
        )
    }
}
