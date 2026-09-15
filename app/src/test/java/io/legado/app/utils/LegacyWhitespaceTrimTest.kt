package io.legado.app.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWhitespaceTrimTest {

    @Test
    fun `旧式裁剪只移除不大于普通空格的边界字符`() {
        val cases = linkedMapOf(
            "U+0000" to "\u0000正文\u0000" to "正文",
            "U+001F" to "\u001F正文\u001F" to "正文",
            "U+0020" to " 正文 " to "正文",
            "U+00A0" to "\u00A0正文\u00A0" to "\u00A0正文\u00A0",
            "U+3000" to "\u3000正文\u3000" to "\u3000正文\u3000",
        )

        val mismatches = cases.mapNotNull { (descriptionAndInput, expected) ->
            val (description, input) = descriptionAndInput
            val actual = input.trimAsciiControlAndSpace()
            if (actual == expected) null else description
        }

        assertTrue("旧式裁剪改变边界语义：$mismatches", mismatches.isEmpty())
    }
}
