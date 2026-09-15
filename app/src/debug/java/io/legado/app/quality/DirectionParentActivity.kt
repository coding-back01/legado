package io.legado.app.quality

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import io.legado.app.BuildConfig

class DirectionParentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        check(BuildConfig.DEBUG) { "方向夹具只允许运行在 Debug 变体" }
        super.onCreate(savedInstanceState)
        requestedOrientation = intent.getIntExtra(
            EXTRA_REQUESTED_ORIENTATION,
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        )
    }

    companion object {
        const val EXTRA_REQUESTED_ORIENTATION =
            "io.legado.app.quality.requestedOrientation"
    }
}
