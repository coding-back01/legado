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
class DictionaryArchiveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "archive-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val ids = arrayListOf<String>()
    private val store = LocalDictionaryStore(context)

    @After fun cleanup() = runBlocking { ids.forEach { store.delete(it) }; root.deleteRecursively(); Unit }

    @Test fun fourArchiveFormatsExtractAndProduceQueryableDictionary() = runBlocking {
        for (name in listOf("import.zip", "import.rar", "import.7z", "import.tar.gz")) {
            val source = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
            DictionaryTestFixtures.copy(source, name)
            val expanded = File(source, "expanded").apply { mkdirs() }
            DictionaryArchive.extract(File(source, name), expanded) {}
            assertTrue(name, File(expanded, "sample.mdx").isFile)
            assertTrue(name, File(expanded, "sample.mdd").isFile)
            val dictionary = store.importFiles(expanded, "sample.mdx").also { ids.add(it.id) }
            assertEquals("go", store.lookup(dictionary.id, "went").first().headword)
        }
    }

    @Test fun traversalSymlinksAndDeclaredResourceExhaustionAreRejected() {
        for ((name, expected) in listOf("escape.zip" to DictionaryProblem.INVALID_PATH,
            "symlink.tar.gz" to DictionaryProblem.INVALID_PATH, "oversized.tar.gz" to DictionaryProblem.LIMIT, "too-many.tar.gz" to DictionaryProblem.LIMIT)) {
            val source = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
            DictionaryTestFixtures.copy(source, name)
            val expanded = File(source, "expanded").apply { mkdirs() }
            val error = assertThrows(DictionaryException::class.java) { DictionaryArchive.extract(File(source, name), expanded) {} }
            assertEquals(name, expected, error.problem)
            assertFalse(File(source, "outside.txt").exists())
        }
    }

    @Test fun multipartArchiveIsRejectedBeforePartialExtraction() {
        val file = File(root, "dictionary.part1.rar").apply { writeBytes(byteArrayOf(1)) }
        val error = assertThrows(DictionaryException::class.java) { DictionaryArchive.extract(file, root) {} }
        assertEquals(DictionaryProblem.UNSUPPORTED, error.problem)
    }

    @Test fun encryptedArchiveShowsProtectionInsteadOfPublishingPartialContent() {
        DictionaryTestFixtures.copy(root, "protected.zip")
        val target = File(root, "protected").apply { mkdirs() }
        val error = assertThrows(DictionaryException::class.java) { DictionaryArchive.extract(File(root, "protected.zip"), target) {} }
        assertEquals(DictionaryProblem.PROTECTED, error.problem)
    }
}
