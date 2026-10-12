package io.legado.app.model.dictionary

data class DictionaryRequestToken(val request: Long, val book: String, val selection: Long, val view: Long)

class DictionaryRequestFence {
    private var sequence = 0L
    private var current: DictionaryRequestToken? = null
    fun begin(book: String, selection: Long, view: Long): DictionaryRequestToken =
        DictionaryRequestToken(++sequence, book, selection, view).also { current = it }
    fun isCurrent(token: DictionaryRequestToken): Boolean = current == token
    fun invalidate() { current = null }
}
