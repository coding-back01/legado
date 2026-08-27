package io.legado.app.help.update

import org.junit.Assert.assertEquals
import org.junit.Test

class StableUpdateChannelTest {

    @Test
    fun `全部旧偏好和未知值永久归一为普通稳定版`() {
        listOf(
            "default_version",
            "official_version",
            "beta_release_version",
            "beta_releaseA_version",
            "unknown_legacy_value",
            "",
            null
        ).forEach { savedPreference ->
            assertEquals(
                AppVariant.OFFICIAL,
                StableUpdateChannel.normalize(savedPreference)
            )
            assertEquals(
                "https://api.github.com/repos/coding-back01/legado/releases/latest",
                StableUpdateChannel.latestReleaseUrl(savedPreference)
            )
        }
    }
}
