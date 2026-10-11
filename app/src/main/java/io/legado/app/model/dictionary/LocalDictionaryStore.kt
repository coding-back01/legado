package io.legado.app.model.dictionary

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.LocalDictionary
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

enum class DictionaryOperation { BEFORE_PUBLISH, BEFORE_REGISTER, BEFORE_REBUILD_SWAP, OLD_INDEX_MOVED, NEW_INDEX_PUBLISHED }

class LocalDictionaryStore(
    private val context: Context,
    private val beforeOperation: (DictionaryOperation) -> Unit = {}
) {
    private val root = File(context.filesDir, "local-dictionaries")
    private val preferences = context.getSharedPreferences("local_dictionary", Context.MODE_PRIVATE)
    private val queryCache = DictionaryQueryCache()

    suspend fun prepare() = withContext(Dispatchers.IO) {
        if (root.path !in preparedDirectories) importMutex.withLock {
            if (root.path !in preparedDirectories) {
                recoverLocked()
                preparedDirectories.add(root.path)
            }
        }
    }

    suspend fun recover() = withContext(Dispatchers.IO) {
        importMutex.withLock { recoverLocked(); preparedDirectories.add(root.path) }
    }

    private fun recoverLocked() {
        val known = all.filter { it.state == "ready" }.map { it.id }.toSet()
        File(root, "data").mkdirs()
        File(root, "recovery").listFiles()?.forEach { previous ->
            if (Regex("[0-9a-f-]{36}").matches(previous.name)) {
                val current = directory(previous.name)
                if (previous.name in known && !File(current, "ready").isFile) {
                    current.deleteRecursively()
                    DictionaryLimits.check(previous.renameTo(current))
                } else previous.deleteRecursively()
            }
        }
        File(root, "staging").listFiles()?.forEach { it.deleteRecursively() }
        File(root, "data").listFiles()?.filter { it.name !in known }?.forEach { it.deleteRecursively() }
    }

    var includeFilesInBackup: Boolean
        get() = context.defaultSharedPreferences.getBoolean(PreferKey.backupLocalDictionaryFiles, false)
        set(value) { context.defaultSharedPreferences.edit { putBoolean(PreferKey.backupLocalDictionaryFiles, value) } }

    var lastDictionaryId: String?
        get() = preferences.getString("last", null)
        set(value) { preferences.edit { putString("last", value) } }

    val all: List<LocalDictionary> get() = appDb.localDictionaryDao.all

    fun directory(id: String): File {
        DictionaryLimits.check(Regex("[0-9a-f-]{36}").matches(id), DictionaryProblem.INVALID_PATH)
        return DictionaryPaths.file(File(root, "data"), id)
    }

    suspend fun importDocuments(
        uris: List<Uri>,
        onProgress: (String) -> Unit = {},
        choose: suspend (List<String>) -> Set<String> = { it.toSet() }
    ): List<LocalDictionary> =
        withContext(Dispatchers.IO) {
            prepare()
            importMutex.withLock {
                val coroutine = currentCoroutineContext()
                val checkpoint = { coroutine.ensureActive() }
                val staging = File(root, "staging").apply { mkdirs() }
                val folder = File(staging, UUID.randomUUID().toString()).apply { mkdirs() }
                try {
                    val sources = File(folder, "sources").apply { mkdirs() }
                    var count = 0
                    var total = 0L
                    fun copyDocument(document: DocumentFile, parent: File, depth: Int = 0) {
                        checkpoint()
                        DictionaryLimits.check(depth <= 64, DictionaryProblem.LIMIT)
                        DictionaryLimits.check(++count <= DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
                        val name = document.name ?: throw DictionaryException(DictionaryProblem.INVALID_PATH)
                        val output = DictionaryPaths.file(parent, name)
                        onProgress(name)
                        if (document.isDirectory) {
                            DictionaryLimits.check(output.mkdirs() || output.isDirectory)
                            document.listFiles().forEach { copyDocument(it, output, depth + 1) }
                        } else {
                            DictionaryLimits.check(!output.exists())
                            context.contentResolver.openInputStream(document.uri)?.use { input ->
                                output.outputStream().buffered().use { stream ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        checkpoint()
                                        val size = input.read(buffer)
                                        if (size < 0) break
                                        total += size
                                        DictionaryLimits.check(total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
                                        stream.write(buffer, 0, size)
                                    }
                                }
                            } ?: throw DictionaryException(DictionaryProblem.MISSING_FILE, name)
                            if (isArchive(output) && !isResourceArchive(output)) {
                                val expanded = File(parent, output.name + ".contents").apply { mkdirs() }
                                DictionaryArchive.extract(output, expanded, checkpoint)
                                output.delete()
                            }
                        }
                    }
                    uris.forEach { uri ->
                        // API 21 的目录选择结果使用 tree 路径；isTreeUri 直到 API 24 才公开。
                        val document = if (uri.scheme == "content" && uri.pathSegments.firstOrNull() == "tree") {
                            DocumentFile.fromTreeUri(context, uri)
                        } else DocumentFile.fromSingleUri(context, uri)
                        copyDocument(document ?: throw DictionaryException(DictionaryProblem.MISSING_FILE), sources)
                    }
                    var discovered = 0
                    var discoveredSize = 0L
                    val files = sources.walkTopDown().filter { it.isFile }.onEach {
                        discoveredSize += it.length()
                        DictionaryLimits.check(++discovered <= DictionaryLimits.MAX_FILES && discoveredSize <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
                    }.toList()
                    val candidates = files.filter { DictionaryFormat.detect(it) != null }
                    DictionaryLimits.check(candidates.isNotEmpty(), DictionaryProblem.UNSUPPORTED)
                    val names = candidates.map { it.relativeTo(sources).invariantSeparatorsPath }
                    val selected = if (names.size > 1) choose(names) else names.toSet()
                    candidates.filter { it.relativeTo(sources).invariantSeparatorsPath in selected }.map { source ->
                        checkpoint()
                        onProgress(source.name)
                        importSource(requireNotNull(source.parentFile), source.name, checkpoint)
                    }
                } finally {
                    folder.deleteRecursively()
                }
            }
        }

    suspend fun importFiles(sources: File, sourceFile: String): LocalDictionary = withContext(Dispatchers.IO) {
        prepare()
        importMutex.withLock {
            val coroutine = currentCoroutineContext()
            importSource(sources, sourceFile) { coroutine.ensureActive() }
        }
    }

    private fun importSource(sources: File, sourceFile: String, checkpoint: () -> Unit): LocalDictionary {
        val fingerprint = fingerprint(sources, sourceFile, checkpoint)
        appDb.localDictionaryDao.findFingerprint(fingerprint)?.let {
            val marker = File(directory(it.id), "ready")
            if (!marker.isFile || marker.readText().trim() != DictionaryIndex.VERSION.toString()) rebuildLocked(it.id, checkpoint)
            return it
        }
        val pending = all.firstOrNull { it.fingerprint == fingerprint && it.state != "ready" }
        val id = pending?.id ?: UUID.randomUUID().toString()
        val staging = File(root, "staging").apply { mkdirs() }
        val folder = File(staging, "build-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            copyTree(sources, File(folder, "sources"), checkpoint)
            val built = build(folder, sourceFile, checkpoint)
            val title = pending?.name ?: uniqueName(built.first.title)
            val dictionary = LocalDictionary(id, title, built.first.format.name, sourceFile, fingerprint,
                built.first.inputLanguage, built.first.outputLanguage, pending?.sortNumber ?: all.size, "ready", built.second, built.third)
            checkpoint()
            val published = directory(id)
            DictionaryLimits.check(published.parentFile?.mkdirs() == true || published.parentFile?.isDirectory == true)
            DictionaryLimits.check(!published.exists())
            beforeOperation(DictionaryOperation.BEFORE_PUBLISH)
            DictionaryLimits.check(folder.renameTo(published))
            try {
                beforeOperation(DictionaryOperation.BEFORE_REGISTER)
                appDb.localDictionaryDao.insert(dictionary)
            } catch (e: Exception) {
                published.deleteRecursively()
                throw e
            }
            return dictionary
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun build(folder: File, sourceFile: String, checkpoint: () -> Unit): Triple<DictionaryMetadata, Long, Int> {
        val source = DictionaryPaths.file(File(folder, "sources"), sourceFile)
        val format = DictionaryFormat.detect(source) ?: throw DictionaryException(DictionaryProblem.UNSUPPORTED)
        val parser: DictionaryParser = when (format) {
            DictionaryFormat.MDICT -> MdictParser()
            DictionaryFormat.STARDICT -> StarDictParser()
            DictionaryFormat.MOBI -> MobiParser()
            DictionaryFormat.DSL -> DslParser()
            DictionaryFormat.DICTD -> DictdParser()
            DictionaryFormat.XDXF -> XdxfParser()
            DictionaryFormat.BGL -> BglParser()
        }
        val work = File(folder, "work").apply { mkdirs() }
        return try {
            DictionaryIndex(folder, true).use { index ->
                var metadata: DictionaryMetadata? = null
                val baseSink = index.sink(checkpoint)
                val referenced = linkedSetOf<String>()
                val available = linkedSetOf<String>()
                val resourceAttributes = Regex("(?i)(?:src|href)\\s*=\\s*([\"'])(.*?)\\1")
                val sink = object : DictionarySink by baseSink {
                    override fun entry(id: Long, words: List<String>, html: String, target: String?) {
                        resourceAttributes.findAll(html).forEach { match ->
                            val raw = match.groupValues[2]
                            val path = when {
                                raw.startsWith("sound://") -> raw.removePrefix("sound://")
                                DictionaryHtml.isLocal(raw) && raw.isNotEmpty() && !raw.startsWith('#') -> raw
                                else -> null
                            }
                            if (path != null) referenced.add(android.net.Uri.decode(path).lowercase(Locale.ROOT))
                        }
                        DictionaryLimits.check(referenced.size <= DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
                        baseSink.entry(id, words, html, target)
                    }
                    override fun resource(path: String, bytes: ByteArray) {
                        baseSink.resource(path, bytes)
                        available.add(path.lowercase(Locale.ROOT))
                    }
                }
                val count = index.build {
                    metadata = parser.parse(source, work, sink)
                    val sourceDirectory = requireNotNull(source.parentFile)
                    sourceDirectory.walkTopDown().filter { it.isFile && isResource(it) }.forEach { file ->
                        checkpoint()
                        DictionaryLimits.check(file.length() <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
                        sink.resource(file.relativeTo(sourceDirectory).invariantSeparatorsPath, file.readBytes())
                    }
                    val base = source.name.substringBeforeLast('.')
                    sourceDirectory.listFiles()?.filter { file ->
                        val name = file.name.lowercase(Locale.ROOT)
                        file.isFile && when (format) {
                            DictionaryFormat.DSL -> name == source.name.lowercase(Locale.ROOT) + ".files.zip" ||
                                name == source.name.removeSuffix(".dz").lowercase(Locale.ROOT) + ".files.zip"
                            DictionaryFormat.STARDICT -> name == "res.zip" || name == "$base.res.zip".lowercase(Locale.ROOT)
                            else -> false
                        }
                    }?.forEachIndexed { ordinal, archive ->
                        val expanded = File(work, "resources-$ordinal").apply { mkdirs() }
                        DictionaryArchive.extract(archive, expanded, checkpoint)
                        expanded.walkTopDown().filter { it.isFile && isResource(it) }.forEach { resource ->
                            checkpoint()
                            DictionaryLimits.check(resource.length() <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
                            val path = resource.relativeTo(expanded).invariantSeparatorsPath
                            sink.resource(if (format == DictionaryFormat.STARDICT) "res/$path" else path, resource.readBytes())
                        }
                    }
                }
                val missing = referenced.count { it !in available && "res/$it" !in available }
                Triple(requireNotNull(metadata), count, missing)
            }
        } catch (e: DictionaryException) {
            throw e
        } catch (e: java.io.IOException) {
            val storageFull = generateSequence<Throwable>(e) { it.cause }.take(16).any {
                it is android.system.ErrnoException && it.errno == android.system.OsConstants.ENOSPC
            }
            throw DictionaryException(if (storageFull) DictionaryProblem.LIMIT else DictionaryProblem.CORRUPT, source.name, e)
        } catch (e: android.database.sqlite.SQLiteFullException) {
            throw DictionaryException(DictionaryProblem.LIMIT, source.name, e)
        } finally {
            work.deleteRecursively()
        }
    }

    suspend fun lookup(id: String, word: String): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        prepare()
        DictionaryLimits.check(word.length <= DictionaryLimits.MAX_WORD_BYTES, DictionaryProblem.LIMIT)
        DictionaryLimits.check(appDb.localDictionaryDao.get(id)?.state == "ready", DictionaryProblem.MISSING_FILE)
        val coroutine = currentCoroutineContext()
        val marker = File(directory(id), "ready")
        if (marker.isFile && marker.readText().trim() == DictionaryIndex.VERSION.toString()) {
            queryCache.get(id, word)?.let { return@withContext it }
        }
        DictionaryIndex(directory(id)).use { it.lookup(word) { coroutine.ensureActive() } }.also { queryCache.put(id, word, it) }
    }

    suspend fun byId(id: String, entryId: Long): DictionaryEntry? = withContext(Dispatchers.IO) {
        prepare()
        val coroutine = currentCoroutineContext()
        val mobi = appDb.localDictionaryDao.get(id)?.format == DictionaryFormat.MOBI.name
        DictionaryIndex(directory(id)).use { it.byId(entryId, mobi) { coroutine.ensureActive() } }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        prepare()
        importMutex.withLock {
            appDb.localDictionaryDao.delete(id)
            directory(id).deleteRecursively()
            queryCache.remove(id)
        }
    }

    suspend fun move(id: String, delta: Int) = withContext(Dispatchers.IO) {
        val list = all.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from >= 0 && to in list.indices) {
            java.util.Collections.swap(list, from, to)
            appDb.runInTransaction { list.forEachIndexed { position, item -> appDb.localDictionaryDao.sort(item.id, position) } }
        }
    }

    suspend fun rebuild(id: String) = withContext(Dispatchers.IO) {
        prepare()
        importMutex.withLock {
            val coroutine = currentCoroutineContext()
            rebuildLocked(id) { coroutine.ensureActive() }
        }
    }

    private fun rebuildLocked(id: String, checkpoint: () -> Unit) {
            val dictionary = appDb.localDictionaryDao.get(id) ?: throw DictionaryException(DictionaryProblem.MISSING_FILE)
            val current = directory(id)
            DictionaryLimits.check(fingerprint(File(current, "sources"), dictionary.sourceFile, checkpoint) == dictionary.fingerprint)
            val staging = File(root, "staging").apply { mkdirs() }
            val next = File(staging, "rebuild-${UUID.randomUUID()}").apply { mkdirs() }
            val recovery = File(root, "recovery").apply { mkdirs() }
            val previous = DictionaryPaths.file(recovery, id)
            DictionaryLimits.check(!previous.exists())
            try {
                copyTree(File(current, "sources"), File(next, "sources"), checkpoint)
                build(next, dictionary.sourceFile, checkpoint)
                checkpoint()
                beforeOperation(DictionaryOperation.BEFORE_REBUILD_SWAP)
                DictionaryLimits.check(current.renameTo(previous))
                beforeOperation(DictionaryOperation.OLD_INDEX_MOVED)
                if (!next.renameTo(current)) {
                    previous.renameTo(current)
                    throw DictionaryException(DictionaryProblem.CORRUPT)
                }
                beforeOperation(DictionaryOperation.NEW_INDEX_PUBLISHED)
                previous.deleteRecursively()
                queryCache.remove(id)
            } catch (e: Exception) {
                if (previous.isDirectory) {
                    current.deleteRecursively()
                    DictionaryLimits.check(previous.renameTo(current))
                }
                throw e
            } finally {
                next.deleteRecursively()
            }
    }

    fun resource(id: String, path: String): File {
        val directory = directory(id)
        val root = File(directory, "resources")
        for (candidate in listOf(path, "res/$path")) {
            val exact = DictionaryPaths.file(root, candidate)
            if (exact.isFile) return exact
            val matched = DictionaryIndex(directory).use { it.resource(candidate) }
            if (matched != null) return DictionaryPaths.file(root, matched)
        }
        throw DictionaryException(DictionaryProblem.MISSING_FILE, path)
    }

    private fun uniqueName(title: String): String {
        val names = all.map { it.name }.toSet()
        if (title !in names) return title
        var number = 2
        while ("$title ($number)" in names) number++
        return "$title ($number)"
    }

    data class BackupManifest(
        val version: Int = 1,
        val selectedId: String?,
        val includesFiles: Boolean,
        val dictionaries: List<LocalDictionary>
    )

    suspend fun backup(target: File): List<File> = withContext(Dispatchers.IO) {
        prepare()
        importMutex.withLock {
            val coroutine = currentCoroutineContext()
            val checkpoint = { coroutine.ensureActive() }
            val dictionaries = all
            val included = includeFilesInBackup
            val manifestFile = File(target, MANIFEST)
            target.mkdirs()
            manifestFile.writeText(GSON.toJson(BackupManifest(1, lastDictionaryId, included, dictionaries)))
            val output = arrayListOf(manifestFile)
            if (included) {
                val files = File(target, FILES).apply { mkdirs() }
                for (dictionary in dictionaries.filter { it.state == "ready" }) {
                    checkpoint()
                    copyTree(File(directory(dictionary.id), "sources"), File(files, dictionary.id), checkpoint)
                }
                output.add(files)
            }
            output
        }
    }

    suspend fun restore(target: File): List<String> = withContext(Dispatchers.IO) {
        prepare()
        importMutex.withLock {
            val file = File(target, MANIFEST)
            if (!file.isFile) return@withLock emptyList()
            DictionaryLimits.check(file.length() <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
            val json = file.reader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            if (json.get("version")?.asInt != 1) return@withLock listOf("version")
            val included = json.get("includesFiles")?.asBoolean == true
            val list = json.getAsJsonArray("dictionaries") ?: throw DictionaryException(DictionaryProblem.CORRUPT)
            DictionaryLimits.check(list.size() <= DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
            val coroutine = currentCoroutineContext()
            val checkpoint = { coroutine.ensureActive() }
            val mapping = linkedMapOf<String, String>()
            val failures = arrayListOf<String>()
            val dictionaries = list.map { element ->
                checkpoint()
                val item = element.asJsonObject
                for (key in listOf("id", "name", "format", "sourceFile", "fingerprint", "state")) {
                    DictionaryLimits.check(item.get(key)?.isJsonPrimitive == true)
                }
                val dictionary = LocalDictionary(
                    item.get("id").asString, item.get("name").asString, item.get("format").asString,
                    item.get("sourceFile").asString, item.get("fingerprint").asString,
                    item.get("inputLanguage")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                    item.get("outputLanguage")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                    item.get("sortNumber")?.asInt ?: 0, item.get("state").asString,
                    item.get("entryCount")?.asLong ?: 0, item.get("warningCount")?.asInt ?: 0
                )
                DictionaryLimits.check(dictionary.name.isNotBlank() && dictionary.name.length <= DictionaryLimits.MAX_WORD_BYTES)
                DictionaryLimits.check(dictionary.state in setOf("ready", "missing") && dictionary.sortNumber >= 0)
                directory(dictionary.id)
                DictionaryPaths.file(File(target, FILES), dictionary.id)
                DictionaryPaths.file(target, dictionary.sourceFile)
                DictionaryLimits.check(Regex("[0-9a-f]{64}").matches(dictionary.fingerprint))
                DictionaryLimits.check(dictionary.format in DictionaryFormat.entries.map { it.name })
                dictionary
            }
            DictionaryLimits.check(dictionaries.map { it.id }.distinct().size == dictionaries.size)
            for (dictionary in dictionaries) {
                checkpoint()
                appDb.localDictionaryDao.findFingerprint(dictionary.fingerprint)?.let {
                    mapping[dictionary.id] = it.id
                    appDb.localDictionaryDao.sort(it.id, dictionary.sortNumber)
                    continue
                }
                if (included && dictionary.state == "ready") {
                    try {
                        val sources = DictionaryPaths.file(File(target, FILES), dictionary.id)
                        DictionaryLimits.check(fingerprint(sources, dictionary.sourceFile, checkpoint) == dictionary.fingerprint)
                        val restored = importSource(sources, dictionary.sourceFile, checkpoint)
                        appDb.localDictionaryDao.sort(restored.id, dictionary.sortNumber)
                        mapping[dictionary.id] = restored.id
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failures.add(dictionary.name)
                    }
                } else {
                    val existing = appDb.localDictionaryDao.get(dictionary.id)
                    val id = if (existing == null || existing.fingerprint == dictionary.fingerprint) dictionary.id else UUID.randomUUID().toString()
                    appDb.localDictionaryDao.insert(dictionary.copy(id = id, state = "missing", entryCount = 0))
                    mapping[dictionary.id] = id
                }
            }
            val selected = json.get("selectedId")?.takeUnless { it.isJsonNull }?.asString
            lastDictionaryId = if (selected == null) null else mapping[selected] ?: lastDictionaryId
            failures
        }
    }

    private fun fingerprint(sources: File, sourceFile: String, checkpoint: () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun name(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(8).putLong(bytes.size.toLong()).array())
            digest.update(bytes)
        }
        name(sourceFile)
        var count = 0
        var total = 0L
        val files = sources.walkTopDown().onEnter { folder ->
            if (folder != sources) {
                val relative = folder.relativeTo(sources).invariantSeparatorsPath
                DictionaryLimits.check(relative.count { it == '/' } < 64, DictionaryProblem.LIMIT)
                DictionaryPaths.file(sources, relative)
            }
            true
        }.filter { it.isFile }.onEach { file ->
            DictionaryPaths.file(sources, file.relativeTo(sources).invariantSeparatorsPath)
            total += file.length()
            DictionaryLimits.check(++count <= DictionaryLimits.MAX_FILES && total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
        }.sortedBy { it.relativeTo(sources).path }
        files.forEach { file ->
            checkpoint()
            name(file.relativeTo(sources).invariantSeparatorsPath)
            digest.update(java.nio.ByteBuffer.allocate(8).putLong(file.length()).array())
            file.inputStream().buffered().use { input ->
                val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    checkpoint()
                    val size = input.read(bytes)
                    if (size < 0) break
                    digest.update(bytes, 0, size)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun copyTree(source: File, target: File, checkpoint: () -> Unit) {
        DictionaryLimits.check(source.isDirectory, DictionaryProblem.MISSING_FILE)
        DictionaryLimits.check(target.mkdirs() || target.isDirectory)
        var total = 0L
        var count = 0
        source.walkTopDown().filter { it.isFile }.forEach { file ->
            checkpoint()
            DictionaryLimits.check(++count <= DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
            val relative = file.relativeTo(source).invariantSeparatorsPath
            val input = DictionaryPaths.file(source, relative)
            val output = DictionaryPaths.file(target, relative)
            DictionaryLimits.check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
            input.inputStream().buffered().use { stream ->
                output.outputStream().buffered().use { destination ->
                    val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkpoint()
                        val size = stream.read(bytes)
                        if (size < 0) break
                        total += size
                        DictionaryLimits.check(total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
                        destination.write(bytes, 0, size)
                    }
                }
            }
        }
    }

    companion object {
        private val importMutex = Mutex()
        private val preparedDirectories = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val instance by lazy { LocalDictionaryStore(appCtx) }
        const val MANIFEST = "localDictionary.json"
        const val FILES = "localDictionaryFiles"
        fun isArchive(file: File): Boolean = Regex("(?i)\\.(zip|rar|7z|tar\\.gz|tgz)$").containsMatchIn(file.name)
        private fun isResourceArchive(file: File): Boolean = Regex("(?i)(\\.files\\.zip|\\.res\\.zip|^res\\.zip)$").containsMatchIn(file.name)
        fun isResource(file: File): Boolean = file.extension.lowercase(Locale.ROOT) in setOf(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "css", "woff", "woff2", "ttf", "otf",
            "mp3", "wav", "ogg", "m4a", "flac", "html", "htm"
        )
    }
}
