package io.legado.app.model.dictionary

import io.legado.app.data.entities.LocalDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

data class DictionaryDisplay(val previews: List<DictionaryEntryPreview>, val html: String, val limitedLayout: Boolean,
    val originalText: String = "")

class DictionaryDisplayProcessor(private val analyze: (DictionaryEntry) -> DictionaryEntryPreview = DictionarySummaries::analyze,
    private val cache: DictionaryDisplayCache = DictionaryDisplayCache(), private val strategyVersion: Int = DictionarySummaries.VERSION) {
    suspend fun prepare(source: LocalDictionary, entries: List<DictionaryEntry>, night: Boolean, fontSize: Int): DictionaryDisplay = withContext(Dispatchers.Default) {
        ensureActive()
        val key = DictionaryDisplayKey(source.id, source.fingerprint, entries.map { it.id }, night, fontSize, strategyVersion)
        cache.get(key)?.let { cached ->
            // 正文缓存可共享，查询路径属于当前请求，不能复用上次命中的来源。
            return@withContext cached.copy(previews = cached.previews.zip(entries) { preview, entry -> preview.copy(entry = entry) })
        }
        val previews = entries.map(analyze)
        val raw = entries.joinToString("<hr>") { it.html }
        val html = DictionaryHtml.render(raw, source.id, night, fontSize)
        ensureActive()
        DictionaryDisplay(previews, html, raw.contains("<script", true) || raw.contains("onclick", true),
            org.jsoup.Jsoup.parse(html).body().text()).also { cache.put(key, it) }
    }
}
