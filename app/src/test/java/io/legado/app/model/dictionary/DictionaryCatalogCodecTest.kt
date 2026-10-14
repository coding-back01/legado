package io.legado.app.model.dictionary

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class DictionaryCatalogCodecTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/dictionaries/reading/$name"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    @Test fun oldCatalogDefaultsIdentityToOneAndNewCatalogRetainsExplicitVersion() {
        val old = DictionaryCatalogCodec.decodeDictionary(fixture("catalog-v1.json").getAsJsonArray("dictionaries")[0].asJsonObject)
        assertEquals(1, old.identityVersion)
        assertEquals("00000000-0000-0000-0000-000000000001", old.id)
        assertEquals(3, old.sortNumber)
        val current = DictionaryCatalogCodec.decodeDictionary(fixture("catalog-v2.json").getAsJsonArray("dictionaries")[0].asJsonObject)
        assertEquals(2, current.identityVersion)
        assertEquals(old.id, current.id)
    }
}
