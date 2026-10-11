package io.legado.app.ui.dict

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.LocalDictionary
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.dictionary.DictionaryEntry
import io.legado.app.model.dictionary.LocalDictionaryStore

data class DictSource(val local: LocalDictionary? = null, val online: DictRule? = null) {
    val name: String get() = local?.name ?: online?.name.orEmpty()
}

data class DictResult(val entries: List<DictionaryEntry> = emptyList(), val onlineHtml: String? = null)

class DictViewModel(application: Application) : BaseViewModel(application) {

    private var dictJob: Coroutine<DictResult>? = null
    private var request = 0L

    fun initData(onSuccess: (List<DictSource>) -> Unit) {
        execute {
            LocalDictionaryStore.instance.prepare()
            appDb.localDictionaryDao.all.filter { it.state == "ready" }.map { DictSource(local = it) } +
                appDb.dictRuleDao.enabled.map { DictSource(online = it) }
        }.onSuccess {
            onSuccess.invoke(it)
        }
    }

    fun dict(
        source: DictSource,
        word: String,
        onSuccess: (DictResult) -> Unit,
        onError: (Throwable) -> Unit,
        entryId: Long? = null
    ) {
        cancelQuery()
        val token = request
        dictJob = execute {
            val local = source.local
            if (local != null) {
                val entries = if (entryId == null) LocalDictionaryStore.instance.lookup(local.id, word)
                else listOfNotNull(LocalDictionaryStore.instance.byId(local.id, entryId))
                DictResult(entries = entries)
            } else DictResult(onlineHtml = requireNotNull(source.online).search(word))
        }.onSuccess {
            if (request == token) onSuccess(it)
        }.onError {
            if (request == token) onError(it)
        }
    }

    fun cancelQuery() { request++; dictJob?.cancel() }
}
