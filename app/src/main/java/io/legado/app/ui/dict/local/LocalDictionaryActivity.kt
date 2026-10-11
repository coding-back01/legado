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
                            AlertDialog.Builder(this@LocalDictionaryActivity).setTitle(R.string.dictionary_select)
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
        val choices = arrayOf(getString(R.string.dictionary_set_current), getString(R.string.dictionary_details),
            getString(R.string.dictionary_rebuild), getString(R.string.dictionary_move_up),
            getString(R.string.dictionary_move_down), getString(R.string.delete))
        AlertDialog.Builder(this).setTitle(dictionary.name).setItems(choices) { _, which ->
            when (which) {
                0 -> if (dictionary.state == "ready") LocalDictionaryStore.instance.lastDictionaryId = dictionary.id
                else toastOnUi(R.string.dictionary_missing_state)
                1 -> AlertDialog.Builder(this).setTitle(dictionary.name)
                    .setMessage(getString(R.string.dictionary_info, dictionary.format, dictionary.entryCount,
                        dictionary.inputLanguage, dictionary.outputLanguage)).setPositiveButton(R.string.ok, null).show()
                2 -> viewModel.rebuild(dictionary.id)
                3 -> viewModel.move(dictionary.id, -1)
                4 -> viewModel.move(dictionary.id, 1)
                5 -> AlertDialog.Builder(this).setMessage(getString(R.string.dictionary_delete_confirm, dictionary.name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ -> viewModel.delete(dictionary.id) }.show()
            }
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
                getString(R.string.dictionary_info, item.format, item.entryCount, item.inputLanguage, item.outputLanguage)
            } else getString(R.string.dictionary_missing_state)
            holder.binding.root.setOnClickListener { actions(item) }
        }
    }
    private class Holder(val binding: ItemLocalDictionaryBinding) : RecyclerView.ViewHolder(binding.root)
}
