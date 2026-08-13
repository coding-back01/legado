package io.legado.app.constant

import androidx.annotation.IntDef

@Suppress("ConstPropertyName")
object PageAnim {

    const val coverPageAnim = 0

    const val slidePageAnim = 1

    const val simulationPageAnim = 2

    const val scrollPageAnim = 3

    const val noAnim = 4

    const val kindlePageAnim = 5

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        coverPageAnim,
        slidePageAnim,
        simulationPageAnim,
        scrollPageAnim,
        noAnim,
        kindlePageAnim,
    )
    annotation class Anim

    fun normalize(value: Int): Int {
        return when (value) {
            coverPageAnim,
            slidePageAnim,
            simulationPageAnim,
            scrollPageAnim,
            noAnim,
            kindlePageAnim -> value

            else -> noAnim
        }
    }

}
