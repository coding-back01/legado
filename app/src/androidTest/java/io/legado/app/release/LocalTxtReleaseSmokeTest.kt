package io.legado.app.release

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.help.config.LocalConfig
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.putPrefBoolean
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@ReleaseSmoke
@RunWith(AndroidJUnit4::class)
class LocalTxtReleaseSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val testContext = instrumentation.context
    private var fixtureFile: File? = null
    private var fixtureBookUrl: String? = null

    @After
    fun cleanFixture() {
        fixtureBookUrl?.let { bookUrl ->
            appDb.bookChapterDao.delByBook(bookUrl)
            appDb.bookDao.getBook(bookUrl)?.delete()
        }
        fixtureFile?.delete()
    }

    @Test
    fun importsReadsTurnsPageAndReturnsToBookshelf() {
        assertTrue("发布烟测只能运行 Debug 变体", BuildConfig.DEBUG)
        prepareDisposableAppState()

        val file = File(context.cacheDir, FIXTURE_NAME).also { target ->
            testContext.assets.open(FIXTURE_ASSET).use { input ->
                target.outputStream().use(input::copyTo)
            }
        }
        fixtureFile = file

        val book = LocalBook.importFile(Uri.fromFile(file))
        fixtureBookUrl = book.bookUrl
        assertTrue("离线 TXT 未进入书架数据库", appDb.bookDao.has(book.bookUrl))

        val readIntent = Intent(context, ReadBookActivity::class.java).apply {
            putExtra("bookUrl", book.bookUrl)
            putExtra("inBookshelf", true)
        }
        ActivityScenario.launch<ReadBookActivity>(readIntent).use { scenario ->
            waitUntil("离线 TXT 正文未完成分页") {
                ReadBook.curTextChapter?.let { it.isCompleted && it.pageSize > 1 } == true
            }

            val textChapter = ReadBook.curTextChapter
            assertNotNull("阅读模型没有当前正文", textChapter)
            assertTrue(
                "正文没有离线 sentinel",
                textChapter!!.getContent().contains(SENTINEL)
            )

            val before = ReadBook.durChapterPos
            var moved = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                moved = ReadBook.moveToNextPage()
            }
            assertTrue("多页 fixture 无法执行下一页", moved)
            waitUntil("翻页后阅读进度未增加") { ReadBook.durChapterPos > before }

            scenario.onActivity {
                it.onBackPressedDispatcher.onBackPressed()
            }
            waitUntil("阅读页返回后没有销毁") {
                scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED
            }

            waitUntil("翻页进度未持久化") {
                (appDb.bookDao.getBook(book.bookUrl)?.durChapterPos ?: 0) > before
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.view_pager_main)).check(matches(isDisplayed()))
            assertTrue("返回书架后测试书不存在", appDb.bookDao.has(book.bookUrl))
        }
    }

    private fun prepareDisposableAppState() {
        LocalConfig.privacyPolicyOk = true
        LocalConfig.password = ""
        LocalConfig.versionCode = BuildConfig.VERSION_CODE.toLong()
        LocalConfig.edit()
            .putBoolean("firstOpen", false)
            .putBoolean("firstRead", false)
            .putInt("readHelpVersion", 1)
            .apply()
        context.putPrefBoolean(PreferKey.autoRefresh, false)
        context.putPrefBoolean(PreferKey.syncBookProgress, false)
        context.putPrefBoolean(PreferKey.syncBookProgressPlus, false)
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
        private const val FIXTURE_ASSET = "release-smoke-sentinel.txt"
        private const val FIXTURE_NAME = "legado_release_smoke_20260830.txt"
        private const val SENTINEL = "LEGADO_RELEASE_SMOKE_SENTINEL_20260830"
    }
}
