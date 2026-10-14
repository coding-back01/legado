package io.legado.app.model.dictionary

import android.os.ParcelFileDescriptor
import android.system.OsConstants
import io.legado.app.utils.compress.LibArchiveUtils
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import java.io.File
import java.nio.ByteBuffer

internal object DictionaryArchive {
    fun extract(file: File, target: File, checkpoint: () -> Unit) {
        if (Regex("(?i)\\.part[0-9]+\\.rar$").containsMatchIn(file.name)) {
            throw DictionaryException(DictionaryProblem.UNSUPPORTED, "multipart archive")
        }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            // 已复制到应用内的常规文件可直接交给原生 FD；API 21 的 Java ByteBuffer 回调行为不同。
            val archive = LibArchiveUtils.openArchive(descriptor, useCb = false)
            var count = 0
            var total = 0L
            val buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            try {
                while (true) {
                    checkpoint()
                    val entry = Archive.readNextHeader(archive)
                    if (entry == 0L) break
                    DictionaryLimits.check(!ArchiveEntry.isEncrypted(entry), DictionaryProblem.PROTECTED)
                    DictionaryLimits.check(ArchiveEntry.size(entry) <= DictionaryLimits.MAX_TOTAL_BYTES - total, DictionaryProblem.LIMIT)
                    DictionaryLimits.check(++count <= DictionaryLimits.MAX_FILES, DictionaryProblem.LIMIT)
                    val path = ArchiveEntry.pathnameUtf8(entry)
                        ?: ArchiveEntry.pathname(entry)?.toString(Charsets.UTF_8)
                        ?: throw DictionaryException(DictionaryProblem.INVALID_PATH)
                    val output = DictionaryPaths.file(target, path.trimEnd('/'))
                    val stat = ArchiveEntry.stat(entry)
                    if (OsConstants.S_ISDIR(stat.stMode)) {
                        DictionaryLimits.check(output.mkdirs() || output.isDirectory)
                        continue
                    }
                    DictionaryLimits.check(OsConstants.S_ISREG(stat.stMode), DictionaryProblem.INVALID_PATH)
                    DictionaryLimits.check(!output.exists())
                    DictionaryLimits.check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
                    output.outputStream().buffered().use { stream ->
                        while (true) {
                            checkpoint()
                            buffer.clear()
                            Archive.readData(archive, buffer)
                            buffer.flip()
                            val size = buffer.remaining()
                            if (size == 0) break
                            total += size
                            DictionaryLimits.check(total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
                            buffer.get(bytes, 0, size)
                            stream.write(bytes, 0, size)
                        }
                    }
                }
            } finally {
                Archive.free(archive)
            }
        }
    }
}
