package io.legado.app.api

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.legado.app.R
import io.legado.app.receiver.SharedReceiverActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.main.MainActivity

object ShortCuts {

    private inline fun <reified T> buildIntent(context: Context): Intent {
        val intent = Intent(context, T::class.java)
        intent.action = Intent.ACTION_VIEW
        return intent
    }

    private fun buildBookShelfShortCutInfo(context: Context): ShortcutInfoCompat {
        val bookShelfIntent = ShortcutLaunch.mark(
            buildIntent<MainActivity>(context),
            ShortcutLaunch.BOOKSHELF,
        )
        return ShortcutInfoCompat.Builder(context, ShortcutLaunch.BOOKSHELF)
            .setShortLabel(context.getString(R.string.bookshelf))
            .setLongLabel(context.getString(R.string.bookshelf))
            .setIcon(IconCompat.createWithResource(context, R.drawable.icon_read_book))
            .setIntent(bookShelfIntent)
            .build()
    }

    private fun buildReadBookShortCutInfo(context: Context): ShortcutInfoCompat {
        val bookShelfIntent = buildIntent<MainActivity>(context)
        val readBookIntent = ShortcutLaunch.mark(
            buildIntent<ReadBookActivity>(context),
            ShortcutLaunch.LAST_READ,
        )
        return ShortcutInfoCompat.Builder(context, ShortcutLaunch.LAST_READ)
            .setShortLabel(context.getString(R.string.last_read))
            .setLongLabel(context.getString(R.string.last_read))
            .setIcon(IconCompat.createWithResource(context, R.drawable.icon_read_book))
            .setIntents(arrayOf(bookShelfIntent, readBookIntent))
            .build()
    }

    private fun buildReadAloudShortCutInfo(context: Context): ShortcutInfoCompat {
        val readAloudIntent = ShortcutLaunch.mark(
            buildIntent<SharedReceiverActivity>(context),
            ShortcutLaunch.READ_ALOUD,
        )
        readAloudIntent.putExtra("action", "readAloud")
        return ShortcutInfoCompat.Builder(context, ShortcutLaunch.READ_ALOUD)
            .setShortLabel(context.getString(R.string.read_aloud))
            .setLongLabel(context.getString(R.string.read_aloud))
            .setIcon(IconCompat.createWithResource(context, R.drawable.icon_read_book))
            .setIntent(readAloudIntent)
            .build()
    }

    fun buildShortCuts(context: Context) {
        ShortcutManagerCompat.setDynamicShortcuts(
            context, listOf(
                buildReadBookShortCutInfo(context),
                buildReadAloudShortCutInfo(context),
                buildBookShelfShortCutInfo(context)
            )
        )
    }

}
