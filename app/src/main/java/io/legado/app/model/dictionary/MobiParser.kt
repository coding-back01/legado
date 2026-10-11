package io.legado.app.model.dictionary

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

internal object MobiNative {
    init { System.loadLibrary("legado_dictionary") }
    external fun extract(input: ByteArray, output: ByteArray, limit: Long): Int
}

class MobiParser : DictionaryParser {
    override fun parse(file: File, workDir: File, sink: DictionarySink): DictionaryMetadata {
        // JNI 接收标准 UTF-8 路径，避免补充平面字符被修改后的 UTF-8 编码破坏。
        sink.checkpoint()
        DictionaryLimits.check(file.length() <= 256L * 1024 * 1024, DictionaryProblem.LIMIT)
        val result = MobiNative.extract(file.absolutePath.toByteArray(), workDir.absolutePath.toByteArray(), 512L * 1024 * 1024)
        if (result != 0) throw DictionaryException(when (result) {
            2 -> DictionaryProblem.UNSUPPORTED
            3 -> DictionaryProblem.PROTECTED
            4 -> DictionaryProblem.LIMIT
            else -> DictionaryProblem.CORRUPT
        })
        sink.checkpoint()
        var title = file.nameWithoutExtension
        var inputLanguage = ""
        var outputLanguage = ""
        DataInputStream(File(workDir, "mobi.index").inputStream().buffered()).use { input ->
            DictionaryLimits.check(input.readInt() == 0x4c444d49)
            fun charset(code: Int): Charset = when (code) {
                65001 -> Charsets.UTF_8
                1252 -> Charset.forName("windows-1252")
                else -> throw DictionaryException(DictionaryProblem.UNSUPPORTED)
            }
            val encoding = charset(input.readInt())
            fun text(charset: Charset = Charsets.UTF_8): String {
                val size = input.readInt()
                DictionaryLimits.check(size in 0..DictionaryLimits.MAX_ENTRY_BYTES)
                return ByteArray(size).also { input.readFully(it) }.toString(charset)
            }
            title = text().ifBlank { title }
            val count = input.readInt()
            DictionaryLimits.check(count in 1..DictionaryLimits.MAX_ENTRIES.toInt())
            inputLanguage = text()
            outputLanguage = text()
            // libmobi 已将 ORDT 中的 UTF-16 字符表转换为 UTF-8 标签。
            fun indexCharset(code: Int): Charset = if (code == 65002) Charsets.UTF_8 else charset(code)
            val wordEncoding = indexCharset(input.readInt())
            val inflectionEncoding = indexCharset(input.readInt())
            RandomAccessFile(File(workDir, "mobi.body"), "r").use { body ->
                repeat(count) {
                    sink.checkpoint()
                    val offset = input.readInt().toLong() and 0xffffffffL
                    val length = input.readInt().toLong() and 0xffffffffL
                    val word = text(wordEncoding)
                    val inflections = Jsoup.parse(text(inflectionEncoding), "", Parser.xmlParser()).select("idx|iform, idx\\:iform")
                        .map { entry -> entry.attr("value") }
                    if (length == 0L) {
                        sink.alias(word, offset)
                        return@repeat
                    }
                    val document = Jsoup.parseBodyFragment(body.readDictionaryBytes(offset, length).toString(encoding))
                    document.select("img[recindex]").forEach { image ->
                        val number = image.attr("recindex").toIntOrNull()
                        if (number != null) {
                            val resource = workDir.listFiles()?.firstOrNull { candidate ->
                                candidate.name.startsWith("resource%05d.".format(java.util.Locale.ROOT, number - 1))
                            }
                            if (resource != null) image.attr("src", resource.name)
                        }
                    }
                    document.select("a[filepos]").forEach { link -> link.attr("href", "entry-id://${link.attr("filepos")}") }
                    sink.entry(offset, listOf(word) + inflections, document.body().html())
                }
            }
        }
        workDir.listFiles()?.filter { it.name.startsWith("resource") || it.name.startsWith("flow") }?.forEach {
            sink.checkpoint()
            sink.resource(it.name, it.readBytes())
        }
        return DictionaryMetadata(title, DictionaryFormat.MOBI, inputLanguage, outputLanguage)
    }
}
