package io.legado.app.model.dictionary

import io.legado.app.data.entities.LocalDictionary
import org.junit.Assert.*
import org.junit.Test

class DictionaryQueryPlanTest {
    private val english = LocalDictionary("en", "English", "DSL", "en.dsl", "hash", inputLanguage = "English")
    private val chinese = LocalDictionary("zh", "Chinese", "DSL", "zh.dsl", "hash", inputLanguage = "Chinese")

    @Test fun explicitLanguagesSelectTheirOwnPrimaryAndUnknownLanguageRequiresChoice() {
        val settings = DictionaryQuerySettings().withPrimary("en", english.id).withPrimary("zh", chinese.id)
        val catalog = listOf(english, chinese)
        assertEquals(english.id, (DictionaryQueryPlanner.plan("en", settings, catalog) as DictionaryQueryPlan.Ready).primary.id)
        assertEquals(chinese.id, (DictionaryQueryPlanner.plan("zh", settings, catalog) as DictionaryQueryPlan.Ready).primary.id)
        assertEquals(DictionaryQueryPlan.ChooseLanguage, DictionaryQueryPlanner.plan(null, settings, catalog))
        assertTrue(DictionaryQueryPlanner.plan("en", settings.withPrimary("en", "deleted"), catalog) is DictionaryQueryPlan.Unavailable)
        assertTrue(DictionaryQueryPlanner.plan("en", settings, listOf(english.copy(state = "missing"), chinese)) is DictionaryQueryPlan.Unavailable)
    }

    @Test fun noPreferenceUsesOnlyOneClassifiedReadySourceOtherwiseRequiresChoice() {
        val settings = DictionaryQuerySettings()
        assertEquals(english.id, (DictionaryQueryPlanner.plan("en", settings, listOf(english, chinese)) as DictionaryQueryPlan.Ready).primary.id)
        assertTrue(DictionaryQueryPlanner.plan("en", settings, listOf(english, english.copy(id = "second"))) is DictionaryQueryPlan.ChooseDictionary)
        assertTrue(DictionaryQueryPlanner.plan("en", settings, listOf(english.copy(inputLanguage = ""))) is DictionaryQueryPlan.ChooseDictionary)
        assertTrue(settings.languages.isEmpty())
    }
}
