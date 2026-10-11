package io.legado.app.model.dictionary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import io.legado.app.data.appDb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryRestoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "restore-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val store = LocalDictionaryStore(context)
    private val ids = linkedSetOf<String>()
    private val option = store.includeFilesInBackup
    private val selected = store.lastDictionaryId

    @After fun cleanup() = runBlocking { ids.forEach { store.delete(it) }; store.includeFilesInBackup = option; store.lastDictionaryId = selected; root.deleteRecursively(); Unit }

    private suspend fun dictionary(name: String, vararg companions: String) = run {
        val source = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        DictionaryTestFixtures.copy(source, name, *companions)
        store.importFiles(source, name).also { ids.add(it.id) }
    }

    @Test fun matchingContentIsReusedAndSelectionRemapsWithoutOverwritingSameNamedContent() = runBlocking {
        val first = dictionary("sample.mdx", "sample.mdd")
        val second = dictionary("legacy.mdx")
        store.lastDictionaryId = first.id
        val backup = File(root, "full")
        store.includeFilesInBackup = true
        store.backup(backup)
        val count = store.all.size
        assertTrue(store.restore(backup).isEmpty())
        assertEquals(count, store.all.size)
        assertEquals(first.id, store.lastDictionaryId)
        assertNotEquals(first.id, second.id)
        assertTrue(store.lookup(second.id, "学习").isNotEmpty())
        store.delete(first.id)
        assertTrue(store.restore(backup).isEmpty())
        val remapped = requireNotNull(store.lastDictionaryId)
        ids.add(remapped)
        assertEquals("go", store.lookup(remapped, "went").first().headword)
        assertTrue(store.lookup(second.id, "go").isNotEmpty())
    }

    @Test fun checksumFailureAndUnknownManifestVersionPreserveExistingDictionary() = runBlocking {
        val existing = dictionary("legacy.mdx")
        val incoming = dictionary("sample.mdx")
        val backup = File(root, "full")
        store.includeFilesInBackup = true
        store.backup(backup)
        store.delete(incoming.id)
        File(backup, "${LocalDictionaryStore.FILES}/${incoming.id}/sample.mdx").appendBytes(byteArrayOf(1))
        assertTrue(store.restore(backup).isNotEmpty())
        assertTrue(store.lookup(existing.id, "go").isNotEmpty())
        val manifest = File(backup, LocalDictionaryStore.MANIFEST)
        val json = JsonParser.parseString(manifest.readText()).asJsonObject
        json.addProperty("version", 999)
        manifest.writeText(json.toString())
        assertEquals(listOf("version"), store.restore(backup))
        assertTrue(store.lookup(existing.id, "学习").isNotEmpty())
    }

    @Test fun invalidPathsAndNullRequiredFieldsAreRejectedWithoutChangingExistingData() = runBlocking {
        val existing = dictionary("legacy.mdx")
        val backup = File(root, "catalog")
        store.includeFilesInBackup = false
        store.backup(backup)
        val file = File(backup, LocalDictionaryStore.MANIFEST)
        val json = JsonParser.parseString(file.readText()).asJsonObject
        val original = json.deepCopy()
        json.getAsJsonArray("dictionaries")[0].asJsonObject.addProperty("sourceFile", "../outside.mdx")
        file.writeText(json.toString())
        try { store.restore(backup); fail() } catch (e: DictionaryException) { assertEquals(DictionaryProblem.INVALID_PATH, e.problem) }
        assertTrue(store.lookup(existing.id, "go").isNotEmpty())
        for (key in listOf("id", "name", "format", "sourceFile", "fingerprint", "state")) {
            val invalid = original.deepCopy()
            invalid.getAsJsonArray("dictionaries")[0].asJsonObject.add(key, com.google.gson.JsonNull.INSTANCE)
            file.writeText(invalid.toString())
            try { store.restore(backup); fail(key) } catch (e: DictionaryException) { assertEquals(key, DictionaryProblem.CORRUPT, e.problem) }
            assertTrue(store.lookup(existing.id, "go").isNotEmpty())
        }
    }

    @Test fun cancelledOrOutOfSpaceRestoreDoesNotReplaceExistingDictionary() = runBlocking {
        val existing = dictionary("legacy.mdx")
        val incoming = dictionary("sample.mdx")
        val backup = File(root, "full")
        store.includeFilesInBackup = true
        store.backup(backup)
        store.delete(incoming.id)
        val cancelled = LocalDictionaryStore(context) { if (it == DictionaryOperation.BEFORE_PUBLISH) throw CancellationException("cancelled restore") }
        try { cancelled.restore(backup); fail() } catch (_: CancellationException) { }
        assertTrue(store.lookup(existing.id, "go").isNotEmpty())
        val outOfSpace = LocalDictionaryStore(context) { if (it == DictionaryOperation.BEFORE_PUBLISH) throw android.database.sqlite.SQLiteFullException("injected storage full") }
        assertTrue(outOfSpace.restore(backup).isNotEmpty())
        assertTrue(store.lookup(existing.id, "学习").isNotEmpty())
        assertEquals("ready", appDb.localDictionaryDao.get(existing.id)?.state)
    }
}
