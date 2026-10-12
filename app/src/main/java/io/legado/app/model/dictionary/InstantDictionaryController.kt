package io.legado.app.model.dictionary

class InstantDictionaryController {
    fun invalidate() { fence.invalidate() }
    fun isCurrent(token: DictionaryRequestToken): Boolean = fence.isCurrent(token)
    private var book = ""
    private var enabled = false
    private var consumed = Long.MIN_VALUE
    private val fence = DictionaryRequestFence()
    fun configure(book: String, enabled: Boolean) {
        if (this.book != book || this.enabled != enabled) { consumed = Long.MIN_VALUE; fence.invalidate() }
        this.book = book
        this.enabled = enabled
    }
    fun stable(selection: Long, word: String): DictionaryRequestToken? {
        if (!enabled || book.isBlank() || selection == consumed) return null
        consumed = selection
        if (!Regex("[\\p{L}\\p{M}\\p{N}'’\\-]+").matches(word) || word.none { it.isLetterOrDigit() }) return null
        return fence.begin(book, selection, 0)
    }
}
