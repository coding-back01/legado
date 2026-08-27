package io.legado.app.help.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class StableReleaseParserTest {

    private val parser = StableReleaseParser()

    @Test
    fun `普通版资产选择不受 releaseA 顺序和相同时间戳影响`() {
        val normalAsset = asset(name = "legado_app_3.26.082216_release.apk")
        val releaseAAsset = asset(name = "legado_app_3.26.082216_releaseA.apk")

        listOf(
            listOf(normalAsset, releaseAAsset),
            listOf(releaseAAsset, normalAsset)
        ).forEach { assets ->
            val result = parser.parseAndCheck(
                releaseJson(tag = "3.26.082216", assets = assets),
                installedVersion = "3.26.082215"
            )

            assertTrue(result is StableUpdateResult.UpdateAvailable)
            result as StableUpdateResult.UpdateAvailable
            assertEquals("3.26.082216", result.release.versionName)
            assertEquals("legado_app_3.26.082216_release.apk", result.release.name)
            assertEquals("https://example.invalid/release.apk", result.release.downloadUrl)
        }
    }

    @Test
    fun `普通版资产必须存在且唯一`() {
        assertThrows(ReleaseDataException::class.java) {
            parser.parseAndCheck(
                releaseJson(
                    tag = "3.26.082216",
                    assets = listOf(asset(name = "legado_app_3.26.082216_releaseA.apk"))
                ),
                installedVersion = "3.26.082215"
            )
        }

        val normalAsset = asset(name = "legado_app_3.26.082216_release.apk")
        assertThrows(ReleaseDataException::class.java) {
            parser.parseAndCheck(
                releaseJson(
                    tag = "3.26.082216",
                    assets = listOf(normalAsset, normalAsset)
                ),
                installedVersion = "3.26.082215"
            )
        }
    }

    @Test
    fun `普通版资产必须完成上传并使用 APK MIME`() {
        listOf(
            asset(
                name = "legado_app_3.26.082216_release.apk",
                contentType = "application/octet-stream"
            ),
            asset(
                name = "legado_app_3.26.082216_release.apk",
                state = "new"
            )
        ).forEach { invalidAsset ->
            assertThrows(ReleaseDataException::class.java) {
                parser.parseAndCheck(
                    releaseJson(tag = "3.26.082216", assets = listOf(invalidAsset)),
                    installedVersion = "3.26.082215"
                )
            }
        }
    }

    @Test
    fun `Release JSON 与 tag 格式错误可以区分`() {
        assertThrows(ReleaseJsonException::class.java) {
            parser.parseAndCheck("{not-json", installedVersion = "3.26.082215")
        }

        listOf("3.26.82216", "v3.26.082216", "3.26.132216", "beta").forEach { tag ->
            assertThrows(ReleaseDataException::class.java) {
                parser.parseAndCheck(
                    releaseJson(
                        tag = tag,
                        assets = listOf(asset(name = "legado_app_${tag}_release.apk"))
                    ),
                    installedVersion = "3.26.082215"
                )
            }
        }
    }

    @Test
    fun `完整数字版本比较覆盖同日跨日和跨年`() {
        listOf(
            "3.26.082215" to "3.26.082216",
            "3.26.082316" to "3.26.082400",
            "3.26.123123" to "3.27.010100"
        ).forEach { (installed, remote) ->
            val result = parser.parseAndCheck(
                releaseJson(
                    tag = remote,
                    assets = listOf(asset(name = "legado_app_${remote}_release.apk"))
                ),
                installedVersion = installed
            )

            assertTrue("$installed 应早于 $remote", result is StableUpdateResult.UpdateAvailable)
        }
    }

    @Test
    fun `相同或更高的正式版本返回已是最新`() {
        listOf("3.26.082216", "3.27.010100").forEach { installed ->
            val result = parser.parseAndCheck(
                releaseJson(
                    tag = "3.26.082216",
                    assets = listOf(asset(name = "legado_app_3.26.082216_release.apk"))
                ),
                installedVersion = installed
            )

            assertEquals(StableUpdateResult.UpToDate, result)
        }
    }

    @Test
    fun `当前正式版版本格式错误不能猜测比较结果`() {
        assertThrows(ReleaseVersionException::class.java) {
            parser.parseAndCheck(
                releaseJson(
                    tag = "3.26.082216",
                    assets = listOf(asset(name = "legado_app_3.26.082216_release.apk"))
                ),
                installedVersion = "3.26.82215"
            )
        }
    }

    @Test
    fun `Debug 安装明确不支持正式更新且不返回下载`() {
        val result = parser.parseAndCheck(
            releaseJson(
                tag = "3.26.082216",
                assets = listOf(asset(name = "legado_app_3.26.082216_release.apk"))
            ),
            installedVersion = "3.26.082215debug"
        )

        assertEquals(StableUpdateResult.DebugBuildUnsupported, result)
        assertTrue(result !is StableUpdateResult.UpdateAvailable)
    }

    @Test
    fun `显式提供 Latest JSON 时执行真实资产烟测`() {
        val fixturePath = System.getenv("LEGADO_LATEST_RELEASE_JSON")
        assumeTrue("未提供 LEGADO_LATEST_RELEASE_JSON", !fixturePath.isNullOrBlank())

        val result = parser.parseAndCheck(
            File(fixturePath!!).readText(),
            installedVersion = "3.00.010100"
        )

        assertTrue(result is StableUpdateResult.UpdateAvailable)
        result as StableUpdateResult.UpdateAvailable
        assertEquals(
            "legado_app_${result.release.versionName}_release.apk",
            result.release.name
        )
    }

    private fun releaseJson(tag: String, assets: List<String>): String = """
        {
          "tag_name": "$tag",
          "body": "更新说明",
          "draft": false,
          "prerelease": false,
          "assets": [${assets.joinToString(",")}]
        }
    """.trimIndent()

    private fun asset(
        name: String,
        contentType: String = "application/vnd.android.package-archive",
        state: String = "uploaded"
    ): String = """
        {
          "browser_download_url": "https://example.invalid/release.apk",
          "content_type": "$contentType",
          "created_at": "2026-08-22T16:00:00Z",
          "download_count": 1,
          "id": 1,
          "name": "$name",
          "state": "$state",
          "url": "https://api.example.invalid/assets/1"
        }
    """.trimIndent()
}
