package io.legado.app.model.dictionary

import org.anarres.lzo.LzoDecompressor1x
import org.anarres.lzo.lzo_uintp
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.Adler32
import java.util.zip.InflaterInputStream

/** MDict 的文件结构参考 mdict4j；按块写入临时文件，避免保留整本词头和正文。 */
class MdictParser : DictionaryParser {
    override fun parse(file: File, workDir: File, sink: DictionarySink): DictionaryMetadata {
        val attributes = parseFile(file, workDir, sink, false)
        val base = file.name.substringBeforeLast('.')
        file.parentFile?.listFiles()?.filter {
            val name = it.name.lowercase(Locale.ROOT)
            it.isFile && (name == "$base.mdd".lowercase(Locale.ROOT) ||
                (name.startsWith("$base.".lowercase(Locale.ROOT)) && name.endsWith(".mdd")))
        }?.sortedBy { it.name }?.forEach { parseFile(it, workDir, sink, true) }
        val title = attributes["Title"].orEmpty().ifBlank { base }
        return DictionaryMetadata(Jsoup.parse(title).text(), DictionaryFormat.MDICT)
    }

    private fun parseFile(file: File, workDir: File, sink: DictionarySink, resource: Boolean): Map<String, String> {
        RandomAccessFile(file, "r").use { input ->
            val headerLength = input.readInt()
            DictionaryLimits.check(headerLength in 1..(1024 * 1024), DictionaryProblem.LIMIT)
            val header = ByteArray(headerLength).also { input.readFully(it) }
            val checksum = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            DictionaryLimits.check(Adler32().apply { update(header) }.value == checksum)
            val element = Jsoup.parse(header.toString(Charsets.UTF_16LE), "", Parser.xmlParser())
                .selectFirst(if (resource) "Library_Data" else "Dictionary")
                ?: throw DictionaryException(DictionaryProblem.CORRUPT)
            val attributes = element.attributes().associate { it.key to it.value }
            val styles = attributes["StyleSheet"].orEmpty().replace("\r", "").split('\n').chunked(3)
                .filter { it.size == 3 }.associate { it[0] to (it[1] to it[2]) }
            val version = attributes["GeneratedByEngineVersion"] ?: attributes["RequiredEngineVersion"] ?: "2.0"
            DictionaryLimits.check(version.startsWith("1.") || version.startsWith("2."), DictionaryProblem.UNSUPPORTED)
            val wide = version.startsWith("2.")
            val encrypted = when (attributes["Encrypted"].orEmpty()) {
                "", "No" -> 0
                "Yes" -> 1
                else -> attributes["Encrypted"]?.toIntOrNull() ?: throw DictionaryException(DictionaryProblem.CORRUPT)
            }
            DictionaryLimits.check(encrypted and 1 == 0, DictionaryProblem.PROTECTED)
            val charset = if (resource) Charsets.UTF_16LE else charset(attributes["Encoding"] ?: "UTF-8")
            fun number(): Long = if (wide) input.readLong() else input.readInt().toLong() and 0xffffffffL
            val keyHeaderStart = input.filePointer
            val blocks = number()
            val count = number()
            val infoExpanded = if (wide) number() else 0L
            val infoSize = number()
            val keysSize = number()
            DictionaryLimits.check(blocks in 1..65_536 && count in 1..DictionaryLimits.MAX_ENTRIES, DictionaryProblem.LIMIT)
            if (wide) {
                val end = input.filePointer
                input.seek(keyHeaderStart)
                val bytes = ByteArray(40).also { input.readFully(it) }
                input.seek(end)
                val adler = input.readInt().toLong() and 0xffffffffL
                DictionaryLimits.check(Adler32().apply { update(bytes) }.value == adler)
            }
            DictionaryLimits.check(infoSize in 1..DictionaryLimits.MAX_BLOCK_BYTES)
            val infoRaw = ByteArray(infoSize.toInt()).also { input.readFully(it) }
            val info = if (wide) unpack(infoRaw, infoExpanded, encrypted and 2 != 0) else infoRaw
            val compressed = LongArray(blocks.toInt())
            val expanded = LongArray(blocks.toInt())
            val blockCounts = LongArray(blocks.toInt())
            DataInputStream(ByteArrayInputStream(info)).use { index ->
                fun indexNumber(): Long = if (wide) index.readLong() else index.readInt().toLong() and 0xffffffffL
                for (i in compressed.indices) {
                    blockCounts[i] = indexNumber()
                    DictionaryLimits.check(blockCounts[i] in 0..count)
                    repeat(2) {
                        val length = if (wide) index.readUnsignedShort() else index.readUnsignedByte()
                        val size = length * if (charset == Charsets.UTF_16LE) 2 else 1
                        DictionaryLimits.check(size <= index.available())
                        index.skipBytes(size)
                        if (wide) index.skipBytes(if (charset == Charsets.UTF_16LE) 2 else 1)
                    }
                    compressed[i] = indexNumber()
                    expanded[i] = indexNumber()
                }
                DictionaryLimits.check(index.available() == 0 && compressed.sum() == keysSize && blockCounts.sum() == count)
            }
            val keys = File(workDir, "mdict.keys")
            var actualCount = 0L
            DataOutputStream(keys.outputStream().buffered()).use { output ->
                for (i in compressed.indices) {
                    sink.checkpoint()
                    val raw = readBlock(input, compressed[i])
                    DataInputStream(ByteArrayInputStream(unpack(raw, expanded[i]))).use { keyBlock ->
                        repeat(blockCounts[i].toInt()) {
                            val offset = if (wide) keyBlock.readLong() else keyBlock.readInt().toLong() and 0xffffffffL
                            val word = keyWord(keyBlock, charset)
                            output.writeLong(offset)
                            output.writeUTF(word)
                            actualCount++
                        }
                        DictionaryLimits.check(keyBlock.available() == 0)
                    }
                }
            }
            DictionaryLimits.check(actualCount == count)
            val recordBlocks = number()
            val recordCount = number()
            val recordInfoSize = number()
            val recordSize = number()
            DictionaryLimits.check(recordBlocks in 1..262_144 && recordCount == count, DictionaryProblem.LIMIT)
            DictionaryLimits.check(recordInfoSize == recordBlocks * (if (wide) 16 else 8))
            val recordCompressed = LongArray(recordBlocks.toInt())
            val recordExpanded = LongArray(recordBlocks.toInt())
            for (i in recordCompressed.indices) {
                recordCompressed[i] = number()
                recordExpanded[i] = number()
            }
            DictionaryLimits.check(recordCompressed.sum() == recordSize)
            val body = File(workDir, "mdict.records")
            var total = 0L
            body.outputStream().buffered().use { output ->
                for (i in recordCompressed.indices) {
                    sink.checkpoint()
                    val bytes = unpack(readBlock(input, recordCompressed[i]), recordExpanded[i])
                    total += bytes.size
                    DictionaryLimits.check(total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
                    output.write(bytes)
                }
            }
            RandomAccessFile(body, "r").use { data ->
                DataInputStream(keys.inputStream().buffered()).use { index ->
                    var read = 1L
                    var offset = index.readLong()
                    val words = arrayListOf(index.readUTF())
                    fun emit(end: Long) {
                        val bytes = data.readDictionaryBytes(offset, end - offset)
                        if (resource) {
                            for (word in words) sink.resource(word.replace('\\', '/').trimStart('/'), bytes)
                        } else {
                            val rawHtml = bytes.toString(charset).trimEnd('\u0000')
                            val html = Regex("`([0-9]+)`([^`]*)").replace(rawHtml) { match ->
                                val style = styles[match.groupValues[1]]
                                if (style == null) match.value else style.first + match.groupValues[2] + style.second
                            }
                            val target = if (html.startsWith("@@@LINK=")) html.removePrefix("@@@LINK=").trim() else null
                            val css = File(file.parentFile, file.name.substringBeforeLast('.') + ".css")
                            val styled = if (css.isFile && target == null) "<link rel=\"stylesheet\" href=\"${org.jsoup.nodes.Entities.escape(css.name)}\">$html" else html
                            sink.entry(offset, words.toList(), styled, target)
                        }
                    }
                    while (read < count) {
                        sink.checkpoint()
                        val next = index.readLong()
                        val word = index.readUTF()
                        DictionaryLimits.check(next >= offset)
                        if (next > offset) {
                            emit(next)
                            offset = next
                            words.clear()
                        }
                        words.add(word)
                        read++
                    }
                    emit(total)
                }
            }
            keys.delete()
            body.delete()
            return attributes
        }
    }

    private fun charset(name: String): Charset = try {
        when (name.uppercase(Locale.ROOT)) {
            "UTF-16", "UTF-16LE" -> Charsets.UTF_16LE
            "GBK", "GB2312" -> Charset.forName("GB18030")
            else -> Charset.forName(name)
        }
    } catch (e: Exception) {
        throw DictionaryException(DictionaryProblem.UNSUPPORTED, name, e)
    }

    private fun keyWord(input: DataInputStream, charset: Charset): String {
        if (charset != Charsets.UTF_16LE) return input.readWord(charset)
        val output = java.io.ByteArrayOutputStream()
        while (true) {
            val a = input.readUnsignedByte()
            val b = input.readUnsignedByte()
            if (a == 0 && b == 0) break
            DictionaryLimits.check(output.size() < DictionaryLimits.MAX_WORD_BYTES, DictionaryProblem.LIMIT)
            output.write(a)
            output.write(b)
        }
        return output.toByteArray().toString(charset)
    }

    private fun readBlock(input: RandomAccessFile, length: Long): ByteArray {
        DictionaryLimits.check(length in 8..DictionaryLimits.MAX_BLOCK_BYTES, DictionaryProblem.LIMIT)
        DictionaryLimits.check(length <= input.length() - input.filePointer)
        return ByteArray(length.toInt()).also { input.readFully(it) }
    }

    private fun unpack(raw: ByteArray, expected: Long, encrypted: Boolean = false): ByteArray {
        DictionaryLimits.check(raw.size >= 8)
        DictionaryLimits.check(expected in 0..DictionaryLimits.MAX_BLOCK_BYTES, DictionaryProblem.LIMIT)
        var payload = raw.copyOfRange(8, raw.size)
        if (encrypted) {
            val key = MdictIndexDigest.digest(raw.copyOfRange(4, 8) + byteArrayOf(0x95.toByte(), 0x36, 0, 0))
            var previous = 0x36
            val decodedPayload = ByteArray(payload.size)
            for (i in payload.indices) {
                val value = payload[i].toInt() and 0xff
                val decoded = ((value ushr 4) or (value shl 4)) xor previous xor (i and 0xff) xor key[i % 16].toInt()
                previous = value
                decodedPayload[i] = decoded.toByte()
            }
            payload = decodedPayload
        }
        val type = ByteBuffer.wrap(raw, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val bytes = when (type) {
            0 -> payload
            1 -> {
                val output = ByteArray(expected.toInt())
                val length = lzo_uintp(output.size)
                val result = LzoDecompressor1x().decompress(payload, 0, payload.size, output, 0, length)
                DictionaryLimits.check(result == 0 && length.value == output.size)
                output
            }
            2 -> InflaterInputStream(ByteArrayInputStream(payload)).use { input ->
                val output = ByteArray(expected.toInt())
                DataInputStream(input).readFully(output)
                DictionaryLimits.check(input.read() == -1)
                output
            }
            else -> throw DictionaryException(DictionaryProblem.UNSUPPORTED, "MDict:$type")
        }
        DictionaryLimits.check(bytes.size.toLong() == expected)
        val adler = ByteBuffer.wrap(raw, 4, 4).int.toLong() and 0xffffffffL
        DictionaryLimits.check(Adler32().apply { update(bytes) }.value == adler)
        return bytes
    }
}
