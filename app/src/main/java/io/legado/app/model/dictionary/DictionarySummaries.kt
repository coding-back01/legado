package io.legado.app.model.dictionary

data class DictionarySummary(val definition: String, val pronunciation: String? = null,
    val partOfSpeech: String? = null, val examples: List<String> = emptyList(), val truncated: Boolean = false)

data class DictionaryEntryPreview(val entry: DictionaryEntry, val summary: DictionarySummary?)

object DictionarySummaries {
    const val VERSION = 1
    fun analyze(entry: DictionaryEntry): DictionaryEntryPreview {
        DictionaryLimits.check(entry.html.length <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
        val document = org.jsoup.Jsoup.parse(entry.html)
        if (document.select("script").isNotEmpty() || document.allElements.any { element ->
                element.attributes().any { it.key.startsWith("on", true) }
            }) return DictionaryEntryPreview(entry, null)
        if (document.select("table, svg, math, frameset, details").isNotEmpty()) return DictionaryEntryPreview(entry, null)
        val definitions = document.select(".definition, def").map { it.text().trim() }.filter { it.isNotBlank() }
        if (definitions.isEmpty()) return DictionaryEntryPreview(entry, null)
        if (definitions.any { it.equals(entry.headword, true) }) return DictionaryEntryPreview(entry, null)
        if (definitions.any { Regex("(?i)^(?:copyright\\b.*[0-9]{4}|all rights reserved|版权(?:说明|声明|所有))").containsMatchIn(it) })
            return DictionaryEntryPreview(entry, null)
        val full = definitions.joinToString("\n")
        val points = full.codePointCount(0, full.length)
        val truncated = points > 480
        val short = if (truncated) full.substring(0, full.offsetByCodePoints(0, 480)) else full
        return DictionaryEntryPreview(entry, DictionarySummary(short,
            document.selectFirst(".phonetic, .pronunciation")?.text()?.takeIf { it.isNotBlank() },
            document.selectFirst(".pos")?.text()?.takeIf { it.isNotBlank() },
            document.select(".example").map { it.text() }.filter { it.isNotBlank() }, truncated))
    }
}
