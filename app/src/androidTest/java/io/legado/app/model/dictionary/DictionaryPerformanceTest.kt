package io.legado.app.model.dictionary

import android.os.Debug
import androidx.test.core.app.ActivityScenario
import io.legado.app.ui.dict.DictDialog
import io.legado.app.ui.dict.local.LocalDictionaryActivity
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class DictionaryPerformanceTest {
    @Test fun millionHeadwordsUseBoundedMemoryAndIndexedQueries() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "dictionary-performance-${UUID.randomUUID()}").apply { mkdirs() }
        val store = LocalDictionaryStore(context)
        var id: String? = null
        try {
            val count = 1_000_000
            val definition = "<p>benchmark sentinel</p>".padEnd(160, ' ').toByteArray()
            val body = File(folder, "large.dict")
            DataOutputStream(File(folder, "large.idx").outputStream().buffered(64 * 1024)).use { index ->
                body.outputStream().buffered(64 * 1024).use { data ->
                    repeat(count) { n ->
                        index.write(("entry" + n.toString().padStart(7, '0')).toByteArray())
                        index.writeByte(0)
                        index.writeInt(n * definition.size)
                        index.writeInt(definition.size)
                        data.write(definition)
                    }
                }
            }
            File(folder, "large.ifo").writeText("StarDict's dict ifo file\nversion=3.0.0\nbookname=性能夹具\nwordcount=$count\nsametypesequence=h\n")
            val before = SystemClock.elapsedRealtime()
            val sampling = AtomicBoolean(true)
            val peakPss = AtomicLong(Debug.getPss())
            val sampler = Thread {
                while (sampling.get()) {
                    val sample = Debug.getPss()
                    peakPss.updateAndGet { previous -> maxOf(previous, sample) }
                    SystemClock.sleep(50)
                }
            }.apply { start() }
            val dictionary = try {
                store.importFiles(folder, "large.ifo")
            } finally {
                sampling.set(false)
                sampler.join()
            }
            id = dictionary.id
            val importMillis = SystemClock.elapsedRealtime() - before
            assertEquals(count.toLong(), dictionary.entryCount)
            val cold = ArrayList<Long>()
            val warm = ArrayList<Long>()
            for (n in listOf(0, 100, 50_000, 500_000, 900_000, 999_999)) {
                val word = "entry" + n.toString().padStart(7, '0')
                val start = SystemClock.elapsedRealtime()
                val result = store.lookup(dictionary.id, word)
                cold.add(SystemClock.elapsedRealtime() - start)
                assertEquals(word, result.single().headword)
                assertTrue(result.single().html.contains("benchmark sentinel"))
                val next = SystemClock.elapsedRealtime()
                store.lookup(dictionary.id, word)
                warm.add(SystemClock.elapsedRealtime() - next)
            }
            val firstScreen = arrayListOf<Long>()
            val previous = store.lastDictionaryId
            store.lastDictionaryId = dictionary.id
            try {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
                    for (number in listOf(0, 500_000, 999_999)) {
                        val word = "entry" + number.toString().padStart(7, '0')
                        val dialog = DictDialog(word, dictionaryId = dictionary.id)
                        val start = SystemClock.elapsedRealtime()
                        scenario.onActivity { dialog.show(it.supportFragmentManager, "performance-card") }
                        val rendered = AtomicBoolean(false)
                        var requested = false
                        while (SystemClock.elapsedRealtime() - start < 5_000) {
                            instrumentation.waitForIdleSync()
                            scenario.onActivity {
                                val web = dialog.view?.findViewById<android.webkit.WebView>(io.legado.app.R.id.web_view)
                                if (web != null && web.isShown && web.progress == 100 && web.contentHeight > 0 && !requested) {
                                    requested = true
                                    if (android.os.Build.VERSION.SDK_INT >= 23) {
                                        web.postVisualStateCallback(1, object : android.webkit.WebView.VisualStateCallback() {
                                            override fun onComplete(requestId: Long) { rendered.set(true) }
                                        })
                                    } else rendered.set(true)
                                }
                            }
                            if (rendered.get()) break
                            SystemClock.sleep(25)
                        }
                        firstScreen.add(SystemClock.elapsedRealtime() - start)
                        assertTrue("词典首屏超过预先固定的 5 秒阈值", rendered.get())
                        scenario.onActivity { dialog.dismiss() }
                        instrumentation.waitForIdleSync()
                    }
                }
            } finally {
                store.lastDictionaryId = previous
            }
            val diskBytes = store.directory(dictionary.id).walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val pssKiB = maxOf(peakPss.get(), Debug.getPss())
            val report = """
                {"api":${android.os.Build.VERSION.SDK_INT},"entries":$count,"sourceBytes":${body.length()},"importMillis":$importMillis,"coldMillis":${cold},"warmMillis":${warm},"firstScreenMillis":${firstScreen},"pssKiB":$pssKiB,"diskBytes":$diskBytes}
            """.trimIndent()
            File(context.getExternalFilesDir(null) ?: context.filesDir, "dictionary-performance.json").writeText(report)
            println("DictionaryPerformance: $report")
            assertTrue("导入超过预先固定的 15 分钟阈值", importMillis < 900_000)
            assertTrue("冷查询超过 500 毫秒阈值", cold.maxOrNull().orZero() < 500)
            assertTrue("缓存查询超过 100 毫秒阈值", warm.maxOrNull().orZero() < 100)
            assertTrue("驻留内存超过 512 MiB 阈值", pssKiB < 512 * 1024)
            assertTrue("词库磁盘占用超过 2 GiB 阈值", diskBytes < 2L * 1024 * 1024 * 1024)
        } finally {
            id?.let { store.delete(it) }
            folder.deleteRecursively()
        }
    }

    private fun Long?.orZero(): Long = this ?: 0
}
