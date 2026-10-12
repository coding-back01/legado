package io.legado.app.model.dictionary

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 两个测试入口之间由验证脚本终止目标进程，验证磁盘偏好与索引。 */
@RunWith(AndroidJUnit4::class)
class DictionaryRestartTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = LocalDictionaryStore(context)
    private val state = context.getSharedPreferences("dictionary-restart-test", Context.MODE_PRIVATE)

    private suspend fun cleanupRestartState() {
        val id = state.getString("id", null) ?: return
        store.delete(id)
        store.lastDictionaryId = state.getString("previous", null)
        DictionaryQuerySettings.decode(state.getString("previousQuerySettings", null))?.let { store.querySettings = it }
        state.getStringSet("books", emptySet()).orEmpty().forEach { io.legado.app.data.appDb.bookDao.getBook(it)?.delete() }
        state.edit().clear().commit()
    }

    @Test fun prepareRestart() = runBlocking {
        cleanupRestartState()
        val folder = File(context.cacheDir, "restart-dictionary").apply { mkdirs() }
        DictionaryTestFixtures.copy(folder, "legacy.mdx")
        val previous = store.lastDictionaryId
        val dictionary = store.importFiles(folder, "legacy.mdx")
        assertTrue(state.edit().putString("id", dictionary.id).putString("previous", previous)
            .putString("previousQuerySettings", store.querySettings.encode())
            .putLong("indexModified", File(store.directory(dictionary.id), "index.db").lastModified())
            .putString("indexReady", File(store.directory(dictionary.id), "ready").readText()).commit())
        store.lastDictionaryId = dictionary.id
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "en")
            .withPrimary("en", dictionary.id).withPrimary("zh", "missing-chinese")
            .withFallbacks("en", listOf("second", "third"))
        val bookIds = linkedSetOf<String>()
        for (enabled in listOf(true, false)) {
            val book = io.legado.app.data.entities.Book(bookUrl = "instant-restart-${java.util.UUID.randomUUID()}", name = "restart-$enabled")
            book.config.dictionaryInstantLookup = enabled
            book.config.dictionaryQueryLanguage = if (enabled) "en" else null
            io.legado.app.data.appDb.bookDao.insert(book)
            bookIds.add(book.bookUrl)
            state.edit().putString(if (enabled) "enabledBook" else "disabledBook", book.bookUrl).commit()
        }
        state.edit().putStringSet("books", bookIds).commit()
        assertNotNull("准备阶段必须保存书籍标识", state.getString("enabledBook", null))
        assertNotNull("准备阶段必须保存书籍记录", io.legado.app.data.appDb.bookDao.getBook(requireNotNull(state.getString("enabledBook", null))))
        assertTrue(store.lookup(dictionary.id, "uncollected").isEmpty())
        assertEquals(dictionary.id, store.lastDictionaryId)
        folder.deleteRecursively()
        android.os.SystemClock.sleep(200)
    }

    @Test fun verifyAfterRestart() = runBlocking {
        val id = requireNotNull(state.getString("id", null))
        try {
            assertEquals(id, store.lastDictionaryId)
            assertEquals(id, store.querySettings.languages["en"]?.primaryId)
            assertEquals("missing-chinese", store.querySettings.languages["zh"]?.primaryId)
            assertEquals(listOf("second", "third"), store.querySettings.languages["en"]?.fallbackIds)
            assertEquals("en", store.querySettings.languageOverrides[id])
            val enabledBook = requireNotNull(io.legado.app.data.appDb.bookDao.getBook(requireNotNull(state.getString("enabledBook", null)) { "启用书籍标识丢失" })) { "启用书籍记录丢失" }
            val disabledBook = requireNotNull(io.legado.app.data.appDb.bookDao.getBook(requireNotNull(state.getString("disabledBook", null))))
            assertTrue(enabledBook.config.dictionaryInstantLookup)
            assertEquals("en", enabledBook.config.dictionaryQueryLanguage)
            assertFalse(disabledBook.config.dictionaryInstantLookup)
            assertNull(disabledBook.config.dictionaryQueryLanguage)
            assertEquals(state.getLong("indexModified", -1), File(store.directory(id), "index.db").lastModified())
            assertEquals(state.getString("indexReady", null), File(store.directory(id), "ready").readText())
            assertEquals("go", store.lookup(id, "go").single().headword)
            assertTrue(store.lookup(id, "学习").single().html.contains("知识"))
        } finally {
            cleanupRestartState()
        }
    }
}
