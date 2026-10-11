package io.legado.app.model.dictionary

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** 验证脚本先关闭可丢弃模拟器的网络，再单独执行本测试。 */
@RunWith(AndroidJUnit4::class)
class DictionaryOfflineTest {
    @Test fun definitionsImagesAndAudioRemainAvailableWithoutAnActiveNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        assertFalse("必须先关闭测试设备网络", connectivity.activeNetworkInfo?.isConnected == true)
        val store = LocalDictionaryStore(context)
        val source = File(context.cacheDir, "offline-${UUID.randomUUID()}").apply { mkdirs() }
        var id: String? = null
        try {
            DictionaryTestFixtures.copy(source, "media.mdx", "media.mdd")
            val dictionary = store.importFiles(source, "media.mdx")
            id = dictionary.id
            source.deleteRecursively()
            assertTrue(store.lookup(dictionary.id, "media").single().html.contains("meaning"))
            assertTrue(store.resource(dictionary.id, "diagram.png").isFile)
            assertTrue(store.resource(dictionary.id, "tone.wav").length() > 1000)
            assertTrue(store.lookup(dictionary.id, "uncollected").isEmpty())
        } finally {
            id?.let { store.delete(it) }
            source.deleteRecursively()
        }
    }
}
