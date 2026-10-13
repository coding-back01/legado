package io.legado.app.model.dictionary

import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist
import java.net.URLDecoder

object DictionaryHtml {
    const val HOST = "dictionary.local"

    fun render(html: String, dictionaryId: String, night: Boolean, fontSize: Int): String {
        DictionaryLimits.check(html.length <= DictionaryLimits.MAX_ENTRY_BYTES && html.count { it == '<' } <= 100_000, DictionaryProblem.LIMIT)
        val document = Jsoup.parse(html, "https://$HOST/$dictionaryId/")
        document.select("script,iframe,object,embed,form,meta,base").remove()
        val styles = document.select("style").map { it.data() }
        val styleLinks = document.select("link[rel=stylesheet]").map { it.attr("href") }
        document.select("img").forEach { image ->
            if (!image.hasAttr("src") && image.hasAttr("data-src")) image.attr("src", image.attr("data-src"))
        }
        val safe = Safelist.relaxed().addTags("span", "div", "sup", "sub", "hr")
            .addAttributes(":all", "class", "style", "id", "title")
            .addProtocols("a", "href", "entry", "entry-id", "bword", "sound")
            .addProtocols("img", "src", "https")
            .preserveRelativeLinks(true)
        val content = Cleaner(safe).clean(document)
        content.select("[style]").forEach { element ->
            element.attr("style", visibleCss(element.attr("style")))
        }
        val background = if (night) "#202020" else "#ffffff"
        val text = if (night) "#dedede" else "#222222"
        val csp = "default-src 'none'; img-src https://$HOST; style-src 'unsafe-inline' https://$HOST; font-src https://$HOST; script-src 'none'; base-uri 'none'"
        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(csp).append("\">")
            // WebView 使用应用生成的基地址，词库自己的 base 标签已移除。
            styles.forEach { append("<style>").append(visibleCss(it).replace("</style", "", true)).append("</style>") }
            styleLinks.filter { isLocal(it) }.forEach {
                append("<link rel=\"stylesheet\" href=\"").append(Entities.escape(it)).append("\">")
            }
            append("<style>body{background:").append(background).append("!important;color:").append(text)
            append("!important;font-size:").append(fontSize.coerceIn(12, 40)).append("px!important;line-height:1.55;font-family:serif;margin:0;padding:16px;overflow-wrap:anywhere}")
            append("p,div,span,td,th,li{font-size:inherit!important}img{max-width:100%;height:auto}table{max-width:100%;display:block;overflow-x:auto}p{margin:0 0 0.8em}hr{border:0;border-top:1px solid #888;margin:1em 0}")
            if (night) append("body *{color:").append(text).append("!important;background-color:transparent!important}")
            append(".pronunciation,.phonetic{margin-right:.75em}.pos{font-style:italic}.example{display:block;font-style:italic;margin:.6em 0}.definition{margin:.6em 0}")
            append("a{color:#448aff!important;text-decoration:underline}</style>")
            append("</head><body>").append(content.body().html()).append("</body></html>")
        }
    }

    fun visibleCss(css: String): String = css
        .replace(Regex("(?i)display\\s*:\\s*none[^;{}]*;?"), "")
        .replace(Regex("(?i)visibility\\s*:\\s*hidden[^;{}]*;?"), "")

    fun isLocal(value: String): Boolean = !Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(value) && !value.startsWith("//")

    fun lookupLink(link: String): String? = when {
        link.startsWith("entry://lookup/") -> URLDecoder.decode(link.removePrefix("entry://lookup/"), "UTF-8")
        link.startsWith("entry://") -> android.net.Uri.decode(link.removePrefix("entry://"))
        link.startsWith("bword://") -> android.net.Uri.decode(link.removePrefix("bword://"))
        else -> null
    }
}
