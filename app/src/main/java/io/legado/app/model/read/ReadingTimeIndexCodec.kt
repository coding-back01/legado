package io.legado.app.model.read

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

data class ReadingTimeIndexData(
    val bookIdentityHash: Long,
    val tocPrefixHash: Long,
    val sourceLastModified: Long,
    val rawLengths: IntArray,
    val visibleLengths: IntArray = IntArray(rawLengths.size) {
        ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
    },
)

object ReadingTimeIndexCodec {

    private const val MAGIC = 0x52544931
    private const val LEGACY_VERSION = 1
    private const val VERSION = 2
    private const val FIXED_BYTES_WITH_CRC = 44
    private const val MAX_CHAPTER_COUNT = 1_000_000

    fun encode(data: ReadingTimeIndexData): ByteArray {
        require(data.rawLengths.size <= MAX_CHAPTER_COUNT)
        require(data.rawLengths.size == data.visibleLengths.size)
        val byteCount = FIXED_BYTES_WITH_CRC +
                data.rawLengths.size * Int.SIZE_BYTES * 2
        val bytes = ByteArray(byteCount)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putInt(VERSION)
        buffer.putLong(data.bookIdentityHash)
        buffer.putLong(data.tocPrefixHash)
        buffer.putLong(data.sourceLastModified)
        buffer.putInt(data.rawLengths.size)
        buffer.putInt(data.rawLengths.count { it > 0 })
        data.rawLengths.forEach(buffer::putInt)
        data.visibleLengths.forEach(buffer::putInt)
        buffer.putInt(crc32(bytes, bytes.size - Int.SIZE_BYTES))
        return bytes
    }

    fun decode(bytes: ByteArray): ReadingTimeIndexData? {
        if (bytes.size < FIXED_BYTES_WITH_CRC) return null
        val storedCrc = ByteBuffer.wrap(bytes, bytes.size - Int.SIZE_BYTES, Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .int
        if (storedCrc != crc32(bytes, bytes.size - Int.SIZE_BYTES)) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (buffer.int != MAGIC) return null
        val version = buffer.int
        if (version != LEGACY_VERSION && version != VERSION) return null
        val bookIdentityHash = buffer.long
        val tocPrefixHash = buffer.long
        val sourceLastModified = buffer.long
        val chapterCount = buffer.int
        val knownCount = buffer.int
        if (chapterCount !in 0..MAX_CHAPTER_COUNT) return null
        val arraysPerChapter = if (version == LEGACY_VERSION) 1L else 2L
        val expectedSize = FIXED_BYTES_WITH_CRC.toLong() +
                chapterCount.toLong() * Int.SIZE_BYTES * arraysPerChapter
        if (expectedSize != bytes.size.toLong()) return null
        if (knownCount !in 0..chapterCount) return null
        val rawLengths = IntArray(chapterCount) { buffer.int }
        if (rawLengths.count { it > 0 } != knownCount) return null
        val visibleLengths = if (version == LEGACY_VERSION) {
            IntArray(chapterCount) { ReadingTimeIndexSnapshot.UNKNOWN_LENGTH }
        } else {
            IntArray(chapterCount) { buffer.int }
        }
        return ReadingTimeIndexData(
            bookIdentityHash = bookIdentityHash,
            tocPrefixHash = tocPrefixHash,
            sourceLastModified = sourceLastModified,
            rawLengths = rawLengths,
            visibleLengths = visibleLengths,
        )
    }

    fun read(file: File): ReadingTimeIndexData? {
        return kotlin.runCatching {
            if (!file.isFile) return null
            decode(file.readBytes())
        }.getOrNull()
    }

    fun write(file: File, data: ReadingTimeIndexData): Boolean {
        return kotlin.runCatching {
            val parent = file.parentFile ?: return false
            if (!parent.exists() && !parent.mkdirs()) return false
            val tempFile = File(parent, "${file.name}.tmp")
            val backupFile = File(parent, "${file.name}.bak")
            if (tempFile.exists() && !tempFile.delete()) return false
            tempFile.writeBytes(encode(data))
            if (read(tempFile) == null) {
                tempFile.delete()
                return false
            }
            if (backupFile.exists() && !backupFile.delete()) return false
            if (file.exists() && !file.renameTo(backupFile)) return false
            if (!tempFile.renameTo(file)) {
                if (backupFile.exists()) backupFile.renameTo(file)
                tempFile.delete()
                return false
            }
            backupFile.delete()
            true
        }.getOrDefault(false)
    }

    private fun crc32(bytes: ByteArray, length: Int): Int {
        val crc = CRC32()
        crc.update(bytes, 0, length)
        return crc.value.toInt()
    }
}

object ReadingTimeIdentity {

    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L

    fun hash(parts: Iterable<String>): Long {
        var result = FNV_OFFSET_BASIS
        parts.forEach { part ->
            part.forEach { char ->
                result = (result xor char.code.toLong()) * FNV_PRIME
            }
            result = (result xor 0xffL) * FNV_PRIME
        }
        return result
    }

    fun extend(seed: Long, part: String): Long {
        var result = seed
        part.forEach { char ->
            result = (result xor char.code.toLong()) * FNV_PRIME
        }
        return (result xor 0xffL) * FNV_PRIME
    }

    fun initialHash(): Long = FNV_OFFSET_BASIS
}
