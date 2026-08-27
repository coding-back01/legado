package io.legado.app.help.update

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.legado.app.exception.NoStackTraceException
import java.time.DateTimeException
import java.time.Instant

data class AppReleaseInfo(
    val appVariant: AppVariant,
    val createdAt: Long,
    val note: String,
    val name: String,
    val downloadUrl: String,
    val assetUrl: String,
    val versionName: String
)

enum class AppVariant {
    OFFICIAL,
    BETA_RELEASEA,
    BETA_RELEASE,
    UNKNOWN;

    fun isBeta(): Boolean {
        return this == BETA_RELEASE || this == BETA_RELEASEA
    }

}

object StableUpdateChannel {

    const val LAST_RELEASE_URL =
        "https://api.github.com/repos/coding-back01/legado/releases/latest"

    fun normalize(@Suppress("UNUSED_PARAMETER") savedPreference: String?): AppVariant {
        return AppVariant.OFFICIAL
    }

    fun latestReleaseUrl(savedPreference: String?): String {
        normalize(savedPreference)
        return LAST_RELEASE_URL
    }
}

@Keep
data class GithubRelease(
    val assets: List<Asset>?,
    val body: String?,
    val draft: Boolean,
    @SerializedName("prerelease")
    val isPreRelease: Boolean,
    @SerializedName("tag_name")
    val tagName: String?,
) {
    fun gitReleaseToAppReleaseInfo(): List<AppReleaseInfo> {
        return listOf(StableReleaseParser().selectStableRelease(this))
    }
}

@Keep
data class Asset(
    @SerializedName("browser_download_url")
    val apkUrl: String?,
    @SerializedName("content_type")
    val contentType: String?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("download_count")
    val downloadCount: Int,
    val id: Int,
    val name: String?,
    val state: String?,
    val url: String?
)

sealed interface StableUpdateResult {

    data class UpdateAvailable(val release: AppReleaseInfo) : StableUpdateResult

    data object UpToDate : StableUpdateResult

    data object DebugBuildUnsupported : StableUpdateResult
}

open class StableUpdateException(message: String) : NoStackTraceException(message)

class ReleaseNetworkException(message: String) : StableUpdateException(message)

class ReleaseJsonException(message: String) : StableUpdateException(message)

class ReleaseDataException(message: String) : StableUpdateException(message)

class ReleaseVersionException(message: String) : StableUpdateException(message)

class StableReleaseParser(
    private val gson: Gson = Gson()
) {

    fun parseAndCheck(releaseJson: String, installedVersion: String): StableUpdateResult {
        if (isDebugVersion(installedVersion)) {
            return StableUpdateResult.DebugBuildUnsupported
        }
        val release = try {
            gson.fromJson(releaseJson, GithubRelease::class.java)
                ?: throw ReleaseJsonException("更新响应不是有效 JSON 对象")
        } catch (e: ReleaseJsonException) {
            throw e
        } catch (_: Exception) {
            throw ReleaseJsonException("更新响应 JSON 无法解析")
        }
        val selectedRelease = selectStableRelease(release)
        val installed = StableVersion.parseInstalled(installedVersion)
        val remote = StableVersion.parseReleaseTag(selectedRelease.versionName)
        return if (remote > installed) {
            StableUpdateResult.UpdateAvailable(selectedRelease)
        } else {
            StableUpdateResult.UpToDate
        }
    }

    fun selectStableRelease(release: GithubRelease): AppReleaseInfo {
        if (release.draft || release.isPreRelease) {
            throw ReleaseDataException("Latest Release 不是普通正式版")
        }
        val tag = release.tagName
            ?: throw ReleaseDataException("Latest Release 缺少 tag_name")
        StableVersion.parseReleaseTag(tag)
        val expectedName = "legado_app_${tag}_release.apk"
        val matchingAssets = release.assets
            ?.filter { it.name == expectedName }
            ?: throw ReleaseDataException("Latest Release 缺少 assets")
        if (matchingAssets.size != 1) {
            throw ReleaseDataException("Latest Release 必须包含唯一普通版 APK")
        }
        val asset = matchingAssets.single()
        if (asset.contentType != APK_MIME || asset.state != UPLOADED_STATE) {
            throw ReleaseDataException("普通版 APK 的 MIME 或上传状态无效")
        }
        val downloadUrl = asset.apkUrl?.takeIf { it.isNotBlank() }
            ?: throw ReleaseDataException("普通版 APK 缺少下载地址")
        val assetUrl = asset.url?.takeIf { it.isNotBlank() }
            ?: throw ReleaseDataException("普通版 APK 缺少资产地址")
        val createdAt = asset.createdAt?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
        } ?: 0L
        return AppReleaseInfo(
            appVariant = AppVariant.OFFICIAL,
            createdAt = createdAt,
            note = release.body.orEmpty(),
            name = expectedName,
            downloadUrl = downloadUrl,
            assetUrl = assetUrl,
            versionName = tag
        )
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val UPLOADED_STATE = "uploaded"

        fun isDebugVersion(versionName: String): Boolean {
            return versionName.endsWith("debug")
        }
    }
}

private data class StableVersion(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int
) : Comparable<StableVersion> {

    override fun compareTo(other: StableVersion): Int {
        return compareValuesBy(
            this,
            other,
            StableVersion::year,
            StableVersion::month,
            StableVersion::day,
            StableVersion::hour
        )
    }

    companion object {
        private val versionRegex = Regex("^3\\.(\\d{2})\\.(\\d{2})(\\d{2})(\\d{2})$")

        fun parseReleaseTag(value: String): StableVersion {
            return parse(value) { ReleaseDataException("Release tag 格式无效") }
        }

        fun parseInstalled(value: String): StableVersion {
            return parse(value) { ReleaseVersionException("当前安装版本格式无效") }
        }

        private fun parse(
            value: String,
            exception: () -> StableUpdateException
        ): StableVersion {
            val groups = versionRegex.matchEntire(value)?.groupValues ?: throw exception()
            val year = groups[1].toInt()
            val month = groups[2].toInt()
            val day = groups[3].toInt()
            val hour = groups[4].toInt()
            try {
                java.time.LocalDateTime.of(2000 + year, month, day, hour, 0)
            } catch (_: DateTimeException) {
                throw exception()
            }
            return StableVersion(year, month, day, hour)
        }
    }
}
