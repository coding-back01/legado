package io.legado.app.utils

import android.view.inputmethod.InputMethodManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiInputNavigationContractTest {

    @Test
    fun `输入法显示使用普通用户请求 flag`() {
        val source = repoFile(
            "app/src/main/java/io/legado/app/utils/ViewExtensions.kt"
        ).readText()
        val showSoftInput = source.substring(
            source.indexOf("fun EditText.showSoftInput"),
            source.indexOf("fun View.disableAutoFill")
        )

        assertTrue(showSoftInput.contains("InputMethodManager.SHOW_IMPLICIT"))
        assertFalse(showSoftInput.contains("InputMethodManager.RESULT_SHOWN"))
        assertEquals(1, InputMethodManager.SHOW_IMPLICIT)
    }

    @Test
    fun `导航滚动条只依赖公开 RecyclerView 接口`() {
        val source = repoFile(
            "app/src/main/java/io/legado/app/utils/NavigationViewUtils.kt"
        ).readText()

        assertTrue(source.contains("getChildAt(0) as? RecyclerView"))
        assertTrue(source.contains("isVerticalScrollBarEnabled = false"))
        assertFalse(source.contains("com.google.android.material.internal"))
        assertFalse(source.contains("NavigationMenuView"))
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
