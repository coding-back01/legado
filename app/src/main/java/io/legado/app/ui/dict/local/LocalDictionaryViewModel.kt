package io.legado.app.ui.dict.local

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.model.dictionary.LocalDictionaryStore
import io.legado.app.model.dictionary.DictionaryFileGroups
import java.io.File
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
    val selectionTitle = MutableStateFlow<String?>(null)
    private var pendingSelection: CancellableContinuation<Set<String>>? = null
    private val choices = linkedSetOf<String>()

    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            try {
                progress.value = context.getString(R.string.loading)
                val result = LocalDictionaryStore.instance.importDocuments(uris, {
                    progress.value = context.getString(R.string.dictionary_importing, it)
                }, choose = { names -> requestSelection(names, true, context.getString(R.string.dictionary_select)) },
                    chooseResources = { source, names -> requestSelection(names, false, context.getString(R.string.dictionary_associate_resources, source)) })
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

    private suspend fun requestSelection(names: List<String>, allSelected: Boolean, title: String): Set<String> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            choices.clear()
            if (allSelected) choices.addAll(names)
            pendingSelection = continuation
            selectionTitle.value = title
            selection.value = names
            continuation.invokeOnCancellation { selection.value = null; pendingSelection = null }
        }
    }

    fun selected(name: String): Boolean = name in choices
    fun select(name: String, checked: Boolean) { if (checked) choices.add(name) else choices.remove(name) }
    fun submitSelection(cancel: Boolean = false) {
        val continuation = pendingSelection
        pendingSelection = null
        selection.value = null
        if (continuation?.isActive == true) {
            if (cancel) continuation.cancel() else continuation.resume(choices.toSet())
        }
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

    fun associate(pending: String, source: String) = viewModelScope.launch {
        try { LocalDictionaryStore.instance.associatePending(pending, source) }
        catch (error: Exception) { messages.emit(context.dictionaryMessage(error)) }
    }

    fun confirmResources(id: String) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            try {
                progress.value = context.getString(R.string.loading)
                val store = LocalDictionaryStore.instance
                val dictionary = withContext(Dispatchers.IO) { store.all.first { it.id == id } }
                val resources = withContext(Dispatchers.IO) { DictionaryFileGroups.plan(File(store.directory(id), "sources"), dictionary.sourceFile).unassignedResources }
                val selected = requestSelection(resources, false, context.getString(R.string.dictionary_associate_resources, dictionary.name))
                store.upgradeIdentity(id, selected)
                messages.emit(context.getString(R.string.dictionary_association_complete))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { messages.emit(context.dictionaryMessage(error)) }
            finally { progress.value = null }
        }
    }
}
