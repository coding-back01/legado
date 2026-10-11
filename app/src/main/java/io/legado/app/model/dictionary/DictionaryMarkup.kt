package io.legado.app.model.dictionary

import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser

object DictionaryMarkup {
    fun xdxf(xml: String): String {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        document.select("k").remove()
        document.select("kref").forEach { it.tagName("a").attr("href", dictionaryLink(it.text())) }
        document.select("rref").forEach { it.tagName("img").attr("src", it.text()).empty() }
        document.select("tr").forEach { it.tagName("span").addClass("pronunciation") }
        document.select("def").forEach { it.tagName("div") }
        return document.html()
    }

    fun dsl(text: String, headword: String): String {
        var html = Entities.escape(text.replace("~", headword))
        html = Regex("\\[s\\](.*?)\\[/s\\]", RegexOption.DOT_MATCHES_ALL).replace(html) {
            val path = Entities.unescape(it.groupValues[1]).trim()
            val escaped = Entities.escape(path)
            if (Regex("(?i)\\.(wav|mp3|ogg|m4a|flac)$").containsMatchIn(path)) {
                "<a href=\"sound://$escaped\">♪</a>"
            } else "<img src=\"$escaped\">"
        }
        html = Regex("\\[ref\\](.*?)\\[/ref\\]", RegexOption.DOT_MATCHES_ALL).replace(html) {
            "<a href=\"${dictionaryLink(Entities.unescape(it.groupValues[1]))}\">${it.groupValues[1]}</a>"
        }
        for ((tag, output) in mapOf("b" to "b", "i" to "i", "u" to "u", "p" to "i", "ex" to "span", "trn" to "span")) {
            html = html.replace("[$tag]", "<$output>").replace("[/$tag]", "</$output>")
        }
        html = Regex("\\[c(?: [^\\]]+)?\\]").replace(html, "<span>").replace("[/c]", "</span>")
        html = Regex("\\[m[0-9]*\\]").replace(html, "<div>").replace("[/m]", "</div>")
        html = html.replace("[sup]", "<sup>").replace("[/sup]", "</sup>")
            .replace("[sub]", "<sub>").replace("[/sub]", "</sub>")
        return html.replace("\n", "<br>")
    }
}
