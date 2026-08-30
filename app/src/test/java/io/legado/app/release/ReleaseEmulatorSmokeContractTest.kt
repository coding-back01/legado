package io.legado.app.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseEmulatorSmokeContractTest {

    private val runner = repoFile(".github/scripts/run-release-emulator-smoke.sh").readText()
    private val providerTest = repoFile(
        "app/src/androidTest/java/io/legado/app/ExampleInstrumentedTest.kt"
    ).readText()
    private val txtSmoke = repoFile(
        "app/src/androidTest/java/io/legado/app/release/LocalTxtReleaseSmokeTest.kt"
    ).readText()
    private val previewSmoke = repoFile(
        "app/src/androidTest/java/io/legado/app/release/BookSourceImportPreviewSmokeTest.kt"
    ).readText()
    private val fixtureDispatch = repoFile(
        "app/src/androidTest/java/io/legado/app/release/ReleaseSmokeFixtureDispatchActivity.java"
    ).readText()
    private val fixtureProvider = repoFile(
        "app/src/androidTest/java/io/legado/app/release/ReleaseSmokeFixtureProvider.java"
    ).readText()
    private val testManifest = repoFile("app/src/androidTest/AndroidManifest.xml").readText()
    private val buildGradle = repoFile("app/build.gradle").readText()
    private val versions = repoFile("gradle/libs.versions.toml").readText()

    @Test
    fun `发布烟测只运行精确 annotation 而非整个设备测试套件`() {
        assertTrue(runner.contains("-e annotation io.legado.app.release.ReleaseSmoke"))
        assertFalse(runner.contains("connectedAppDebugAndroidTest"))
        assertTrue(runner.contains("serial\" != emulator-*"))
        assertTrue(runner.contains("getprop ro.kernel.qemu"))
        assertTrue(runner.contains("pm clear io.legado.app.debug"))
        assertTrue(runner.contains("pm clear io.legado.app.debug.test"))
        assertTrue(txtSmoke.contains("@ReleaseSmoke"))
        assertTrue(previewSmoke.contains("@ReleaseSmoke"))
    }

    @Test
    fun `ReaderProvider 使用运行时包名和真实复数路径`() {
        assertTrue(providerTest.contains("${'$'}{appContext.packageName}.readerProvider"))
        assertTrue(providerTest.contains("bookSources/query"))
        assertFalse(providerTest.contains("io.legado.app.api.ReaderProvider"))
        assertFalse(providerTest.contains("/sources/query"))
    }

    @Test
    fun `离线核心路径覆盖正文翻页书架和取消导入预览`() {
        assertTrue(txtSmoke.contains("release-smoke-sentinel.txt"))
        assertTrue(txtSmoke.contains("ReadBook.moveToNextPage()"))
        assertTrue(txtSmoke.contains("R.id.view_pager_main"))
        assertTrue(previewSmoke.contains("R.id.tv_cancel"))
        assertTrue(previewSmoke.contains("assertEquals"))
        assertFalse(previewSmoke.contains("R.id.tv_ok"))
    }

    @Test
    fun `导入预览使用测试 APK 的只读 Provider 向目标应用授权`() {
        assertTrue(testManifest.contains("${'$'}{applicationId}.releaseSmokeFixtureProvider"))
        assertTrue(testManifest.contains("android:exported=\"false\""))
        assertTrue(previewSmoke.contains("ReleaseSmokeFixtureDispatchActivity"))
        assertTrue(fixtureProvider.contains("class ReleaseSmokeFixtureProvider"))
        assertTrue(fixtureProvider.contains("if (!\"r\".equals(mode))"))
        assertFalse(fixtureProvider.contains("androidx."))
        assertTrue(fixtureDispatch.contains("io.legado.app.debug"))
        assertTrue(fixtureDispatch.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
        assertFalse(previewSmoke.contains("${'$'}{context.packageName}.fileProvider"))
    }

    @Test
    fun `现有 AndroidX Test 与 Espresso 足够且未增加 UIAutomator`() {
        assertFalse(buildGradle.contains("uiautomator", ignoreCase = true))
        assertFalse(versions.contains("uiautomator", ignoreCase = true))
        assertTrue(versions.contains("androidx-espresso-core"))
        assertTrue(versions.contains("androidx-runner"))
    }

    private fun repoFile(path: String): File = requireNotNull(
        sequenceOf(File("../$path"), File(path)).firstOrNull(File::isFile)
    ) { "找不到 $path" }
}
