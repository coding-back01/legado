package io.legado.app.model.dictionary

data class InstantDictionaryPlacement(val above: Boolean, val showText: Boolean)

object InstantDictionaryPlacements {
    fun plan(height: Int, top: Int, bottom: Int, desiredHeight: Int, margin: Int): InstantDictionaryPlacement {
        val above = (top - margin).coerceAtLeast(0)
        val below = (height - bottom - margin).coerceAtLeast(0)
        return InstantDictionaryPlacement(above >= below, maxOf(above, below) >= desiredHeight)
    }
}
