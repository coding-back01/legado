package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Test

class EnglishDictionaryFormsTest {
    @Test fun singingRestoresSingButNeverSinge() {
        assertEquals(listOf("sing"), EnglishDictionaryForms.candidates("singing"))
        assertEquals(listOf("singe"), EnglishDictionaryForms.candidates("singeing"))
    }

    @Test fun wolvesRestoresWolfInsteadOfWolve() {
        assertEquals(listOf("wolf"), EnglishDictionaryForms.candidates("wolves"))
    }

    @Test fun knivesRestoresKnifeInsteadOfKnive() {
        assertEquals(listOf("knife"), EnglishDictionaryForms.candidates("knives"))
    }

    @Test fun leavesKeepsLeafAndLeaveAsDistinctRealCandidates() {
        assertEquals(setOf("leaf", "leave"), EnglishDictionaryForms.candidates("leaves").toSet())
    }

    @Test fun onlyExplicitEnglishEnablesExtraForms() {
        assertEquals(listOf("go"), EnglishDictionaryForms.candidates("went", "en"))
        for (language in listOf(null, "", "de", "fr", "zh", "ja", "ko")) {
            assertTrue("language=$language", EnglishDictionaryForms.candidates("went", language).isEmpty())
        }
        for (word in listOf("學習", "読む", "한글")) {
            assertTrue(EnglishDictionaryForms.candidates(word, "en").isEmpty())
        }
    }
}
