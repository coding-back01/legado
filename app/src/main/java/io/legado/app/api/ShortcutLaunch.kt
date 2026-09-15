package io.legado.app.api

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat

object ShortcutLaunch {
    const val BOOKSHELF = "bookshelf"
    const val LAST_READ = "lastRead"
    const val READ_ALOUD = "readAloud"

    internal const val SOURCE = "io.legado.app.dynamicShortcut"
    private const val EXTRA_SOURCE = "io.legado.app.extra.SHORTCUT_SOURCE"
    private const val EXTRA_ID = "io.legado.app.extra.SHORTCUT_ID"
    private const val EXTRA_CONSUMED = "io.legado.app.extra.SHORTCUT_CONSUMED"
    private val supportedIds = setOf(BOOKSHELF, LAST_READ, READ_ALOUD)

    fun mark(intent: Intent, shortcutId: String): Intent {
        require(shortcutId in supportedIds) { "不支持的动态快捷方式：$shortcutId" }
        return intent
            .putExtra(EXTRA_SOURCE, SOURCE)
            .putExtra(EXTRA_ID, shortcutId)
            .putExtra(EXTRA_CONSUMED, false)
    }

    fun consume(intent: Intent, expectedId: String): String? {
        val result = consume(
            State(
                source = intent.getStringExtra(EXTRA_SOURCE),
                shortcutId = intent.getStringExtra(EXTRA_ID),
                consumed = intent.getBooleanExtra(EXTRA_CONSUMED, false),
            ),
            expectedId,
        )
        if (result.shortcutId != null) {
            intent.putExtra(EXTRA_CONSUMED, true)
        }
        return result.shortcutId
    }

    fun report(context: Context, shortcutId: String) {
        require(shortcutId in supportedIds) { "不支持的动态快捷方式：$shortcutId" }
        ShortcutManagerCompat.reportShortcutUsed(context, shortcutId)
    }

    internal fun consume(state: State, expectedId: String): Result {
        val matches = state.source == SOURCE &&
                state.shortcutId == expectedId &&
                expectedId in supportedIds &&
                !state.consumed
        return if (matches) {
            Result(state.copy(consumed = true), expectedId)
        } else {
            Result(state, null)
        }
    }

    internal data class State(
        val source: String?,
        val shortcutId: String?,
        val consumed: Boolean,
    )

    internal data class Result(
        val state: State,
        val shortcutId: String?,
    )
}
