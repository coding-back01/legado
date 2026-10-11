package io.legado.app.model.dictionary

import java.util.Locale

/** 只生成检索候选，不改写原词；词典存在候选词条时才显示命中。 */
object EnglishDictionaryForms {
    private val irregular = mapOf(
        "went" to "go", "gone" to "go", "goes" to "go", "was" to "be", "were" to "be",
        "am" to "be", "is" to "be", "are" to "be", "been" to "be", "being" to "be",
        "had" to "have", "has" to "have", "did" to "do", "done" to "do", "does" to "do",
        "said" to "say", "made" to "make", "took" to "take", "taken" to "take",
        "came" to "come", "saw" to "see", "seen" to "see", "got" to "get", "gotten" to "get",
        "gave" to "give", "given" to "give", "knew" to "know", "known" to "know",
        "thought" to "think", "bought" to "buy", "brought" to "bring", "caught" to "catch",
        "found" to "find", "felt" to "feel", "left" to "leave", "kept" to "keep",
        "held" to "hold", "heard" to "hear", "told" to "tell", "sold" to "sell",
        "ran" to "run", "written" to "write", "wrote" to "write", "read" to "read",
        "spoke" to "speak", "spoken" to "speak", "broke" to "break", "broken" to "break",
        "chose" to "choose", "chosen" to "choose", "drove" to "drive", "driven" to "drive",
        "ate" to "eat", "eaten" to "eat", "fell" to "fall", "fallen" to "fall",
        "flew" to "fly", "flown" to "fly", "grew" to "grow", "grown" to "grow",
        "sang" to "sing", "sung" to "sing", "slept" to "sleep", "stood" to "stand",
        "swam" to "swim", "swum" to "swim", "won" to "win", "lost" to "lose",
        "children" to "child", "men" to "man", "women" to "woman", "people" to "person",
        "died" to "die", "dying" to "die", "lying" to "lie", "tied" to "tie",
        "mice" to "mouse", "geese" to "goose", "teeth" to "tooth", "feet" to "foot",
        "better" to "good", "best" to "good", "worse" to "bad", "worst" to "bad"
    )
    private val unchanged = setOf("news", "series", "species", "business", "physics", "mathematics", "analysis", "basis", "crisis")
    private val adjectives = setOf("old", "young", "small", "large", "long", "short", "high", "low", "fast", "slow",
        "bright", "dark", "warm", "cold", "hard", "soft", "cheap", "rich", "poor", "kind", "strong", "weak",
        "clean", "clear", "near", "new", "tall", "deep", "wide", "nice", "safe", "fine", "late", "simple",
        "gentle", "easy", "happy", "busy", "heavy", "hot", "big", "thin", "fat", "sad", "wet", "red")

    fun candidates(text: String): List<String> {
        val word = text.lowercase(Locale.ROOT)
        if (word.contains(' ')) {
            val first = word.substringBefore(' ')
            val rest = word.substringAfter(' ')
            if (!Regex("[a-z]+(?: [a-z]+)*").matches(rest)) return emptyList()
            return candidates(first).map { "$it $rest" }
        }
        if (!Regex("[a-z]+(?:'[a-z]+)?").matches(word) || word in unchanged) return emptyList()
        irregular[word]?.let { return listOf(it) }
        val result = linkedSetOf<String>()
        fun base(suffix: String): String = word.removeSuffix(suffix)
        if (word.endsWith("'s") && word.length > 3) result.add(base("'s"))
        if (word.endsWith("ies") && word.length > 4) result.add(base("ies") + "y")
        if (word.endsWith("ied") && word.length > 4) result.add(base("ied") + "y")
        for (suffix in listOf("ing", "ed")) {
            if (word.endsWith(suffix) && word.length >= suffix.length + 3) {
                val stem = base(suffix)
                if (stem.length >= 2 && stem.last() == stem[stem.lastIndex - 1] && stem.last() in "bdgmnprt") {
                    result.add(stem.dropLast(1))
                }
                result.add(stem + "e")
                result.add(stem)
                result.removeAll { candidate -> suffixForm(candidate, suffix) != word }
            }
        }
        for (suffix in listOf("est", "er")) {
            if (word.endsWith(suffix) && word.length >= suffix.length + 3) {
                val stem = base(suffix)
                val options = linkedSetOf(stem, stem + "e")
                if (stem.endsWith('i')) options.add(stem.dropLast(1) + "y")
                if (stem.length > 1 && stem.last() == stem[stem.lastIndex - 1]) options.add(stem.dropLast(1))
                result.addAll(options.filter { it in adjectives })
            }
        }
        if (word.endsWith("es") && word.length > 4 && listOf("ses", "xes", "zes", "ches", "shes", "oes").any { word.endsWith(it) }) {
            result.add(base("es"))
        }
        if (word.endsWith('s') && word.length > 3 && !word.endsWith("ss") && !word.endsWith("is")) {
            result.add(base("s"))
        }
        result.remove(word)
        return result.toList()
    }

    private fun suffixForm(base: String, suffix: String): String {
        if (suffix == "ed" && base.endsWith('e')) return base + "d"
        if (suffix == "ed" && base.endsWith('y') && base.length > 1 && base[base.lastIndex - 1] !in "aeiou") return base.dropLast(1) + "ied"
        if (suffix == "ing" && base.endsWith('e') && !base.endsWith("ee")) return base.dropLast(1) + suffix
        val stress = setOf("admit", "commit", "permit", "prefer", "refer", "infer", "occur", "compel", "control", "regret")
        val vowels = "aeiou"
        val cvc = base.length >= 3 && base.last() !in vowels + "wxy" && base[base.lastIndex - 1] in vowels && base[base.lastIndex - 2] !in vowels
        val groups = Regex("[aeiou]+").findAll(base).count()
        return if (cvc && (groups == 1 || base in stress)) base + base.last() + suffix else base + suffix
    }
}
