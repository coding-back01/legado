package io.legado.app.model.dictionary

/** 查询缓存只保存已经解析的词条，不保留数据库或文件句柄。 */
internal class DictionaryQueryCache(private val maximumBytes: Long = 4L * 1024 * 1024) {
    private data class Value(val dictionary: String, val entries: List<DictionaryEntry>, val bytes: Long)
    private val values = LinkedHashMap<String, Value>(16, 0.75f, true)
    private var used = 0L

    @Synchronized fun get(dictionary: String, word: String): List<DictionaryEntry>? = values["$dictionary\u0000$word"]?.entries

    @Synchronized fun put(dictionary: String, word: String, entries: List<DictionaryEntry>) {
        val key = "$dictionary\u0000$word"
        values.remove(key)?.let { used -= it.bytes }
        val bytes = key.length * 2L + entries.sumOf { it.html.length * 2L + it.headword.length * 2L + 64 }
        if (bytes > maximumBytes) return
        values[key] = Value(dictionary, entries.toList(), bytes)
        used += bytes
        val iterator = values.entries.iterator()
        while (used > maximumBytes && iterator.hasNext()) {
            used -= iterator.next().value.bytes
            iterator.remove()
        }
    }

    @Synchronized fun remove(dictionary: String) {
        val iterator = values.values.iterator()
        while (iterator.hasNext()) {
            val value = iterator.next()
            if (value.dictionary == dictionary) { used -= value.bytes; iterator.remove() }
        }
    }

    @Synchronized fun bytes(): Long = used
}
