package io.legado.app.model.dictionary

import com.google.gson.JsonParser
import io.legado.app.data.entities.LocalDictionary
import org.junit.Assert.*
import org.junit.Test

class DictionaryBackupCodecTest {
    @Test fun ordinaryVersionTwoCatalogContainsPreferencesAndIdentityButNoFilePayloadOrIndex() {
        val id = "00000000-0000-0000-0000-000000000001"
        val settings = DictionaryQuerySettings().withLanguage(id, "en").withPrimary("en", id)
            .withFallbacks("en", listOf("second", "third"))
        val dictionary = LocalDictionary(id, "title", "MDICT", "first.mdx", "a".repeat(64), identityVersion = 2)
        val record = DictionaryIdentityRecord(DictionaryFileGroup("first.mdx", listOf("first.mdx")), listOf("b".repeat(64)))
        val manifest = LocalDictionaryStore.BackupManifest(selectedId = id, includesFiles = false, dictionaries = listOf(dictionary))
        val encoded = DictionaryCatalogCodec.encodeBackup(manifest, settings, mapOf(id to record))
        val json = JsonParser.parseString(encoded).asJsonObject
        assertEquals(2, json.get("version").asInt)
        assertEquals(2, json.getAsJsonArray("dictionaries")[0].asJsonObject.get("identityVersion").asInt)
        assertEquals(settings, DictionaryQuerySettings.decode(json.get("querySettings").toString()))
        assertEquals("b".repeat(64), json.getAsJsonObject("identities").getAsJsonObject(id).getAsJsonArray("legacyFingerprints")[0].asString)
        assertFalse(json.get("includesFiles").asBoolean)
        assertFalse(encoded.contains("index.db"))
        assertFalse(encoded.contains("entries.bin"))
        assertFalse(encoded.contains("/Users/"))
    }

    @Test fun versionTwoCatalogDecodesPreferencesAndVerifiedAliases() {
        val settings = DictionaryQuerySettings().withPrimary("en", "first").withFallbacks("en", listOf("second"))
        val dictionary = LocalDictionary("first", "title", "MDICT", "first.mdx", "a".repeat(64), identityVersion = 2)
        val record = DictionaryIdentityRecord(DictionaryFileGroup("first.mdx", listOf("first.mdx")), listOf("b".repeat(64)))
        val encoded = DictionaryCatalogCodec.encodeBackup(LocalDictionaryStore.BackupManifest(
            selectedId = "first", includesFiles = false, dictionaries = listOf(dictionary)), settings, mapOf("first" to record))
        val decoded = requireNotNull(DictionaryCatalogCodec.decodeBackup(encoded))
        assertEquals(settings, decoded.querySettings)
        assertEquals(record, decoded.identities["first"])
        assertEquals(listOf(dictionary), decoded.dictionaries)
    }

    @Test fun unknownPreferencesVersionIsNotTreatedAsMissingLegacySettings() {
        assertNull(DictionaryCatalogCodec.decodeBackup("""{"version":2,"dictionaries":[],"includesFiles":false,"querySettings":{"version":99}}"""))
        assertNull(DictionaryCatalogCodec.decodeBackup("""{"version":99,"dictionaries":[]}"""))
    }

    @Test fun restoreKeyIgnoresWhitespaceAndObjectKeyOrder() {
        assertEquals(DictionaryCatalogCodec.restoreKey("""{"version":2,"dictionaries":[],"includesFiles":false}"""),
            DictionaryCatalogCodec.restoreKey("""{ "includesFiles": false, "dictionaries": [], "version": 2 }"""))
    }
}
