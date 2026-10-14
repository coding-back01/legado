package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Test
import io.legado.app.data.entities.LocalDictionary

class DictionaryQuerySettingsTest {
    @Test fun languagePreferencesAreIndependentAndFallbackOrderIsExplicit() {
        val settings = DictionaryQuerySettings()
            .withPrimary("zh", "chinese")
            .withPrimary("en", "english")
            .withFallbacks("en", listOf("second", "english", "third", "second"))
        assertEquals(2, settings.version)
        assertEquals("chinese", settings.languages["zh"]?.primaryId)
        assertEquals("english", settings.languages["en"]?.primaryId)
        assertEquals(listOf("second", "third"), settings.languages["en"]?.fallbackIds)
        assertEquals(emptyList<String>(), settings.languages["zh"]?.fallbackIds)
        val restored = DictionaryQuerySettings.decode(settings.encode())
        assertEquals(settings, restored)
    }

    @Test fun userLanguageWinsWithoutChangingMetadataAndSurvivesSerialization() {
        val dictionary = LocalDictionary("a", "sample", "MDICT", "sample.mdx", "hash", inputLanguage = "German")
        val settings = DictionaryQuerySettings().withLanguage(dictionary.id, "en")
        assertEquals("en", settings.languageOf(dictionary))
        assertEquals("German", dictionary.inputLanguage)
        val restored = requireNotNull(DictionaryQuerySettings.decode(settings.encode()))
        assertEquals("en", restored.languageOf(dictionary.copy(entryCount = 10)))
        assertNull(restored.languageOf(dictionary.copy(id = "unclassified", inputLanguage = "")))
        assertEquals("zh", restored.languageOf(dictionary.copy(id = "chinese", inputLanguage = "Chinese")))
    }

    @Test fun explicitUnclassifiedChoiceOverridesDeclaredLanguageAndSurvivesRestoreMapping() {
        val dictionary = LocalDictionary("classified", "sample", "DSL", "sample.dsl", "hash", inputLanguage = "English")
        val settings = DictionaryQuerySettings().withLanguage(dictionary.id, "")
        assertNull("明确选择未分类不能重新采用词库声明的语言", settings.languageOf(dictionary))
        val restored = requireNotNull(DictionaryQuerySettings.decode(settings.encode())).remap(mapOf(dictionary.id to "restored"))
        assertNull(restored.languageOf(dictionary.copy(id = "restored")))
        assertEquals("English", dictionary.inputLanguage)
    }

    @Test fun legacySelectionMigratesOnceWithoutReplacingNewSettings() {
        val english = LocalDictionary("old", "old", "DSL", "old.dsl", "hash", inputLanguage = "English")
        val migrated = DictionaryQuerySettings().migrateLegacy("old", listOf(english))
        assertEquals("old", migrated.languages["en"]?.primaryId)
        assertEquals("old", migrated.legacySelectedId)
        assertTrue(migrated.legacyMigrated)
        val changed = migrated.withPrimary("en", "new").withFallbacks("en", listOf("fallback"))
        assertEquals(changed, changed.migrateLegacy("old", listOf(english)))
        val alreadyConfigured = DictionaryQuerySettings().withPrimary("en", "new")
            .migrateLegacy("old", listOf(english))
        assertEquals("new", alreadyConfigured.languages["en"]?.primaryId)
    }

    @Test fun unclassifiedAndDeletedLegacyChoicesKeepTheirIdsWithoutGuessingLanguage() {
        val unclassified = LocalDictionary("old", "English sounding name", "MDICT", "old.mdx", "hash")
        val migrated = DictionaryQuerySettings().migrateLegacy("old", listOf(unclassified))
        assertEquals("old", migrated.legacySelectedId)
        assertTrue(migrated.languages.isEmpty())
        assertEquals("deleted", DictionaryQuerySettings().migrateLegacy("deleted", emptyList()).legacySelectedId)
        val configured = DictionaryQuerySettings().withPrimary("en", "old").withFallbacks("en", listOf("new"))
        assertEquals("old", configured.migrateLegacy("old", listOf(unclassified.copy(state = "missing")))
            .languages["en"]?.primaryId)
    }

    @Test fun declaredIsoThreeLetterLanguagesRemainUsableForClassification() {
        assertEquals("en", DictionaryLanguages.canonical("eng"))
        assertEquals("zh", DictionaryLanguages.canonical("zho"))
        assertEquals("pl", DictionaryLanguages.canonical("pol"))
        assertNull(DictionaryLanguages.canonical("unclassified"))
    }

    @Test fun restoreRemapsPreferencesInOrderWithoutDroppingUnavailableReferences() {
        val settings = DictionaryQuerySettings().withPrimary("en", "old-a").withPrimary("zh", "old-z")
            .withFallbacks("en", listOf("old-b", "old-c", "missing"))
            .withLanguage("old-a", "en").withLanguage("old-z", "zh")
        val restored = settings.remap(mapOf("old-a" to "new-a", "old-b" to "new-b", "old-c" to "new-b", "old-z" to "new-z"))
        assertEquals("new-a", restored.languages["en"]?.primaryId)
        assertEquals(listOf("new-b", "missing"), restored.languages["en"]?.fallbackIds)
        assertEquals("new-z", restored.languages["zh"]?.primaryId)
        assertEquals(mapOf("new-a" to "en", "new-z" to "zh"), restored.languageOverrides)
    }

    @Test fun replayingTheSameRestoreDoesNotReplaceNewUserPreferences() {
        val current = DictionaryQuerySettings().withPrimary("en", "new-choice").withFallbacks("en", listOf("new-fallback"))
        val oldBackup = DictionaryQuerySettings().withPrimary("en", "old-choice")
        assertEquals(current, current.restored(oldBackup, emptyMap(), true))
        assertEquals("mapped", current.restored(oldBackup, mapOf("old-choice" to "mapped"), false).languages["en"]?.primaryId)
    }
}
