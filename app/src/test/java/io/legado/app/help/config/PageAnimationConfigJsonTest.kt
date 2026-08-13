package io.legado.app.help.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import io.legado.app.constant.PageAnim
import io.legado.app.data.entities.Book
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PageAnimationConfigJsonTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(
            LocalDate::class.java,
            JsonSerializer<LocalDate> { value, _, _ -> JsonPrimitive(value.toString()) },
        )
        .registerTypeAdapter(
            LocalDate::class.java,
            JsonDeserializer { json, _, _ -> LocalDate.parse(json.asString) },
        )
        .create()

    @Test
    fun `global reading config preserves Kindle values`() {
        val config = gson.fromJson(
            """{"pageAnim":5,"pageAnimEInk":5}""",
            ReadBookConfig.Config::class.java,
        )

        val json = JsonParser.parseString(gson.toJson(config)).asJsonObject

        assertEquals(PageAnim.kindlePageAnim, json["pageAnim"].asInt)
        assertEquals(PageAnim.kindlePageAnim, json["pageAnimEInk"].asInt)
    }

    @Test
    fun `old global config keeps historical defaults when animation fields are absent`() {
        val config = gson.fromJson("{}", ReadBookConfig.Config::class.java)
        val json = JsonParser.parseString(gson.toJson(config)).asJsonObject

        assertEquals(PageAnim.coverPageAnim, json["pageAnim"].asInt)
        assertEquals(PageAnim.noAnim, json["pageAnimEInk"].asInt)
    }

    @Test
    fun `per book config preserves Kindle value`() {
        val config = gson.fromJson(
            """{"pageAnim":5}""",
            Book.ReadConfig::class.java,
        )

        val roundTrip = gson.fromJson(gson.toJson(config), Book.ReadConfig::class.java)

        assertEquals(PageAnim.kindlePageAnim, roundTrip.pageAnim)
    }

    @Test
    fun `old per book config remains without an override`() {
        val config = gson.fromJson("{}", Book.ReadConfig::class.java)

        assertNull(config.pageAnim)
        assertFalse(config.reverseToc)
    }
}
