package io.legado.app

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun testContentProvider() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse(
            "content://${appContext.packageName}.readerProvider/bookSources/query"
        )
        appContext.contentResolver.query(
            uri,
            arrayOf("result"),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue("ReaderProvider 未返回结果行: $uri", cursor?.moveToFirst() == true)
            val result = cursor!!.getString(cursor.getColumnIndexOrThrow("result"))
            assertTrue("ReaderProvider 返回了空结果: $uri", result.isNotBlank())
            Log.d("ReaderProviderTest", "bookSources query succeeded")
        }
    }
}
