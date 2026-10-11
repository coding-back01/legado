package io.legado.app.model.dictionary

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

/** BGL 块布局参考 PyGlossary 的格式读取说明；不引入 Python 运行时。 */
class BglParser : DictionaryParser {
    override fun parse(file: File, workDir: File, sink: DictionarySink): DictionaryMetadata {
        var title = file.name.removeSuffix(".bgl")
        var source = Charset.forName("windows-1252")
        var target = source
        var utf8 = false
        var id = 0L
        val expanded = File(workDir, "bgl.blocks")
        DataInputStream(FileInputStream(file)).use { input ->
            val magic = input.readInt()
            DictionaryLimits.check(magic == 0x12340001 || magic == 0x12340002)
            val offset = input.readUnsignedShort()
            DictionaryLimits.check(offset >= 6)
            input.skipBytes(offset - 6)
            BglGzip.extract(input, expanded, sink)
        }
        fun blocks(consume: (Int, ByteArray) -> Unit) {
            DataInputStream(expanded.inputStream().buffered()).use { input ->
                var blockCount = 0L
                while (true) {
                    sink.checkpoint()
                    val marker = input.read()
                    if (marker < 0) break
                    DictionaryLimits.check(++blockCount <= DictionaryLimits.MAX_ENTRIES, DictionaryProblem.LIMIT)
                    val encoded = marker ushr 4
                    val size = if (encoded >= 4) (encoded - 4).toLong() else {
                        var length = 0L
                        repeat(encoded + 1) { length = (length shl 8) or input.readUnsignedByte().toLong() }
                        length
                    }
                    DictionaryLimits.check(size <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
                    consume(marker and 15, ByteArray(size.toInt()).also { input.readFully(it) })
                }
            }
        }
        blocks { type, bytes ->
            if (type == 3 && bytes.size >= 3) {
                val code = ((bytes[0].toInt() and 255) shl 8) or (bytes[1].toInt() and 255)
                when (code) {
                    0x11 -> {
                        var flags = 0L
                        for (i in 2 until bytes.size) flags = (flags shl 8) or (bytes[i].toInt() and 255).toLong()
                        utf8 = flags and 0x8000 != 0L
                    }
                    0x1a -> source = charset(bytes[2].toInt() and 255)
                    0x1b -> target = charset(bytes[2].toInt() and 255)
                }
            }
        }
        if (utf8) { source = Charsets.UTF_8; target = Charsets.UTF_8 }
        blocks { type, bytes ->
            if (bytes.isNotEmpty()) DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                fun text(size: Long, encoding: Charset): String {
                    DictionaryLimits.check(size >= 0 && size <= input.available())
                    return ByteArray(size.toInt()).also { input.readFully(it) }.toString(encoding)
                }
                when (type) {
                    3 -> {
                        if (bytes.size >= 3 && input.readUnsignedShort() == 1) title = text(input.available().toLong(), target)
                    }
                    2 -> {
                        val path = text(input.readUnsignedByte().toLong(), source)
                        sink.resource(path, ByteArray(input.available()).also { input.readFully(it) })
                    }
                    1, 7, 10, 13 -> {
                        val words = arrayListOf(text(input.readUnsignedByte().toLong(), source))
                        val html = text(input.readUnsignedShort().toLong(), target).substringBefore('\u0014')
                        while (input.available() > 0) words.add(text(input.readUnsignedByte().toLong(), source))
                        sink.entry(id++, words, html)
                    }
                    11 -> {
                        var wordLength = 0L
                        repeat(5) { wordLength = (wordLength shl 8) or input.readUnsignedByte().toLong() }
                        val words = arrayListOf(text(wordLength, source))
                        val aliases = input.readInt()
                        DictionaryLimits.check(aliases in 0..DictionaryLimits.MAX_CANDIDATES)
                        repeat(aliases) { words.add(text(input.readInt().toLong(), source)) }
                        val html = text(input.readInt().toLong(), target).substringBefore('\u0014')
                        sink.entry(id++, words, html)
                    }
                }
            }
        }
        expanded.delete()
        DictionaryLimits.check(id > 0)
        return DictionaryMetadata(title, DictionaryFormat.BGL)
    }

    private fun charset(code: Int): Charset = Charset.forName(when (code) {
        0x41, 0x42 -> "windows-1252"
        0x43 -> "windows-1250"
        0x44 -> "windows-1251"
        0x45 -> "Shift_JIS"
        0x46 -> "Big5"
        0x47 -> "GB18030"
        0x48 -> "windows-1257"
        0x49 -> "windows-1253"
        0x4a -> "EUC-KR"
        0x4b -> "windows-1254"
        0x4c -> "windows-1255"
        0x4d -> "windows-1256"
        0x4e -> "TIS-620"
        else -> throw DictionaryException(DictionaryProblem.UNSUPPORTED, "BGL:$code")
    })
}
