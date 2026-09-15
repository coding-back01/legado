package io.legado.app.api

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShortcutEntryContractTest {

    @Test
    fun `三个真实入口均在完成对应动作后上报`() {
        val entries = mapOf(
            "app/src/main/java/io/legado/app/ui/main/MainActivity.kt" to
                    "ShortcutLaunch.consume(intent, ShortcutLaunch.BOOKSHELF)",
            "app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt" to
                    "ShortcutLaunch.consume(intent, ShortcutLaunch.LAST_READ)",
            "app/src/main/java/io/legado/app/receiver/SharedReceiverActivity.kt" to
                    "ShortcutLaunch.consume(intent, ShortcutLaunch.READ_ALOUD)",
        )

        entries.forEach { (path, consumeCall) ->
            val source = repoFile(path).readText()
            val consumePosition = source.indexOf(consumeCall)
            val reportPosition = source.indexOf("ShortcutLaunch.report", consumePosition)
            assertTrue("$path 缺少对应快捷方式消费", consumePosition >= 0)
            assertTrue("$path 必须先消费再上报", reportPosition > consumePosition)
        }
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
