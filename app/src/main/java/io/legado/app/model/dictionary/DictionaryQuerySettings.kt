package io.legado.app.model.dictionary

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.legado.app.data.entities.LocalDictionary
import java.util.Locale

object DictionaryLanguages {
    private val isoLanguages = Locale.getISOLanguages().toSet()
    private val isoThreeLetterLanguages = isoLanguages.mapNotNull { language ->
        runCatching { Locale(language).getISO3Language() to language }.getOrNull()
    }.toMap()
    fun canonical(value: String?): String? {
        val text = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val named = when (text) {
            "english" -> "en"
            "chinese", "chinese (simplified)", "chinese (traditional)", "simplified chinese", "traditional chinese" -> "zh"
            "german" -> "de"
            "french" -> "fr"
            "japanese" -> "ja"
            "korean" -> "ko"
            else -> text.substringBefore('-').substringBefore('_')
        }
        return named.takeIf { it in isoLanguages } ?: isoThreeLetterLanguages[named]
    }
}

data class DictionaryLanguagePreference(val primaryId: String? = null, val fallbackIds: List<String> = emptyList())

data class DictionaryQuerySettings(
    val version: Int = 2,
    val languages: Map<String, DictionaryLanguagePreference> = emptyMap(),
    val languageOverrides: Map<String, String> = emptyMap(),
    val legacySelectedId: String? = null,
    val legacyMigrated: Boolean = false
) {
    fun restored(incoming: DictionaryQuerySettings?, mapping: Map<String, String>, repeated: Boolean): DictionaryQuerySettings =
        if (repeated) this else incoming?.remap(mapping) ?: this

    fun remap(mapping: Map<String, String>): DictionaryQuerySettings {
        fun id(value: String): String = mapping[value] ?: value
        return copy(languages = languages.mapValues { (_, preference) ->
            val primary = preference.primaryId?.let(::id)
            preference.copy(primaryId = primary, fallbackIds = preference.fallbackIds.map(::id).distinct().filter { it != primary })
        }, languageOverrides = languageOverrides.entries.associate { id(it.key) to it.value },
            legacySelectedId = legacySelectedId?.let(::id))
    }

    fun migrateLegacy(id: String?, dictionaries: List<LocalDictionary>): DictionaryQuerySettings {
        if (legacyMigrated) return this
        val migrated = copy(legacySelectedId = id, legacyMigrated = true)
        val dictionary = dictionaries.firstOrNull { it.id == id && it.state == "ready" } ?: return migrated
        val language = languageOf(dictionary) ?: return migrated
        return if (languages[language]?.primaryId == null) migrated.withPrimary(language, id) else migrated
    }

    fun withLanguage(id: String, language: String?): DictionaryQuerySettings {
        val tag = DictionaryLanguages.canonical(language)
        // 空字符串表示用户明确选择未分类；null 才是撤销覆盖、恢复原始元数据。
        return copy(languageOverrides = when {
            language != null && language.isBlank() -> languageOverrides + (id to "")
            tag == null -> languageOverrides - id
            else -> languageOverrides + (id to tag)
        })
    }

    fun languageOf(dictionary: LocalDictionary): String? = DictionaryLanguages.canonical(
        languageOverrides[dictionary.id] ?: dictionary.inputLanguage
    )

    fun withPrimary(language: String, id: String?): DictionaryQuerySettings {
        val previous = languages[language] ?: DictionaryLanguagePreference()
        return copy(languages = languages + (language to previous.copy(primaryId = id,
            fallbackIds = previous.fallbackIds.filter { it != id }.distinct())))
    }

    fun withFallbacks(language: String, ids: List<String>): DictionaryQuerySettings {
        val previous = languages[language] ?: DictionaryLanguagePreference()
        return copy(languages = languages + (language to previous.copy(fallbackIds = ids.filter {
            it.isNotBlank() && it != previous.primaryId
        }.distinct())))
    }

    fun encode(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()
        fun decode(json: String?): DictionaryQuerySettings? {
            if (json.isNullOrBlank() || json.length > DictionaryLimits.MAX_ENTRY_BYTES) return null
            return runCatching {
                val objectValue = JsonParser.parseString(json).asJsonObject
                if (objectValue.get("version")?.asInt != 2) return null
                gson.fromJson(objectValue, DictionaryQuerySettings::class.java)
            }.getOrNull()
        }
    }
}
