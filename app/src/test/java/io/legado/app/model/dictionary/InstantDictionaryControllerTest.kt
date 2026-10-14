package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Test

class InstantDictionaryControllerTest {
    @Test fun disabledModeHasNoRequestsAndEnabledStableInitialWordQueriesOnce() {
        val controller = InstantDictionaryController()
        controller.configure("book-a", false)
        assertNull(controller.stable(1, "word"))
        controller.configure("book-a", true)
        assertNotNull(controller.stable(2, "word"))
        assertNull(controller.stable(2, "word"))
    }

    @Test fun closeDragAndMultipleWordsDoNotReopenOldSelection() {
        val controller = InstantDictionaryController()
        controller.configure("book", true)
        val token = requireNotNull(controller.stable(1, "word"))
        controller.invalidate()
        assertFalse(controller.isCurrent(token))
        assertNull(controller.stable(1, "word"))
        assertNull(controller.stable(2, "two words"))
        assertNull(controller.stable(3, "word\nnext"))
        assertNull(controller.stable(4, "。"))
        assertNotNull(controller.stable(5, "学习"))
    }

    @Test fun bookOrModeChangesInvalidateExistingRequests() {
        val controller = InstantDictionaryController()
        controller.configure("first", true)
        val first = requireNotNull(controller.stable(1, "word"))
        controller.configure("second", false)
        assertFalse(controller.isCurrent(first))
        assertNull(controller.stable(2, "word"))
        controller.configure("second", true)
        assertNotNull(controller.stable(3, "word"))
    }
}
