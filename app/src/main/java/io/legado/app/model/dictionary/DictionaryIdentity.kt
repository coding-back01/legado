package io.legado.app.model.dictionary

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import io.legado.app.data.entities.LocalDictionary

data class DictionaryIdentityRecord(
    val group: DictionaryFileGroup,
    val legacyFingerprints: List<String> = emptyList()
)

internal data class DictionaryIdentityUpgrade(val dictionary: LocalDictionary, val record: DictionaryIdentityRecord)
internal data class DictionaryBackupIdentity(
    val dictionary: LocalDictionary,
    val record: DictionaryIdentityRecord?,
    val verifiedAliases: List<String> = emptyList()
)

internal object DictionaryIdentity {
    fun relinkPending(pending: LocalDictionary, imported: LocalDictionary): LocalDictionary {
        DictionaryLimits.check(pending.state == "missing" && imported.state == "ready" && pending.id != imported.id)
        return imported.copy(id = pending.id, name = pending.name, sortNumber = pending.sortNumber)
    }

    fun fromBackup(root: File, dictionary: LocalDictionary, record: DictionaryIdentityRecord? = null,
        checkpoint: () -> Unit = {}): DictionaryBackupIdentity {
        if (dictionary.identityVersion == 1) {
            DictionaryLimits.check(legacy(root, dictionary.sourceFile, checkpoint) == dictionary.fingerprint)
            val group = DictionaryFileGroups.plan(root, dictionary.sourceFile, checkpoint = checkpoint)
            if (group.unassignedResources.isNotEmpty()) return DictionaryBackupIdentity(dictionary, null, listOf(dictionary.fingerprint))
            val actual = DictionaryIdentityRecord(group, listOf(dictionary.fingerprint))
            return DictionaryBackupIdentity(dictionary.copy(identityVersion = 2,
                fingerprint = calculate(root, group, checkpoint)), actual, listOf(dictionary.fingerprint))
        }
        DictionaryLimits.check(dictionary.identityVersion == 2, DictionaryProblem.UNSUPPORTED)
        val actual = record ?: throw DictionaryException(DictionaryProblem.CORRUPT, "missing identity")
        DictionaryLimits.check(actual.group.sourceFile == dictionary.sourceFile)
        DictionaryLimits.check(calculate(root, actual.group, checkpoint) == dictionary.fingerprint)
        DictionaryLimits.check(DictionaryFileGroups.paths(root, checkpoint) == actual.group.files.distinct().sorted())
        return DictionaryBackupIdentity(dictionary, actual, listOf(legacy(root, dictionary.sourceFile, checkpoint)))
    }

    fun upgrade(root: File, dictionary: LocalDictionary, linkedResources: Set<String>? = null,
        checkpoint: () -> Unit = {}): DictionaryIdentityUpgrade? {
        if (dictionary.identityVersion != 1) return null
        DictionaryLimits.check(legacy(root, dictionary.sourceFile, checkpoint) == dictionary.fingerprint)
        val group = DictionaryFileGroups.plan(root, dictionary.sourceFile, linkedResources.orEmpty(), checkpoint)
        if (linkedResources == null && group.unassignedResources.isNotEmpty()) return null
        val publishedGroup = group.copy(unassignedResources = emptyList())
        return DictionaryIdentityUpgrade(dictionary.copy(identityVersion = 2,
            fingerprint = calculate(root, publishedGroup, checkpoint)),
            DictionaryIdentityRecord(publishedGroup, listOf(dictionary.fingerprint)))
    }

    fun calculate(root: File, group: DictionaryFileGroup, checkpoint: () -> Unit = {}): String {
        DictionaryLimits.check(group.identityVersion == 2 && group.sourceFile in group.files)
        DictionaryLimits.check(group.files.size <= DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
        val digest = MessageDigest.getInstance("SHA-256")
        fun name(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(8).putLong(bytes.size.toLong()).array())
            digest.update(bytes)
        }
        name("legado-dictionary-files:2")
        name(group.sourceFile)
        var total = 0L
        for (path in group.files.distinct().sorted()) {
            checkpoint()
            val file = DictionaryPaths.file(root, path)
            DictionaryLimits.check(file == File(root.canonicalFile, path).absoluteFile, DictionaryProblem.INVALID_PATH)
            DictionaryLimits.check(file.isFile, DictionaryProblem.MISSING_FILE)
            total += file.length()
            DictionaryLimits.check(total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
            name(path)
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

    fun legacy(sources: File, sourceFile: String, checkpoint: () -> Unit): String {
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

}
