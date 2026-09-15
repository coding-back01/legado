package io.legado.app.lib.prefs

internal object LauncherIconSelection {

    fun resolveIconIndex(
        entryValues: Array<CharSequence>?,
        value: String?,
        iconCount: Int,
    ): Int {
        if (iconCount <= 0) return -1
        val matchedIndex = entryValues?.indexOfFirst { it.toString() == value } ?: -1
        return matchedIndex.takeIf { it in 0 until iconCount } ?: 0
    }
}
