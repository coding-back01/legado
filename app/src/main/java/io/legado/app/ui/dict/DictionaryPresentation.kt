package io.legado.app.ui.dict

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.model.dictionary.DictionaryDisplay

/** 仅负责展示；不推断字段，也不改变查询结果。 */
object DictionaryPresentation {
    fun dialogContext(context: Context): Context = androidx.appcompat.view.ContextThemeWrapper(context,
        if (io.legado.app.help.config.AppConfig.isNightTheme) R.style.DictionaryDialogTheme_Night else R.style.DictionaryDialogTheme)

    fun summary(context: Context, display: DictionaryDisplay, secondary: Int, showOmission: Boolean = true): CharSequence {
        val text = SpannableStringBuilder()
        fun paragraph(value: String, vararg spans: Any) {
            if (text.isNotEmpty()) text.append("\n\n")
            val start = text.length
            text.append(value)
            spans.forEach { text.setSpan(it, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        display.previews.forEach { preview ->
            val summary = preview.summary ?: return@forEach
            if (display.previews.size > 1) paragraph(preview.entry.headword, StyleSpan(Typeface.BOLD))
            listOfNotNull(summary.pronunciation, summary.partOfSpeech).takeIf { it.isNotEmpty() }?.let {
                paragraph(it.joinToString("   "), RelativeSizeSpan(0.85f), ForegroundColorSpan(secondary))
            }
            paragraph(summary.definition)
            summary.examples.forEach { paragraph(it, StyleSpan(Typeface.ITALIC)) }
            if (showOmission && summary.truncated) paragraph(context.getString(R.string.dictionary_summary_truncated),
                RelativeSizeSpan(0.85f), ForegroundColorSpan(secondary))
        }
        return text
    }

    fun foreground(background: Int): Int = if (ColorUtils.calculateLuminance(background) < 0.35) 0xffe5e3df.toInt() else 0xff252522.toInt()
    fun secondary(background: Int): Int = ColorUtils.blendARGB(foreground(background), background, 0.25f)

    fun surface(view: View, background: Int) {
        val ink = foreground(background)
        val density = view.resources.displayMetrics.density
        view.background = GradientDrawable().apply {
            setColor(background)
            cornerRadius = 2 * density
            setStroke(density.toInt().coerceAtLeast(1), ColorUtils.blendARGB(ink, background, 0.76f))
        }
        fun color(child: View) {
            when (child) {
                is TextView -> {
                    child.setTextColor(ink)
                    child.compoundDrawablesRelative.filterNotNull().forEach { it.mutate().setTint(ink) }
                }
                is ImageView -> child.setColorFilter(ink)
                is ViewGroup -> for (index in 0 until child.childCount) color(child.getChildAt(index))
            }
        }
        color(view)
    }

    fun readingSurface(background: Int): Int {
        val base = if (ColorUtils.calculateLuminance(background) < 0.35) 0xff242423.toInt() else 0xfffaf9f6.toInt()
        return ColorUtils.blendARGB(base, ColorUtils.setAlphaComponent(background, 255), 0.12f)
    }
}
