package io.legado.app.model.dictionary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryQueryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = File(context.cacheDir, "query-test-${UUID.randomUUID()}").apply { mkdirs() }
    @After fun cleanup() { folder.deleteRecursively() }

    private fun index(builder: (DictionarySink) -> Unit): DictionaryIndex {
        DictionaryIndex(folder, true).use { index -> index.build { builder(index.sink {}) } }
        return DictionaryIndex(folder)
    }

    @Test fun originalHeadwordWinsOverAliasWhileAllOriginalSensesRemainSeparate() {
        index { sink ->
            sink.entry(0, listOf("go", "went"), "verb go")
            sink.entry(1, listOf("went"), "original sense one")
            sink.entry(2, listOf("went"), "original sense two")
            sink.entry(3, listOf("GO"), "uppercase name")
        }.use { index ->
            assertEquals(listOf("original sense one", "original sense two"), index.lookup("went") {}.map { it.html })
            assertEquals("uppercase name", index.lookup("GO") {}.single().html)
            assertEquals("verb go", index.lookup("gone") {}.single().html)
        }
    }

    @Test fun simplifiedCandidateCanReturnDistinctTraditionalWordsWithoutMergingTheirDefinitions() {
        index { sink ->
            sink.entry(0, listOf("乾"), "dry meaning")
            sink.entry(1, listOf("幹"), "stem meaning")
            sink.entry(2, listOf("学习"), "simplified original")
            sink.entry(3, listOf("學習"), "traditional original")
            sink.entry(4, listOf("说"), "modern meaning")
        }.use { index ->
            assertEquals(setOf("dry meaning", "stem meaning"), index.lookup("干") {}.map { it.html }.toSet())
            assertEquals("simplified original", index.lookup("学习") {}.single().html)
            assertEquals("traditional original", index.lookup("學習") {}.single().html)
            assertTrue(index.lookup("悦") {}.isEmpty())
        }
    }

    @Test fun japaneseKoreanAndTheirDeclaredAliasesRemainExact() {
        index { sink ->
            sink.entry(0, listOf("読む", "よむ"), "Japanese definition")
            sink.entry(1, listOf("한국어", "한국"), "Korean definition")
        }.use { index ->
            assertEquals("読む", index.lookup("よむ") {}.single().headword)
            assertEquals("한국어", index.lookup("한국") {}.single().headword)
            assertTrue(index.lookup("読んだ") {}.isEmpty())
            assertTrue(index.lookup("한국어s") {}.isEmpty())
        }
    }

    @Test fun englishFormsUseValidatedParadigmsPhrasesAndNegativeExamples() {
        index { sink ->
            sink.entry(0, listOf("hope"), "hope")
            sink.entry(1, listOf("hop"), "hop")
            sink.entry(2, listOf("give up"), "phrase")
            sink.entry(3, listOf("study"), "study")
            sink.entry(4, listOf("new"), "new")
            sink.entry(5, listOf("count"), "count")
        }.use { index ->
            assertEquals("hope", index.lookup("hoped") {}.single().headword)
            assertEquals("hop", index.lookup("hopping") {}.single().headword)
            assertEquals("give up", index.lookup("gave up") {}.single().headword)
            assertEquals("study", index.lookup("studied") {}.single().headword)
            assertTrue(index.lookup("news") {}.isEmpty())
            assertTrue(index.lookup("counter") {}.isEmpty())
        }
    }

    @Test fun aliasDepthAndCancellationAreBounded() {
        index { sink ->
            repeat(18) { n -> sink.entry(n.toLong(), listOf("word$n"), "link", if (n < 17) "word${n+1}" else null) }
        }.use { index ->
            assertThrows(DictionaryException::class.java) { index.lookup("word0") {} }
            assertThrows(kotlinx.coroutines.CancellationException::class.java) {
                index.lookup("word17") { throw kotlinx.coroutines.CancellationException("cancelled") }
            }
        }
    }

    @Test fun excessSensesReturnAnExplicitLimitInsteadOfSilentlyDroppingDefinitions() {
        index { sink -> repeat(DictionaryLimits.MAX_CANDIDATES + 1) { sink.entry(it.toLong(), listOf("same"), "sense $it") } }.use {
            val error = assertThrows(DictionaryException::class.java) { it.lookup("same") {} }
            assertEquals(DictionaryProblem.LIMIT, error.problem)
        }
    }

    @Test fun embeddedResourcesCountDirectoriesAndDuplicatesWithoutExceedingTheLimit() {
        DictionaryIndex(folder, true, maximumResourceFiles = 3).use { index ->
            val sink = index.sink {}
            sink.resource("images/a.png", byteArrayOf(1))
            sink.resource("images/b.png", byteArrayOf(2))
            sink.resource("images/a.png", byteArrayOf(1))
            val limit = assertThrows(DictionaryException::class.java) { sink.resource("other/c.png", byteArrayOf(3)) }
            assertEquals(DictionaryProblem.LIMIT, limit.problem)
            assertFalse(File(folder, "resources/other").exists())
        }
        val deep = (List(65) { "nested" } + "image.png").joinToString("/")
        val depth = assertThrows(DictionaryException::class.java) { DictionaryPaths.file(folder, deep) }
        assertEquals(DictionaryProblem.LIMIT, depth.problem)
    }
}
