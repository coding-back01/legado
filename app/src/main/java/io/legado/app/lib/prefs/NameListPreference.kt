package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils


class NameListPreference(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {

    private val isBottomBackground: Boolean

    init {
        layoutResource = R.layout.view_preference
        widgetLayoutResource = R.layout.item_fillet_text
        var parsedIsBottomBackground = false
        context.withStyledAttributes(attrs, R.styleable.Preference) {
            parsedIsBottomBackground = getBoolean(
                R.styleable.Preference_isBottomBackground,
                parsedIsBottomBackground
            )
        }
        isBottomBackground = parsedIsBottomBackground
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val v = Preference.bindView<TextView>(
            context, holder, icon, title, summary, widgetLayoutResource,
            R.id.text_view, isBottomBackground = isBottomBackground
        )
        if (v is TextView) {
            v.text = entry
            if (isBottomBackground) {
                val bgColor = context.bottomBackground
                val pTextColor = context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
                v.setTextColor(pTextColor)
            }
        }
        super.onBindViewHolder(holder)
    }

}
