package io.legado.app.model.dictionary

import java.io.File
import java.io.RandomAccessFile

class DictdParser : DictionaryParser {
    override fun parse(file: File, workDir: File, sink: DictionarySink): DictionaryMetadata {
        val base = file.name.removeSuffix(".index")
        val original = File(file.parentFile, "$base.dict").takeIf { it.isFile }
            ?: File(file.parentFile, "$base.dict.dz")
        val data = File(workDir, "dictd.data")
        dictionaryInput(original).use { it.copyDictionaryTo(data, sink) }
        RandomAccessFile(data, "r").use { body ->
            file.bufferedReader().use { reader ->
                var id = 0L
                reader.forDictionaryLines(sink::checkpoint) { line ->
                    sink.checkpoint()
                    val fields = line.split('\t')
                    DictionaryLimits.check(fields.size >= 3)
                    val content = body.readDictionaryBytes(number(fields[1]), number(fields[2])).toString(Charsets.UTF_8)
                    sink.entry(id++, listOf(fields[0]), plainDictionaryHtml(content))
                    DictionaryLimits.check(id <= DictionaryLimits.MAX_ENTRIES, DictionaryProblem.LIMIT)
                }
                DictionaryLimits.check(id > 0)
            }
        }
        data.delete()
        return DictionaryMetadata(base, DictionaryFormat.DICTD)
    }

    private fun number(value: String): Long {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        var result = 0L
        DictionaryLimits.check(value.isNotEmpty())
        for (char in value) {
            val digit = alphabet.indexOf(char)
            DictionaryLimits.check(digit >= 0 && result <= (Long.MAX_VALUE - digit) / 64)
            result = result * 64 + digit
        }
        return result
    }
}
