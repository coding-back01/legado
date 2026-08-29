package io.legado.app.utils

import io.legado.app.exception.RegexTimeoutException
import java.util.regex.Pattern

object RegexSafety {

    const val MAX_PATTERN_LENGTH = 4_096

    // 128 次字符访问可覆盖仓库内含可变长度后向断言的默认目录规则；
    // 绝对上限保证超大输入上的单次 Matcher 仍然会被中止。
    private const val MIN_MATCH_STEPS = 100_000
    private const val MAX_MATCH_STEPS = 100_000_000
    private const val MATCH_STEPS_PER_CHARACTER = 128

    fun compile(regex: String, flags: Int = 0): Pattern {
        require(regex.length <= MAX_PATTERN_LENGTH) {
            "正则长度不能超过 $MAX_PATTERN_LENGTH 个字符"
        }
        return Pattern.compile(regex, flags)
    }

    fun limitInput(
        input: CharSequence,
        maxSteps: Int = defaultMatchSteps(input.length)
    ): CharSequence {
        require(maxSteps > 0) { "正则匹配步数必须大于 0" }
        return StepLimitedCharSequence(input, StepCounter(maxSteps))
    }

    private fun defaultMatchSteps(inputLength: Int): Int {
        return (inputLength.toLong() * MATCH_STEPS_PER_CHARACTER)
            .coerceIn(MIN_MATCH_STEPS.toLong(), MAX_MATCH_STEPS.toLong())
            .toInt()
    }

    private class StepCounter(val maxSteps: Int) {
        var steps: Int = 0
    }

    private class StepLimitedCharSequence(
        private val source: CharSequence,
        private val counter: StepCounter
    ) : CharSequence {

        override val length: Int
            get() = source.length

        override fun get(index: Int): Char {
            counter.steps++
            if (counter.steps > counter.maxSteps) {
                throw RegexTimeoutException("正则匹配超过安全步数预算")
            }
            return source[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return StepLimitedCharSequence(source.subSequence(startIndex, endIndex), counter)
        }

        override fun toString(): String = source.toString()
    }
}
