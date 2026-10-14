package io.legado.app.model.dictionary

import io.legado.app.data.entities.LocalDictionary

sealed interface DictionaryLookupResult {
    data class Hit(val source: LocalDictionary, val entries: List<DictionaryEntry>) : DictionaryLookupResult
    data class NotFound(val triedIds: List<String>) : DictionaryLookupResult
    data class NeedChoice(val plan: DictionaryQueryPlan) : DictionaryLookupResult
    data class Failure(val sourceId: String, val error: Throwable) : DictionaryLookupResult
}

class DictionaryQueryCoordinator(
    private val read: suspend (LocalDictionary, String, String) -> List<DictionaryEntry>
) {
    suspend fun manual(id: String, word: String, language: String?, catalog: List<LocalDictionary>): DictionaryLookupResult {
        val source = catalog.firstOrNull { it.id == id && it.state == "ready" }
            ?: return DictionaryLookupResult.Failure(id, DictionaryException(DictionaryProblem.MISSING_FILE))
        return try {
            val entries = read(source, word, DictionaryLanguages.canonical(language).orEmpty())
            if (entries.isEmpty()) DictionaryLookupResult.NotFound(listOf(id)) else DictionaryLookupResult.Hit(source, entries)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { DictionaryLookupResult.Failure(id, error) }
    }

    suspend fun lookup(word: String, language: String?, settings: DictionaryQuerySettings,
        catalog: List<LocalDictionary>): DictionaryLookupResult {
        val plan = DictionaryQueryPlanner.plan(language, settings, catalog)
        if (plan !is DictionaryQueryPlan.Ready) return DictionaryLookupResult.NeedChoice(plan)
        val tried = arrayListOf<String>()
        for (id in (listOf(plan.primary.id) + plan.fallbackIds).distinct()) {
            val source = catalog.firstOrNull { it.id == id }
            if (source == null || source.state != "ready") return DictionaryLookupResult.Failure(id, DictionaryException(DictionaryProblem.MISSING_FILE))
            if (settings.languageOf(source) != plan.language) return DictionaryLookupResult.Failure(id, DictionaryException(DictionaryProblem.UNSUPPORTED, "language"))
            tried.add(id)
            val entries = try { read(source, word, plan.language) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { return DictionaryLookupResult.Failure(id, error) }
            if (entries.isNotEmpty()) return DictionaryLookupResult.Hit(source, entries)
        }
        return DictionaryLookupResult.NotFound(tried)
    }
}
