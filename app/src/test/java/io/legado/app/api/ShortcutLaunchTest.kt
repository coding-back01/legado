package io.legado.app.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutLaunchTest {

    @Test
    fun `快捷方式 ID 保持既有持久标识`() {
        assertEquals("bookshelf", ShortcutLaunch.BOOKSHELF)
        assertEquals("lastRead", ShortcutLaunch.LAST_READ)
        assertEquals("readAloud", ShortcutLaunch.READ_ALOUD)
    }

    @Test
    fun `普通启动没有稳定来源标记时不消费`() {
        val result = ShortcutLaunch.consume(
            ShortcutLaunch.State(null, null, consumed = false),
            ShortcutLaunch.BOOKSHELF,
        )

        assertNull(result.shortcutId)
        assertFalse(result.state.consumed)
    }

    @Test
    fun `入口只消费与自身匹配的快捷方式`() {
        val result = ShortcutLaunch.consume(
            ShortcutLaunch.State(
                ShortcutLaunch.SOURCE,
                ShortcutLaunch.LAST_READ,
                consumed = false,
            ),
            ShortcutLaunch.BOOKSHELF,
        )

        assertNull(result.shortcutId)
        assertFalse(result.state.consumed)
    }

    @Test
    fun `同一次快捷方式 Intent 重放不重复消费`() {
        val first = ShortcutLaunch.consume(
            ShortcutLaunch.State(
                ShortcutLaunch.SOURCE,
                ShortcutLaunch.READ_ALOUD,
                consumed = false,
            ),
            ShortcutLaunch.READ_ALOUD,
        )
        val replay = ShortcutLaunch.consume(first.state, ShortcutLaunch.READ_ALOUD)

        assertEquals(ShortcutLaunch.READ_ALOUD, first.shortcutId)
        assertTrue(first.state.consumed)
        assertNull(replay.shortcutId)
        assertTrue(replay.state.consumed)
    }
}
