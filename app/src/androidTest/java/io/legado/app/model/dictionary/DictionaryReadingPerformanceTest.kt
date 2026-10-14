package io.legado.app.model.dictionary

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
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
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** 固定 API 36、百万词头及真实阅读宿主；三个新进程由验证脚本逐次终止并启动。 */
@RunWith(AndroidJUnit4::class)
class DictionaryReadingPerformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = LocalDictionaryStore(context)
    private val state = context.getSharedPreferences("dictionary-reading-performance", Context.MODE_PRIVATE)

    @Test fun prepareFixture() = runBlocking {
        assertEquals(36, android.os.Build.VERSION.SDK_INT)
        val folder = File(context.cacheDir, "reading-performance-source").apply { mkdirs() }
        val definition = "<div class=\"definition\">benchmark sentinel</div>".padEnd(160, ' ').toByteArray()
        DataOutputStream(File(folder, "reading.idx").outputStream().buffered(65536)).use { index ->
            File(folder, "reading.dict").outputStream().buffered(65536).use { body ->
                repeat(1_000_000) { number ->
                    index.write(("entry" + number.toString().padStart(7, '0')).toByteArray())
                    index.writeByte(0); index.writeInt(number * 160); index.writeInt(160); body.write(definition)
                }
            }
        }
        File(folder, "reading.ifo").writeText("StarDict's dict ifo file\nversion=3.0.0\nbookname=阅读性能夹具\nwordcount=1000000\nsametypesequence=h\n")
        assertEquals(160_000_000, File(folder, "reading.dict").length())
        val previous = store.querySettings.encode()
        val start = SystemClock.elapsedRealtime()
        val dictionary = store.importFiles(folder, "reading.ifo")
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "en").withPrimary("en", dictionary.id)
        val file = File(context.filesDir, "reading-performance-book.txt").apply { writeText(("entry0000000 阅读性能验证。\n").repeat(100)) }
        val book = LocalBook.importFile(Uri.fromFile(file))
        book.config.dictionaryInstantLookup = true; book.config.dictionaryQueryLanguage = "en"
        appDb.bookDao.insert(book)
        assertTrue(state.edit().putString("dictionary", dictionary.id).putString("book", book.bookUrl)
            .putString("previous", previous).putLong("importMillis", SystemClock.elapsedRealtime() - start).commit())
        folder.deleteRecursively()
        Unit
    }

    private fun waitReady(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < end) { instrumentation.waitForIdleSync(); if (check()) return; SystemClock.sleep(20) }
        assertTrue("性能宿主没有准备好", check())
    }

    @Test fun measureReadingScreen() = runBlocking {
        assertEquals(36, android.os.Build.VERSION.SDK_INT)
        LocalConfig.privacyPolicyOk = true; LocalConfig.password = ""; LocalConfig.versionCode = BuildConfig.VERSION_CODE.toLong()
        LocalConfig.edit().putBoolean("firstOpen", false).putBoolean("firstRead", false).putInt("readHelpVersion", 1).apply()
        context.putPrefBoolean(PreferKey.autoRefresh, false); context.putPrefBoolean(PreferKey.syncBookProgress, false)
        context.putPrefBoolean(PreferKey.syncBookProgressPlus, false)
        context.putPrefString(PreferKey.screenOrientation, "1")
        val bookUrl = requireNotNull(state.getString("book", null))
        val dictionaryId = requireNotNull(state.getString("dictionary", null))
        assertTrue("性能词典必须仍然可用", store.all.any { it.id == dictionaryId && it.state == "ready" })
        store.querySettings = store.querySettings.withLanguage(dictionaryId, "en").withPrimary("en", dictionaryId).withFallbacks("en", emptyList())
        assertTrue(DictionaryQueryPlanner.plan("en", store.querySettings, store.all) is DictionaryQueryPlan.Ready)
        val cold = InstrumentationRegistry.getArguments().getString("cold") == "true"
        val limit = if (cold) 1000L else 500L
        val values = arrayListOf<Long>()
        val closeValues = arrayListOf<Long>()
        ActivityScenario.launch<ReadBookActivity>(Intent(context, ReadBookActivity::class.java)
            .putExtra("bookUrl", bookUrl).putExtra("inBookshelf", true)).use { scenario ->
            waitReady { ReadBook.curTextChapter?.isCompleted == true && ReadBook.book?.bookUrl == bookUrl }
            repeat(if (cold) 1 else 10) { number ->
                val committed = CountDownLatch(1)
                val drawn = AtomicLong(0)
                val word = "entry" + number.toString().padStart(7, '0')
                val start = SystemClock.elapsedRealtimeNanos()
                scenario.onActivity { activity ->
                    val text = activity.findViewById<TextView>(R.id.instant_dictionary_text)
                    val observer = object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            if (text.isShown && text.text.contains("benchmark sentinel")) {
                                text.viewTreeObserver.removeOnPreDrawListener(this)
                                text.viewTreeObserver.registerFrameCommitCallback {
                                    drawn.set(SystemClock.elapsedRealtimeNanos()); committed.countDown()
                                }
                            }
                            return true
                        }
                    }
                    text.viewTreeObserver.addOnPreDrawListener(observer)
                    activity.onStableDictionarySelection(word, number.toLong() + 100)
                }
                val received = committed.await(5, TimeUnit.SECONDS)
                if (!received) scenario.onActivity { activity ->
                    val text = activity.findViewById<TextView>(R.id.instant_dictionary_text)
                    println("PERFORMANCE_DRAW_STATE: shown=${text.isShown},hw=${text.isHardwareAccelerated},text=${text.text},size=${text.width}x${text.height}")
                }
                assertTrue("未捕获真实绘制提交", received)
                val elapsed = (drawn.get() - start) / 1_000_000
                values.add(elapsed)
                scenario.onActivity { it.findViewById<View>(R.id.instant_dictionary_close).performClick() }
                assertTrue("首屏 $elapsed 毫秒，超过固定 $limit 毫秒门槛", elapsed <= limit)
            }
            repeat(5) { number ->
                scenario.onActivity { activity ->
                    activity.onStableDictionarySelection("entry0999999", number.toLong() + 1000)
                    val start = SystemClock.elapsedRealtimeNanos()
                    activity.findViewById<View>(R.id.instant_dictionary_close).performClick()
                    closeValues.add((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000)
                    assertFalse(activity.findViewById<View>(R.id.instant_dictionary_card).isShown)
                }
            }
            SystemClock.sleep(200)
            scenario.onActivity { assertFalse(it.findViewById<View>(R.id.instant_dictionary_card).isShown) }
        }
        assertTrue(closeValues.all { it <= 150 })
        val report = "{\"cold\":$cold,\"screenMillis\":$values,\"closeMillis\":$closeValues,\"api\":36}"
        File(context.filesDir, "reading-performance-${if (cold) "cold" else "warm"}.json").writeText(report)
        println("ReadingPerformance: $report")
        Unit
    }

    @Test fun cleanupFixture() = runBlocking {
        state.getString("dictionary", null)?.let { store.delete(it) }
        state.getString("book", null)?.let { url -> appDb.bookChapterDao.delByBook(url); appDb.bookDao.getBook(url)?.delete() }
        DictionaryQuerySettings.decode(state.getString("previous", null))?.let { store.querySettings = it }
        File(context.filesDir, "reading-performance-book.txt").delete()
        state.edit().clear().commit()
        Unit
    }

    @Test fun longArticlePreprocessingDoesNotBlockCloseOrSourceSwitch() = runBlocking {
        assertEquals(36, android.os.Build.VERSION.SDK_INT)
        val previous = store.querySettings
        val root = File(context.cacheDir, "pending-article-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val ids = arrayListOf<String>()
        var bookUrl: String? = null
        val closeMillis = AtomicLong()
        val switchMillis = AtomicLong()
        fun preprocessing(): Boolean = Thread.getAllStackTraces().values.any { frames ->
            frames.any { it.className.startsWith("org.jsoup.") } &&
                frames.any { it.className.contains("DictionaryDisplayProcessor") || it.className.contains("DictionarySummaries") }
        }
        fun waitForPreprocessing() {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (preprocessing()) return
                SystemClock.sleep(1)
            }
            fail("必须先观测到真实长正文预处理，不能把尚未开始的查询当作测量")
        }
        try {
            File(root, "long.xdxf").bufferedWriter().use { out ->
                out.write("<xdxf lang_from=\"en\"><full_name>长正文响应夹具</full_name><ar><k>long</k><def>")
                repeat(60_000) { index ->
                    if (index % 2 == 0) out.write("<span>")
                    out.write("long article paragraph with a known definition sentinel ")
                    if (index % 2 == 1) out.write("</span>")
                }
                out.write("</def></ar></xdxf>")
            }
            val long = store.importFiles(root, "long.xdxf").also { ids.add(it.id) }
            val longEntry = store.lookup(long.id, "long", "en").single()
            assertTrue("测量正文必须处于既有 HTML 标签限额内", longEntry.html.count { it == '<' } <= 100_000)
            File(root, "short.xdxf").writeText("<xdxf lang_from=\"en\"><full_name>短词条切换夹具</full_name><ar><k>long</k><def>short response sentinel</def></ar></xdxf>")
            val short = store.importFiles(root, "short.xdxf").also { ids.add(it.id) }
            store.querySettings = store.querySettings.withLanguage(long.id, "en").withLanguage(short.id, "en").withPrimary("en", long.id)
                .withFallbacks("en", emptyList())
            LocalConfig.privacyPolicyOk = true; LocalConfig.password = ""; LocalConfig.versionCode = BuildConfig.VERSION_CODE.toLong()
            LocalConfig.edit().putBoolean("firstOpen", false).putBoolean("firstRead", false).putInt("readHelpVersion", 1).apply()
            context.putPrefBoolean(PreferKey.autoRefresh, false); context.putPrefBoolean(PreferKey.syncBookProgress, false)
            context.putPrefBoolean(PreferKey.syncBookProgressPlus, false); context.putPrefString(PreferKey.screenOrientation, "1")
            val book = LocalBook.importFile(Uri.fromFile(File(root, "长正文响应.txt").apply { writeText("long 阅读正文。\n".repeat(100)) }))
            bookUrl = book.bookUrl
            book.config.dictionaryInstantLookup = true; book.config.dictionaryQueryLanguage = "en"; appDb.bookDao.insert(book)
            ActivityScenario.launch<ReadBookActivity>(Intent(context, ReadBookActivity::class.java)
                .putExtra("bookUrl", book.bookUrl).putExtra("inBookshelf", true)).use { scenario ->
                waitReady { ReadBook.curTextChapter?.isCompleted == true && ReadBook.book?.bookUrl == book.bookUrl }
                scenario.onActivity { it.onStableDictionarySelection("long", 5000) }
                waitForPreprocessing()
                val start = SystemClock.elapsedRealtimeNanos()
                scenario.onActivity { activity ->
                    assertEquals(context.getString(R.string.loading), activity.findViewById<TextView>(R.id.instant_dictionary_text).text.toString())
                    activity.findViewById<View>(R.id.instant_dictionary_close).performClick()
                    assertFalse(activity.findViewById<View>(R.id.instant_dictionary_card).isShown)
                }
                closeMillis.set((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000)
                assertTrue("长正文关闭超出固定门槛", closeMillis.get() <= 150)
                waitReady { !preprocessing() }
                scenario.onActivity { io.legado.app.ui.dict.DictDialog("long", "en", long.id).show(it.supportFragmentManager, "pending") }
                waitReady {
                    var ready = false
                    scenario.onActivity { activity ->
                        val dialog = activity.supportFragmentManager.fragments.filterIsInstance<io.legado.app.ui.dict.DictDialog>().lastOrNull()
                        ready = dialog?.view != null && io.legado.app.ui.dict.DictDialog::class.java
                            .getDeclaredField("currentDisplay").apply { isAccessible = true }.get(dialog) != null
                    }; ready
                }
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.fragments.filterIsInstance<io.legado.app.ui.dict.DictDialog>().last()
                        .requireView().findViewById<View>(R.id.btn_dictionary).performClick()
                }
                val row = java.util.concurrent.atomic.AtomicReference<View>()
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(short.name))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check { view, error ->
                        if (error != null) throw error
                        row.set(requireNotNull(view))
                    }
                // 先准备可操作的来源列表，再用无展示缓存的实际查询进入长正文处理。
                // 不人为暂停或延长工作；计时仍从用户来源选择开始，包含主线程排队。
                scenario.onActivity { activity ->
                    val dialog = activity.supportFragmentManager.fragments.filterIsInstance<io.legado.app.ui.dict.DictDialog>().last()
                    val model = androidx.lifecycle.ViewModelProvider(dialog)[io.legado.app.ui.dict.DictViewModel::class.java]
                    io.legado.app.ui.dict.DictViewModel::class.java.getDeclaredField("displayProcessor")
                        .apply { isAccessible = true }.set(model, DictionaryDisplayProcessor())
                    io.legado.app.ui.dict.DictDialog::class.java.getDeclaredMethod("query", String::class.java, java.lang.Long::class.java)
                        .apply { isAccessible = true }.invoke(dialog, "long", null)
                }
                waitForPreprocessing()
                val switchStart = SystemClock.elapsedRealtimeNanos()
                instrumentation.runOnMainSync {
                    assertTrue("切换瞬间必须仍有实际长正文预处理", preprocessing())
                    val view = row.get()
                    assertTrue(view.isShown)
                    val list = generateSequence(view.parent) { it.parent }.filterIsInstance<android.widget.AdapterView<*>>().first()
                    val position = list.getPositionForView(view)
                    assertTrue(position >= 0)
                    assertTrue(list.performItemClick(view, position, list.getItemIdAtPosition(position)))
                }
                switchMillis.set((SystemClock.elapsedRealtimeNanos() - switchStart) / 1_000_000)
                assertTrue("长正文切换超出固定门槛", switchMillis.get() <= 150)
                waitReady {
                    var fresh = false
                    scenario.onActivity { activity ->
                        val dialog = activity.supportFragmentManager.fragments.filterIsInstance<io.legado.app.ui.dict.DictDialog>().last()
                        fresh = dialog.requireView().findViewById<TextView>(R.id.tv_dict).text.contains("short response sentinel")
                        assertFalse(activity.findViewById<View>(R.id.instant_dictionary_card).isShown)
                    }; fresh
                }
                SystemClock.sleep(500)
                scenario.onActivity { activity ->
                    val dialog = activity.supportFragmentManager.fragments.filterIsInstance<io.legado.app.ui.dict.DictDialog>().last()
                    assertTrue(dialog.requireView().findViewById<TextView>(R.id.tv_dict).text.contains("short response sentinel"))
                    assertEquals(long.id, store.querySettings.languages["en"]?.primaryId)
                }
            }
            val report = "{\"api\":36,\"paragraphs\":60000,\"closeMillis\":${closeMillis.get()},\"switchMillis\":${switchMillis.get()},\"observedPreprocessing\":true}"
            File(context.filesDir, "pending-article-performance.json").writeText(report)
            println("PendingArticlePerformance: $report")
        } finally {
            ids.forEach { store.delete(it) }; store.querySettings = previous
            bookUrl?.let { appDb.bookChapterDao.delByBook(it); appDb.bookDao.getBook(it)?.delete() }
            root.deleteRecursively()
        }
        Unit
    }
}
