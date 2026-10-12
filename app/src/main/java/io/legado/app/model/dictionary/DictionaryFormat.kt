package io.legado.app.model.dictionary

import java.io.File
import java.io.IOException
import java.util.Locale

enum class DictionaryFormat {
    MDICT, STARDICT, MOBI, DSL, DICTD, XDXF, BGL;

    companion object {
        fun detect(file: File): DictionaryFormat? {
            val name = file.name.lowercase(Locale.ROOT)
            return when {
                name.endsWith(".mdx") -> MDICT
                name.endsWith(".ifo") -> STARDICT
                name.endsWith(".dsl") || name.endsWith(".dsl.dz") -> DSL
                name.endsWith(".index") -> DICTD
                name.endsWith(".xdxf") || name.endsWith(".xdxf.dz") -> XDXF
                name.endsWith(".bgl") -> BGL
                name.endsWith(".mobi") || name.endsWith(".prc") || name.endsWith(".azw") -> MOBI
                else -> null
            }
        }
    }
}

enum class DictionaryProblem {
    CORRUPT, MISSING_FILE, UNSUPPORTED, PROTECTED, LIMIT, INVALID_PATH, ASSOCIATION_REQUIRED
}

class DictionaryException(
    val problem: DictionaryProblem,
    val detail: String = "",
    cause: Throwable? = null
) : IOException(problem.name, cause)

data class DictionaryMetadata(
    val title: String,
    val format: DictionaryFormat,
    val inputLanguage: String = "",
    val outputLanguage: String = ""
)

interface DictionarySink {
    fun entry(id: Long, words: List<String>, html: String, target: String? = null)
    fun alias(word: String, targetId: Long)
    fun resource(path: String, bytes: ByteArray)
    fun checkpoint()
}

interface DictionaryParser {
    fun parse(file: File, workDir: File, sink: DictionarySink): DictionaryMetadata
}

object DictionaryLimits {
    const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
    const val MAX_BLOCK_BYTES = 64 * 1024 * 1024
    const val MAX_WORD_BYTES = 16 * 1024
    const val MAX_FILES = 100_000
    const val MAX_ENTRIES = 5_000_000L
    const val MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024
    const val MAX_CANDIDATES = 64
    const val MAX_LINK_DEPTH = 16

    fun check(condition: Boolean, problem: DictionaryProblem = DictionaryProblem.CORRUPT) {
        if (!condition) throw DictionaryException(problem)
    }
}
