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
import io.legado.app.help.config.AppConfig
import io.legado.app.model.dictionary.DictionaryEntry
import io.legado.app.model.dictionary.DictionaryHtml
import io.legado.app.model.dictionary.LocalDictionaryStore
import io.legado.app.ui.dict.local.LocalDictionaryActivity
import io.legado.app.utils.setHtml
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import org.jsoup.nodes.Entities
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
    constructor(word: String) : this() { arguments = Bundle().apply { putString("word", word) } }
    private val viewModel by viewModels<DictViewModel>()
    private val binding by viewBinding(DialogDictBinding::bind)
    private var word = ""
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
    private val history = java.util.ArrayDeque<Query>()

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        expanded = savedInstanceState?.getBoolean("expanded") == true
        word = arguments?.getString("word").orEmpty()
        if (word.isBlank()) { toastOnUi(R.string.cannot_empty); dismiss(); return }
        binding.tvQuery.text = word
        binding.tvDict.movementMethod = LinkMovementMethod.getInstance()
        binding.btnDictionary.setOnClickListener { choose() }
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
        viewModel.initData { list ->
            if (view == null) return@initData
            sources = list
            loaded = true
            val last = LocalDictionaryStore.instance.lastDictionaryId
            val locals = list.filter { it.local != null }
            val source = when {
                last != null -> locals.firstOrNull { it.local?.id == last }
                locals.size == 1 -> locals.first()
                locals.isEmpty() -> selected?.takeIf { it.online != null }?.let { previous -> list.firstOrNull { it.online?.name == previous.online?.name } }
                else -> null
            }
            if (source != null) select(source)
            else {
                viewModel.cancelQuery()
                stopAudio()
                selected = null
                shownDictionaryId = null
                binding.webView.stopLoading()
                binding.btnDictionary.setText(R.string.dictionary_select)
                message(getString(when {
                last != null -> R.string.dictionary_previous_unavailable
                locals.isEmpty() -> R.string.dictionary_empty
                else -> R.string.dictionary_select_prompt
                }))
            }
        }
    }

    private fun choose() {
        if (sources.isEmpty()) { startActivity(Intent(requireContext(), LocalDictionaryActivity::class.java)); return }
        AlertDialog.Builder(requireContext()).setTitle(R.string.dictionary_select)
            .setItems(sources.map { it.name }.toTypedArray()) { _, index -> select(sources[index]) }.show()
    }

    private fun select(source: DictSource) {
        selected = source
        source.local?.let { LocalDictionaryStore.instance.lastDictionaryId = it.id }
        binding.btnDictionary.text = source.name
        history.clear()
        stopAudio()
        query(word)
    }

    private fun query(text: String, entryId: Long? = null) {
        val source = selected ?: return
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
                    runCatching { display(result.entries) }.onFailure { message(requireContext().dictionaryMessage(it)) }
                }
            } else {
                binding.webView.isVisible = false
                binding.tvDict.isVisible = true
                binding.tvDict.setHtml(result.onlineHtml.orEmpty())
                binding.tvQuery.text = word
            }
        }, { error -> if (view != null) message(requireContext().dictionaryMessage(error)) }, entryId)
    }

    private fun display(entries: List<DictionaryEntry>) {
        val id = selected?.local?.id ?: return
        shownDictionaryId = id
        binding.btnBack.isVisible = history.isNotEmpty()
        binding.tvDict.isVisible = false
        binding.webView.isVisible = true
        val displayed = if (currentQuery.entryId == null) currentQuery.word else entries.first().headword
        binding.tvQuery.text = getString(R.string.dictionary_match, displayed, entries.first().headword)
        val html = entries.joinToString("<hr>") { "<h3>${Entities.escape(it.headword)}</h3>${it.html}" }
        limitedLayout = html.contains("<script", true) || html.contains("onclick", true)
        updateWarning()
        binding.webView.loadDataWithBaseURL("https://${DictionaryHtml.HOST}/$id/",
            DictionaryHtml.render(html, id, AppConfig.isNightTheme, (18 * resources.configuration.fontScale).toInt()), "text/html", "UTF-8", null)
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
        }
        binding.tvWarning.text = messages.joinToString("\n")
        binding.tvWarning.isVisible = messages.isNotEmpty()
    }

    override fun onDestroyView() {
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
