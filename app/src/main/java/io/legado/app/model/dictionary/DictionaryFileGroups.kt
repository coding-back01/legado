package io.legado.app.model.dictionary

import java.io.File
import java.util.Locale

data class DictionaryFileGroup(
    val sourceFile: String,
    val files: List<String>,
    val unassignedResources: List<String> = emptyList(),
    val identityVersion: Int = 2
)

internal object DictionaryFileGroups {
    fun paths(root: File, checkpoint: () -> Unit = {}): List<String> = discover(root, checkpoint)
        .map { it.relativeTo(root.canonicalFile).invariantSeparatorsPath }.sorted()

    fun copy(root: File, group: DictionaryFileGroup, target: File, checkpoint: () -> Unit) {
        DictionaryLimits.check(group.files.size <= DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
        DictionaryLimits.check(target.mkdirs() || target.isDirectory)
        var bytes = 0L
        for (path in group.files) {
            checkpoint()
            val input = DictionaryPaths.file(root, path)
            DictionaryLimits.check(input == File(root.canonicalFile, path).absoluteFile, DictionaryProblem.INVALID_PATH)
            DictionaryLimits.check(input.isFile, DictionaryProblem.MISSING_FILE)
            val output = DictionaryPaths.file(target, path)
            DictionaryLimits.check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
            input.inputStream().buffered().use { stream ->
                output.outputStream().buffered().use { destination ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkpoint()
                        val size = stream.read(buffer)
                        if (size < 0) break
                        bytes += size
                        DictionaryLimits.check(bytes <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
                        destination.write(buffer, 0, size)
                    }
                }
            }
        }
    }

    fun plan(root: File, sourceFile: String, linkedResources: Set<String> = emptySet(), checkpoint: () -> Unit = {}): DictionaryFileGroup {
        val files = discover(root, checkpoint)
        val source = DictionaryPaths.file(root, sourceFile)
        DictionaryLimits.check(source.isFile, DictionaryProblem.MISSING_FILE)
        val format = DictionaryFormat.detect(source) ?: throw DictionaryException(DictionaryProblem.UNSUPPORTED)
        val selected = linkedSetOf(sourceFile)
        val base = source.name.substringBeforeLast('.').lowercase(Locale.ROOT)
        if (format == DictionaryFormat.MDICT) {
            source.parentFile?.listFiles()?.filter { file ->
                val name = file.name.lowercase(Locale.ROOT)
                file.isFile && (name == "$base.css" || name == "$base.mdd" || Regex("${Regex.escape(base)}\\.\\d+\\.mdd").matches(name))
            }?.forEach { selected.add(it.relativeTo(root.canonicalFile).invariantSeparatorsPath) }
        }
        if (format == DictionaryFormat.STARDICT) {
            val companions = setOf("$base.idx", "$base.idx.gz", "$base.dict", "$base.dict.dz", "$base.syn", "$base.res.zip")
            source.parentFile?.listFiles()?.filter { it.isFile && it.name.lowercase(Locale.ROOT) in companions }
                ?.forEach { selected.add(it.relativeTo(root.canonicalFile).invariantSeparatorsPath) }
            for (alternatives in listOf(listOf("$base.idx", "$base.idx.gz"), listOf("$base.dict", "$base.dict.dz"))) {
                DictionaryLimits.check(alternatives.any { name -> selected.any { File(it).name.lowercase(Locale.ROOT) == name } },
                    DictionaryProblem.MISSING_FILE)
            }
        }
        if (format == DictionaryFormat.DSL) {
            val archives = setOf(source.name.lowercase(Locale.ROOT) + ".files.zip",
                source.name.lowercase(Locale.ROOT).removeSuffix(".dz") + ".files.zip")
            source.parentFile?.listFiles()?.filter { it.isFile && it.name.lowercase(Locale.ROOT) in archives }
                ?.forEach { selected.add(it.relativeTo(root.canonicalFile).invariantSeparatorsPath) }
        }
        if (format == DictionaryFormat.DICTD) {
            val bodies = setOf("$base.dict", "$base.dict.dz")
            val companion = source.parentFile?.listFiles()?.firstOrNull { it.isFile && it.name.lowercase(Locale.ROOT) in bodies }
                ?: throw DictionaryException(DictionaryProblem.MISSING_FILE, "$base.dict")
            selected.add(companion.relativeTo(root.canonicalFile).invariantSeparatorsPath)
        }
        for (path in linkedResources) {
            val resource = DictionaryPaths.file(root, path)
            DictionaryLimits.check(resource.isFile, DictionaryProblem.MISSING_FILE)
            DictionaryLimits.check(LocalDictionaryStore.isResource(resource) || resource.name == "res.zip", DictionaryProblem.UNSUPPORTED)
            selected.add(resource.relativeTo(root.canonicalFile).invariantSeparatorsPath)
        }
        val primaries = files.filter { DictionaryFormat.detect(it) != null }
        val styles = primaries.map { File(it.parentFile, it.name.substringBeforeLast('.') + ".css").path }.toSet()
        val unassigned = files.filter {
            (LocalDictionaryStore.isResource(it) || it.name == "res.zip") && it.path !in styles
        }.map { it.relativeTo(root.canonicalFile).invariantSeparatorsPath }.filter { it !in selected }.sorted().toList()
        return DictionaryFileGroup(sourceFile, selected.sorted(), unassigned)
    }

    private fun discover(root: File, checkpoint: () -> Unit): List<File> {
        DictionaryLimits.check(root.isDirectory, DictionaryProblem.MISSING_FILE)
        val canonical = root.canonicalFile
        var count = 0
        var bytes = 0L
        fun validate(file: File) {
            checkpoint()
            if (file != canonical) {
                val path = file.relativeTo(canonical).invariantSeparatorsPath
                DictionaryLimits.check(DictionaryPaths.file(canonical, path) == file.absoluteFile, DictionaryProblem.INVALID_PATH)
            }
            DictionaryLimits.check(++count <= DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
        }
        return canonical.walkTopDown().onEnter { validate(it); true }.filter { it.isFile }.onEach {
            validate(it)
            bytes += it.length()
            DictionaryLimits.check(bytes <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
        }.toList()
    }
}
