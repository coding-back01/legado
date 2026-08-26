package io.legado.app.help.update

import kotlinx.coroutines.withTimeout

object AppUpdate {

    val gitHubUpdate: AppUpdateInterface? by lazy {
        AppUpdateGitHub
    }

    data class UpdateInfo(
        val tagName: String,
        val updateLog: String,
        val downloadUrl: String,
        val fileName: String
    )

    interface AppUpdateInterface {

        suspend fun check(): StableUpdateResult

    }

}

class UpdateUnavailableException : StableUpdateException("更新功能不可用")

object UpdateCheckExecutor {

    suspend fun execute(
        updater: AppUpdate.AppUpdateInterface?,
        timeoutMillis: Long = 10_000,
        onFinally: () -> Unit
    ): StableUpdateResult {
        return try {
            val availableUpdater = updater ?: throw UpdateUnavailableException()
            withTimeout(timeoutMillis) {
                availableUpdater.check()
            }
        } finally {
            onFinally()
        }
    }
}
