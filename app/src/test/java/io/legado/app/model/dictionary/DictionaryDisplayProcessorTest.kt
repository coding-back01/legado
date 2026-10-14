package io.legado.app.model.dictionary

import io.legado.app.data.entities.LocalDictionary
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class DictionaryDisplayProcessorTest {
    private val dictionary = LocalDictionary("id", "title", "MDICT", "sample.mdx", "content-hash")
    private val entry = DictionaryEntry(1, "word", "<div class=definition>definition</div>")

    @Test fun preprocessingRunsOffCallingThreadAndPreservesFullArticle() = runBlocking {
        val callingThread = Thread.currentThread().id
        var workerThread = callingThread
        val processor = DictionaryDisplayProcessor(analyze = { workerThread = Thread.currentThread().id; DictionarySummaries.analyze(it) })
        val display = processor.prepare(dictionary, listOf(entry), false, 18)
        assertNotEquals(callingThread, workerThread)
        assertEquals(entry.html, display.previews.single().entry.html)
        assertTrue(display.html.contains("definition"))
    }

    @Test fun displayCacheEvictsOldEntriesAndNeverExceedsItsBudget() {
        val cache = DictionaryDisplayCache(1000)
        val first = DictionaryDisplayKey("id", "hash", listOf(1), false, 18, 1)
        val second = first.copy(entries = listOf(2))
        val display = DictionaryDisplay(listOf(DictionaryEntryPreview(entry, null)), "x".repeat(200), false)
        cache.put(first, display)
        cache.put(second, display)
        assertTrue(cache.bytes() <= 1000)
        assertNull(cache.get(first))
        assertNotNull(cache.get(second))
        cache.put(first, display.copy(html = "x".repeat(2000)))
        assertNull(cache.get(first))
        assertTrue(cache.bytes() <= 1000)
    }

    @Test fun repeatedPreparationReusesOnlyMatchingStrategyThemeAndIdentity() = runBlocking {
        val cache = DictionaryDisplayCache()
        var calls = 0
        val analyze: (DictionaryEntry) -> DictionaryEntryPreview = { calls++; DictionarySummaries.analyze(it) }
        val first = DictionaryDisplayProcessor(analyze, cache, 1)
        first.prepare(dictionary, listOf(entry), false, 18)
        first.prepare(dictionary, listOf(entry), false, 18)
        assertEquals(1, calls)
        DictionaryDisplayProcessor(analyze, cache, 2).prepare(dictionary, listOf(entry), false, 18)
        assertEquals(2, calls)
        first.prepare(dictionary, listOf(entry), true, 18)
        assertEquals(3, calls)
        first.prepare(dictionary.copy(fingerprint = "changed"), listOf(entry), false, 18)
        assertEquals(4, calls)
    }

    @Test fun cancelledPreprocessingDoesNotPublishIntoCache() = runBlocking {
        val cache = DictionaryDisplayCache()
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val processor = DictionaryDisplayProcessor(analyze = {
            started.countDown()
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            DictionarySummaries.analyze(it)
        }, cache = cache)
        val job = launch(kotlinx.coroutines.Dispatchers.Default) { processor.prepare(dictionary, listOf(entry), false, 18) }
        assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
        job.cancel()
        release.countDown()
        job.join()
        assertEquals(0L, cache.bytes())
    }

    @Test fun sameEntryReachedThroughDifferentMatchesKeepsCurrentProvenance() = runBlocking {
        val processor = DictionaryDisplayProcessor()
        val exact = processor.prepare(dictionary, listOf(entry), false, 18)
        val transformed = processor.prepare(dictionary, listOf(entry.copy(match = DictionaryMatch.ENGLISH_FORM)), false, 18)
        assertEquals(DictionaryMatch.EXACT, exact.previews.single().entry.match)
        assertEquals(DictionaryMatch.ENGLISH_FORM, transformed.previews.single().entry.match)
    }
}
