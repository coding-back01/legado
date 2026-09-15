package io.legado.app.quality

import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BitmapDensityInstrumentedTest {

    @Test
    fun bitmapDensityAndMemoryContract() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val profile = requireNotNull(arguments.getString(PROFILE_ARGUMENT))
        val mode = requireNotNull(arguments.getString(MODE_ARGUMENT))
        val density = context.resources.displayMetrics.densityDpi
        val scale = when (mode) {
            MODE_BASELINE -> density / DisplayMetrics.DENSITY_DEFAULT
            MODE_NODPI -> 1
            else -> error("未知位图密度模式: $mode")
        }
        val expectedResourceDensity = when (mode) {
            MODE_BASELINE -> TypedValue.DENSITY_DEFAULT
            else -> TypedValue.DENSITY_NONE
        }
        val rows = mutableListOf(
            "resource\tsource_width\tsource_height\tresource_density\tdevice_density" +
                    "\tdecoded_width\tdecoded_height\tbyte_count",
        )

        RESOURCES.forEach { resource ->
            val value = TypedValue()
            context.resources.getValue(resource.id, value, true)
            assertEquals(resource.name, expectedResourceDensity, value.density)
            val bitmap = BitmapFactory.decodeResource(context.resources, resource.id)
            assertNotNull(resource.name, bitmap)
            requireNotNull(bitmap)
            assertEquals(resource.name, resource.width * scale, bitmap.width)
            assertEquals(resource.name, resource.height * scale, bitmap.height)
            assertEquals(resource.name, bitmap.width * bitmap.height * 4, bitmap.byteCount)
            rows += listOf(
                resource.name,
                resource.width,
                resource.height,
                value.density,
                density,
                bitmap.width,
                bitmap.height,
                bitmap.byteCount,
            ).joinToString("\t")
            bitmap.recycle()
        }

        File(context.filesDir, "bitmap-density-$profile-$mode.tsv")
            .writeText(rows.joinToString("\n", postfix = "\n"))
    }

    private data class Resource(
        val name: String,
        val id: Int,
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val PROFILE_ARGUMENT = "legado.golden.profile"
        const val MODE_ARGUMENT = "legado.bitmap.mode"
        const val MODE_BASELINE = "baseline"
        const val MODE_NODPI = "nodpi"
        val RESOURCES = listOf(
            Resource("icon_read_book", R.drawable.icon_read_book, 200, 200),
            Resource("image_cover_default", R.drawable.image_cover_default, 600, 900),
            Resource("image_legado", R.drawable.image_legado, 192, 192),
            Resource("image_loading_error", R.drawable.image_loading_error, 512, 512),
            Resource("image_rss", R.drawable.image_rss, 500, 500),
            Resource("image_rss_article", R.drawable.image_rss_article, 500, 500),
        )
    }
}
