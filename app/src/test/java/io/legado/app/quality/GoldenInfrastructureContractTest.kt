package io.legado.app.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoldenInfrastructureContractTest {

    private val repositoryRoot = requireNotNull(
        File(requireNotNull(System.getProperty("user.dir"))).parentFile
    )
    private val screenshot = repositoryRoot.resolve(
        "app/src/androidTest/java/io/legado/app/quality/GoldenScreenshot.kt"
    ).readText()
    private val matrix = repositoryRoot.resolve("scripts/android-golden-matrix.tsv").readText()
    private val runner = repositoryRoot.resolve("scripts/run-android-golden-profile.sh").readText()
    private val fixture = repositoryRoot.resolve(
        "app/src/debug/java/io/legado/app/quality/GoldenFixtureActivity.kt"
    ).readText()

    @Test
    fun goldenUpdatesRequireExplicitInstrumentationArgument() {
        assertTrue(screenshot.contains("legado.golden.update"))
        assertTrue(screenshot.contains("toBoolean()"))
        assertTrue(screenshot.contains("golden-updates"))
        assertTrue(screenshot.contains("getWindowVisibleDisplayFrame"))
        assertFalse(screenshot.contains("app/src/androidTest/assets"))
        assertTrue(runner.contains("--update"))
        assertTrue(runner.contains("legado.golden.update"))
        assertTrue(runner.contains("--rss-interaction"))
        assertTrue(runner.contains("RssItemInteractionInstrumentedTest"))
        assertTrue(runner.contains("--overdraw-solid-update"))
        assertTrue(runner.contains("OverdrawSolidGoldenInstrumentedTest"))
        assertTrue(runner.contains("--overdraw-interaction"))
        assertTrue(runner.contains("OverdrawInteractionInstrumentedTest"))
        assertTrue(runner.contains("--manga-layout"))
        assertTrue(runner.contains("MangaMenuLayoutInstrumentedTest"))
        assertTrue(runner.contains("--file-path-layout"))
        assertTrue(runner.contains("FilePathLayoutInstrumentedTest"))
        assertTrue(runner.contains("--launcher3-update"))
        assertTrue(runner.contains("Launcher3GoldenInstrumentedTest"))
        assertTrue(runner.contains("exec-out run-as io.legado.app.debug"))
    }

    @Test
    fun matrixPinsRequiredApiThemeDensityAndDeviceKinds() {
        listOf("21", "23", "36").forEach { api ->
            assertTrue("缺少 API $api", matrix.lineSequence().any { it.startsWith("$api\t") })
        }
        assertTrue(matrix.contains("\tlight\t"))
        assertTrue(matrix.contains("\tdark\t"))
        assertTrue(matrix.contains("\t160\t"))
        assertTrue(matrix.contains("\t640\t"))
        assertTrue(matrix.lineSequence().any { it.endsWith("\tphone") })
        assertTrue(matrix.lineSequence().any { it.endsWith("\ttablet") })
        assertTrue(matrix.lineSequence().any { it.endsWith("\tmultiwindow") })
    }

    @Test
    fun runnerGuardsEmulatorAndDebugPackageBoundary() {
        assertTrue(runner.contains("ro.kernel.qemu"))
        assertTrue(runner.contains("io.legado.app.debug"))
        assertTrue(runner.contains("io.legado.app.debug.test"))
        assertFalse(runner.contains("io.legado.app.release"))
        assertTrue(runner.contains("font_scale 1.0"))
        assertTrue(runner.contains("window_animation_scale 0"))
        assertTrue(runner.contains("immersive_mode_confirmations confirmed"))
        assertTrue(runner.contains("-skin \"\$window\""))
        assertTrue(runner.contains("init.svc.bootanim"))
        assertTrue(runner.contains("service check activity"))
        assertTrue(runner.contains("service check window"))
        assertTrue(runner.contains("service check package"))
        assertTrue(runner.contains("display_configuration_matches"))
        assertTrue(runner.contains("existing_emulator_serials"))
        assertTrue(runner.contains("find_new_emulator_serial"))
        assertTrue(runner.contains("kill -0 \"\$emulator_pid\""))
        assertTrue(fixture.contains("FLAG_FULLSCREEN"))
        assertTrue(fixture.contains("SYSTEM_UI_FLAG_HIDE_NAVIGATION"))
        assertTrue(fixture.contains("onWindowFocusChanged"))
        assertTrue(fixture.contains("showFixture"))
        assertTrue(fixture.contains("createBitmapPreview"))
        assertTrue(fixture.contains("densitySampleSize"))
        assertTrue(fixture.contains("inSampleSize"))
        assertTrue(fixture.contains("minOf(\n            1f,"))
        assertTrue(fixture.contains("source.recycle()"))
    }
}
