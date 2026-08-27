package io.legado.app.help.update

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckExecutorTest {

    @Test
    fun `成功路径关闭等待状态`() = runBlocking {
        var finalized = 0
        val expected = StableUpdateResult.UpToDate

        val actual = UpdateCheckExecutor.execute(
            updater = FakeUpdater { expected },
            onFinally = { finalized++ }
        )

        assertTrue(actual === expected)
        assertEquals(1, finalized)
    }

    @Test
    fun `失败路径关闭等待状态并保留原始错误`() = runBlocking {
        var finalized = 0
        val expected = ReleaseNetworkException("网络失败")

        val failure = runCatching {
            UpdateCheckExecutor.execute(
                updater = FakeUpdater { throw expected },
                onFinally = { finalized++ }
            )
        }.exceptionOrNull()

        assertTrue(failure is ReleaseNetworkException)
        assertEquals(expected.message, failure?.message)
        assertEquals(1, finalized)
    }

    @Test
    fun `超时路径关闭等待状态`() = runBlocking {
        var finalized = 0

        val failure = runCatching {
            UpdateCheckExecutor.execute(
                updater = FakeUpdater {
                    delay(100)
                    StableUpdateResult.UpToDate
                },
                timeoutMillis = 1,
                onFinally = { finalized++ }
            )
        }.exceptionOrNull()

        assertTrue(failure is TimeoutCancellationException)
        assertEquals(1, finalized)
    }

    @Test
    fun `空更新实现也关闭等待状态`() = runBlocking {
        var finalized = 0

        val failure = runCatching {
            UpdateCheckExecutor.execute(
                updater = null,
                onFinally = { finalized++ }
            )
        }.exceptionOrNull()

        assertTrue(failure is UpdateUnavailableException)
        assertEquals(1, finalized)
    }

    private class FakeUpdater(
        private val result: suspend () -> StableUpdateResult
    ) : AppUpdate.AppUpdateInterface {

        override suspend fun check(): StableUpdateResult = result()
    }
}
