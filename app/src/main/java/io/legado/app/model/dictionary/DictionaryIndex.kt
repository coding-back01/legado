package io.legado.app.model.dictionary

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import androidx.core.database.sqlite.transaction
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.text.Normalizer
import java.util.Locale

data class DictionaryEntry(val id: Long, val headword: String, val html: String)

internal class DictionaryIndex(
    private val directory: File,
    write: Boolean = false,
    private val maximumResourceFiles: Int = DictionaryLimits.MAX_FILES
) : Closeable {
    private val database = run {
        if (!write) DictionaryLimits.check(File(directory, "ready").isFile &&
            File(directory, "ready").readText().trim() == VERSION.toString() && File(directory, "entries.bin").isFile)
        if (write) SQLiteDatabase.openOrCreateDatabase(File(directory, "index.db"), null)
        else SQLiteDatabase.openDatabase(File(directory, "index.db").path, null, SQLiteDatabase.OPEN_READONLY)
    }
    private val body = RandomAccessFile(File(directory, "entries.bin"), if (write) "rw" else "r")
    private var insertEntry: SQLiteStatement? = null
    private var insertWord: SQLiteStatement? = null

    init {
        DictionaryLimits.check(maximumResourceFiles in 1..DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
        if (write) {
            database.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
            database.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY, headword TEXT NOT NULL, offset INTEGER NOT NULL, length INTEGER NOT NULL, target TEXT)")
            database.execSQL("CREATE TABLE words (word TEXT NOT NULL, normalized TEXT NOT NULL, simplified TEXT NOT NULL, entryId INTEGER NOT NULL, alias INTEGER NOT NULL, PRIMARY KEY(word, entryId))")
            database.execSQL("CREATE INDEX normalizedWords ON words(normalized)")
            database.execSQL("CREATE INDEX simplifiedWords ON words(simplified)")
            database.execSQL("CREATE TABLE resources (path TEXT PRIMARY KEY, normalized TEXT NOT NULL)")
            database.execSQL("CREATE INDEX resourceNames ON resources(normalized)")
            database.version = VERSION
            insertEntry = database.compileStatement("INSERT OR IGNORE INTO entries(id,headword,offset,length,target) VALUES(?,?,?,?,?)")
            insertWord = database.compileStatement("INSERT OR IGNORE INTO words VALUES(?,?,?,?,?)")
        } else {
            if (database.version != VERSION) {
                body.close()
                database.close()
                throw DictionaryException(DictionaryProblem.CORRUPT)
            }
        }
    }

    fun sink(checkpoint: () -> Unit): DictionarySink = object : DictionarySink {
        private var count = 0L
        private var resourceBytes = 0L
        private var resourceFiles = 0
        override fun entry(id: Long, words: List<String>, html: String, target: String?) {
            checkpoint()
            DictionaryLimits.check(words.isNotEmpty() && words.all { it.isNotBlank() })
            DictionaryLimits.check(words.size <= DictionaryLimits.MAX_CANDIDATES, DictionaryProblem.LIMIT)
            val bytes = html.toByteArray(Charsets.UTF_8)
            DictionaryLimits.check(target != null || html.isNotBlank())
            DictionaryLimits.check(bytes.size <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
            val position = body.length()
            val statement = requireNotNull(insertEntry)
            statement.clearBindings()
            statement.bindLong(1, id)
            statement.bindString(2, words.first())
            statement.bindLong(3, position)
            statement.bindLong(4, bytes.size.toLong())
            if (target == null) statement.bindNull(5) else statement.bindString(5, target)
            if (statement.executeInsert() != -1L) {
                body.seek(position)
                body.write(bytes)
                DictionaryLimits.check(body.length() <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
            }
            words.forEachIndexed { index, word -> addWord(word, id, index != 0) }
            DictionaryLimits.check(++count <= DictionaryLimits.MAX_ENTRIES, DictionaryProblem.LIMIT)
        }

        override fun alias(word: String, targetId: Long) {
            checkpoint()
            database.rawQuery("SELECT id FROM entries WHERE id=?", arrayOf(targetId.toString())).use {
                DictionaryLimits.check(it.moveToFirst())
            }
            addWord(word, targetId, true)
        }

        override fun resource(path: String, bytes: ByteArray) {
            checkpoint()
            DictionaryLimits.check(bytes.size <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
            val file = DictionaryPaths.file(File(directory, "resources"), path)
            if (!file.exists()) {
                val root = File(directory, "resources").canonicalFile
                var parent = file.parentFile
                var added = 1
                while (parent != null && parent != root && !parent.exists()) {
                    added++
                    parent = parent.parentFile
                }
                DictionaryLimits.check(added <= maximumResourceFiles - resourceFiles, DictionaryProblem.LIMIT)
                resourceFiles += added
            }
            DictionaryLimits.check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
            if (file.exists()) {
                DictionaryLimits.check(file.readBytes().contentEquals(bytes))
            } else file.writeBytes(bytes)
            resourceBytes += bytes.size
            DictionaryLimits.check(resourceBytes + body.length() <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
            database.execSQL("INSERT OR IGNORE INTO resources VALUES(?,?)", arrayOf(path, path.lowercase(Locale.ROOT)))
        }

        override fun checkpoint() = checkpoint.invoke()
    }

    private fun addWord(word: String, id: Long, alias: Boolean) {
        DictionaryLimits.check(word.length <= DictionaryLimits.MAX_WORD_BYTES, DictionaryProblem.LIMIT)
        val statement = requireNotNull(insertWord)
        statement.clearBindings()
        statement.bindString(1, word)
        statement.bindString(2, normalize(word))
        statement.bindString(3, simplified(word))
        statement.bindLong(4, id)
        statement.bindLong(5, if (alias) 1 else 0)
        statement.executeInsert()
    }

    fun build(block: () -> Unit): Long {
        database.transaction { block() }
        body.fd.sync()
        val count = database.rawQuery("SELECT count(*) FROM entries", null).use { it.moveToFirst(); it.getLong(0) }
        DictionaryLimits.check(count > 0)
        File(directory, "ready").writeText(VERSION.toString())
        return count
    }

    fun lookup(word: String, checkpoint: () -> Unit): List<DictionaryEntry> {
        fun find(column: String, value: String): List<Long> = database.rawQuery(
            "SELECT entryId FROM words WHERE $column=? AND alias=(SELECT min(alias) FROM words WHERE $column=?) ORDER BY entryId LIMIT ${DictionaryLimits.MAX_CANDIDATES + 1}",
            arrayOf(value, value)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getLong(0))
                DictionaryLimits.check(size <= DictionaryLimits.MAX_CANDIDATES, DictionaryProblem.LIMIT)
            }
        }
        var ids = find("word", word)
        if (ids.isEmpty()) ids = find("normalized", normalize(word))
        if (ids.isEmpty() && word.any { it.code > 127 }) ids = find("simplified", simplified(word))
        if (ids.isEmpty()) {
            for (candidate in EnglishDictionaryForms.candidates(word)) {
                checkpoint()
                ids = find("word", candidate)
                if (ids.isEmpty()) ids = find("normalized", normalize(candidate))
                if (ids.isNotEmpty()) break
            }
        }
        var size = 0L
        val entries = linkedMapOf<Long, DictionaryEntry>()
        for (id in ids.distinct()) {
            checkpoint()
            val entry = resolve(id, linkedSetOf(), checkpoint) ?: continue
            if (entry.id !in entries) {
                size += entry.html.length * 2L + entry.headword.length * 2L
                DictionaryLimits.check(size <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
                entries[entry.id] = entry
            }
        }
        return entries.values.toList()
    }

    fun resource(path: String): String? = database.rawQuery(
        "SELECT path FROM resources WHERE normalized=? LIMIT 2", arrayOf(path.lowercase(Locale.ROOT))
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else {
            val candidate = cursor.getString(0)
            if (cursor.moveToNext()) null else candidate
        }
    }

    fun byId(id: Long, nearest: Boolean, checkpoint: () -> Unit): DictionaryEntry? {
        val target = if (nearest) database.rawQuery("SELECT id FROM entries WHERE id<=? ORDER BY id DESC LIMIT 1", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) else null } else id
        return target?.let { resolve(it, linkedSetOf(), checkpoint) }
    }

    private fun resolve(id: Long, visited: MutableSet<Long>, checkpoint: () -> Unit): DictionaryEntry? {
        checkpoint()
        if (!visited.add(id) || visited.size > DictionaryLimits.MAX_LINK_DEPTH) {
            throw DictionaryException(DictionaryProblem.CORRUPT, "alias cycle")
        }
        database.rawQuery("SELECT headword,offset,length,target FROM entries WHERE id=?", arrayOf(id.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val target = cursor.getString(3)
            if (!target.isNullOrBlank()) {
                val next = database.rawQuery("SELECT entryId FROM words WHERE word=? ORDER BY alias,entryId LIMIT 1", arrayOf(target))
                    .use { if (it.moveToFirst()) it.getLong(0) else null }
                    ?: throw DictionaryException(DictionaryProblem.CORRUPT, "missing alias target")
                return resolve(next, visited, checkpoint)
            }
            val html = body.readDictionaryBytes(cursor.getLong(1), cursor.getLong(2)).toString(Charsets.UTF_8)
            return DictionaryEntry(id, cursor.getString(0), html)
        }
    }

    override fun close() { insertEntry?.close(); insertWord?.close(); body.close(); database.close() }

    companion object {
        const val VERSION = 2
        fun normalize(word: String): String = Normalizer.normalize(word.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)
        fun simplified(word: String): String {
            val normalized = normalize(word)
            return if (normalized.all { it.code < 128 }) normalized else {
                // 只扩展索引候选，保留乾、榦等原始词头与释义用于精确查询。
                io.legado.app.utils.ChineseUtils.t2s(normalized).replace('乾', '干').replace('榦', '干')
            }
        }
    }
}
