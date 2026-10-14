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
import java.util.Locale
import java.util.UUID

enum class DictionaryOperation { BEFORE_PUBLISH, BEFORE_REGISTER, BEFORE_REBUILD_SWAP, OLD_INDEX_MOVED, NEW_INDEX_PUBLISHED, IDENTITY_METADATA_PUBLISHED }

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
        val settingsJson = preferences.getString("query_settings", null)
        val settings = DictionaryQuerySettings.decode(settingsJson)
        if ((settingsJson == null || settings != null) && settings?.legacyMigrated != true) {
            importMutex.withLock { migrateQuerySettingsLocked() }
        }
    }

    private fun migrateQuerySettingsLocked() {
        val json = preferences.getString("query_settings", null)
        val decoded = DictionaryQuerySettings.decode(json)
        if (json != null && decoded == null) return
        val current = decoded ?: DictionaryQuerySettings()
        val migrated = current.migrateLegacy(lastDictionaryId, all)
        if (migrated != current) querySettings = migrated
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

    var querySettings: DictionaryQuerySettings
        get() = DictionaryQuerySettings.decode(preferences.getString("query_settings", null)) ?: DictionaryQuerySettings()
        set(value) {
            val previous = DictionaryQuerySettings.decode(preferences.getString("query_settings", null))
            val saved = if (!value.legacyMigrated && previous?.legacyMigrated == true) {
                value.copy(legacyMigrated = true, legacySelectedId = previous.legacySelectedId)
            } else value
            preferences.edit { putString("query_settings", saved.encode()) }
        }

    val all: List<LocalDictionary> get() = appDb.localDictionaryDao.all

    fun directory(id: String): File {
        DictionaryLimits.check(Regex("[0-9a-f-]{36}").matches(id), DictionaryProblem.INVALID_PATH)
        return DictionaryPaths.file(File(root, "data"), id)
    }

    suspend fun importDocuments(
        uris: List<Uri>,
        onProgress: (String) -> Unit = {},
        choose: suspend (List<String>) -> Set<String> = { it.toSet() },
        chooseResources: suspend (String, List<String>) -> Set<String> = { _, _ -> emptySet() }
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
                        val parent = requireNotNull(source.parentFile)
                        val plan = DictionaryFileGroups.plan(parent, source.name, checkpoint = checkpoint)
                        val resources = if (plan.unassignedResources.isEmpty()) emptySet() else
                            chooseResources(source.name, plan.unassignedResources)
                        importSource(parent, source.name, checkpoint, resources)
                    }
                } finally {
                    folder.deleteRecursively()
                }
            }
        }

    suspend fun importFiles(sources: File, sourceFile: String, linkedResources: Set<String> = emptySet()): LocalDictionary = withContext(Dispatchers.IO) {
        prepare()
        importMutex.withLock {
            val coroutine = currentCoroutineContext()
            importSource(sources, sourceFile, { coroutine.ensureActive() }, linkedResources)
        }
    }

    private fun importSource(sources: File, sourceFile: String, checkpoint: () -> Unit, linkedResources: Set<String> = emptySet(),
        restoredIdentity: DictionaryBackupIdentity? = null): LocalDictionary {
        val legacyRestore = restoredIdentity?.dictionary?.identityVersion == 1 && restoredIdentity.record == null
        val group = restoredIdentity?.record?.group ?: DictionaryFileGroups.plan(sources, sourceFile, linkedResources, checkpoint)
        val staging = File(root, "staging").apply { mkdirs() }
        val folder = File(staging, "build-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val copiedSources = File(folder, "sources")
            if (legacyRestore) copyTree(sources, copiedSources, checkpoint)
            else DictionaryFileGroups.copy(sources, group, copiedSources, checkpoint)
            val publishedGroup = group.copy(unassignedResources = emptyList())
            val fingerprint = if (legacyRestore) DictionaryIdentity.legacy(copiedSources, sourceFile, checkpoint)
                else DictionaryIdentity.calculate(copiedSources, publishedGroup, checkpoint)
            if (restoredIdentity != null) DictionaryLimits.check(fingerprint == restoredIdentity.dictionary.fingerprint)
            val aliases = (listOf(DictionaryIdentity.legacy(copiedSources, sourceFile, checkpoint)) +
                restoredIdentity?.verifiedAliases.orEmpty()).distinct()
            for (old in all.filter { restoredIdentity == null && it.identityVersion == 1 && it.state == "ready" && it.sourceFile == sourceFile }) {
                try { upgradeIdentityLocked(old.id, null, checkpoint) }
                catch (error: CancellationException) { throw error }
                catch (_: DictionaryException) { /* 保留无法验证的旧数据；不按名称关联。 */ }
            }
            appDb.localDictionaryDao.findFingerprint(fingerprint)?.let {
                val marker = File(directory(it.id), "ready")
                if (!marker.isFile || marker.readText().trim() != DictionaryIndex.VERSION.toString()) rebuildLocked(it.id, checkpoint)
                return it
            }
            val pending = all.firstOrNull { it.state != "ready" && (it.fingerprint == fingerprint ||
                it.identityVersion == 1 && it.fingerprint in aliases) }
            val restored = restoredIdentity?.dictionary
            val id = pending?.id ?: restored?.id?.takeIf { appDb.localDictionaryDao.get(it) == null } ?: UUID.randomUUID().toString()
            val built = build(folder, sourceFile, checkpoint)
            val title = pending?.name ?: uniqueName(restored?.name ?: built.first.title)
            val dictionary = LocalDictionary(id, title, built.first.format.name, sourceFile, fingerprint,
                built.first.inputLanguage, built.first.outputLanguage, pending?.sortNumber ?: restored?.sortNumber ?: all.size,
                "ready", built.second, built.third, if (legacyRestore) 1 else 2)
            if (!legacyRestore) writeIdentityRecord(File(folder, "identity.json"), DictionaryIdentityRecord(publishedGroup,
                (aliases + restoredIdentity?.record?.legacyFingerprints.orEmpty() + listOfNotNull(pending?.fingerprint)).distinct()))
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

    private fun build(folder: File, sourceFile: String, checkpoint: () -> Unit,
        sources: File = File(folder, "sources")): Triple<DictionaryMetadata, Long, Int> {
        val source = DictionaryPaths.file(sources, sourceFile)
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

    suspend fun lookup(id: String, word: String, language: String? = null): List<DictionaryEntry> = withContext(Dispatchers.IO) {
        prepare()
        DictionaryLimits.check(word.length <= DictionaryLimits.MAX_WORD_BYTES, DictionaryProblem.LIMIT)
        val dictionary = appDb.localDictionaryDao.get(id)
        DictionaryLimits.check(dictionary?.state == "ready", DictionaryProblem.MISSING_FILE)
        val queryLanguage = language ?: dictionary?.let { querySettings.languageOf(it) }
        val cacheKey = "${queryLanguage.orEmpty()}\u0000$word"
        val coroutine = currentCoroutineContext()
        val marker = File(directory(id), "ready")
        if (marker.isFile && marker.readText().trim() == DictionaryIndex.VERSION.toString()) {
            queryCache.get(id, cacheKey)?.let { return@withContext it }
        }
        DictionaryIndex(directory(id)).use { it.lookup(word, queryLanguage) { coroutine.ensureActive() } }.also { queryCache.put(id, cacheKey, it) }
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
            pendingIdentityFile(id).delete()
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

    suspend fun upgradeIdentity(id: String, linkedResources: Set<String>? = null): Boolean = withContext(Dispatchers.IO) {
        prepare()
        importMutex.withLock {
            val coroutine = currentCoroutineContext()
            upgradeIdentityLocked(id, linkedResources) { coroutine.ensureActive() }
        }
    }

    suspend fun associatePending(pendingId: String, importedId: String): Boolean = withContext(Dispatchers.IO) {
        prepare()
        importMutex.withLock {
            val pending = appDb.localDictionaryDao.get(pendingId) ?: throw DictionaryException(DictionaryProblem.MISSING_FILE)
            val imported = appDb.localDictionaryDao.get(importedId) ?: throw DictionaryException(DictionaryProblem.MISSING_FILE)
            val linked = DictionaryIdentity.relinkPending(pending, imported)
            val coroutine = currentCoroutineContext()
            val checkpoint = { coroutine.ensureActive() }
            val source = directory(importedId)
            val sources = File(source, "sources")
            val fingerprint = if (imported.identityVersion == 2) DictionaryIdentity.calculate(sources, identityRecord(imported).group, checkpoint)
                else DictionaryIdentity.legacy(sources, imported.sourceFile, checkpoint)
            DictionaryLimits.check(fingerprint == imported.fingerprint)
            val target = directory(pendingId)
            DictionaryLimits.check(!target.exists())
            val settingsJson = preferences.getString("query_settings", null)
            val selected = lastDictionaryId
            checkpoint()
            DictionaryLimits.check(source.renameTo(target))
            try {
                checkpoint()
                beforeOperation(DictionaryOperation.BEFORE_REGISTER)
                appDb.runInTransaction {
                    appDb.localDictionaryDao.insert(linked)
                    appDb.localDictionaryDao.delete(importedId)
                }
                DictionaryQuerySettings.decode(settingsJson)?.let { previous ->
                    val remapped = previous.remap(mapOf(importedId to pendingId))
                    val language = previous.languageOverrides[pendingId]
                    querySettings = if (language == null) remapped else remapped.withLanguage(pendingId, language)
                }
                if (selected == importedId) lastDictionaryId = pendingId
                queryCache.remove(importedId); queryCache.remove(pendingId)
                true
            } catch (error: Exception) {
                DictionaryLimits.check(target.renameTo(source))
                appDb.runInTransaction { appDb.localDictionaryDao.insert(pending, imported) }
                preferences.edit { putString("query_settings", settingsJson); putString("last", selected) }
                throw error
            }
        }
    }

    private fun upgradeIdentityLocked(id: String, linkedResources: Set<String>?, checkpoint: () -> Unit): Boolean {
        val dictionary = appDb.localDictionaryDao.get(id) ?: throw DictionaryException(DictionaryProblem.MISSING_FILE)
        DictionaryLimits.check(dictionary.state == "ready", DictionaryProblem.MISSING_FILE)
        val current = directory(id)
        val upgrade = DictionaryIdentity.upgrade(File(current, "sources"), dictionary, linkedResources, checkpoint) ?: return false
        val identity = File(current, "identity.json")
        DictionaryLimits.check(!identity.exists() || identity.length() <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
        val previous = identity.takeIf { it.isFile }?.readBytes()
        val next = File(current, "identity-${UUID.randomUUID()}.tmp")
        try {
            writeIdentityRecord(next, upgrade.record)
            checkpoint()
            DictionaryLimits.check(next.renameTo(identity))
            beforeOperation(DictionaryOperation.IDENTITY_METADATA_PUBLISHED)
            checkpoint()
            appDb.localDictionaryDao.insert(upgrade.dictionary)
            return true
        } catch (error: Exception) {
            if (previous == null) identity.delete() else identity.writeBytes(previous)
            appDb.localDictionaryDao.insert(dictionary)
            throw error
        } finally {
            next.delete()
        }
    }

    private fun writeIdentityRecord(file: File, record: DictionaryIdentityRecord) {
        val estimated = record.group.files.sumOf { it.length.toLong() * 6 + 4 } + 1024
        DictionaryLimits.check(estimated <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
        val bytes = GSON.toJson(record).toByteArray(Charsets.UTF_8)
        DictionaryLimits.check(bytes.size <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
        java.io.FileOutputStream(file).use { stream -> stream.write(bytes); stream.fd.sync() }
    }

    private fun identityRecord(dictionary: LocalDictionary): DictionaryIdentityRecord {
        val file = if (dictionary.state == "missing") pendingIdentityFile(dictionary.id) else File(directory(dictionary.id), "identity.json")
        DictionaryLimits.check(file.isFile, DictionaryProblem.MISSING_FILE)
        DictionaryLimits.check(file.length() <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
        return try {
            val record = GSON.fromJson(file.readText(), DictionaryIdentityRecord::class.java)
            DictionaryLimits.check(record.group.identityVersion == 2 && record.group.sourceFile == dictionary.sourceFile)
            DictionaryLimits.check(record.legacyFingerprints.size <= DictionaryLimits.MAX_CANDIDATES, DictionaryProblem.LIMIT)
            DictionaryLimits.check(record.legacyFingerprints.all { Regex("[0-9a-f]{64}").matches(it) })
            record
        } catch (error: DictionaryException) { throw error }
        catch (error: Exception) { throw DictionaryException(DictionaryProblem.CORRUPT, "identity", error) }
    }

    private fun pendingIdentityFile(id: String): File {
        directory(id)
        return DictionaryPaths.file(File(root, "pending-identities"), "$id.json")
    }

    private fun rebuildLocked(id: String, checkpoint: () -> Unit) {
            val dictionary = appDb.localDictionaryDao.get(id) ?: throw DictionaryException(DictionaryProblem.MISSING_FILE)
            val current = directory(id)
            val source = File(current, "sources")
            val record = if (dictionary.identityVersion == 2) identityRecord(dictionary) else null
            val actual = if (record == null) fingerprint(source, dictionary.sourceFile, checkpoint)
                else DictionaryIdentity.calculate(source, record.group, checkpoint)
            DictionaryLimits.check(actual == dictionary.fingerprint)
            val staging = File(root, "staging").apply { mkdirs() }
            val next = File(staging, "rebuild-${UUID.randomUUID()}").apply { mkdirs() }
            val recovery = File(root, "recovery").apply { mkdirs() }
            val previous = DictionaryPaths.file(recovery, id)
            DictionaryLimits.check(!previous.exists())
            try {
                copyTree(source, File(next, "sources"), checkpoint)
                val selected = if (record == null) File(next, "sources") else File(next, "build-sources").also {
                    DictionaryFileGroups.copy(source, record.group, it, checkpoint)
                    writeIdentityRecord(File(next, "identity.json"), record)
                }
                build(next, dictionary.sourceFile, checkpoint, selected)
                if (record != null) selected.deleteRecursively()
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
        val version: Int = 2,
        val selectedId: String?,
        val includesFiles: Boolean,
        val dictionaries: List<LocalDictionary>,
        val querySettings: DictionaryQuerySettings? = null,
        val identities: Map<String, DictionaryIdentityRecord> = emptyMap()
    )

    suspend fun backup(target: File): List<File> = withContext(Dispatchers.IO) {
        prepare()
        importMutex.withLock {
            val coroutine = currentCoroutineContext()
            val checkpoint = { coroutine.ensureActive() }
            val included = includeFilesInBackup
            if (included) {
                for (old in all.filter { it.state == "ready" && it.identityVersion == 1 }) {
                    checkpoint()
                    DictionaryLimits.check(upgradeIdentityLocked(old.id, null, checkpoint), DictionaryProblem.ASSOCIATION_REQUIRED)
                }
            }
            val dictionaries = all
            val manifestFile = File(target, MANIFEST)
            target.mkdirs()
            val records = dictionaries.filter { it.identityVersion == 2 &&
                (it.state == "ready" || pendingIdentityFile(it.id).isFile) }.associate {
                it.id to identityRecord(it)
            }
            manifestFile.writeText(DictionaryCatalogCodec.encodeBackup(
                BackupManifest(selectedId = lastDictionaryId, includesFiles = included, dictionaries = dictionaries), querySettings, records))
            val output = arrayListOf(manifestFile)
            if (included) {
                val files = File(target, FILES).apply { mkdirs() }
                for (dictionary in dictionaries.filter { it.state == "ready" }) {
                    checkpoint()
                    val record = requireNotNull(records[dictionary.id])
                    val sources = File(directory(dictionary.id), "sources")
                    DictionaryLimits.check(DictionaryIdentity.calculate(sources, record.group, checkpoint) == dictionary.fingerprint)
                    DictionaryFileGroups.copy(sources, record.group, File(files, dictionary.id), checkpoint)
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
            if (json.get("version")?.asInt !in 1..2) return@withLock listOf("version")
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
                val dictionary = DictionaryCatalogCodec.decodeDictionary(item)
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
            val manifest = DictionaryCatalogCodec.decodeBackup(json.toString()) ?: return@withLock listOf("version")
            val restoreKey = DictionaryCatalogCodec.restoreKey(json.toString())
            val restoredKeys = preferences.getStringSet("restored_manifests", emptySet()).orEmpty()
            val repeated = restoreKey in restoredKeys
            val originalRows = all
            val originalIds = originalRows.map { it.id }.toSet()
            val data = File(root, "data")
            val originalDirectories = data.listFiles()?.map { it.name }?.toSet().orEmpty()
            val originalSettings = preferences.getString("query_settings", null)
            val originalSelected = lastDictionaryId
            var preferencesChanged = false
            val pendingBefore = linkedMapOf<File, ByteArray?>()
            fun rollback() {
                val currentRows = all
                data.listFiles()?.filter { it.name !in originalDirectories }?.forEach { it.deleteRecursively() }
                appDb.runInTransaction {
                    currentRows.filter { it.id !in originalIds }.forEach { appDb.localDictionaryDao.delete(it.id) }
                    appDb.localDictionaryDao.insert(*originalRows.toTypedArray())
                }
                (currentRows + originalRows).forEach { queryCache.remove(it.id) }
                pendingBefore.forEach { (file, bytes) -> if (bytes == null) file.delete() else file.writeBytes(bytes) }
                if (preferencesChanged) preferences.edit {
                    putString("query_settings", originalSettings)
                    putString("last", originalSelected)
                    putStringSet("restored_manifests", restoredKeys)
                }
            }
            try {
                for (dictionary in dictionaries) {
                    checkpoint()
                    if (included && dictionary.state == "ready") {
                        try {
                            val sources = DictionaryPaths.file(File(target, FILES), dictionary.id)
                            val identity = DictionaryIdentity.fromBackup(sources, dictionary, manifest.identities[dictionary.id], checkpoint)
                            val restored = appDb.localDictionaryDao.findFingerprint(identity.dictionary.fingerprint)
                                ?: appDb.localDictionaryDao.findFingerprint(dictionary.fingerprint)
                                ?: importSource(sources, dictionary.sourceFile, checkpoint, restoredIdentity = identity)
                            appDb.localDictionaryDao.sort(restored.id, dictionary.sortNumber)
                            mapping[dictionary.id] = restored.id
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failures.add(dictionary.name)
                        }
                    } else {
                        appDb.localDictionaryDao.findFingerprint(dictionary.fingerprint)?.let {
                            mapping[dictionary.id] = it.id
                            appDb.localDictionaryDao.sort(it.id, dictionary.sortNumber)
                            continue
                        }
                        val existing = appDb.localDictionaryDao.get(dictionary.id)
                        val id = if (existing == null || existing.fingerprint == dictionary.fingerprint) dictionary.id else UUID.randomUUID().toString()
                        appDb.localDictionaryDao.insert(dictionary.copy(id = id, state = "missing", entryCount = 0))
                        manifest.identities[dictionary.id]?.let { record ->
                            DictionaryLimits.check(record.group.sourceFile == dictionary.sourceFile && record.group.identityVersion == 2)
                            record.group.files.forEach { DictionaryPaths.file(File(root, "validation"), it) }
                            val pendingFile = pendingIdentityFile(id)
                            DictionaryLimits.check(!pendingFile.exists() || pendingFile.length() <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
                            if (pendingFile !in pendingBefore) pendingBefore[pendingFile] = pendingFile.takeIf { it.isFile }?.readBytes()
                            pendingFile.parentFile?.mkdirs()
                            writeIdentityRecord(pendingFile, record)
                        }
                        mapping[dictionary.id] = id
                    }
                }
                val selected = json.get("selectedId")?.takeUnless { it.isJsonNull }?.asString
                if (failures.isEmpty()) {
                    if (!repeated) {
                        preferencesChanged = true
                        lastDictionaryId = if (selected == null) null else mapping[selected] ?: lastDictionaryId
                        querySettings = if (manifest.querySettings != null) querySettings.restored(manifest.querySettings, mapping, false)
                            else querySettings.copy(legacyMigrated = false).migrateLegacy(lastDictionaryId, all)
                        preferences.edit { putStringSet("restored_manifests", restoredKeys + restoreKey) }
                    }
                }
                if (failures.isNotEmpty()) rollback()
                failures
            } catch (error: Exception) {
                rollback()
                throw error
            }
        }
    }

    private fun fingerprint(sources: File, sourceFile: String, checkpoint: () -> Unit): String =
        DictionaryIdentity.legacy(sources, sourceFile, checkpoint)

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
