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
class MobiCompatibilityIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "mobi-compatibility-${UUID.randomUUID()}").apply { mkdirs() }
    private val store = LocalDictionaryStore(context)
    private val ids = arrayListOf<String>()

    @After fun cleanup() = runBlocking {
        ids.forEach { store.delete(it) }
        root.deleteRecursively()
        Unit
    }

    private suspend fun dictionary(name: String): String {
        val source = File(root, UUID.randomUUID().toString())
        DictionaryTestFixtures.copy(source, name)
        return store.importFiles(source, name).id.also { ids.add(it) }
    }

    @Test fun orthAliasesBeforeTheirTargetsAndChainsKeepCanonicalHeadwords() = runBlocking {
        val id = dictionary("mobi-orth-alias.mobi")
        for (word in listOf("异体", "链式别名", "规范词")) {
            val entry = store.lookup(id, word).single()
            assertEquals("规范词", entry.headword)
            assertTrue(entry.html.contains("中文规范词释义"))
            assertFalse(entry.html.contains("first definition"))
        }
    }

    @Test fun supplementaryFlowDoesNotChangeMainEntryBoundaries() = runBlocking {
        val id = dictionary("mobi-extra-flow.mobi")
        val entry = store.lookup(id, "异体").single()
        assertEquals("规范词", entry.headword)
        assertFalse(entry.html.contains("search panel sentinel"))
        assertTrue(store.resource(id, "flow00001.html").readText().contains("search panel sentinel"))
    }

    @Test fun cyclicAndOutOfRangeOrthAliasesAreRejectedBeforePublishing() = runBlocking {
        for (name in listOf("mobi-orth-cycle.mobi", "mobi-orth-outside.mobi")) {
            try {
                dictionary(name)
                fail("应拒绝无效别名：$name")
            } catch (e: DictionaryException) {
                assertEquals(DictionaryProblem.CORRUPT, e.problem)
            }
        }
    }
}
