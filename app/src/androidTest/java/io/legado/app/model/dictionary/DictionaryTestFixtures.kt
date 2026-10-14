package io.legado.app.model.dictionary

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipInputStream

internal object DictionaryTestFixtures {
    fun copy(directory: File, vararg names: String) {
        directory.mkdirs()
        val remaining = names.toMutableSet()
        val context = InstrumentationRegistry.getInstrumentation().context
        ZipInputStream(context.assets.open("dictionary-fixtures.zip")).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (entry.name in remaining) {
                    val file = DictionaryPaths.file(directory, entry.name)
                    file.parentFile?.mkdirs()
                    file.outputStream().use { stream.copyTo(it) }
                    remaining.remove(entry.name)
                }
            }
        }
        check(remaining.isEmpty()) { remaining.toString() }
    }
}
