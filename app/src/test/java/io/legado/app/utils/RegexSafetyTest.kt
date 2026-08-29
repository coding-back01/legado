package io.legado.app.utils

import io.legado.app.data.entities.TxtTocRule
import io.legado.app.exception.RegexTimeoutException
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class RegexSafetyTest {

    @Test
    fun `正常正则保持原有语义`() {
        val pattern = RegexSafety.compile("^第\\d+章.*$")
        val matcher = pattern.matcher(RegexSafety.limitInput("第12章 标题"))

        assertTrue(matcher.matches())
    }

    @Test
    fun `过长正则在编译前被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            RegexSafety.compile("a".repeat(RegexSafety.MAX_PATTERN_LENGTH + 1))
        }
    }

    @Test
    fun `灾难性回溯超过步数预算时中止`() {
        val pattern = RegexSafety.compile("(a+)+$")
        val input = RegexSafety.limitInput("a".repeat(5_000) + "!", maxSteps = 10_000)

        assertThrows(RegexTimeoutException::class.java) {
            pattern.matcher(input).matches()
        }
    }

    @Test
    fun `默认 TXT 目录规则可扫描完整首块内容`() {
        val rules = GSON.fromJsonArray<TxtTocRule>(
            repoFile("app/src/main/assets/defaultData/txtTocRule.json").readText()
        ).getOrThrow()
        val content = buildString(512_000) {
            while (length < 510_000) {
                append("这是用于验证目录正则预算的普通正文内容。\n")
            }
            rules.mapNotNull { it.example }.forEach {
                append(it)
                append('\n')
            }
        }

        rules.forEach { rule ->
            try {
                val matcher = RegexSafety.compile(rule.rule, Pattern.MULTILINE)
                    .matcher(RegexSafety.limitInput(content))
                while (matcher.find()) {
                    matcher.group()
                }
            } catch (e: RegexTimeoutException) {
                throw AssertionError("默认目录规则超过安全预算：${rule.name}", e)
            }
        }
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
