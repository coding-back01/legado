package io.legado.app.model.dictionary

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

class StarDictParser : DictionaryParser {
    override fun parse(file: File, workDir: File, sink: DictionarySink): DictionaryMetadata {
        DictionaryLimits.check(file.length() <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
        val lines = file.readLines()
        DictionaryLimits.check(lines.firstOrNull() == "StarDict's dict ifo file")
        val info = lines.drop(1).mapNotNull {
            val split = it.indexOf('=')
            if (split < 0) null else it.substring(0, split) to it.substring(split + 1)
        }.toMap()
        DictionaryLimits.check(info["version"] in setOf("2.4.2", "3.0.0"), DictionaryProblem.UNSUPPORTED)
        val count = info["wordcount"]?.toLongOrNull() ?: throw DictionaryException(DictionaryProblem.CORRUPT)
        DictionaryLimits.check(count in 1..DictionaryLimits.MAX_ENTRIES, DictionaryProblem.LIMIT)
        val base = file.name.removeSuffix(".ifo")
        fun component(suffix: String, alternate: String): File {
            val first = File(file.parentFile, base + suffix)
            return first.takeIf { it.isFile } ?: File(file.parentFile, base + alternate)
        }
        val index = component(".idx", ".idx.gz")
        val data = component(".dict", ".dict.dz")
        val expanded = File(workDir, "stardict.data")
        dictionaryInput(data).use { it.copyDictionaryTo(expanded, sink) }
        val wide = info["idxoffsetbits"] == "64"
        DictionaryLimits.check(info["idxoffsetbits"] == null || info["idxoffsetbits"] in setOf("32", "64"))
        RandomAccessFile(expanded, "r").use { body ->
            DataInputStream(dictionaryInput(index)).use { input ->
                repeat(count.toInt()) { ordinal ->
                    sink.checkpoint()
                    val word = input.readWord()
                    val offset = if (wide) input.readLong() else input.readInt().toLong() and 0xffffffffL
                    val size = input.readInt().toLong() and 0xffffffffL
                    val html = decode(body.readDictionaryBytes(offset, size), info["sametypesequence"].orEmpty(), sink, ordinal)
                    sink.entry(ordinal.toLong(), listOf(word), html)
                }
                DictionaryLimits.check(input.read() == -1)
            }
        }
        val synonyms = File(file.parentFile, "$base.syn")
        if (synonyms.exists()) {
            DataInputStream(synonyms.inputStream().buffered()).use { input ->
                var aliases = 0L
                while (input.available() > 0) {
                    sink.checkpoint()
                    val word = input.readWord()
                    val target = input.readInt().toLong() and 0xffffffffL
                    DictionaryLimits.check(target < count)
                    DictionaryLimits.check(++aliases <= DictionaryLimits.MAX_ENTRIES, DictionaryProblem.LIMIT)
                    sink.alias(word, target)
                }
            }
        }
        expanded.delete()
        return DictionaryMetadata(info["bookname"].orEmpty().ifBlank { base }, DictionaryFormat.STARDICT)
    }

    private fun decode(bytes: ByteArray, sequence: String, sink: DictionarySink, id: Int): String {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val result = StringBuilder()
        var field = 0
        while (input.available() > 0) {
            val type = if (sequence.isEmpty()) input.readUnsignedByte().toChar() else sequence.getOrNull(field)
                ?: throw DictionaryException(DictionaryProblem.CORRUPT)
            val last = sequence.isNotEmpty() && field == sequence.lastIndex
            val content = when {
                last -> ByteArray(input.available()).also { input.readFully(it) }
                type.isUpperCase() -> {
                    val size = input.readInt()
                    DictionaryLimits.check(size >= 0 && size <= input.available())
                    ByteArray(size).also { input.readFully(it) }
                }
                else -> input.readWord().toByteArray(Charsets.UTF_8)
            }
            when (type) {
                'h' -> result.append(content.toString(Charsets.UTF_8))
                'x' -> result.append(DictionaryMarkup.xdxf(content.toString(Charsets.UTF_8)))
                'm', 'l', 'g', 't', 'y', 'k', 'w' -> result.append(plainDictionaryHtml(content.toString(Charsets.UTF_8)))
                'r' -> content.toString(Charsets.UTF_8).lineSequence().forEach { line ->
                    val path = line.substringAfter(':', "")
                    when (line.substringBefore(':').lowercase(Locale.ROOT)) {
                        "img" -> result.append("<img src=\"${org.jsoup.nodes.Entities.escape(path)}\">")
                        "snd" -> result.append("<a href=\"sound://${org.jsoup.nodes.Entities.escape(path)}\">♪</a>")
                        else -> throw DictionaryException(DictionaryProblem.UNSUPPORTED, "StarDict:$type")
                    }
                }
                'W', 'P' -> {
                    val path = "embedded/$id-$field.${if (type == 'W') "wav" else "png"}"
                    sink.resource(path, content)
                    result.append(if (type == 'W') "<a href=\"sound://$path\">♪</a>" else "<img src=\"$path\">")
                }
                else -> throw DictionaryException(DictionaryProblem.UNSUPPORTED, "StarDict:$type")
            }
            field++
        }
        DictionaryLimits.check(sequence.isEmpty() || field == sequence.length)
        return result.toString()
    }
}
