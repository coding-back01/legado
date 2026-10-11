package io.legado.app.model.dictionary

import java.io.BufferedInputStream
import java.io.File
import java.nio.charset.Charset

class DslParser : DictionaryParser {
    override fun parse(file: File, workDir: File, sink: DictionarySink): DictionaryMetadata {
        var title = file.name.removeSuffix(".dz").removeSuffix(".dsl")
        var inputLanguage = ""
        var outputLanguage = ""
        val words = arrayListOf<String>()
        val body = StringBuilder()
        var id = 0L
        var comment = false
        fun uncomment(line: String): String {
            val text = StringBuilder()
            var index = 0
            while (index < line.length) {
                when {
                    comment && line.startsWith("}}", index) -> { comment = false; index += 2 }
                    comment -> index++
                    line[index] == '\\' && index + 1 < line.length -> { text.append(line[index]).append(line[index + 1]); index += 2 }
                    line.startsWith("{{", index) -> { comment = true; index += 2 }
                    else -> text.append(line[index++])
                }
            }
            return text.toString()
        }
        fun flush() {
            if (words.isEmpty()) return
            DictionaryLimits.check(body.isNotBlank())
            sink.entry(id++, words.toList(), DictionaryMarkup.dsl(body.toString(), words.first()))
            DictionaryLimits.check(id <= DictionaryLimits.MAX_ENTRIES, DictionaryProblem.LIMIT)
            words.clear()
            body.clear()
        }
        BufferedInputStream(dictionaryInput(file, sink)).use { input ->
            input.mark(4)
            val first = input.read()
            val second = input.read()
            val third = input.read()
            input.reset()
            val encoding = when {
                first == 0xff && second == 0xfe -> Charsets.UTF_16LE
                first == 0xfe && second == 0xff -> Charsets.UTF_16BE
                first == 0xef && second == 0xbb && third == 0xbf -> Charsets.UTF_8
                second == 0 -> Charsets.UTF_16LE
                else -> Charsets.UTF_8
            }
            parseLines(input.reader(encoding).buffered(), sink) { line ->
                val cleaned = uncomment(line.removePrefix("\uFEFF"))
                if (cleaned.startsWith('#')) {
                    val value = cleaned.substringAfter(' ', "").trim().removeSurrounding("\"")
                    when (cleaned.substringBefore(' ')) {
                        "#NAME" -> title = value
                        "#INDEX_LANGUAGE" -> inputLanguage = value
                        "#CONTENTS_LANGUAGE" -> outputLanguage = value
                    }
                } else if (cleaned.isNotBlank()) {
                    if (cleaned.first().isWhitespace()) {
                        DictionaryLimits.check(words.isNotEmpty())
                        body.append(cleaned.trimStart()).append('\n')
                        DictionaryLimits.check(body.length <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
                    } else {
                        if (body.isNotEmpty()) flush()
                        words.addAll(DslHeadwords.expand(cleaned))
                    }
                }
            }
        }
        flush()
        DictionaryLimits.check(id > 0 && !comment)
        return DictionaryMetadata(title, DictionaryFormat.DSL, inputLanguage, outputLanguage)
    }

    private fun parseLines(reader: java.io.BufferedReader, sink: DictionarySink, consume: (String) -> Unit) {
        reader.use { it.forDictionaryLines(sink::checkpoint, consume) }
    }
}

internal object DslHeadwords {
    fun expand(value: String): List<String> {
        var index = 0
        fun segment(end: Char? = null, depth: Int = 0): List<String> {
            DictionaryLimits.check(depth <= DictionaryLimits.MAX_LINK_DEPTH, DictionaryProblem.LIMIT)
            var choices = listOf("")
            while (index < value.length) {
                val char = value[index++]
                if (end != null && char == end) return choices
                when (char) {
                    '\\' -> {
                        DictionaryLimits.check(index < value.length)
                        val literal = value[index++]
                        choices = choices.map { it + literal }
                    }
                    '{' -> {
                        var found = false
                        while (index < value.length) if (value[index++] == '}') { found = true; break }
                        DictionaryLimits.check(found)
                    }
                    '(' -> {
                        val optional = segment(')', depth + 1) + ""
                        choices = choices.flatMap { prefix -> optional.map { prefix + it } }.distinct()
                        DictionaryLimits.check(choices.size <= DictionaryLimits.MAX_CANDIDATES, DictionaryProblem.LIMIT)
                    }
                    ')' -> DictionaryLimits.check(false)
                    else -> choices = choices.map { it + char }
                }
            }
            DictionaryLimits.check(end == null)
            return choices
        }
        val words = segment().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        DictionaryLimits.check(words.isNotEmpty() && words.all { it.length <= DictionaryLimits.MAX_WORD_BYTES })
        return words
    }
}
