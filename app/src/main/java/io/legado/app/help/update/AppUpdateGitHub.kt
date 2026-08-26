package io.legado.app.help.update

import androidx.annotation.Keep
import io.legado.app.constant.AppConst
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import kotlinx.coroutines.CancellationException

@Keep
@Suppress("unused")
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    const val LAST_RELEASE_URL = StableUpdateChannel.LAST_RELEASE_URL

    private val releaseParser = StableReleaseParser()

    private suspend fun getLatestReleaseJson(): String {
        val res = try {
            okHttpClient.newCallResponse {
                url(StableUpdateChannel.latestReleaseUrl(AppConfig.updateToVariant))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw ReleaseNetworkException("连接更新服务失败")
        }
        return res.use { response ->
            if (!response.isSuccessful) {
                throw ReleaseNetworkException("更新服务返回 HTTP ${response.code}")
            }
            val body = try {
                response.body.text()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                throw ReleaseNetworkException("读取更新响应失败")
            }
            if (body.isBlank()) {
                throw ReleaseJsonException("更新响应为空")
            }
            body
        }
    }

    override suspend fun check(): StableUpdateResult {
        val installedVersion = AppConst.appInfo.versionName
        if (StableReleaseParser.isDebugVersion(installedVersion)) {
            return StableUpdateResult.DebugBuildUnsupported
        }
        return releaseParser.parseAndCheck(
            getLatestReleaseJson(),
            installedVersion
        )
    }
}
