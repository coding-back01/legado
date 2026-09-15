package io.legado.app.receiver

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.api.ShortcutLaunch
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.startActivity
import io.legado.app.utils.trimAsciiControlAndSpace
import splitties.init.appCtx

class SharedReceiverActivity : AppCompatActivity() {

    private val receivingType = "text/plain"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initIntent()
        finish()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun initIntent() {
        when {
            intent.action == Intent.ACTION_SEND && intent.type == receivingType -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    dispose(it)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && intent.action == Intent.ACTION_PROCESS_TEXT
                    && intent.type == receivingType -> {
                intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)?.let {
                    dispose(it)
                }
            }
            intent.getStringExtra("action") == "readAloud" -> {
                val shortcutId = ShortcutLaunch.consume(intent, ShortcutLaunch.READ_ALOUD)
                MediaButtonReceiver.readAloud(appCtx, false)
                if (shortcutId != null) {
                    ShortcutLaunch.report(this, shortcutId)
                }
            }
        }
    }

    private fun dispose(text: String) {
        if (text.isBlank()) {
            return
        }
        val urls = text.split("\\s".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val result = StringBuilder()
        for (url in urls) {
            if (url.matches("http.+".toRegex()))
                result.append("\n").append(url.trimAsciiControlAndSpace())
        }
        if (result.length > 1) {
            startActivity<MainActivity>()
        } else {
            SearchActivity.start(this, text)
        }
    }
}
