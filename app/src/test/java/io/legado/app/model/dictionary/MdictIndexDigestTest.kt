package io.legado.app.model.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MdictIndexDigestTest {
    @Test fun publishedRipemd128VectorsMatchIncludingMultipleBlocks() {
        val vectors = mapOf(
            "" to "cdf26213a150dc3ecb610f18f6b38b46",
            "a" to "86be7afa339d0fc7cfc785e72f578d33",
            "abc" to "c14a12199c66e4ba84636b0f69144c77",
            "message digest" to "9e327b3d6e523062afc1132d7df9d1b8",
            "abcdefghijklmnopqrstuvwxyz" to "fd2aa607f71dc8f510714922b371834e",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" to "d1e959eb179c911faea4624c60c5c702",
            "1234567890".repeat(8) to "3f45ef194732c2dbb2c4a2c769795fa3"
        )
        vectors.forEach { (input, expected) ->
            val actual = MdictIndexDigest.digest(input.toByteArray()).joinToString("") { "%02x".format(Locale.ROOT, it) }
            assertEquals(input, expected, actual)
        }
    }
}
