package io.legado.app.model.dictionary

import org.jsoup.nodes.Entities
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.io.Reader
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

internal fun dictionaryInput(file: File, sink: DictionarySink? = null, xml: Boolean = false): InputStream {
    if (!file.isFile) throw DictionaryException(DictionaryProblem.MISSING_FILE, file.name)
    val input = BufferedInputStream(file.inputStream())
    return try {
        input.mark(2)
        val gzip = input.read() == 0x1f && input.read() == 0x8b
        input.reset()
        val decoded = if (gzip) GZIPInputStream(input) else input
        object : FilterInputStream(decoded) {
            private var total = 0L
            private var markup = false
            private var tokenSize = 0
            private var quote = 0
            private var previous = 0
            private var beforePrevious = 0
            private val prefix = StringBuilder()

            private fun accept(value: Int) {
                DictionaryLimits.check(++total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
                if (total % DEFAULT_BUFFER_SIZE == 0L) sink?.checkpoint()
                if (!xml) return
                // XML 的文本由 SAX 分块处理；标记、注释和 CDATA 也必须有独立上限。
                if (value == 0) throw DictionaryException(DictionaryProblem.UNSUPPORTED, "XML encoding")
                if (!markup && value == '<'.code) {
                    markup = true; tokenSize = 0; quote = 0; prefix.setLength(0)
                }
                if (markup) {
                    DictionaryLimits.check(++tokenSize <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
                    if (prefix.length < 9) prefix.append(value.toChar())
                    val comment = prefix.startsWith("<!--")
                    val cdata = prefix.startsWith("<![CDATA[")
                    if (!comment && !cdata && (value == '\''.code || value == '"'.code)) {
                        if (quote == value) quote = 0 else if (quote == 0) quote = value
                    }
                    if (value == '>'.code && quote == 0 &&
                        (!comment || previous == '-'.code && beforePrevious == '-'.code) &&
                        (!cdata || previous == ']'.code && beforePrevious == ']'.code)) markup = false
                }
                beforePrevious = previous; previous = value
            }

            override fun read(): Int = `in`.read().also { if (it >= 0) accept(it) }
            override fun markSupported() = false
            override fun mark(readLimit: Int) = Unit
            override fun reset(): Unit = throw java.io.IOException("mark is not supported")
            override fun skip(count: Long): Long {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var skipped = 0L
                while (skipped < count) {
                    val size = read(buffer, 0, minOf(buffer.size.toLong(), count - skipped).toInt())
                    if (size < 0) break
                    skipped += size
                }
                return skipped
            }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                sink?.checkpoint()
                return `in`.read(bytes, offset, length).also { size ->
                    if (xml) for (index in offset until offset + maxOf(size, 0)) accept(bytes[index].toInt() and 255)
                    else if (size > 0) {
                        total += size
                        DictionaryLimits.check(total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
                    }
                }
            }
        }
    } catch (e: Exception) {
        input.close()
        throw e
    }
}

internal fun InputStream.copyDictionaryTo(file: File, sink: DictionarySink) {
    file.outputStream().buffered().use { output ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            sink.checkpoint()
            val count = read(buffer)
            if (count < 0) break
            total += count
            DictionaryLimits.check(total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
            output.write(buffer, 0, count)
        }
    }
}

internal fun DataInputStream.readWord(charset: Charset = Charsets.UTF_8): String {
    val bytes = ByteArrayOutputStream()
    while (true) {
        val value = read()
        if (value == -1) throw DictionaryException(DictionaryProblem.CORRUPT)
        if (value == 0) break
        DictionaryLimits.check(bytes.size() < DictionaryLimits.MAX_WORD_BYTES, DictionaryProblem.LIMIT)
        bytes.write(value)
    }
    return bytes.toByteArray().toString(charset)
}

internal fun RandomAccessFile.readDictionaryBytes(offset: Long, length: Long): ByteArray {
    DictionaryLimits.check(offset >= 0 && length >= 0 && offset <= length() && length <= length() - offset)
    DictionaryLimits.check(length <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
    seek(offset)
    return ByteArray(length.toInt()).also { readFully(it) }
}

internal fun plainDictionaryHtml(text: String): String =
    "<div class=\"definition\">${Entities.escape(text).replace("\n", "<br>")}</div>"

internal fun dictionaryLink(word: String): String =
    "entry://lookup/" + java.net.URLEncoder.encode(word, "UTF-8")

internal fun Reader.forDictionaryLines(checkpoint: () -> Unit, consume: (String) -> Unit) {
    val buffer = CharArray(DEFAULT_BUFFER_SIZE)
    val line = StringBuilder()
    var carriageReturn = false
    while (true) {
        checkpoint()
        val size = read(buffer)
        if (size < 0) break
        for (index in 0 until size) {
            val char = buffer[index]
            if (char == '\n' && carriageReturn) { carriageReturn = false; continue }
            carriageReturn = char == '\r'
            if (char == '\n' || char == '\r') {
                consume(line.toString())
                line.setLength(0)
            } else {
                DictionaryLimits.check(line.length < DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
                line.append(char)
            }
        }
    }
    if (line.isNotEmpty()) consume(line.toString())
}

object DictionaryPaths {
    fun file(root: File, relative: String): File {
        val path = relative.replace('\\', '/')
        if (path.isEmpty() || path.startsWith('/') || path.contains('\u0000') ||
            path.split('/').any { it == ".." } || Regex("^[A-Za-z]:").containsMatchIn(path)
        ) throw DictionaryException(DictionaryProblem.INVALID_PATH)
        DictionaryLimits.check(path.length <= DictionaryLimits.MAX_WORD_BYTES && path.count { it == '/' } < 64, DictionaryProblem.LIMIT)
        val parent = root.canonicalFile
        val file = File(parent, path).canonicalFile
        if (!file.path.startsWith(parent.path + File.separator)) {
            throw DictionaryException(DictionaryProblem.INVALID_PATH)
        }
        return file
    }
}
