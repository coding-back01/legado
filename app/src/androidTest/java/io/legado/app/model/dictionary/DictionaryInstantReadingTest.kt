package io.legado.app.model.dictionary

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
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
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryInstantReadingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = LocalDictionaryStore(context)
    private val previous = store.querySettings
    private val root = File(context.cacheDir, "instant-reading-${UUID.randomUUID()}").apply { mkdirs() }
    private val books = arrayListOf<String>()
    private val ids = arrayListOf<String>()
    @After fun cleanup() = runBlocking {
        ids.forEach { store.delete(it) }; store.querySettings = previous
        books.forEach { appDb.bookChapterDao.delByBook(it); appDb.bookDao.getBook(it)?.delete() }
        root.deleteRecursively(); Unit
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (check()) return
            SystemClock.sleep(30)
        }
        assertTrue("即时词典未达到预期阅读状态", check())
    }

    private fun longPress(scenario: ActivityScenario<ReadBookActivity>, character: String) {
        val point = FloatArray(2)
        waitUntil {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.findViewById<ReadView>(R.id.read_view).curPage.textPage.lines.any {
                    it.text.contains("go 学习") && it.columns.filterIsInstance<TextColumn>().any { column -> column.charData == character }
                }
            }; ready
        }
        scenario.onActivity { activity ->
            val read = activity.findViewById<ReadView>(R.id.read_view)
            val line = read.curPage.textPage.lines.first { it.text.contains("go 学习") }
            val column = line.columns.filterIsInstance<TextColumn>().first { it.charData == character }
            val position = IntArray(2).also(read::getLocationOnScreen)
            point[0] = position[0] + (column.start + column.end) / 2
            point[1] = position[1] + read.curPage.headerHeight + (line.lineTop + line.lineBottom) / 2
        }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(750)
        }
    }

    private fun initReader() {
        LocalConfig.privacyPolicyOk = true; LocalConfig.password = ""; LocalConfig.versionCode = BuildConfig.VERSION_CODE.toLong()
        LocalConfig.edit().putBoolean("firstOpen", false).putBoolean("firstRead", false).putInt("readHelpVersion", 1).apply()
        context.putPrefBoolean(PreferKey.autoRefresh, false); context.putPrefBoolean(PreferKey.syncBookProgress, false)
        context.putPrefBoolean(PreferKey.syncBookProgressPlus, false); context.putPrefBoolean(PreferKey.textSelectAble, true)
    }

    @Test fun chineseSelectionAndDisabledOtherBookKeepIndependentModesAndCopyRemovesCard() = runBlocking {
        initReader()
        DictionaryTestFixtures.copy(root, "dictd.index", "dictd.dict.dz")
        val dictionary = store.importFiles(root, "dictd.index").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "zh").withPrimary("zh", dictionary.id)
        repeat(2) { index ->
            val file = File(root, "中文模式$index.txt").apply { writeText(("go 学习 meaning 阅读正文。\n").repeat(100)) }
            val book = LocalBook.importFile(Uri.fromFile(file)).also { books.add(it.bookUrl) }
            book.config.dictionaryInstantLookup = index == 0
            book.config.dictionaryQueryLanguage = "zh"
            appDb.bookDao.insert(book)
            ActivityScenario.launch<ReadBookActivity>(Intent(context, ReadBookActivity::class.java)
                .putExtra("bookUrl", book.bookUrl).putExtra("inBookshelf", true)).use { scenario ->
                waitUntil { ReadBook.curTextChapter?.isCompleted == true && ReadBook.book?.bookUrl == book.bookUrl }
                longPress(scenario, "学")
                if (index == 0) {
                    waitUntil {
                        var shown = false
                        scenario.onActivity { activity -> shown = activity.findViewById<TextView>(R.id.instant_dictionary_text).text.contains("知识") }
                        shown
                    }
                    scenario.onActivity { activity ->
                        fun find(view: View): TextView? {
                            if (view is TextView && view.text.toString() == context.getString(android.R.string.copy)) return view
                            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                            return null
                        }
                        requireNotNull(find(activity.textActionMenu.contentView)).performClick()
                        assertFalse(activity.findViewById<View>(R.id.instant_dictionary_card).isShown)
                    }
                } else {
                    SystemClock.sleep(250)
                    scenario.onActivity { activity -> assertFalse(activity.findViewById<View>(R.id.instant_dictionary_card).isShown) }
                }
            }
        }
    }

    @Test fun landscapeLargeFontKeepsSelectionMenuAndFullEntryReachable() = runBlocking {
        initReader()
        val previousScale = context.getPrefInt(PreferKey.fontScale)
        val previousOrientation = io.legado.app.help.config.AppConfig.screenOrientation
        context.putPrefString(PreferKey.screenOrientation, "2")
        context.putPrefBoolean(PreferKey.autoRefresh, false)
        context.putPrefInt(PreferKey.fontScale, 20)
        try {
            DictionaryTestFixtures.copy(root, "dictd.index", "dictd.dict.dz")
            val dictionary = store.importFiles(root, "dictd.index").also { ids.add(it.id) }
            store.querySettings = store.querySettings.withLanguage(dictionary.id, "en").withPrimary("en", dictionary.id)
            val file = File(root, "横屏验证.txt").apply { writeText(("go 学习 meaning 阅读正文。\n").repeat(100)) }
            val book = LocalBook.importFile(Uri.fromFile(file)).also { books.add(it.bookUrl) }
            book.config.dictionaryInstantLookup = true; book.config.dictionaryQueryLanguage = "en"
            appDb.bookDao.insert(book)
            ActivityScenario.launch<ReadBookActivity>(Intent(context, ReadBookActivity::class.java)
                .putExtra("bookUrl", book.bookUrl).putExtra("inBookshelf", true)).use { scenario ->
                scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                waitUntil { var landscape = false; scenario.onActivity { landscape = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }; landscape }
                waitUntil { ReadBook.curTextChapter?.isCompleted == true }
                longPress(scenario, "g")
                waitUntil { var card = false; scenario.onActivity { card = it.findViewById<View>(R.id.instant_dictionary_card).isShown }; card }
                scenario.onActivity { activity ->
                    val card = activity.findViewById<View>(R.id.instant_dictionary_card)
                    val full = activity.findViewById<View>(R.id.instant_dictionary_full)
                    val rect = android.graphics.Rect()
                    assertTrue(full.getGlobalVisibleRect(rect)); assertTrue(rect.width() > 0 && rect.height() > 0)
                    assertTrue(activity.textActionMenu.isShowing)
                    assertEquals("go", activity.selectedText.trim())
                    assertTrue(card.height <= activity.findViewById<ReadView>(R.id.read_view).height)
                    val cursor = activity.findViewById<View>(R.id.cursor_left)
                    val selected = android.graphics.Rect().also(cursor::getGlobalVisibleRect)
                    val bounds = android.graphics.Rect().also(card::getGlobalVisibleRect)
                    assertFalse("卡片不得遮住选区光标", android.graphics.Rect.intersects(bounds, selected))
                    full.performClick()
                }
                waitUntil { var opened = false; scenario.onActivity { opened = it.supportFragmentManager.fragments.any { fragment -> fragment is io.legado.app.ui.dict.DictDialog } }; opened }
                scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            }
        } finally { context.putPrefInt(PreferKey.fontScale, previousScale); context.putPrefString(PreferKey.screenOrientation, previousOrientation) }
        Unit
    }

    @Test fun enabledBookShowsReadingCardAndClosingDoesNotClearSelectionOrReopen() = runBlocking {
        LocalConfig.privacyPolicyOk = true; LocalConfig.password = ""; LocalConfig.versionCode = BuildConfig.VERSION_CODE.toLong()
        LocalConfig.edit().putBoolean("firstOpen", false).putBoolean("firstRead", false).putInt("readHelpVersion", 1).apply()
        context.putPrefBoolean(PreferKey.autoRefresh, false); context.putPrefBoolean(PreferKey.syncBookProgress, false)
        context.putPrefBoolean(PreferKey.syncBookProgressPlus, false); context.putPrefBoolean(PreferKey.textSelectAble, true)
        DictionaryTestFixtures.copy(root, "dictd.index", "dictd.dict.dz")
        val dictionary = store.importFiles(root, "dictd.index").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "en").withPrimary("en", dictionary.id)
        val file = File(root, "即时阅读.txt").apply { writeText(("go 学习 meaning 阅读正文。\n").repeat(100)) }
        val book = LocalBook.importFile(Uri.fromFile(file)).also { books.add(it.bookUrl) }
        book.config.dictionaryInstantLookup = true
        book.config.dictionaryQueryLanguage = null
        appDb.bookDao.insert(book)
        ActivityScenario.launch<ReadBookActivity>(Intent(context, ReadBookActivity::class.java)
            .putExtra("bookUrl", book.bookUrl).putExtra("inBookshelf", true)).use { scenario ->
            waitUntil { ReadBook.curTextChapter?.isCompleted == true && ReadBook.book?.bookUrl == book.bookUrl }
            val position = ReadBook.durChapterPos
            longPress(scenario, "g")
            waitUntil {
                var prompt = false
                scenario.onActivity { prompt = it.findViewById<TextView>(R.id.instant_dictionary_text).text.toString() == context.getString(R.string.dictionary_language_unknown) }
                prompt
            }
            scenario.onActivity { it.findViewById<View>(R.id.instant_dictionary_language).performClick() }
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(context.getString(R.string.dictionary_language_en)))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click())
            waitUntil { appDb.bookDao.getBook(book.bookUrl)?.config?.dictionaryQueryLanguage == "en" }
            waitUntil {
                var shown = false
                scenario.onActivity { activity ->
                    val viewId = context.resources.getIdentifier("instant_dictionary_text", "id", context.packageName)
                    val text = if (viewId == 0) null else activity.findViewById<TextView>(viewId)
                    shown = text?.isShown == true && text.text.contains("移动")
                }; shown
            }
            scenario.onActivity { activity ->
                assertEquals("go", activity.selectedText.trim())
                assertTrue(activity.textActionMenu.isShowing)
                val closeId = context.resources.getIdentifier("instant_dictionary_close", "id", context.packageName)
                activity.findViewById<View>(closeId).performClick()
                assertEquals("go", activity.selectedText.trim())
            }
            SystemClock.sleep(200)
            scenario.onActivity { activity ->
                val cardId = context.resources.getIdentifier("instant_dictionary_card", "id", context.packageName)
                assertFalse(activity.findViewById<View>(cardId).isShown)
            }
            assertEquals(position, ReadBook.durChapterPos)
            longPress(scenario, "g")
            waitUntil { var shown = false; scenario.onActivity { shown = it.findViewById<View>(R.id.instant_dictionary_card).isShown }; shown }
            val start = FloatArray(2)
            val target = FloatArray(2)
            scenario.onActivity { activity ->
                val read = activity.findViewById<ReadView>(R.id.read_view)
                val cursor = activity.findViewById<View>(R.id.cursor_right)
                val location = IntArray(2).also(cursor::getLocationOnScreen)
                start[0] = location[0] + cursor.width / 2f; start[1] = location[1] + cursor.height / 2f
                val line = read.curPage.textPage.lines.first { it.text.contains("go 学习") }
                val column = line.columns.filterIsInstance<TextColumn>().first { it.charData == "习" }
                val offset = IntArray(2).also(read::getLocationOnScreen)
                target[0] = offset[0] + column.end + cursor.width
                target[1] = offset[1] + read.curPage.headerHeight + (line.lineTop + line.lineBottom) / 2 + cursor.height
            }
            val down = SystemClock.uptimeMillis()
            for ((action, point) in listOf(MotionEvent.ACTION_DOWN to start, MotionEvent.ACTION_MOVE to target, MotionEvent.ACTION_UP to target)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
            }
            waitUntil { var expanded = false; scenario.onActivity { expanded = it.selectedText.contains("学习") }; expanded }
            SystemClock.sleep(200)
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.instant_dictionary_card).isShown)
                assertTrue(activity.textActionMenu.isShowing)
                assertEquals(position, ReadBook.durChapterPos)
                activity.onMenuItemSelected(R.id.menu_bookmark)
                activity.onMenuActionFinally()
                assertFalse(activity.findViewById<View>(R.id.instant_dictionary_card).isShown)
                activity.supportFragmentManager.fragments.filterIsInstance<io.legado.app.ui.book.bookmark.BookmarkDialog>().forEach { it.dismiss() }
            }
            // 销毁旧视图后不能恢复旧选区卡片，书籍开关仍然有效。
            scenario.recreate()
            waitUntil { ReadBook.book?.bookUrl == book.bookUrl }
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.instant_dictionary_card).isShown)
                assertTrue(ReadBook.book!!.config.dictionaryInstantLookup)
            }
        }
        assertTrue(requireNotNull(appDb.bookDao.getBook(book.bookUrl)).config.dictionaryInstantLookup)
    }
}
