package io.legado.app.api

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutLaunchInstrumentedTest {

    @Test
    fun markedIntentIsConsumedOnlyOnce() {
        val intent = ShortcutLaunch.mark(Intent(), ShortcutLaunch.BOOKSHELF)

        assertEquals(
            ShortcutLaunch.BOOKSHELF,
            ShortcutLaunch.consume(intent, ShortcutLaunch.BOOKSHELF),
        )
        assertNull(ShortcutLaunch.consume(intent, ShortcutLaunch.BOOKSHELF))
    }

    @Test
    fun ordinaryIntentIsNotConsumed() {
        assertNull(ShortcutLaunch.consume(Intent(), ShortcutLaunch.LAST_READ))
    }

    @Test
    fun dynamicShortcutsCarryMatchingLaunchMarkers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        try {
            ShortCuts.buildShortCuts(context)
            val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)

            assertEquals(
                setOf(
                    ShortcutLaunch.BOOKSHELF,
                    ShortcutLaunch.LAST_READ,
                    ShortcutLaunch.READ_ALOUD,
                ),
                shortcuts.map { it.id }.toSet(),
            )
            shortcuts.forEach { shortcut ->
                assertEquals(
                    shortcut.id,
                    ShortcutLaunch.consume(shortcut.intents.last(), shortcut.id),
                )
            }
        } finally {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        }
    }
}
