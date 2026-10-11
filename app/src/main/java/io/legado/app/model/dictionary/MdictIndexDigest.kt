package io.legado.app.model.dictionary

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** RIPEMD-128 仅用于 MDict 索引混淆，不注册或替换应用的加密提供者。 */
internal object MdictIndexDigest {
    private val leftOrder = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
        1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2
    )
    private val rightOrder = intArrayOf(
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
        6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
        8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14
    )
    private val leftShift = intArrayOf(
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
        7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
        11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12
    )
    private val rightShift = intArrayOf(
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
        9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
        15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8
    )
    private val leftConstant = intArrayOf(0, 0x5a827999, 0x6ed9eba1, 0x8f1bbcdc.toInt())
    private val rightConstant = intArrayOf(0x50a28be6, 0x5c4dd124, 0x6d703ef3, 0)

    private fun combine(round: Int, x: Int, y: Int, z: Int): Int = when (round) {
        0 -> x xor y xor z
        1 -> (x and y) or (x.inv() and z)
        2 -> (x or y.inv()) xor z
        else -> (x and z) or (y and z.inv())
    }

    fun digest(input: ByteArray): ByteArray {
        val size = ((input.size.toLong() + 8) / 64 + 1) * 64
        DictionaryLimits.check(size <= DictionaryLimits.MAX_BLOCK_BYTES, DictionaryProblem.LIMIT)
        val padded = ByteArray(size.toInt())
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN).putLong(padded.size - 8, input.size.toLong() * 8)
        val state = intArrayOf(0x67452301, 0xefcdab89.toInt(), 0x98badcfe.toInt(), 0x10325476)
        val words = IntArray(16)
        for (offset in padded.indices step 64) {
            val buffer = ByteBuffer.wrap(padded, offset, 64).order(ByteOrder.LITTLE_ENDIAN)
            for (i in words.indices) words[i] = buffer.int
            var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
            var aa = a; var bb = b; var cc = c; var dd = d
            for (i in 0 until 64) {
                val round = i / 16
                val left = Integer.rotateLeft(a + combine(round, b, c, d) + words[leftOrder[i]] + leftConstant[round], leftShift[i])
                a = d; d = c; c = b; b = left
                val right = Integer.rotateLeft(aa + combine(3 - round, bb, cc, dd) + words[rightOrder[i]] + rightConstant[round], rightShift[i])
                aa = dd; dd = cc; cc = bb; bb = right
            }
            val next = state[1] + c + dd
            state[1] = state[2] + d + aa
            state[2] = state[3] + a + bb
            state[3] = state[0] + b + cc
            state[0] = next
        }
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply { state.forEach { putInt(it) } }.array()
    }
}
