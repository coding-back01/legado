package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Test

class DictionaryHtmlTest {
    @Test fun scriptsAndEmbeddingAreRemovedWithoutDroppingDefinitions() {
        val result = DictionaryHtml.render("<script>bad()</script><iframe src='https://bad.invalid'></iframe><p onclick='bad()'>meaning</p>", "test", false, 18)
        assertTrue(result.contains("meaning"))
        assertFalse(result.contains("bad()"))
        assertFalse(result.contains("iframe"))
        assertTrue(result.contains("script-src 'none'"))
    }

    @Test fun localStylesAndResourcesArePreservedAndHiddenDefinitionsAreReadable() {
        val result = DictionaryHtml.render("<link rel='stylesheet' href='dict.css'><p style='display:none'>meaning</p><img src='diagram.png'><a href='entry://go'>go</a>", "test", true, 18)
        assertTrue(result.contains("dict.css"))
        assertTrue(result.contains("diagram.png"))
        assertTrue(result.contains("entry://go"))
        assertFalse(result.contains("display:none"))
        assertTrue(result.contains("background-color:transparent!important"))
        assertTrue(result.contains("font-size:18px!important"))
    }

    @Test fun irregularAndRegularEnglishFormsAreBounded() {
        assertEquals(listOf("go"), EnglishDictionaryForms.candidates("went"))
        assertTrue(EnglishDictionaryForms.candidates("running").contains("run"))
        assertTrue(EnglishDictionaryForms.candidates("studied").contains("study"))
        assertTrue(EnglishDictionaryForms.candidates("boxes").contains("box"))
        assertTrue(EnglishDictionaryForms.candidates("news").isEmpty())
        assertTrue(EnglishDictionaryForms.candidates("business").isEmpty())
        assertTrue(EnglishDictionaryForms.candidates("學習").isEmpty())
        assertTrue(EnglishDictionaryForms.candidates("한글").isEmpty())
        assertEquals(listOf("give up"), EnglishDictionaryForms.candidates("gave up"))
        assertTrue(EnglishDictionaryForms.candidates("hoped").contains("hope"))
        assertFalse(EnglishDictionaryForms.candidates("hoped").contains("hop"))
        assertTrue(EnglishDictionaryForms.candidates("scared").contains("scare"))
        assertFalse(EnglishDictionaryForms.candidates("scared").contains("scar"))
        assertTrue(EnglishDictionaryForms.candidates("hotter").contains("hot"))
        assertTrue(EnglishDictionaryForms.candidates("menus").contains("menu"))
    }

    @Test fun unsafeProtocolsAndRemoteStylesCannotAcquireLocalViewPermissions() {
        val html = DictionaryHtml.render("<link rel='stylesheet' href='https://remote.invalid/x.css'>" +
            "<meta http-equiv='refresh' content='0;url=https://remote.invalid'><base href='file:///'>" +
            "<a href='javascript:bad()'>readable meaning</a><img src='file:///private/data'>" +
            "<img src='https://remote.invalid/image.png'>", "test", false, 18)
        assertTrue(html.contains("readable meaning"))
        assertFalse(html.contains("javascript:"))
        assertFalse(html.contains("file:///"))
        assertFalse(html.contains("remote.invalid/x.css"))
        assertFalse(html.contains("http-equiv=\"refresh\""))
        assertTrue(html.contains("img-src https://${DictionaryHtml.HOST}"))
    }
}
