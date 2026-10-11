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

    @Test fun prepareRestart() = runBlocking {
        val folder = File(context.cacheDir, "restart-dictionary").apply { mkdirs() }
        DictionaryTestFixtures.copy(folder, "legacy.mdx")
        val previous = store.lastDictionaryId
        val dictionary = store.importFiles(folder, "legacy.mdx")
        assertTrue(state.edit().putString("id", dictionary.id).putString("previous", previous).commit())
        store.lastDictionaryId = dictionary.id
        assertTrue(store.lookup(dictionary.id, "uncollected").isEmpty())
        assertEquals(dictionary.id, store.lastDictionaryId)
        folder.deleteRecursively()
        android.os.SystemClock.sleep(200)
    }

    @Test fun verifyAfterRestart() = runBlocking {
        val id = requireNotNull(state.getString("id", null))
        try {
            assertEquals(id, store.lastDictionaryId)
            assertEquals("go", store.lookup(id, "go").single().headword)
            assertTrue(store.lookup(id, "学习").single().html.contains("知识"))
        } finally {
            store.delete(id)
            store.lastDictionaryId = state.getString("previous", null)
            state.edit().clear().commit()
        }
    }
}
