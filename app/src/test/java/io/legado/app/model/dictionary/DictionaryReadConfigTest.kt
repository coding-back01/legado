package io.legado.app.model.dictionary

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.legado.app.data.entities.Book
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DictionaryReadConfigTest {
    private val gson = GsonBuilder().registerTypeAdapter(LocalDate::class.java, object : TypeAdapter<LocalDate>() {
        override fun write(out: JsonWriter, value: LocalDate?) { if (value == null) out.nullValue() else out.value(value.toString()) }
        override fun read(input: JsonReader): LocalDate? = if (input.peek() == JsonToken.NULL) { input.nextNull(); null }
            else LocalDate.parse(input.nextString())
    }).create()

    @Test fun oldBooksStayDisabledAndExplicitBookChoiceSurvivesJsonWithoutChangingOtherSettings() {
        val old = gson.fromJson("""{"reverseToc":true,"pageAnim":2}""", Book.ReadConfig::class.java)
        assertFalse(old.dictionaryInstantLookup)
        assertNull(old.dictionaryQueryLanguage)
        val bookA = gson.fromJson("""{"reverseToc":true,"pageAnim":2,"dictionaryInstantLookup":true,"dictionaryQueryLanguage":"en"}""",
            Book.ReadConfig::class.java)
        assertTrue(bookA.dictionaryInstantLookup)
        assertEquals("en", bookA.dictionaryQueryLanguage)
        val restored = gson.fromJson(gson.toJson(bookA), Book.ReadConfig::class.java)
        assertTrue(restored.dictionaryInstantLookup)
        assertEquals("en", restored.dictionaryQueryLanguage)
        assertTrue(restored.reverseToc)
        assertEquals(2, restored.pageAnim)
        val bookB = gson.fromJson("{}", Book.ReadConfig::class.java)
        assertFalse(bookB.dictionaryInstantLookup)
        assertNull(bookB.dictionaryQueryLanguage)
    }
}
