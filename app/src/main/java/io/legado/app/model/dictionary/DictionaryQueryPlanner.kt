package io.legado.app.model.dictionary

import io.legado.app.data.entities.LocalDictionary

sealed interface DictionaryQueryPlan {
    data object ChooseLanguage : DictionaryQueryPlan
    data class ChooseDictionary(val language: String, val candidates: List<LocalDictionary>) : DictionaryQueryPlan
    data class Unavailable(val id: String, val reason: String) : DictionaryQueryPlan
    data class Ready(val language: String, val primary: LocalDictionary, val fallbackIds: List<String>) : DictionaryQueryPlan
}

object DictionaryQueryPlanner {
    fun plan(language: String?, settings: DictionaryQuerySettings, catalog: List<LocalDictionary>): DictionaryQueryPlan {
        val tag = DictionaryLanguages.canonical(language) ?: return DictionaryQueryPlan.ChooseLanguage
        val preference = settings.languages[tag]
        val id = preference?.primaryId
        val eligible = catalog.filter { settings.languageOf(it) == tag }
        val primary = if (id == null) {
            val ready = eligible.filter { it.state == "ready" }
            if (ready.size != 1) return DictionaryQueryPlan.ChooseDictionary(tag, eligible)
            ready.single()
        } else {
            val chosen = catalog.firstOrNull { it.id == id } ?: return DictionaryQueryPlan.Unavailable(id, "missing")
            if (chosen.state != "ready") return DictionaryQueryPlan.Unavailable(id, chosen.state)
            if (settings.languageOf(chosen) != tag) return DictionaryQueryPlan.Unavailable(id, "language")
            chosen
        }
        return DictionaryQueryPlan.Ready(tag, primary, preference?.fallbackIds.orEmpty().distinct().filter { it != primary.id })
    }
}
