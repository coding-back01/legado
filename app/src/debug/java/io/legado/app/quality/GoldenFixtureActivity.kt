package io.legado.app.quality

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.content.ContextCompat
import io.legado.app.BuildConfig
import io.legado.app.R
import java.util.Locale

class GoldenFixtureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        check(BuildConfig.DEBUG) { "golden 夹具只允许运行在 Debug 变体" }
        applyStableConfiguration()
        setTheme(
            if (intent.getStringExtra(EXTRA_PROFILE).orEmpty().contains("dark")) {
                R.style.AppTheme_Dark
            } else {
                R.style.AppTheme_Light
            }
        )
        super.onCreate(savedInstanceState)
        window.setWindowAnimations(0)
        applyStableSystemUi()
        val fixture = intent.getStringExtra(EXTRA_FIXTURE)
            ?: error("缺少 $EXTRA_FIXTURE")
        showFixture(fixture)
        applyStableSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStableSystemUi()
    }

    fun showFixture(fixture: String) {
        setContentView(createFixture(fixture))
    }

    private fun applyStableConfiguration() {
        val profile = intent.getStringExtra(EXTRA_PROFILE).orEmpty()
        val configuration = Configuration(resources.configuration).apply {
            fontScale = 1.0f
            setLocale(Locale.SIMPLIFIED_CHINESE)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (profile.contains("dark")) {
                    Configuration.UI_MODE_NIGHT_YES
                } else {
                    Configuration.UI_MODE_NIGHT_NO
                }
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    @Suppress("DEPRECATION")
    private fun applyStableSystemUi() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun createFixture(fixture: String): View = when (fixture) {
        "bookshelf" -> inflate(R.layout.item_bookshelf_list)
        "rss" -> inflate(R.layout.item_rss)
        "welcome" -> inflate(R.layout.activity_welcome)
        "transparent" -> inflate(R.layout.activity_translucence)
        "dialog" -> centeredFixture(inflate(R.layout.dialog_text_view))
        "manga_menu" -> inflate(R.layout.view_manga_menu)
        "file_path" -> centeredFixture(inflate(R.layout.item_path_picker))
        "overdraw_solid_activity" -> inflate(R.layout.activity_audio_play)
        "overdraw_solid_items" -> overdrawSolidItems()
        "launcher3" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher3Catalog()
        } else {
            error("launcher3 monochrome golden 需要 API 33+")
        }
        "icon_catalog" -> iconCatalog()
        "direction" -> directionFixture()
        else -> error("未知 golden fixture: $fixture")
    }

    private fun inflate(layout: Int): View = LayoutInflater.from(this).inflate(layout, null, false)

    private fun centeredFixture(content: View): View = FrameLayout(this).apply {
        setBackgroundResource(R.color.background)
        addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
    }

    private fun overdrawSolidItems(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.color.background)
        listOf(
            R.layout.item_book_group_manage,
            R.layout.item_group_manage,
            R.layout.item_group_select,
            R.layout.item_server_select,
        ).forEach { layout ->
            addView(
                inflate(layout),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun iconCatalog(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(32, 32, 32, 32)
        setBackgroundResource(R.color.background)
        addView(label("通知、快捷方式与 fallback 图标"))
        val imageResources = intArrayOf(
            R.drawable.icon_read_book,
            R.drawable.image_cover_default,
            R.drawable.image_legado,
            R.drawable.image_loading_error,
            R.drawable.image_rss,
            R.drawable.image_rss_article,
        )
        imageResources.forEach { resource ->
            addView(ImageView(context).apply {
                setImageBitmap(createBitmapPreview(resource))
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, LinearLayout.LayoutParams(240, 240))
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun launcher3Catalog(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundResource(R.color.background)
        addView(label("launcher3 vector / adaptive / monochrome"))
        addLauncherPreview(ContextCompat.getDrawable(this@GoldenFixtureActivity, R.drawable.ic_launcher3))
        addLauncherPreview(ContextCompat.getDrawable(this@GoldenFixtureActivity, R.mipmap.launcher3))
        val adaptive = ContextCompat.getDrawable(
            this@GoldenFixtureActivity,
            R.mipmap.launcher3,
        ) as? AdaptiveIconDrawable
        addLauncherPreview(adaptive?.monochrome)
    }

    private fun LinearLayout.addLauncherPreview(drawable: android.graphics.drawable.Drawable?) {
        addView(ImageView(context).apply {
            setImageDrawable(requireNotNull(drawable) { "launcher3 golden drawable 缺失" })
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(360, 360))
    }

    private fun createBitmapPreview(resource: Int): Bitmap {
        val source = requireNotNull(
            BitmapFactory.decodeResource(
                resources,
                resource,
                BitmapFactory.Options().apply {
                    inSampleSize = densitySampleSize(resource)
                },
            )
        ) {
            "无法解码 golden 位图资源: $resource"
        }
        val previewSize = 240
        val scale = minOf(
            1f,
            previewSize.toFloat() / source.width,
            previewSize.toFloat() / source.height,
        )
        val width = source.width * scale
        val height = source.height * scale
        val left = (previewSize - width) / 2f
        val top = (previewSize - height) / 2f
        val preview = createBitmap(previewSize, previewSize)
        Canvas(preview).drawBitmap(
            source,
            null,
            RectF(left, top, left + width, top + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
        )
        source.recycle()
        return preview
    }

    private fun densitySampleSize(resource: Int): Int {
        val value = TypedValue()
        resources.getValue(resource, value, true)
        val targetDensity = resources.displayMetrics.densityDpi
        val sourceDensity = when (value.density) {
            TypedValue.DENSITY_NONE -> targetDensity
            TypedValue.DENSITY_DEFAULT -> DisplayMetrics.DENSITY_DEFAULT
            else -> value.density
        }
        val densityRatio = maxOf(1, targetDensity / sourceDensity)
        var sampleSize = 1
        while (sampleSize * 2 <= densityRatio) sampleSize *= 2
        return sampleSize
    }

    private fun directionFixture(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundResource(R.color.background)
        addView(label("方向继承治理前基线"))
        addView(label("portrait / landscape / behind / back stack"))
    }

    private fun label(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 18f
        setTextColor(Color.DKGRAY)
        gravity = Gravity.CENTER
        setPadding(16, 16, 16, 16)
    }

    companion object {
        const val EXTRA_FIXTURE = "io.legado.app.quality.fixture"
        const val EXTRA_PROFILE = "io.legado.app.quality.profile"
    }
}
