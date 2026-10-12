package io.legado.app.model.dictionary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionarySettingsIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = LocalDictionaryStore(context)
    private val previous = store.querySettings
    private val folder = File(context.cacheDir, "settings-${UUID.randomUUID()}").apply { mkdirs() }
    private var dictionaryId: String? = null

    @After fun cleanup() = runBlocking {
        dictionaryId?.let { store.delete(it) }
        store.querySettings = previous
        folder.deleteRecursively()
        Unit
    }

    @Test fun settingsPersistAcrossStoreInstancesAndIndexRebuildWithoutChangingMetadata() = runBlocking {
        DictionaryTestFixtures.copy(folder, "sample.mdx")
        val dictionary = store.importFiles(folder, "sample.mdx").also { dictionaryId = it.id }
        store.querySettings = DictionaryQuerySettings().withLanguage(dictionary.id, "en")
            .withPrimary("en", dictionary.id).withPrimary("zh", "unavailable-chinese")
            .withFallbacks("en", listOf("fallback-second", "fallback-first", "fallback-second"))
        val expected = store.querySettings
        val anotherStore = LocalDictionaryStore(context)
        assertEquals(expected, anotherStore.querySettings)
        anotherStore.rebuild(dictionary.id)
        assertEquals(expected, anotherStore.querySettings)
        assertEquals(dictionary.inputLanguage, anotherStore.all.first { it.id == dictionary.id }.inputLanguage)
        assertEquals("en", anotherStore.querySettings.languageOf(dictionary))
        assertEquals("go", anotherStore.lookup(dictionary.id, "go").single().headword)
    }
}
