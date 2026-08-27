package io.legado.app.help.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseWorkflowContractTest {

    private val workflow = repoFile(".github/workflows/release.yml").readText()
    private val buildGradle = repoFile("app/build.gradle").readText()
    private val appConst = repoFile(
        "app/src/main/java/io/legado/app/constant/AppConst.kt"
    ).readText()
    private val preferKey = repoFile(
        "app/src/main/java/io/legado/app/constant/PreferKey.kt"
    ).readText()

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(
            File("../$path"),
            File(path)
        ).firstOrNull(File::isFile)
    ) { "找不到 $path" }

    @Test
    fun `发布只允许带 expected_sha 的手动触发`() {
        assertTrue(workflow.contains("workflow_dispatch:"))
        assertTrue(workflow.contains("expected_sha:"))
        assertTrue(workflow.contains("required: true"))
        assertFalse(workflow.contains("push:"))
        assertFalse(workflow.contains("schedule:"))
        assertFalse(workflow.contains("repository_dispatch:"))
    }

    @Test
    fun `发布前同时核对 actor 和 triggering_actor 为仓库所有者`() {
        assertTrue(workflow.contains("github.actor == github.repository_owner"))
        assertTrue(workflow.contains("github.triggering_actor == github.repository_owner"))
        assertTrue(workflow.contains("github.repository == 'coding-back01/legado'"))
    }

    @Test
    fun `只构建并上传唯一普通版 APK`() {
        assertFalse(workflow.contains("matrix:"))
        assertFalse(workflow.contains("releaseA"))
        assertFalse(workflow.contains("applicationIdSuffix '.releaseA'"))
        assertTrue(workflow.contains("legado_app_\${VERSION}_release.apk"))
        assertTrue(workflow.contains("name: release-apk"))
        assertFalse(workflow.contains("pattern: release-apk-*"))
    }

    @Test
    fun `签名材料缺失时在构建前失败且没有回退签名`() {
        listOf(
            "RELEASE_KEY_STORE",
            "RELEASE_STORE_PASSWORD",
            "RELEASE_KEY_ALIAS",
            "RELEASE_KEY_PASSWORD"
        ).forEach { secretName ->
            assertTrue(workflow.contains("secrets.$secretName"))
        }
        assertTrue(workflow.contains("if [[ -z \"\${!name:-}\" ]]"))
        assertFalse(workflow.contains("legado.jks"))
        assertTrue(
            workflow.indexOf("检查并恢复签名密钥") <
                    workflow.indexOf("构建 APK")
        )
    }

    @Test
    fun `expected_sha 与检出提交和 workflow sha 在构建前一致`() {
        assertTrue(workflow.contains("ref: \${{ inputs.expected_sha }}"))
        assertTrue(workflow.contains("EXPECTED_SHA: \${{ inputs.expected_sha }}"))
        assertTrue(workflow.contains("ACTUAL_SHA=\$(git rev-parse HEAD)"))
        assertTrue(workflow.contains("\"${'$'}ACTUAL_SHA\" != \"${'$'}EXPECTED_SHA\""))
        assertTrue(workflow.contains("\"${'$'}GITHUB_SHA\" != \"${'$'}EXPECTED_SHA\""))
        assertTrue(
            workflow.indexOf("核对候选提交") <
                    workflow.indexOf("检查并恢复签名密钥")
        )
    }

    @Test
    fun `候选提交必须是当前远端 master`() {
        assertTrue(workflow.contains("refs/heads/master:refs/remotes/origin/master"))
        assertTrue(
            workflow.contains(
                "MASTER_SHA=\$(git rev-parse refs/remotes/origin/master)"
            )
        )
        assertTrue(workflow.contains("\"${'$'}MASTER_SHA\" != \"${'$'}EXPECTED_SHA\""))
    }

    @Test
    fun `Release 保持草稿且 tag 和 target 指向 expected_sha`() {
        assertTrue(workflow.contains("--draft"))
        assertTrue(workflow.contains("--target \"${'$'}EXPECTED_SHA\""))
        assertTrue(workflow.contains("git rev-list -n 1 \"${'$'}VERSION\""))
        assertTrue(workflow.contains("gh release view \"${'$'}VERSION\""))
        assertTrue(workflow.contains("TARGET_COMMITISH"))
        assertTrue(workflow.contains("\"${'$'}TAG_SHA\" != \"${'$'}EXPECTED_SHA\""))
        assertTrue(workflow.contains("\"${'$'}TARGET_SHA\" != \"${'$'}EXPECTED_SHA\""))
    }

    @Test
    fun `未来分发链不含 releaseA 且保留历史兼容识别`() {
        assertFalse(workflow.contains("releaseA"))
        assertFalse(buildGradle.contains(".releaseA"))
        assertFalse(buildGradle.contains("@string/app_name_a"))
        assertTrue(appConst.contains("packageName.contains(\"releaseA\")"))
        assertTrue(preferKey.contains("const val updateToVariant = \"updateToVariant\""))
    }
}
