package io.legado.app.ui.dict.local

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.model.dictionary.LocalDictionaryStore
import io.legado.app.ui.dict.dictionaryMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume

class LocalDictionaryViewModel(application: Application) : BaseViewModel(application) {
    val progress = MutableStateFlow<String?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var importJob: Job? = null
    val selection = MutableStateFlow<List<String>?>(null)
    private var pendingSelection: CancellableContinuation<Set<String>>? = null
    private val choices = linkedSetOf<String>()

    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            try {
                progress.value = context.getString(R.string.loading)
                val result = LocalDictionaryStore.instance.importDocuments(uris, {
                    progress.value = context.getString(R.string.dictionary_importing, it)
                }, { names -> withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        choices.clear()
                        choices.addAll(names)
                        pendingSelection = continuation
                        selection.value = names
                        continuation.invokeOnCancellation { selection.value = null; pendingSelection = null }
                    }
                } })
                val message = context.getString(R.string.dictionary_imported, result.size)
                messages.emit(if (result.any { it.warningCount > 0 }) message + "\n" + context.getString(R.string.dictionary_missing_resource) else message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.emit(context.dictionaryMessage(e))
            } finally {
                progress.value = null
            }
        }
    }

    fun cancelImport() { importJob?.cancel() }

    fun selected(name: String): Boolean = name in choices
    fun select(name: String, checked: Boolean) { if (checked) choices.add(name) else choices.remove(name) }
    fun submitSelection(cancel: Boolean = false) {
        val continuation = pendingSelection
        pendingSelection = null
        selection.value = null
        if (continuation?.isActive == true) continuation.resume(if (cancel) emptySet() else choices.toSet())
    }

    fun rebuild(id: String) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            try {
                progress.value = context.getString(R.string.dictionary_rebuild)
                LocalDictionaryStore.instance.rebuild(id)
                messages.emit(context.getString(R.string.dictionary_rebuilt))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.emit(context.dictionaryMessage(e))
            } finally {
                progress.value = null
            }
        }
    }

    fun delete(id: String) = viewModelScope.launch { LocalDictionaryStore.instance.delete(id) }
    fun move(id: String, delta: Int) = viewModelScope.launch { LocalDictionaryStore.instance.move(id, delta) }
}
