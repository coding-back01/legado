package io.legado.app.model.dictionary

import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.ui.dict.dictionaryMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryStorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = LocalDictionaryStore(context)
    private val ids = linkedSetOf<String>()
    private val folders = arrayListOf<File>()

    private fun fixture(vararg names: String): File = File(context.cacheDir, "storage-${UUID.randomUUID()}").also {
        DictionaryTestFixtures.copy(it, *names)
        folders.add(it)
    }

    @After fun cleanup() = runBlocking {
        ids.forEach { store.delete(it) }
        folders.forEach { it.deleteRecursively() }
        Unit
    }

    @Test fun registerFailureRollsBackNewDirectoryAndLeavesExistingDictionaryUsable() = runBlocking {
        val original = store.importFiles(fixture("sample.mdx"), "sample.mdx").also { ids.add(it.id) }
        val count = store.all.size
        val failing = LocalDictionaryStore(context) { stage -> if (stage == DictionaryOperation.BEFORE_REGISTER) throw IOException("injected") }
        try { failing.importFiles(fixture("optional.dsl"), "optional.dsl"); fail() } catch (_: IOException) { }
        assertEquals(count, store.all.size)
        assertEquals("go", store.lookup(original.id, "went").first().headword)
    }

    @Test fun sourceSymlinksCannotReadOutsideTheGrantedGroup() = runBlocking {
        val source = fixture("sample.mdx")
        val outside = File(context.cacheDir, "outside-${UUID.randomUUID()}").apply { writeText("outside sentinel") }
        folders.add(outside)
        android.system.Os.symlink(outside.path, File(source, "outside.txt").path)
        try { store.importFiles(source, "sample.mdx"); fail() } catch (e: DictionaryException) { assertEquals(DictionaryProblem.INVALID_PATH, e.problem) }
        assertEquals("outside sentinel", outside.readText())
    }

    @Test fun stoppedRebuildRecoversPreviousCompleteIndexOnStartup() = runBlocking {
        val dictionary = store.importFiles(fixture("sample.mdx"), "sample.mdx").also { ids.add(it.id) }
        val interrupted = LocalDictionaryStore(context) { stage -> if (stage == DictionaryOperation.OLD_INDEX_MOVED) throw SimulatedProcessStop() }
        try { interrupted.rebuild(dictionary.id); fail() } catch (_: SimulatedProcessStop) { }
        assertFalse(store.directory(dictionary.id).exists())
        store.recover()
        assertEquals("go", store.lookup(dictionary.id, "went").first().headword)
        assertTrue(store.directory(dictionary.id).isDirectory)
    }

    @Test fun stoppedImportLeavesNoQueryableHalfAndAllowsRetryAfterRecovery() = runBlocking {
        val existing = store.importFiles(fixture("sample.mdx"), "sample.mdx").also { ids.add(it.id) }
        val source = fixture("optional.dsl")
        val count = store.all.size
        val interrupted = LocalDictionaryStore(context) { if (it == DictionaryOperation.BEFORE_REGISTER) throw SimulatedProcessStop() }
        try { interrupted.importFiles(source, "optional.dsl"); fail() } catch (_: SimulatedProcessStop) { }
        assertEquals(count, store.all.size)
        store.recover()
        assertEquals("go", store.lookup(existing.id, "went").first().headword)
        val retried = store.importFiles(source, "optional.dsl").also { ids.add(it.id) }
        assertTrue(store.lookup(retried.id, "words").isNotEmpty())
    }

    @Test fun rebuildFailureRollsBackAndOtherDictionaryRemainsUsable() = runBlocking {
        val first = store.importFiles(fixture("sample.mdx"), "sample.mdx").also { ids.add(it.id) }
        val other = store.importFiles(fixture("optional.dsl"), "optional.dsl").also { ids.add(it.id) }
        val failing = LocalDictionaryStore(context) { if (it == DictionaryOperation.OLD_INDEX_MOVED) throw IOException("injected rebuild failure") }
        try { failing.rebuild(first.id); fail() } catch (_: IOException) { }
        assertEquals("go", store.lookup(first.id, "went").first().headword)
        assertTrue(store.lookup(other.id, "word").isNotEmpty())
        store.rebuild(first.id)
        assertEquals("go", store.lookup(first.id, "went").first().headword)
    }

    @Test fun cancelledImportNeverRegistersAUsableHalfDictionary() = runBlocking {
        val count = store.all.size
        val cancelled = LocalDictionaryStore(context) { if (it == DictionaryOperation.BEFORE_PUBLISH) throw CancellationException("cancelled") }
        try { cancelled.importFiles(fixture("optional.dsl"), "optional.dsl"); fail() } catch (_: CancellationException) { }
        assertEquals(count, store.all.size)
    }

    @Test fun cancelledRebuildAndFullStoragePreserveOriginalAndAllowRetry() = runBlocking {
        val source = fixture("sample.mdx")
        val original = store.importFiles(source, "sample.mdx").also { ids.add(it.id) }
        store.lookup(original.id, "go")
        File(store.directory(original.id), "ready").writeText("0")
        try { store.lookup(original.id, "go"); fail() } catch (_: DictionaryException) { }
        store.rebuild(original.id)
        val cancelled = LocalDictionaryStore(context) { if (it == DictionaryOperation.BEFORE_REBUILD_SWAP) throw CancellationException("cancelled rebuild") }
        try { cancelled.rebuild(original.id); fail() } catch (_: CancellationException) { }
        val count = store.all.size
        val full = LocalDictionaryStore(context) { if (it == DictionaryOperation.BEFORE_PUBLISH) throw android.database.sqlite.SQLiteFullException("storage full") }
        try { full.importFiles(fixture("optional.dsl"), "optional.dsl"); fail() } catch (e: android.database.sqlite.SQLiteFullException) {
            assertEquals(context.getString(io.legado.app.R.string.dictionary_limit), context.dictionaryMessage(e))
        }
        assertEquals(count, store.all.size)
        assertTrue(store.lookup(original.id, "学习").isNotEmpty())
    }

    @Test fun documentFolderGrantSupportsMultipleGroupsAndSelection() = runBlocking {
        val testContext = instrumentation.context
        val component = testContext.packageName + "/io.legado.app.model.dictionary.DictionaryGrantActivity"
        instrumentation.uiAutomation.executeShellCommand("am start -n $component").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
        }
        val uri = DocumentsContract.buildTreeDocumentUri(testContext.packageName + ".dictionaryFixture", "collection")
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        while (android.os.SystemClock.elapsedRealtime() < deadline &&
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.canRead() != true) {
            android.os.SystemClock.sleep(25)
        }
        assertTrue(androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.canRead() == true)
        val result = store.importDocuments(listOf(uri), choose = { names -> names.filter { it.endsWith(".mdx") }.toSet() })
        result.forEach { ids.add(it.id) }
        assertEquals(1, result.size)
        assertEquals("go", store.lookup(result.first().id, "went").first().headword)
        val authority = testContext.packageName + ".dictionaryFixture"
        val ungranted = DocumentsContract.buildTreeDocumentUri(authority, "root")
        try { store.importDocuments(listOf(ungranted)); fail() } catch (_: Exception) { }
    }

    @Test fun indexVersionRebuildAndSameNameDifferentContentStayDistinct() = runBlocking {
        val source = fixture("optional.dsl")
        val first = store.importFiles(source, "optional.dsl").also { ids.add(it.id) }
        File(store.directory(first.id), "ready").writeText("0")
        assertEquals(first.id, store.importFiles(source, "optional.dsl").id)
        assertEquals(DictionaryIndex.VERSION.toString(), File(store.directory(first.id), "ready").readText())
        File(source, "optional.dsl").appendText("another\n\tsecond meaning\n")
        val second = store.importFiles(source, "optional.dsl").also { ids.add(it.id) }
        assertNotEquals(first.id, second.id)
        assertNotEquals(first.name, second.name)
        assertTrue(store.lookup(second.id, "another").isNotEmpty())
        store.move(second.id, -1)
        assertEquals(listOf(second.id, first.id), store.all.filter { it.id in ids }.map { it.id })
        store.move(first.id, -1)
        assertEquals(listOf(first.id, second.id), store.all.filter { it.id in ids }.map { it.id })
    }

    private class SimulatedProcessStop : Error()
}
