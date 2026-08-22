package io.legado.app.model.read

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.ln

class ReadingTimeReadConfigTest {

    @Test
    fun `legacy read config uses empty reading time state`() {
        val config = gson.fromJson("""{"reverseToc":true}""", Book.ReadConfig::class.java)

        requireNotNull(config)
        assertTrue(config.reverseToc)
        assertNull(config.readingTimeState)
    }

    @Test
    fun `reading time state survives json round trip`() {
        val state = ReadingTimeState(
            recentLogSecondsPerUnit = ln(0.321),
            recentLogMad = 0.08,
            recentEvidenceMillis = 70_000L,
            acceptedSampleCount = 6,
            totalEffectiveReadingMillis = 70_000L,
            bookIdentityHash = 12L,
            tocChapterCount = 30,
            tocPrefixHash = 34L,
            sourceLastModified = 56L,
        )
        val restored = gson.fromJson(
            gson.toJson(Book.ReadConfig(readingTimeState = state)),
            Book.ReadConfig::class.java,
        )

        assertEquals(state, requireNotNull(restored).readingTimeState)
    }

    @Test
    fun `record disabled snapshot hides every reading time field`() {
        val snapshot = ReadingTimeDisplayFormatter.unavailableSnapshot("—")

        assertEquals("—", snapshot.accumulated)
        assertEquals("—", snapshot.remaining)
        assertEquals("—", snapshot.combined)
    }

    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, object : TypeAdapter<LocalDate>() {
            override fun write(out: JsonWriter, value: LocalDate?) {
                if (value == null) out.nullValue() else out.value(value.toString())
            }

            override fun read(input: JsonReader): LocalDate? {
                return if (input.peek() == com.google.gson.stream.JsonToken.NULL) {
                    input.nextNull()
                    null
                } else {
                    LocalDate.parse(input.nextString())
                }
            }
        })
        .create()
}
