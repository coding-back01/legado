package io.legado.app.utils

import android.content.Context
import androidx.core.os.ConfigurationCompat
import java.text.NumberFormat
import java.util.Locale

object LocalizedNumberFormatter {

    fun formatInteger(context: Context, value: Int): String {
        val locale = ConfigurationCompat.getLocales(
            context.resources.configuration
        )[0] ?: Locale.getDefault()
        return formatInteger(value, locale)
    }

    @JvmStatic
    fun formatInteger(value: Int, locale: Locale): String {
        return NumberFormat.getIntegerInstance(locale).format(value)
    }
}
