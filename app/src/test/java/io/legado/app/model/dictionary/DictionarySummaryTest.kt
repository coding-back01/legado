package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Test

class DictionarySummaryTest {
    @org.junit.Test fun xdxfDefinitionBoundarySurvivesMarkupConversion() {
        val html = DictionaryMarkup.xdxf("<ar><k>word</k><def>a reliable definition</def></ar>")
        val preview = DictionarySummaries.analyze(DictionaryEntry(1, "word", html))
        org.junit.Assert.assertNotNull("XDXF 明确定义边界不能在转换时丢失", preview.summary)
        org.junit.Assert.assertEquals("a reliable definition", preview.summary?.definition)
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/dictionaries/reading/$name"))
        .bufferedReader().use { it.readText() }

    @Test fun explicitlyMarkedDefinitionAndOptionalFieldsPreserveOriginalArticle() {
        val entry = DictionaryEntry(1, "sing", fixture("definition.html"))
        val preview = DictionarySummaries.analyze(entry)
        val summary = requireNotNull(preview.summary)
        assertEquals("用声音表现乐曲。", summary.definition)
        assertEquals("/sɪŋ/", summary.pronunciation)
        assertEquals("verb", summary.partOfSpeech)
        assertEquals(listOf("The birds sing."), summary.examples)
        assertEquals(entry, preview.entry)
        assertFalse(summary.truncated)
    }

    @Test fun copyrightHeadwordComplexLayoutAndScriptDependenceNeverBecomeDefinitions() {
        for (html in listOf("<p>Copyright 2026</p><h2>sing</h2>", "<div class=definition>sing</div>",
            "<div class=definition>Copyright 2026. All rights reserved.</div>",
            "<table><tr><td class=definition>uncertain layout</td></tr></table>",
            "<div class=definition onclick='expand()'>potentially incomplete</div>",
            "<script>expandArticle()</script><div class=definition>potentially incomplete</div>", fixture("complex.html"))) {
            val entry = DictionaryEntry(1, "sing", html)
            val preview = DictionarySummaries.analyze(entry)
            assertNull(html, preview.summary)
            assertEquals(html, preview.entry.html)
        }
    }

    @Test fun longReliableDefinitionsAreMarkedAsTruncatedAndFullArticlesRemainSeparate() {
        val text = "释义内容".repeat(300)
        val first = DictionaryEntry(1, "leaf", "<div class=definition>$text</div>")
        val second = DictionaryEntry(2, "leave", "<div class=definition>离开。</div>")
        val previews = listOf(first, second).map(DictionarySummaries::analyze)
        assertTrue(requireNotNull(previews[0].summary).truncated)
        assertTrue(requireNotNull(previews[0].summary).definition.length <= 480)
        assertEquals(first.html, previews[0].entry.html)
        assertEquals(listOf("leaf", "leave"), previews.map { it.entry.headword })
        assertEquals("离开。", previews[1].summary?.definition)
        assertFalse(requireNotNull(previews[1].summary).truncated)
    }
}
