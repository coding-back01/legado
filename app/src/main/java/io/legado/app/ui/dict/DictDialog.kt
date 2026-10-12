package io.legado.app.ui.dict

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogDictBinding
import io.legado.app.model.dictionary.DictionaryEntry
import io.legado.app.model.dictionary.DictionaryHtml
import io.legado.app.model.dictionary.DictionaryDisplay
import io.legado.app.model.dictionary.DictionaryLookupResult
import io.legado.app.model.dictionary.DictionaryQueryPlan
import io.legado.app.model.dictionary.DictionaryLanguages
import io.legado.app.model.dictionary.LocalDictionaryStore
import io.legado.app.ui.dict.local.LocalDictionaryActivity
import io.legado.app.utils.setHtml
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictDialog() : BaseDialogFragment(R.layout.dialog_dict) {
    constructor(word: String, language: String? = null, dictionaryId: String? = null, bookUrl: String? = null) : this() {
        arguments = Bundle().apply { putString("word", word); putString("language", language); putString("dictionaryId", dictionaryId); putString("bookUrl", bookUrl) }
    }
    private val viewModel by viewModels<DictViewModel>()
    private val binding by viewBinding(DialogDictBinding::bind)
    private var word = ""
    private var queryLanguage: String? = null
    private var automatic = true
    private var sourceEpoch = 0L
    private var sources: List<DictSource> = emptyList()
    private var selected: DictSource? = null
    private var loaded = false
    private var expanded = false
    private var player: MediaPlayer? = null
    private data class Query(val word: String, val entryId: Long? = null)
    private var currentQuery = Query("")
    private var audioJob: Job? = null
    @Volatile private var shownDictionaryId: String? = null
    @Volatile private var resourceRequest = 0L
    private var missingResource = false
    private var limitedLayout = false
    private var currentDisplay: DictionaryDisplay? = null
    private val history = java.util.ArrayDeque<Query>()

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        expanded = savedInstanceState?.getBoolean("expanded") == true
        word = arguments?.getString("word").orEmpty()
        queryLanguage = DictionaryLanguages.canonical(arguments?.getString("language"))
        automatic = arguments?.getString("dictionaryId") == null
        if (word.isBlank()) { toastOnUi(R.string.cannot_empty); dismiss(); return }
        binding.tvQuery.text = word
        binding.tvDict.movementMethod = LinkMovementMethod.getInstance()
        binding.btnDictionary.setOnClickListener { choose() }
        binding.btnDictionaryLanguage.setOnClickListener { chooseLanguage() }
        binding.btnDictionaryPrimary.setOnClickListener {
            val source = selected?.local ?: return@setOnClickListener
            val language = queryLanguage ?: return@setOnClickListener chooseLanguage()
            val store = LocalDictionaryStore.instance
            store.querySettings = store.querySettings.withLanguage(source.id, language).withPrimary(language, source.id)
            automatic = true
            history.clear()
            lookupAutomatic()
        }
        binding.btnManage.setOnClickListener { startActivity(Intent(requireContext(), LocalDictionaryActivity::class.java)) }
        binding.btnBack.setOnClickListener {
            if (history.isNotEmpty()) {
                stopAudio()
                val previous = history.removeLast()
                query(previous.word, previous.entryId)
            }
        }
        binding.btnExpand.setOnClickListener {
            expanded = !expanded
            resizeContent()
            binding.btnExpand.setText(if (expanded) R.string.dictionary_collapse else R.string.dictionary_expand)
            currentDisplay?.let { bindDisplay(it) }
        }
        binding.btnExpand.setText(if (expanded) R.string.dictionary_collapse else R.string.dictionary_expand)
        binding.webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mediaPlaybackRequiresUserGesture = true
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse = resource(request.url)
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String): WebResourceResponse = resource(url.toUri())
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                if (request.hasGesture()) navigate(request.url.toString())
                return true
            }
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean { navigate(url); return true }
        }
        resizeContent()
        refresh()
    }

    override fun onResume() { super.onResume(); if (loaded) refresh() }

    private fun resizeContent() {
        val reserve = (200 * resources.displayMetrics.density).toInt()
        val height = ((resources.displayMetrics.heightPixels * if (expanded) 0.90 else 0.65).toInt() - reserve)
            .coerceAtLeast((72 * resources.displayMetrics.density).toInt())
        binding.contentFrame.layoutParams = binding.contentFrame.layoutParams.apply { this.height = height }
    }

    private fun refresh() {
        val epoch = ++sourceEpoch
        viewModel.initData { list ->
            if (view == null || epoch != sourceEpoch) return@initData
            sources = list
            loaded = true
            if (automatic) lookupAutomatic()
            else {
                val id = selected?.local?.id ?: arguments?.getString("dictionaryId")
                val source = list.firstOrNull { id != null && it.local?.id == id }
                    ?: selected?.online?.name?.let { name -> list.firstOrNull { it.online?.name == name } }
                if (source != null) select(source) else message(getString(R.string.dictionary_previous_unavailable))
            }
        }
    }

    private fun chooseLanguage() {
        AlertDialog.Builder(requireContext()).setTitle(R.string.dictionary_query_language)
            .setItems(arrayOf(getString(R.string.dictionary_language_en), getString(R.string.dictionary_language_zh))) { _, index ->
                queryLanguage = if (index == 0) "en" else "zh"
                arguments?.getString("bookUrl")?.let { url ->
                    io.legado.app.model.ReadBook.book?.takeIf { it.bookUrl == url }?.config?.dictionaryQueryLanguage = queryLanguage
                    val language = queryLanguage
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        io.legado.app.data.appDb.bookDao.getBook(url)?.let { book -> book.config.dictionaryQueryLanguage = language; book.save() }
                    }
                }
                automatic = true
                history.clear()
                lookupAutomatic()
            }.show()
    }

    private fun prepareQuery() {
        resourceRequest++
        currentDisplay = null
        shownDictionaryId = null
        stopAudio()
        binding.webView.stopLoading()
        binding.rotateLoading.isVisible = true
        binding.webView.isVisible = false
        binding.tvDict.isVisible = false
        binding.tvWarning.isVisible = false
        missingResource = false
        limitedLayout = false
    }

    private fun lookupAutomatic() {
        prepareQuery()
        selected = null
        currentQuery = Query(word)
        binding.btnDictionaryPrimary.isVisible = false
        viewModel.lookupLocal(word, queryLanguage, "", 0, sourceEpoch, { result ->
            if (view == null) return@lookupLocal
            binding.rotateLoading.isVisible = false
            when (val lookup = result.lookup) {
                is DictionaryLookupResult.Hit -> {
                    selected = DictSource(local = lookup.source)
                    binding.btnDictionaryPrimary.isVisible = queryLanguage != null
                    binding.btnDictionary.text = lookup.source.name
                    display(lookup.entries, requireNotNull(result.display))
                }
                is DictionaryLookupResult.NotFound -> message(getString(R.string.dictionary_not_found))
                is DictionaryLookupResult.Failure -> message(requireContext().dictionaryMessage(lookup.error))
                is DictionaryLookupResult.NeedChoice -> message(getString(when (lookup.plan) {
                    DictionaryQueryPlan.ChooseLanguage -> R.string.dictionary_language_unknown
                    is DictionaryQueryPlan.Unavailable -> R.string.dictionary_previous_unavailable
                    else -> R.string.dictionary_select_prompt
                }))
                null -> message(getString(R.string.dictionary_select_prompt))
            }
        }, { if (view != null) message(requireContext().dictionaryMessage(it)) })
    }

    private fun choose() {
        if (sources.isEmpty()) { startActivity(Intent(requireContext(), LocalDictionaryActivity::class.java)); return }
        AlertDialog.Builder(requireContext()).setTitle(R.string.dictionary_select)
            .setItems(sources.map { it.name }.toTypedArray()) { _, index -> select(sources[index]) }.show()
    }

    private fun select(source: DictSource) {
        selected = source
        automatic = false
        source.local?.let { if (queryLanguage == null) queryLanguage = LocalDictionaryStore.instance.querySettings.languageOf(it) }
        binding.btnDictionaryPrimary.isVisible = source.local != null && queryLanguage != null
        binding.btnDictionary.text = source.name
        history.clear()
        stopAudio()
        query(word)
    }

    private fun query(text: String, entryId: Long? = null) {
        val source = selected ?: return
        currentDisplay = null
        resourceRequest++
        currentQuery = Query(text, entryId)
        binding.rotateLoading.isVisible = true
        binding.webView.isVisible = false
        binding.tvDict.isVisible = false
        shownDictionaryId = null
        binding.webView.stopLoading()
        missingResource = false
        limitedLayout = false
        binding.tvWarning.isVisible = false
        viewModel.dict(source, text, { result ->
            if (view == null) return@dict
            binding.rotateLoading.isVisible = false
            if (source.local != null) {
                if (result.entries.isEmpty()) message(getString(R.string.dictionary_not_found)) else {
                    runCatching { display(result.entries, requireNotNull(result.display)) }.onFailure { message(requireContext().dictionaryMessage(it)) }
                }
            } else {
                binding.webView.isVisible = false
                binding.tvDict.isVisible = true
                binding.tvDict.setHtml(result.onlineHtml.orEmpty())
                binding.tvQuery.text = word
            }
        }, { error -> if (view != null) message(requireContext().dictionaryMessage(error)) }, entryId, queryLanguage)
    }

    private fun display(entries: List<DictionaryEntry>, display: DictionaryDisplay) {
        val id = selected?.local?.id ?: return
        shownDictionaryId = id
        binding.btnBack.isVisible = history.isNotEmpty()
        val displayed = if (currentQuery.entryId == null) currentQuery.word else entries.first().headword
        binding.tvQuery.text = buildList {
            add(if (displayed == entries.first().headword) displayed else
                getString(R.string.dictionary_match, displayed, entries.first().headword))
            entries.forEach { entry -> requireContext().dictionaryMatchMessage(entry)?.let { add("${entry.headword} · $it") } }
        }.joinToString("\n")
        currentDisplay = display
        limitedLayout = display.limitedLayout
        bindDisplay(display)
    }

    private fun bindDisplay(display: DictionaryDisplay) {
        val id = selected?.local?.id ?: return
        val short = !expanded && display.previews.isNotEmpty() && display.previews.all { it.summary != null }
        binding.tvDict.isVisible = short
        binding.webView.isVisible = !short
        if (short) {
            binding.tvDict.text = display.previews.joinToString("\n\n") { preview ->
                val summary = requireNotNull(preview.summary)
                buildList {
                    if (display.previews.size > 1) add(preview.entry.headword)
                    summary.pronunciation?.let(::add)
                    summary.partOfSpeech?.let(::add)
                    add(summary.definition)
                    addAll(summary.examples)
                }.joinToString("\n")
            }
        } else binding.webView.loadDataWithBaseURL("https://${DictionaryHtml.HOST}/$id/", display.html, "text/html", "UTF-8", null)
        updateWarning()
    }

    private fun message(text: String) {
        binding.rotateLoading.isVisible = false
        binding.webView.isVisible = false
        binding.tvDict.isVisible = true
        binding.tvDict.text = text
        binding.btnBack.isVisible = history.isNotEmpty()
    }

    private fun navigate(link: String) {
        val local = selected?.local ?: return
        DictionaryHtml.lookupLink(link)?.let {
            if (history.size >= 16) history.removeFirst()
            history.addLast(currentQuery)
            stopAudio()
            query(it)
            return
        }
        val uri = link.toUri()
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            "entry-id" -> uri.host?.toLongOrNull()?.let { id ->
                if (history.size >= 16) history.removeFirst()
                history.addLast(currentQuery)
                stopAudio()
                query(currentQuery.word, id)
            }
            "sound" -> {
                stopAudio()
                val relative = Uri.decode(link.substringAfter("sound://"))
                val media = MediaPlayer()
                player = media
                media.setOnPreparedListener { if (player === it && view != null) it.start() else it.release() }
                media.setOnCompletionListener { if (player === it) stopAudio() }
                media.setOnErrorListener { failed, _, _ ->
                    if (player === failed && view != null) { missingResource = true; updateWarning(); stopAudio() }
                    true
                }
                audioJob = viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val file = localResource(local.id, relative)
                            FileInputStream(file).use { media.setDataSource(it.fd) }
                        }
                        ensureActive()
                        if (player === media) media.prepareAsync()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (player === media && view != null) { missingResource = true; updateWarning(); stopAudio() }
                    }
                }
            }
            "http", "https" -> if (uri.host != DictionaryHtml.HOST) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }
        }
    }

    private fun localResource(id: String, relative: String): File {
        return LocalDictionaryStore.instance.resource(id, relative)
    }

    private fun resource(uri: Uri): WebResourceResponse {
        val id = shownDictionaryId
        val request = resourceRequest
        if (id != null && uri.scheme == "https" && uri.host == DictionaryHtml.HOST && uri.path?.startsWith("/$id/") == true) {
            try {
                val file = localResource(id, requireNotNull(uri.path).removePrefix("/$id/"))
                if (file.isFile && LocalDictionaryStore.isResource(file)) {
                    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
                    val stream = if (file.extension.equals("css", true)) {
                        ByteArrayInputStream(DictionaryHtml.visibleCss(file.readText()).toByteArray())
                    } else file.inputStream()
                    return WebResourceResponse(type, "UTF-8", stream)
                }
            } catch (_: Exception) { /* 资源错误只影响当前资源，不开放其他目录。 */ }
            activity?.runOnUiThread {
                if (view != null && shownDictionaryId == id && resourceRequest == request) {
                    missingResource = true
                    updateWarning()
                }
            }
        }
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    private fun stopAudio() { audioJob?.cancel(); audioJob = null; player?.release(); player = null }

    private fun updateWarning() {
        val messages = buildList {
            if (missingResource) add(getString(R.string.dictionary_missing_resource))
            if (limitedLayout) add(getString(R.string.dictionary_layout_limit))
            if (!expanded && currentDisplay?.previews?.any { it.summary?.truncated == true } == true)
                add(getString(R.string.dictionary_summary_truncated))
        }
        binding.tvWarning.text = messages.joinToString("\n")
        binding.tvWarning.isVisible = messages.isNotEmpty()
    }

    override fun onDestroyView() {
        sourceEpoch++
        currentDisplay = null
        viewModel.cancelQuery()
        stopAudio()
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("expanded", expanded)
        super.onSaveInstanceState(outState)
    }
}
