package io.legado.app.ui.dict

import android.content.Context
import io.legado.app.R
import io.legado.app.model.dictionary.DictionaryException
import io.legado.app.model.dictionary.DictionaryProblem

fun Context.dictionaryMessage(error: Throwable): String {
    val storageFull = generateSequence(error) { it.cause }.take(16).any {
        it is android.database.sqlite.SQLiteFullException ||
            it is android.system.ErrnoException && it.errno == android.system.OsConstants.ENOSPC
    }
    if (storageFull) return getString(R.string.dictionary_limit)
    return getString(when ((error as? DictionaryException)?.problem) {
    DictionaryProblem.MISSING_FILE -> R.string.dictionary_missing_file
    DictionaryProblem.UNSUPPORTED -> R.string.dictionary_unsupported
    DictionaryProblem.PROTECTED -> R.string.dictionary_protected
    DictionaryProblem.LIMIT -> R.string.dictionary_limit
    DictionaryProblem.INVALID_PATH -> R.string.dictionary_invalid_path
    else -> R.string.dictionary_corrupt
    })
}
