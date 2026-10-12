package io.legado.app.model.dictionary

data class DictionaryDisplayKey(val dictionaryId: String, val fingerprint: String, val entries: List<Long>,
    val night: Boolean, val fontSize: Int, val strategy: Int)

class DictionaryDisplayCache(private val maximumBytes: Long = 4L * 1024 * 1024) {
    private val values = java.util.LinkedHashMap<DictionaryDisplayKey, DictionaryDisplay>(16, 0.75f, true)
    @Synchronized fun get(key: DictionaryDisplayKey): DictionaryDisplay? = values[key]
    @Synchronized fun put(key: DictionaryDisplayKey, value: DictionaryDisplay) {
        values.remove(key)
        if (weight(value) > maximumBytes) return
        values[key] = value
        while (bytes() > maximumBytes && values.isNotEmpty()) values.remove(values.keys.first())
    }
    @Synchronized fun bytes(): Long = values.values.sumOf { weight(it) }
    private fun weight(value: DictionaryDisplay): Long = (value.html.length + value.originalText.length) * 2L + value.previews.sumOf {
        val summary = it.summary
        it.entry.html.length * 2L + it.entry.headword.length * 2L + (summary?.definition?.length ?: 0) * 2L +
            (summary?.pronunciation?.length ?: 0) * 2L + (summary?.partOfSpeech?.length ?: 0) * 2L +
            summary?.examples.orEmpty().sumOf { text -> text.length * 2L } + 256
    }
}
