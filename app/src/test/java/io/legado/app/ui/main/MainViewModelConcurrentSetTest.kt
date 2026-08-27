package io.legado.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainViewModelConcurrentSetTest {

    @Test
    fun `目录更新集合只暴露 API 21 可用的公共接口`() {
        val source = repoFile(
            "app/src/main/java/io/legado/app/ui/main/MainViewModel.kt"
        ).readText()

        assertTrue(
            source.contains(
                "private val onUpTocBooks: MutableSet<String> = " +
                        "ConcurrentHashMap.newKeySet()"
            )
        )
        assertFalse(source.contains("private val onUpTocBooks = ConcurrentHashMap.newKeySet"))
    }

    @Test
    fun `公共 MutableSet 接口保留并发 key set 语义`() {
        val activeBooks: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val executor = Executors.newFixedThreadPool(8)

        repeat(8) { worker ->
            executor.execute {
                repeat(1_000) { index ->
                    activeBooks.add("book-${index % 100}")
                    if ((index + worker) % 4 == 0) {
                        activeBooks.remove("temporary-$worker-$index")
                    }
                }
            }
        }
        executor.shutdown()

        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(100, activeBooks.size)
        assertTrue(activeBooks.contains("book-0"))
        assertTrue(activeBooks.contains("book-99"))
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
