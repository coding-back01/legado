package io.legado.app.model.dictionary

import io.legado.app.data.entities.LocalDictionary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DictionaryQueryCoordinatorTest {
    private val catalog = listOf("a", "b", "c", "unconfigured").map {
        LocalDictionary(it, it, "DSL", "$it.dsl", "hash", inputLanguage = "English")
    }
    private val settings = DictionaryQuerySettings().withPrimary("en", "a").withFallbacks("en", listOf("b", "c", "b"))

    @Test fun primaryMissQueriesConfiguredFallbackInOrderAndStopsAtFirstHit() = runBlocking {
        val calls = arrayListOf<String>()
        val coordinator = DictionaryQueryCoordinator { source, _, _ ->
            calls.add(source.id)
            if (source.id == "b") listOf(DictionaryEntry(1, "word", "definition")) else emptyList()
        }
        val result = coordinator.lookup("word", "en", settings, catalog) as DictionaryLookupResult.Hit
        assertEquals(listOf("a", "b"), calls)
        assertEquals("b", result.source.id)
        assertEquals("a", settings.languages["en"]?.primaryId)
    }

    @Test fun primaryHitNeverReadsFallbackAndAllMissesStayWithinConfiguredLocalSources() = runBlocking {
        val calls = arrayListOf<String>()
        val hit = DictionaryQueryCoordinator { source, _, _ -> calls.add(source.id); listOf(DictionaryEntry(1, "word", "definition")) }
        assertTrue(hit.lookup("word", "en", settings, catalog) is DictionaryLookupResult.Hit)
        assertEquals(listOf("a"), calls)
        calls.clear()
        val miss = DictionaryQueryCoordinator { source, _, _ -> calls.add(source.id); emptyList() }
        val result = miss.lookup("word", "en", settings, catalog) as DictionaryLookupResult.NotFound
        assertEquals(listOf("a", "b", "c"), calls)
        assertEquals(calls, result.triedIds)
        assertFalse(calls.contains("unconfigured"))
        assertEquals("a", settings.languages["en"]?.primaryId)
    }

    @Test fun readingFailureIsVisibleAndDoesNotQueryAnyFallback() = runBlocking {
        val calls = arrayListOf<String>()
        val error = DictionaryException(DictionaryProblem.LIMIT)
        val reader = DictionaryQueryCoordinator { source, _, _ -> calls.add(source.id); throw error }
        val result = reader.lookup("word", "en", settings, catalog) as DictionaryLookupResult.Failure
        assertSame(error, result.error)
        assertEquals("a", result.sourceId)
        assertEquals(listOf("a"), calls)
    }

    @Test fun unavailableFallbackAndAllReadErrorsRemainDistinctFromNotFound() = runBlocking {
        val reader = DictionaryQueryCoordinator { _, _, _ -> emptyList() }
        val unavailable = reader.lookup("word", "en", settings, catalog.filter { it.id != "b" }) as DictionaryLookupResult.Failure
        assertEquals("b", unavailable.sourceId)
        assertEquals(DictionaryProblem.MISSING_FILE, (unavailable.error as DictionaryException).problem)
        for (error in listOf(DictionaryException(DictionaryProblem.CORRUPT), DictionaryException(DictionaryProblem.LIMIT), java.io.IOException("read"))) {
            val failing = DictionaryQueryCoordinator { _, _, _ -> throw error }
            assertSame(error, (failing.lookup("word", "en", settings, catalog) as DictionaryLookupResult.Failure).error)
        }
        val cancelled = DictionaryQueryCoordinator { _, _, _ -> throw kotlinx.coroutines.CancellationException("cancel") }
        try { cancelled.lookup("word", "en", settings, catalog); fail("应取消") } catch (_: kotlinx.coroutines.CancellationException) { }
    }

    @Test fun activelyViewingOtherSourceDoesNotChangePrimaryOrFallbackOrder() = runBlocking {
        val calls = arrayListOf<String>()
        val reader = DictionaryQueryCoordinator { source, _, _ -> calls.add(source.id); listOf(DictionaryEntry(1, "word", "definition")) }
        val result = reader.manual("unconfigured", "word", "en", catalog) as DictionaryLookupResult.Hit
        assertEquals("unconfigured", result.source.id)
        assertEquals(listOf("unconfigured"), calls)
        assertEquals("a", settings.languages["en"]?.primaryId)
        assertEquals(listOf("b", "c"), settings.languages["en"]?.fallbackIds)
        assertEquals("unconfigured", settings.withPrimary("en", "unconfigured").languages["en"]?.primaryId)
    }
}
