package io.legado.app.lib.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherIconSelectionTest {

    private val values = arrayOf<CharSequence>(
        "ic_launcher",
        "launcher1",
        "launcher2",
        "launcher3",
        "launcher4",
        "launcher5",
        "launcher6",
    )

    @Test
    fun `七个旧字符串值保持原索引`() {
        values.forEachIndexed { index, value ->
            assertEquals(
                index,
                LauncherIconSelection.resolveIconIndex(values, value.toString(), values.size),
            )
        }
    }

    @Test
    fun `未知旧值与空值回退到默认图标`() {
        assertEquals(
            0,
            LauncherIconSelection.resolveIconIndex(values, "unknown_legacy_value", values.size),
        )
        assertEquals(0, LauncherIconSelection.resolveIconIndex(values, null, values.size))
    }

    @Test
    fun `缺失图标资源时不产生越界索引`() {
        assertEquals(-1, LauncherIconSelection.resolveIconIndex(values, "launcher6", 0))
        assertEquals(0, LauncherIconSelection.resolveIconIndex(values, "launcher6", 3))
    }
}
