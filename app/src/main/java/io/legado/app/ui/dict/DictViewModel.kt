package io.legado.app.ui.dict

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.LocalDictionary
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.dictionary.DictionaryEntry
import io.legado.app.model.dictionary.LocalDictionaryStore
import io.legado.app.model.dictionary.DictionaryLookupResult
import io.legado.app.model.dictionary.DictionaryQueryCoordinator
import io.legado.app.model.dictionary.DictionaryRequestFence
import io.legado.app.model.dictionary.DictionaryDisplayProcessor
import io.legado.app.help.config.AppConfig

data class DictSource(val local: LocalDictionary? = null, val online: DictRule? = null) {
    val name: String get() = local?.name ?: online?.name.orEmpty()
}

data class DictResult(val entries: List<DictionaryEntry> = emptyList(), val onlineHtml: String? = null,
    val source: LocalDictionary? = null, val lookup: DictionaryLookupResult? = null,
    val display: io.legado.app.model.dictionary.DictionaryDisplay? = null)

class DictViewModel @JvmOverloads constructor(application: Application,
    private val localRead: suspend (LocalDictionary, String, String) -> List<DictionaryEntry> = { source, word, language ->
        LocalDictionaryStore.instance.lookup(source.id, word, language)
    }) : BaseViewModel(application) {
    fun lookupLocal(word: String, language: String?, book: String, selection: Long, view: Long,
        onSuccess: (DictResult) -> Unit, onError: (Throwable) -> Unit) {
        cancelQuery()
        val token = fence.begin(book, selection, view)
        dictJob = execute {
            val store = LocalDictionaryStore.instance
            store.prepare()
            val result = DictionaryQueryCoordinator(localRead).lookup(word, language, store.querySettings, store.all)
            if (result is DictionaryLookupResult.Hit) DictResult(result.entries, source = result.source, lookup = result,
                display = displayProcessor.prepare(result.source, result.entries, AppConfig.isNightTheme, (18 * context.resources.configuration.fontScale).toInt()))
            else DictResult(lookup = result)
        }.onSuccess { if (fence.isCurrent(token)) onSuccess(it) }
            .onError { if (fence.isCurrent(token)) onError(it) }
    }

    private var dictJob: Coroutine<DictResult>? = null
    private var request = 0L
    private val fence = DictionaryRequestFence()
    private val displayProcessor = DictionaryDisplayProcessor()

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
        entryId: Long? = null,
        language: String? = null
    ) {
        cancelQuery()
        val token = request
        dictJob = execute {
            val local = source.local
            if (local != null) {
                val entries = if (entryId == null) LocalDictionaryStore.instance.lookup(local.id, word, language)
                else listOfNotNull(LocalDictionaryStore.instance.byId(local.id, entryId))
                DictResult(entries = entries, source = local, display = if (entries.isEmpty()) null else
                    displayProcessor.prepare(local, entries, AppConfig.isNightTheme, (18 * context.resources.configuration.fontScale).toInt()))
            } else DictResult(onlineHtml = requireNotNull(source.online).search(word))
        }.onSuccess {
            if (request == token) onSuccess(it)
        }.onError {
            if (request == token) onError(it)
        }
    }

    fun cancelQuery() { request++; fence.invalidate(); dictJob?.cancel(); dictJob = null }

    override fun onCleared() { cancelQuery(); super.onCleared() }
}
