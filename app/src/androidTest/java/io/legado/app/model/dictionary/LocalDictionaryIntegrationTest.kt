package io.legado.app.model.dictionary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.appDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class LocalDictionaryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val store = LocalDictionaryStore(context)
    private val ids = arrayListOf<String>()
    private lateinit var temporary: File
    private var previous: String? = null

    @Before fun setUp() {
        temporary = File(context.cacheDir, "dictionary-test-${UUID.randomUUID()}").apply { mkdirs() }
        previous = store.lastDictionaryId
    }

    @After fun tearDown() = runBlocking {
        ids.distinct().forEach { store.delete(it) }
        store.lastDictionaryId = previous
        temporary.deleteRecursively()
        Unit
    }

    private fun sources(vararg names: String): File {
        val directory = File(temporary, UUID.randomUUID().toString()).apply { mkdirs() }
        names.forEach { name ->
            ZipInputStream(assets.open("dictionary-fixtures.zip")).use { input ->
                var found = false
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name == name) {
                        File(directory, name).outputStream().use { input.copyTo(it) }
                        found = true
                        break
                    }
                }
                check(found) { name }
            }
        }
        return directory
    }

    private suspend fun import(source: File, primary: String) = store.importFiles(source, primary).also { ids.add(it.id) }

    @Test fun sixFormatFamiliesBuildPersistentIndexesAndPreserveAliasesAndResources() = runBlocking {
        val groups = listOf(
            "sample.mdx" to arrayOf("sample.mdx", "sample.mdd", "diagram.png"),
            "sample.ifo" to arrayOf("sample.ifo", "sample.idx.gz", "sample.dict.dz", "sample.syn", "diagram.png"),
            "sample.dsl.dz" to arrayOf("sample.dsl.dz", "diagram.png"),
            "dictd.index" to arrayOf("dictd.index", "dictd.dict.dz"),
            "sample.xdxf" to arrayOf("sample.xdxf", "diagram.png"),
            "sample.bgl" to arrayOf("sample.bgl")
        )
        for ((primary, files) in groups) {
            val source = sources(*files)
            val dictionary = import(source, primary)
            assertEquals("ready", dictionary.state)
            assertTrue(dictionary.entryCount >= 2)
            assertEquals("go", store.lookup(dictionary.id, "went").first().headword)
            val chinese = store.lookup(dictionary.id, "學習").first()
            assertEquals("学习", chinese.headword)
            assertTrue(chinese.html.contains("知识"))
            source.deleteRecursively()
            assertTrue(store.lookup(dictionary.id, "go").isNotEmpty())
        }
    }

    @Test fun mobiDictionaryUsesInflectionIndexAndRejectsOrdinaryAndProtectedBooks() = runBlocking {
        for (name in listOf("mobi-inflections.mobi", "mobi-compression-1.mobi", "mobi-compression-2.mobi", "mobi-dictionary-huff.mobi")) {
            val dictionary = import(sources(name), name)
            assertEquals("być", store.lookup(dictionary.id, "jest").first().headword)
            assertEquals("mieć", store.lookup(dictionary.id, "mam").first().headword)
        }
        for ((file, expected) in listOf("mobi-ordinary-huff.mobi" to DictionaryProblem.UNSUPPORTED,
            "mobi-protected.mobi" to DictionaryProblem.PROTECTED)) {
            try {
                import(sources(file), file)
                fail(file)
            } catch (error: DictionaryException) { assertEquals(expected, error.problem) }
        }
    }

    @Test fun duplicateImportDeleteAndRebuildPreserveOriginalFiles() = runBlocking {
        val source = sources("sample.mdx", "sample.mdd")
        val first = import(source, "sample.mdx")
        assertEquals(first.id, import(source, "sample.mdx").id)
        store.rebuild(first.id)
        assertEquals("go", store.lookup(first.id, "went").first().headword)
        store.delete(first.id)
        assertTrue(File(source, "sample.mdx").exists())
        assertFalse(store.directory(first.id).exists())
    }

    @Test fun fullAndCatalogOnlyBackupsRestoreFilesSelectionAndMissingState() = runBlocking {
        val dictionary = import(sources("sample.mdx", "sample.mdd"), "sample.mdx")
        store.lastDictionaryId = dictionary.id
        val full = File(temporary, "full")
        val ordinary = File(temporary, "ordinary")
        val oldOption = store.includeFilesInBackup
        try {
            store.includeFilesInBackup = false
            store.backup(ordinary)
            assertFalse(File(ordinary, LocalDictionaryStore.FILES).exists())
            store.includeFilesInBackup = true
            store.backup(full)
            store.delete(dictionary.id)
            assertTrue(store.restore(ordinary).isEmpty())
            assertEquals("missing", appDb.localDictionaryDao.get(dictionary.id)?.state)
            assertTrue(store.restore(full).isEmpty())
            val restored = requireNotNull(store.lastDictionaryId)
            ids.add(restored)
            assertEquals("go", store.lookup(restored, "went").first().headword)
            assertEquals(restored, LocalDictionaryStore(context).lastDictionaryId)
            val old = File(temporary, "old").apply { mkdirs() }
            assertTrue(store.restore(old).isEmpty())
            assertTrue(store.lookup(restored, "学习").isNotEmpty())
        } finally { store.includeFilesInBackup = oldOption }
    }

    @Test fun failedImportDoesNotPublishOrDamageExistingDictionary() = runBlocking {
        val source = sources("sample.mdx", "sample.mdd")
        val dictionary = import(source, "sample.mdx")
        val broken = sources("sample.mdx")
        File(broken, "sample.mdx").writeBytes(byteArrayOf(0, 0, 0, 8))
        val count = store.all.size
        try { import(broken, "sample.mdx"); fail() } catch (_: Exception) { }
        assertEquals(count, store.all.size)
        assertTrue(store.lookup(dictionary.id, "go").isNotEmpty())
    }
}
