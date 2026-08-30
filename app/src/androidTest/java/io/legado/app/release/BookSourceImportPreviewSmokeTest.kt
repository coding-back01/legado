package io.legado.app.release

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.ui.association.FileAssociationActivity
import io.legado.app.ui.association.ImportBookSourceDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@ReleaseSmoke
@RunWith(AndroidJUnit4::class)
class BookSourceImportPreviewSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun opensOfflinePreviewThenCancelsWithoutImporting() {
        assertTrue("发布烟测只能运行 Debug 变体", BuildConfig.DEBUG)
        val targetContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val beforeCount = appDb.bookSourceDao.allCount()
        assertFalse("测试书源不应预先存在", appDb.bookSourceDao.has(SOURCE_URL))

        try {
            targetContext.startActivity(
                Intent().apply {
                    setClassName(
                        testContext.packageName,
                        ReleaseSmokeFixtureDispatchActivity::class.java.name
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            waitUntil("导入预览未显示测试书源") {
                isPreviewDisplayed()
            }
            clickPreviewCancel()
            waitUntil("取消预览后导入页没有关闭") {
                !isFileAssociationResumed()
            }

            assertEquals("取消预览后书源总数发生变化", beforeCount, appDb.bookSourceDao.allCount())
            assertFalse("取消预览仍导入了测试书源", appDb.bookSourceDao.has(SOURCE_URL))
        } finally {
            appDb.bookSourceDao.delete(SOURCE_URL)
        }
    }

    private fun isPreviewDisplayed(): Boolean {
        var displayed = false
        instrumentation.runOnMainSync {
            displayed = resumedFileAssociation()
                ?.supportFragmentManager
                ?.fragments
                ?.filterIsInstance<ImportBookSourceDialog>()
                ?.firstOrNull()
                ?.view
                ?.containsText(SOURCE_NAME) == true
        }
        return displayed
    }

    private fun clickPreviewCancel() {
        var clicked = false
        instrumentation.runOnMainSync {
            val dialog = resumedFileAssociation()
                ?.supportFragmentManager
                ?.fragments
                ?.filterIsInstance<ImportBookSourceDialog>()
                ?.firstOrNull()
            val cancel = dialog?.view?.findViewById<View>(R.id.tv_cancel)
            assertTrue("预览取消按钮不可见", cancel?.isShown == true)
            cancel!!.performClick()
            clicked = true
        }
        assertTrue("未执行预览取消", clicked)
    }

    private fun isFileAssociationResumed(): Boolean {
        var resumed = false
        instrumentation.runOnMainSync {
            resumed = resumedFileAssociation() != null
        }
        return resumed
    }

    private fun resumedFileAssociation(): FileAssociationActivity? =
        ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<FileAssociationActivity>()
            .firstOrNull()

    private fun View.containsText(expected: String): Boolean {
        if (this is TextView && text.toString() == expected) return true
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                if (getChildAt(index).containsText(expected)) return true
            }
        }
        return false
    }

    private fun waitUntil(
        description: String,
        timeoutMillis: Long = 30_000,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(description, condition())
    }

    companion object {
        private const val SOURCE_URL = "release-smoke://20260830"
        private const val SOURCE_NAME = "烟测源-20260830"
    }
}
