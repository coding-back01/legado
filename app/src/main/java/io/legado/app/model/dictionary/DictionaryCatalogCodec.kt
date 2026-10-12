package io.legado.app.model.dictionary

import com.google.gson.JsonObject
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import io.legado.app.data.entities.LocalDictionary

internal object DictionaryCatalogCodec {
    fun restoreKey(json: String): String {
        fun canonical(value: com.google.gson.JsonElement, depth: Int = 0): com.google.gson.JsonElement {
            DictionaryLimits.check(depth <= 64, DictionaryProblem.LIMIT)
            return when {
                value.isJsonObject -> JsonObject().also { result ->
                    value.asJsonObject.entrySet().sortedBy { it.key }.forEach { result.add(it.key, canonical(it.value, depth + 1)) }
                }
                value.isJsonArray -> com.google.gson.JsonArray().also { result ->
                    value.asJsonArray.forEach { result.add(canonical(it, depth + 1)) }
                }
                else -> value
            }
        }
        return java.security.MessageDigest.getInstance("SHA-256").digest(canonical(JsonParser.parseString(json)).toString()
            .toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
    }

    fun decodeBackup(json: String): LocalDictionaryStore.BackupManifest? {
        DictionaryLimits.check(json.length <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
        val value = JsonParser.parseString(json).asJsonObject
        val version = value.get("version")?.asInt ?: return null
        if (version !in 1..2) return null
        val list = value.getAsJsonArray("dictionaries") ?: throw DictionaryException(DictionaryProblem.CORRUPT)
        DictionaryLimits.check(list.size() <= DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
        val dictionaries = list.map { decodeDictionary(it.asJsonObject) }
        val settings = value.get("querySettings")?.takeUnless { it.isJsonNull }?.let {
            DictionaryQuerySettings.decode(it.toString())
        }
        if (value.get("querySettings")?.isJsonNull == false && settings == null) return null
        val identities: Map<String, DictionaryIdentityRecord> = value.get("identities")?.takeUnless { it.isJsonNull }?.let {
            Gson().fromJson(it, object : TypeToken<Map<String, DictionaryIdentityRecord>>() {}.type)
        } ?: emptyMap()
        return LocalDictionaryStore.BackupManifest(version,
            value.get("selectedId")?.takeUnless { it.isJsonNull }?.asString,
            value.get("includesFiles")?.asBoolean == true, dictionaries, settings, identities)
    }

    fun encodeBackup(manifest: LocalDictionaryStore.BackupManifest, settings: DictionaryQuerySettings,
        identities: Map<String, DictionaryIdentityRecord>): String = Gson().toJson(manifest.copy(
            version = 2, querySettings = settings, identities = identities
        ))

    fun decodeDictionary(item: JsonObject): LocalDictionary = LocalDictionary(
        item.get("id").asString, item.get("name").asString, item.get("format").asString,
        item.get("sourceFile").asString, item.get("fingerprint").asString,
        item.get("inputLanguage")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
        item.get("outputLanguage")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
        item.get("sortNumber")?.asInt ?: 0, item.get("state").asString,
        item.get("entryCount")?.asLong ?: 0, item.get("warningCount")?.asInt ?: 0,
        item.get("identityVersion")?.asInt ?: 1
    )
}
