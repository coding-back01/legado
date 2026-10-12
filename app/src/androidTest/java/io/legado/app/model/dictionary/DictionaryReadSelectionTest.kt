package io.legado.app.model.dictionary

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import io.legado.app.ui.dict.DictDialog
import io.legado.app.utils.putPrefBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryReadSelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "selection-${UUID.randomUUID()}").apply { mkdirs() }
    private val books = arrayListOf<String>()
    private val store = LocalDictionaryStore(context)
    private var dictionaryId: String? = null
    private val previousSettings = store.querySettings
    private val previous = store.lastDictionaryId

    @After fun cleanup() = runBlocking {
        dictionaryId?.let { store.delete(it) }
        store.lastDictionaryId = previous
        store.querySettings = previousSettings
        books.forEach { appDb.bookChapterDao.delByBook(it); appDb.bookDao.getBook(it)?.delete() }
        root.deleteRecursively()
        Unit
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("阅读选词没有在期限内达到预期状态", condition())
    }

    private fun event(action: Int, x: Float, y: Float, down: Long) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    private fun longPress(scenario: ActivityScenario<ReadBookActivity>) {
        val point = FloatArray(2)
        waitUntil {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.findViewById<ReadView>(R.id.read_view).curPage.textPage.lines.any {
                    it.text.contains("go 学习") && it.columns.filterIsInstance<TextColumn>().any { column -> column.charData == "g" }
                }
            }; ready
        }
        scenario.onActivity { activity ->
            val read = activity.findViewById<ReadView>(R.id.read_view)
            val line = read.curPage.textPage.lines.first { it.text.contains("go 学习") }
            val column = line.columns.filterIsInstance<TextColumn>().first { it.charData == "g" }
            val location = IntArray(2).also(read::getLocationOnScreen)
            point[0] = location[0] + (column.start + column.end) / 2
            point[1] = location[1] + read.curPage.headerHeight + (line.lineTop + line.lineBottom) / 2
        }
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point[0], point[1], down)
        SystemClock.sleep(750)
        event(MotionEvent.ACTION_UP, point[0], point[1], down)
        waitUntil { var selected = false; scenario.onActivity { selected = it.selectedText.trim() == "go" }; selected }
    }

    private fun menuText(root: View, title: String): TextView? {
        if (root is TextView && root.text.toString() == title) return root
        if (root is ViewGroup) for (index in 0 until root.childCount) menuText(root.getChildAt(index), title)?.let { return it }
        return null
    }

    @Test fun longPressDragAndCopyStayManualAndDictionaryUsesSameSelectionAcrossBooks() = runBlocking {
        assertTrue(BuildConfig.DEBUG)
        LocalConfig.privacyPolicyOk = true
        LocalConfig.password = ""
        LocalConfig.versionCode = BuildConfig.VERSION_CODE.toLong()
        LocalConfig.edit().putBoolean("firstOpen", false).putBoolean("firstRead", false).putInt("readHelpVersion", 1).apply()
        context.putPrefBoolean(PreferKey.autoRefresh, false)
        context.putPrefBoolean(PreferKey.syncBookProgress, false)
        context.putPrefBoolean(PreferKey.syncBookProgressPlus, false)
        context.putPrefBoolean(PreferKey.textSelectAble, true)
        DictionaryTestFixtures.copy(root, "legacy.mdx")
        val dictionary = store.importFiles(root, "legacy.mdx")
        dictionaryId = dictionary.id
        store.lastDictionaryId = dictionary.id
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "en").withPrimary("en", dictionary.id)
        repeat(2) { number ->
            val file = File(root, "选词验证$number.txt").apply { writeText(("go 学习 meaning 阅读正文。\n").repeat(100)) }
            val book = LocalBook.importFile(Uri.fromFile(file))
            books.add(book.bookUrl)
            book.config.dictionaryQueryLanguage = "en"
            appDb.bookDao.insert(book)
            val intent = Intent(context, ReadBookActivity::class.java).putExtra("bookUrl", book.bookUrl).putExtra("inBookshelf", true)
            ActivityScenario.launch<ReadBookActivity>(intent).use { scenario ->
                waitUntil { ReadBook.curTextChapter?.isCompleted == true && ReadBook.book?.bookUrl == book.bookUrl }
                val position = ReadBook.durChapterPos
                longPress(scenario)
                scenario.onActivity { assertTrue(it.supportFragmentManager.fragments.none { fragment -> fragment is DictDialog }) }
                if (number == 0) {
                    val start = FloatArray(2)
                    val target = FloatArray(2)
                    scenario.onActivity { activity ->
                        val read = activity.findViewById<ReadView>(R.id.read_view)
                        val cursor = activity.findViewById<View>(R.id.cursor_right)
                        val location = IntArray(2).also(cursor::getLocationOnScreen)
                        start[0] = location[0] + cursor.width / 2f
                        start[1] = location[1] + cursor.height / 2f
                        val line = read.curPage.textPage.lines.first { it.text.contains("go 学习") }
                        val column = line.columns.filterIsInstance<TextColumn>().first { it.charData == "习" }
                        target[0] = column.end + cursor.width
                        target[1] = read.curPage.headerHeight + (line.lineTop + line.lineBottom) / 2 + cursor.height
                    }
                    val down = SystemClock.uptimeMillis()
                    event(MotionEvent.ACTION_DOWN, start[0], start[1], down)
                    event(MotionEvent.ACTION_MOVE, target[0], target[1], down)
                    event(MotionEvent.ACTION_UP, target[0], target[1], down)
                    waitUntil { var expanded = false; scenario.onActivity { expanded = it.selectedText.contains("学习") }; expanded }
                    scenario.onActivity { activity ->
                        assertTrue(activity.supportFragmentManager.fragments.none { it is DictDialog })
                        val selected = activity.selectedText
                        val copy = requireNotNull(menuText(activity.textActionMenu.contentView, context.getString(android.R.string.copy)))
                        copy.performClick()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        assertEquals(selected, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                        assertTrue(activity.supportFragmentManager.fragments.none { it is DictDialog })
                    }
                    longPress(scenario)
                }
                scenario.onActivity { activity ->
                    val dictionaryAction = requireNotNull(menuText(activity.textActionMenu.contentView, context.getString(R.string.dict)))
                    dictionaryAction.performClick()
                }
                waitUntil { var shown = false; scenario.onActivity { shown = it.supportFragmentManager.fragments.any { it is DictDialog && it.view != null } }; shown }
                scenario.onActivity { activity ->
                    val dialog = activity.supportFragmentManager.fragments.filterIsInstance<DictDialog>().single()
                    assertEquals("go", dialog.arguments?.getString("word")?.trim())
                    dialog.dismiss()
                }
                assertEquals(position, ReadBook.durChapterPos)
                assertEquals(dictionary.id, store.lastDictionaryId)
            }
        }
    }
}
