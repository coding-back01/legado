package io.legado.app.model.dictionary

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.zip.CRC32
import java.util.zip.Inflater

/** 部分 BGL 的 gzip 尾部 CRC 为零；保留长度校验，仅对这一已知变体放宽 CRC。 */
internal object BglGzip {
    fun extract(input: DataInputStream, output: File, sink: DictionarySink) {
        DictionaryLimits.check(input.readUnsignedByte() == 0x1f && input.readUnsignedByte() == 0x8b)
        DictionaryLimits.check(input.readUnsignedByte() == 8)
        val flags = input.readUnsignedByte()
        DictionaryLimits.check(flags and 0xe0 == 0)
        val fixed = ByteArray(6).also { input.readFully(it) }
        if (flags and 4 != 0) {
            val size = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
            DictionaryLimits.check(input.skipBytes(size) == size)
        }
        for (flag in listOf(8, 16)) if (flags and flag != 0) {
            var length = 0
            while (input.readUnsignedByte() != 0) DictionaryLimits.check(++length <= DictionaryLimits.MAX_WORD_BYTES, DictionaryProblem.LIMIT)
        }
        if (flags and 2 != 0) input.readUnsignedShort()
        val inflater = Inflater(true)
        val compressed = ByteArray(DEFAULT_BUFFER_SIZE)
        val expanded = ByteArray(DEFAULT_BUFFER_SIZE)
        var inputSize = 0
        var total = 0L
        val checksum = CRC32()
        try {
            output.outputStream().buffered().use { stream ->
                while (!inflater.finished()) {
                    sink.checkpoint()
                    if (inflater.needsInput()) {
                        inputSize = input.read(compressed)
                        DictionaryLimits.check(inputSize > 0)
                        inflater.setInput(compressed, 0, inputSize)
                    }
                    val size = inflater.inflate(expanded)
                    total += size
                    DictionaryLimits.check(total <= DictionaryLimits.MAX_TOTAL_BYTES, DictionaryProblem.LIMIT)
                    if (size > 0) {
                        checksum.update(expanded, 0, size)
                        stream.write(expanded, 0, size)
                    } else DictionaryLimits.check(inflater.finished() || inflater.needsInput())
                }
            }
            val remaining = ByteArrayInputStream(compressed, inputSize - inflater.remaining, inflater.remaining)
            val trailer = DataInputStream(SequenceInputStream(remaining, input))
            val crc = Integer.reverseBytes(trailer.readInt()).toLong() and 0xffffffffL
            val size = Integer.reverseBytes(trailer.readInt()).toLong() and 0xffffffffL
            DictionaryLimits.check((crc == 0L || crc == checksum.value) && size == total and 0xffffffffL)
            DictionaryLimits.check(trailer.read() == -1)
        } finally {
            inflater.end()
        }
    }
}
