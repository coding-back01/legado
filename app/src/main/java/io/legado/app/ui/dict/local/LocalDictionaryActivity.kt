package io.legado.app.ui.dict.local

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.LocalDictionary
import io.legado.app.databinding.ActivityLocalDictionaryBinding
import io.legado.app.databinding.ItemLocalDictionaryBinding
import io.legado.app.model.dictionary.LocalDictionaryStore
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.showHelp
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class LocalDictionaryActivity : BaseActivity<ActivityLocalDictionaryBinding>() {
    override val binding by viewBinding(ActivityLocalDictionaryBinding::inflate)
    private val viewModel by viewModels<LocalDictionaryViewModel>()
    private val files = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.import(it) }
    private val folder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) viewModel.import(listOf(it))
    }
    private val adapter = Adapter()
    private var selectionDialog: AlertDialog? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.progressCancel.setOnClickListener { viewModel.cancelImport() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    LocalDictionaryStore.instance.prepare()
                    appDb.localDictionaryDao.observe().flowOn(Dispatchers.IO).collect {
                        adapter.items = it
                        binding.emptyView.isVisible = it.isEmpty()
                    }
                }
                launch {
                    viewModel.progress.collect {
                        binding.progressText.text = it
                        binding.progressText.isVisible = it != null
                        binding.progressCancel.isVisible = it != null
                    }
                }
                launch { viewModel.messages.collect { toastOnUi(it) } }
                launch {
                    viewModel.selection.collect { names ->
                        selectionDialog?.dismiss()
                        selectionDialog = names?.let {
                            AlertDialog.Builder(this@LocalDictionaryActivity).setTitle(viewModel.selectionTitle.value ?: getString(R.string.dictionary_select))
                                .setMultiChoiceItems(it.toTypedArray(), it.map(viewModel::selected).toBooleanArray()) { _, index, checked ->
                                    viewModel.select(it[index], checked)
                                }
                                .setPositiveButton(R.string.ok) { _, _ -> viewModel.submitSelection() }
                                .setNegativeButton(R.string.cancel) { _, _ -> viewModel.submitSelection(true) }
                                .setOnCancelListener { viewModel.submitSelection(true) }.show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() { selectionDialog?.dismiss(); super.onDestroy() }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.local_dictionary, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_dictionary_import_files -> files.launch(arrayOf("*/*"))
            R.id.menu_dictionary_import_folder -> folder.launch(null)
            R.id.menu_help -> showHelp("localDictionaryHelp")
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun actions(dictionary: LocalDictionary) {
        val actions = listOf(R.string.dictionary_set_language, R.string.dictionary_set_primary,
            R.string.dictionary_toggle_fallback, R.string.dictionary_fallback_up, R.string.dictionary_fallback_down,
            R.string.dictionary_associate_pending, R.string.dictionary_confirm_resources,
            R.string.dictionary_details, R.string.dictionary_rebuild, R.string.dictionary_move_up,
            R.string.dictionary_move_down, R.string.delete)
        AlertDialog.Builder(this).setTitle(dictionary.name).setItems(actions.map(::getString).toTypedArray()) { _, index ->
            val store = LocalDictionaryStore.instance
            when (actions[index]) {
                R.string.dictionary_set_language -> chooseLanguage(dictionary)
                R.string.dictionary_set_primary -> {
                    val language = store.querySettings.languageOf(dictionary)
                    if (language == null) chooseLanguage(dictionary) else if (dictionary.state != "ready") toastOnUi(R.string.dictionary_missing_state)
                    else store.querySettings = store.querySettings.withPrimary(language, dictionary.id)
                }
                R.string.dictionary_toggle_fallback -> {
                    val language = store.querySettings.languageOf(dictionary)
                    if (language == null) chooseLanguage(dictionary) else {
                        val previous = store.querySettings.languages[language]?.fallbackIds.orEmpty()
                        store.querySettings = store.querySettings.withFallbacks(language,
                            if (dictionary.id in previous) previous - dictionary.id else previous + dictionary.id)
                    }
                }
                R.string.dictionary_fallback_up -> moveFallback(dictionary, -1)
                R.string.dictionary_fallback_down -> moveFallback(dictionary, 1)
                R.string.dictionary_associate_pending -> if (dictionary.state == "missing") associatePending(dictionary)
                    else toastOnUi(R.string.dictionary_not_pending)
                R.string.dictionary_confirm_resources -> if (dictionary.identityVersion == 1 && dictionary.state == "ready") viewModel.confirmResources(dictionary.id)
                    else toastOnUi(R.string.dictionary_association_complete)
                R.string.dictionary_details -> AlertDialog.Builder(this).setTitle(dictionary.name)
                    .setMessage(getString(R.string.dictionary_info, dictionary.format, dictionary.entryCount, dictionary.inputLanguage, dictionary.outputLanguage) +
                        "\n" + getString(R.string.dictionary_language_override_info, store.querySettings.languageOf(dictionary).orEmpty()))
                    .setPositiveButton(R.string.ok, null).show()
                R.string.dictionary_rebuild -> viewModel.rebuild(dictionary.id)
                R.string.dictionary_move_up -> viewModel.move(dictionary.id, -1)
                R.string.dictionary_move_down -> viewModel.move(dictionary.id, 1)
                R.string.delete -> AlertDialog.Builder(this).setMessage(getString(R.string.dictionary_delete_confirm, dictionary.name))
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.ok) { _, _ -> viewModel.delete(dictionary.id) }.show()
            }
            adapter.notifyItemChanged(adapter.items.indexOfFirst { it.id == dictionary.id }.coerceAtLeast(0))
        }.show()
    }

    private fun chooseLanguage(dictionary: LocalDictionary) {
        val tags = listOf("en", "zh", "ja", "ko", "de", "fr", "")
        val titles = listOf(R.string.dictionary_language_en, R.string.dictionary_language_zh, R.string.dictionary_language_ja,
            R.string.dictionary_language_ko, R.string.dictionary_language_de, R.string.dictionary_language_fr, R.string.dictionary_unclassified)
        AlertDialog.Builder(this).setTitle(R.string.dictionary_set_language).setItems(titles.map(::getString).toTypedArray()) { _, index ->
            val store = LocalDictionaryStore.instance
            store.querySettings = store.querySettings.withLanguage(dictionary.id, tags[index])
            val position = adapter.items.indexOfFirst { it.id == dictionary.id }
            if (position >= 0) adapter.notifyItemChanged(position)
        }.show()
    }

    private fun moveFallback(dictionary: LocalDictionary, delta: Int) {
        val store = LocalDictionaryStore.instance
        val settings = store.querySettings
        val language = settings.languageOf(dictionary) ?: return
        val values = settings.languages[language]?.fallbackIds.orEmpty().toMutableList()
        val from = values.indexOf(dictionary.id)
        val to = from + delta
        if (from >= 0 && to in values.indices) {
            java.util.Collections.swap(values, from, to)
            store.querySettings = settings.withFallbacks(language, values)
        }
    }

    private fun associatePending(dictionary: LocalDictionary) {
        val ready = adapter.items.filter { it.state == "ready" }
        if (ready.isEmpty()) { toastOnUi(R.string.dictionary_empty); return }
        AlertDialog.Builder(this).setTitle(R.string.dictionary_associate_pending).setItems(ready.map { it.name }.toTypedArray()) { _, index ->
            val source = ready[index]
            AlertDialog.Builder(this).setMessage(getString(R.string.dictionary_associate_confirm, source.name, dictionary.name))
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.ok) { _, _ -> viewModel.associate(dictionary.id, source.id) }.show()
        }.show()
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        var items: List<LocalDictionary> = emptyList()
            set(value) {
                val oldCount = field.size
                if (oldCount > 0) { field = emptyList(); notifyItemRangeRemoved(0, oldCount) }
                field = value
                if (value.isNotEmpty()) notifyItemRangeInserted(0, value.size)
            }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemLocalDictionaryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.binding.nameView.text = item.name
            holder.binding.infoView.text = if (item.state == "ready") {
                getString(R.string.dictionary_info, item.format, item.entryCount, item.inputLanguage, item.outputLanguage) + "\n" +
                    getString(R.string.dictionary_language_override_info, LocalDictionaryStore.instance.querySettings.languageOf(item).orEmpty())
            } else getString(R.string.dictionary_missing_state)
            holder.binding.root.setOnClickListener { actions(item) }
        }
    }
    private class Holder(val binding: ItemLocalDictionaryBinding) : RecyclerView.ViewHolder(binding.root)
}
