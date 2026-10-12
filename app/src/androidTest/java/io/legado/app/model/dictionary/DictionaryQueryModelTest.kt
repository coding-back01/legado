package io.legado.app.model.dictionary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.ui.dict.DictResult
import io.legado.app.ui.dict.DictViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DictionaryQueryModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = LocalDictionaryStore(context)
    private val previous = store.querySettings
    private val root = File(context.cacheDir, "query-model-${UUID.randomUUID()}").apply { mkdirs() }
    private val ids = arrayListOf<String>()
    @After fun cleanup() = runBlocking { ids.forEach { store.delete(it) }; store.querySettings = previous; root.deleteRecursively(); Unit }

    @Test fun actualViewModelQueriesConfiguredFallbackAndLabelsSourceWithoutChangingPrimary() = runBlocking {
        DictionaryTestFixtures.copy(root, "optional.dsl", "sample.mdx")
        val primary = store.importFiles(root, "optional.dsl").also { ids.add(it.id) }
        val fallback = store.importFiles(root, "sample.mdx").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(primary.id, "en").withLanguage(fallback.id, "en")
            .withPrimary("en", primary.id).withFallbacks("en", listOf(fallback.id))
        val finished = CountDownLatch(1)
        val result = AtomicReference<DictResult>()
        val error = AtomicReference<Throwable>()
        lateinit var model: DictViewModel
        instrumentation.runOnMainSync {
            model = DictViewModel(context.applicationContext as android.app.Application)
            model.lookupLocal("go", "en", "book", 1, 1, { result.set(it); finished.countDown() }, { error.set(it); finished.countDown() })
        }
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        assertNull(error.get())
        assertEquals(fallback.id, result.get()?.source?.id)
        assertEquals("go", result.get()?.entries?.single()?.headword)
        assertNotNull(result.get()?.display)
        assertEquals(result.get()?.entries?.single(), result.get()?.display?.previews?.single()?.entry)
        assertEquals(primary.id, store.querySettings.languages["en"]?.primaryId)
        instrumentation.runOnMainSync { model.cancelQuery() }
    }

    @Test fun supersededCancelledOrClearedViewModelNeverSubmitsLateResults() = runBlocking {
        DictionaryTestFixtures.copy(root, "optional.dsl")
        val dictionary = store.importFiles(root, "optional.dsl").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "en").withPrimary("en", dictionary.id)
        for (action in listOf("replace", "cancel", "clear")) {
            val started = CountDownLatch(1)
            val returned = CountDownLatch(1)
            val callbacks = java.util.Collections.synchronizedList(arrayListOf<String>())
            lateinit var model: DictViewModel
            val owner = androidx.lifecycle.ViewModelStore()
            instrumentation.runOnMainSync {
                model = DictViewModel(context.applicationContext as android.app.Application) { _, word, _ ->
                    if (word == "old") {
                        started.countDown()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.delay(250) }
                        returned.countDown()
                    }
                    listOf(DictionaryEntry(1, word, "definition"))
                }
                owner.put("dict", model)
                model.lookupLocal("old", "en", "book-a", 1, 1, { callbacks.add("old") }, { callbacks.add("old-error") })
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val fresh = CountDownLatch(1)
            instrumentation.runOnMainSync {
                when (action) {
                    "replace" -> model.lookupLocal("new", "en", "book-b", 2, 2,
                        { callbacks.add("new"); fresh.countDown() }, { callbacks.add("new-error"); fresh.countDown() })
                    "cancel" -> model.cancelQuery()
                    else -> owner.clear()
                }
            }
            assertTrue(returned.await(5, TimeUnit.SECONDS))
            if (action == "replace") assertTrue(fresh.await(5, TimeUnit.SECONDS))
            android.os.SystemClock.sleep(100)
            instrumentation.waitForIdleSync()
            assertEquals(action, if (action == "replace") listOf("new") else emptyList<String>(), callbacks.toList())
            instrumentation.runOnMainSync { owner.clear() }
        }
    }

    @Test fun localMissDoesNotCallOnlineRulesAndExplicitOnlineRequestStillWorks() = runBlocking {
        DictionaryTestFixtures.copy(root, "sample.mdx")
        val dictionary = store.importFiles(root, "sample.mdx").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "en").withPrimary("en", dictionary.id)
            .withFallbacks("en", emptyList())
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val server = java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    requests.incrementAndGet()
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val body = "online result".toByteArray()
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
                }
            } catch (_: java.io.IOException) { }
        }.apply { isDaemon = true; start() }
        val rule = io.legado.app.data.entities.DictRule("query-model-${UUID.randomUUID()}", "http://127.0.0.1:${server.localPort}/", "")
        io.legado.app.data.appDb.dictRuleDao.insert(rule)
        lateinit var model: DictViewModel
        try {
            val offline = CountDownLatch(1)
            val result = AtomicReference<DictResult>()
            instrumentation.runOnMainSync {
                model = DictViewModel(context.applicationContext as android.app.Application)
                model.lookupLocal("uncollected", "en", "book", 1, 1, { result.set(it); offline.countDown() }, { offline.countDown() })
            }
            assertTrue(offline.await(10, TimeUnit.SECONDS))
            assertTrue("本地未收录实际结果：${result.get()?.lookup}", result.get()?.lookup is DictionaryLookupResult.NotFound)
            assertEquals(0, requests.get())
            val online = CountDownLatch(1)
            instrumentation.runOnMainSync {
                model.dict(io.legado.app.ui.dict.DictSource(online = rule), "word",
                    { result.set(it); online.countDown() }, { online.countDown() })
            }
            assertTrue(online.await(10, TimeUnit.SECONDS))
            assertEquals("online result", result.get()?.onlineHtml)
            assertEquals(1, requests.get())
            assertEquals(dictionary.id, store.querySettings.languages["en"]?.primaryId)
        } finally {
            server.close(); worker.join(1000)
            io.legado.app.data.appDb.dictRuleDao.delete(rule)
        }
    }
}
