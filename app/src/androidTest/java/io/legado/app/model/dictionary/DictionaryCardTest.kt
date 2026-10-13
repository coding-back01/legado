package io.legado.app.model.dictionary

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaPlayer
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.dict.DictDialog
import io.legado.app.ui.dict.DictSource
import io.legado.app.ui.dict.DictViewModel
import io.legado.app.ui.dict.local.LocalDictionaryActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DictionaryCardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = LocalDictionaryStore(context)
    private val folder = File(context.cacheDir, "card-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val ids = linkedSetOf<String>()
    private val previousSettings = store.querySettings
    private val previous = store.lastDictionaryId
    private val fontScale = context.getPrefInt(PreferKey.fontScale)
    private val night = AppConfig.isNightTheme

    @After fun cleanup() = runBlocking {
        ids.forEach { store.delete(it) }
        store.lastDictionaryId = previous
        store.querySettings = previousSettings
        AppConfig.isNightTheme = night
        context.putPrefInt(PreferKey.fontScale, fontScale)
        folder.deleteRecursively()
        Unit
    }

    private suspend fun dictionary(primary: String, vararg companions: String): io.legado.app.data.entities.LocalDictionary {
        val source = File(folder, UUID.randomUUID().toString()).apply { mkdirs() }
        DictionaryTestFixtures.copy(source, primary, *companions)
        return store.importFiles(source, primary).also { ids.add(it.id) }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(50)
        }
        screenshot("failure")
        println("卡片可访问文本：" + accessibleText())
        assertTrue("词典卡片没有在期限内达到预期状态", condition())
    }

    private fun screenshot(label: String) {
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, "dictionary-card-api${android.os.Build.VERSION.SDK_INT}-$label.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val automation = instrumentation.uiAutomation
        val service = automation.serviceInfo
        if (service.flags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS == 0) {
            service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = service
        }
        val roots = automation.windows.mapNotNull { it.root }.ifEmpty { listOfNotNull(automation.rootInActiveWindow) }
        val nodes = arrayListOf<AccessibilityNodeInfo>()
        fun collect(node: AccessibilityNodeInfo) {
            nodes.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::collect)
        }
        roots.forEach(::collect)
        return nodes
    }

    private fun accessibleText() = nodes().joinToString(" ") { "${it.text?.toString().orEmpty()} ${it.contentDescription?.toString().orEmpty()}" }

    private var linkMergeGap = 6

    private fun linkBounds(): List<Rect> {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val groups = arrayListOf<Rect>()
        // 夹具的正文链接位于左侧；排除系统状态栏与导航栏，不依赖窗口的兼容缩放坐标。
        for (y in bitmap.height / 10 until bitmap.height * 9 / 10) {
            var left = bitmap.width
            var right = 0
            for (x in 0 until bitmap.width / 3) {
                val color = bitmap.getPixel(x, y)
                if (android.graphics.Color.blue(color) > 220 && android.graphics.Color.red(color) in 40..115 && android.graphics.Color.green(color) in 100..185) {
                    left = minOf(left, x); right = maxOf(right, x)
                }
            }
            if (right > left) {
                val last = groups.lastOrNull()
                if (last != null && y - last.bottom <= linkMergeGap) last.union(left, y, right + 1, y + 1)
                else groups.add(Rect(left, y, right + 1, y + 1))
            }
        }
        bitmap.recycle()
        return groups
    }

    private fun clickText(text: String, scenario: ActivityScenario<LocalDictionaryActivity>) {
        scenario.onActivity {
            val web = card(scenario).requireView().findViewById<WebView>(R.id.web_view)
            @Suppress("DEPRECATION")
            val scale = web.scale.coerceAtLeast(1f)
            linkMergeGap = (18 * web.resources.configuration.fontScale * scale / 2).toInt().coerceAtLeast(6)
        }
        var groups: List<Rect> = emptyList()
        val count = if (text == "missing") 1 else 2
        waitUntil { groups = linkBounds(); groups.size == count }
        val bounds = groups[if (text == "play" || text == "missing") 0 else 1]
        println("实际点击 $text：$bounds，全部链接 $groups")
        screenshot("tap-$text")
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(80)
        }
    }

    private fun card(scenario: ActivityScenario<LocalDictionaryActivity>): DictDialog {
        val result = AtomicReference<DictDialog>()
        scenario.onActivity { result.set(it.supportFragmentManager.fragments.filterIsInstance<DictDialog>().last()) }
        return result.get()
    }

    private fun open(scenario: ActivityScenario<LocalDictionaryActivity>, word: String, language: String? = null) {
        scenario.onActivity { DictDialog(word, language, if (language == null) store.lastDictionaryId else null).show(it.supportFragmentManager, "card-test") }
    }

    private fun player(dialog: DictDialog): MediaPlayer? =
        DictDialog::class.java.getDeclaredField("player").apply { isAccessible = true }.get(dialog) as MediaPlayer?

    @Test fun reliableDefinitionShowsNativePreviewThenExpandsToFullArticle() = runBlocking {
        val dictionary = dictionary("dictd.index", "dictd.dict.dz")
        store.lastDictionaryId = dictionary.id
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            open(scenario, "学习")
            waitUntil {
                var ready = false
                scenario.onActivity {
                    val view = card(scenario).requireView()
                    val text = view.findViewById<TextView>(R.id.tv_dict)
                    ready = text.visibility == android.view.View.VISIBLE && text.text.contains("知识") &&
                        view.findViewById<WebView>(R.id.web_view).visibility != android.view.View.VISIBLE
                }
                ready
            }
            scenario.onActivity { card(scenario).requireView().findViewById<android.widget.Button>(R.id.btn_expand).performClick() }
            waitUntil {
                var full = false
                scenario.onActivity { full = card(scenario).requireView().findViewById<WebView>(R.id.web_view).visibility == android.view.View.VISIBLE }
                full
            }
        }
    }

    @Test fun longSummaryShowsOmissionNoticeAndFullArticleIsStillScrollable() = runBlocking {
        val source = File(folder, UUID.randomUUID().toString()).apply { mkdirs() }
        val body = "详细释义".repeat(3000) + " full article sentinel"
        File(source, "summary.dict").writeText(body)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        var length = body.toByteArray().size
        var encoded = ""
        while (length > 0) { encoded = alphabet[length % 64] + encoded; length /= 64 }
        File(source, "summary.index").writeText("long-summary\tA\t$encoded\n")
        val dictionary = store.importFiles(source, "summary.index").also { ids.add(it.id) }
        store.lastDictionaryId = dictionary.id
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            open(scenario, "long-summary")
            waitUntil {
                var omitted = false
                scenario.onActivity {
                    val view = card(scenario).requireView()
                    omitted = view.findViewById<TextView>(R.id.tv_warning).text.toString() == context.getString(R.string.dictionary_summary_truncated) &&
                        view.findViewById<TextView>(R.id.tv_dict).text.length <= 480
                }
                omitted
            }
            scenario.onActivity { card(scenario).requireView().findViewById<android.view.View>(R.id.btn_expand).performClick() }
            waitUntil {
                var full = false
                scenario.onActivity {
                    val web = card(scenario).requireView().findViewById<WebView>(R.id.web_view)
                    full = web.isShown && web.contentHeight * resourcesDensity(web) > web.height
                }
                full
            }
            assertTrue(store.lookup(dictionary.id, "long-summary").single().html.contains("full article sentinel"))
        }
    }

    @Test fun localCardRendersResourcesNavigatesAndStopsManualAudio() = runBlocking {
        val dictionary = dictionary("media.mdx", "media.mdd")
        store.lastDictionaryId = dictionary.id
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            open(scenario, "media")
            waitForCard(scenario, "media")
            assertTrue(store.lookup(dictionary.id, "media").single().html.contains("音标"))
            val image = android.graphics.BitmapFactory.decodeFile(store.resource(dictionary.id, "diagram.png").path)
            assertNotNull(image)
            requireNotNull(image).recycle()
            scenario.onActivity {
                val web = card(scenario).requireView().findViewById<WebView>(R.id.web_view)
                assertFalse(web.settings.javaScriptEnabled)
                assertFalse(web.settings.allowFileAccess)
                assertTrue(web.settings.blockNetworkLoads)
                assertNull(player(card(scenario)))
            }
            clickText("other", scenario)
            waitForCard(scenario, "other"); screenshot("linked-entry")
            clickText("missing", scenario)
            waitUntil { var missing = false; scenario.onActivity { missing = card(scenario).requireView().findViewById<TextView>(R.id.tv_dict).text.toString() == context.getString(R.string.dictionary_not_found) }; missing }
            scenario.onActivity { card(scenario).requireView().findViewById<android.view.View>(R.id.btn_back).performClick() }
            waitForCard(scenario, "other")
            scenario.onActivity { card(scenario).requireView().findViewById<android.view.View>(R.id.btn_back).performClick() }
            waitForCard(scenario, "media")
            clickText("play", scenario)
            waitUntil {
                var playing = false
                scenario.onActivity { playing = player(card(scenario))?.isPlaying == true }
                playing
            }
            scenario.onActivity { card(scenario).requireView().findViewById<android.view.View>(R.id.btn_expand).performClick() }
            screenshot("media")
            val dialog = card(scenario)
            scenario.onActivity { dialog.dismiss() }
            waitUntil { dialog.view == null }
            assertNull(player(dialog))
            assertEquals(dictionary.id, store.lastDictionaryId)
        }
    }

    @Test fun switchingAndMissesPersistWhileCancelledQueriesCannotDeliverLateResults() = runBlocking {
        val first = dictionary("media.mdx", "media.mdd")
        val second = dictionary("legacy.mdx")
        store.lastDictionaryId = first.id
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            open(scenario, "media")
            waitForCard(scenario, "media")
            clickText("play", scenario)
            waitUntil { var playing = false; scenario.onActivity { playing = player(card(scenario))?.isPlaying == true }; playing }
            scenario.onActivity { card(scenario).requireView().findViewById<android.view.View>(R.id.btn_dictionary).performClick() }
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(second.name)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click())
            waitUntil { var missing = false; scenario.onActivity { missing = card(scenario).requireView().findViewById<TextView>(R.id.tv_dict).text.toString() == context.getString(R.string.dictionary_not_found) }; missing }
            assertEquals(first.id, store.lastDictionaryId)
            assertNull(player(card(scenario)))
            val model = AtomicReference<DictViewModel>()
            var delivered = ""
            scenario.onActivity {
                model.set(DictViewModel(context.applicationContext as android.app.Application))
                model.get().dict(DictSource(local = first), "media", { delivered = "old" }, { delivered = "old-error" })
                model.get().dict(DictSource(local = second), "go", { delivered = it.entries.first().headword }, { delivered = "error" })
            }
            waitUntil { delivered == "go" }
            scenario.onActivity {
                model.get().dict(DictSource(local = first), "media", { delivered = "closed" }, { delivered = "closed-error" })
                model.get().cancelQuery()
            }
            SystemClock.sleep(300)
            assertEquals("go", delivered)
            scenario.onActivity { card(scenario).dismiss() }
            waitUntil { scenario.onActivity { }; accessibleText().contains("meaning").not() }
            open(scenario, "media")
            waitForCard(scenario, "media")
            scenario.onActivity { card(scenario).dismiss() }
        }
        assertEquals(first.id, LocalDictionaryStore(context).lastDictionaryId)
    }

    @Test fun firstUseAndDeletedHistoryRequireAnExplicitChoiceWithoutOnlineFallback() = runBlocking {
        val first = dictionary("legacy.mdx")
        assertEquals("界面测试需使用仅有测试词典的可丢弃 Debug 环境", 1, store.all.count { it.state == "ready" })
        store.lastDictionaryId = null
        store.querySettings = DictionaryQuerySettings().withLanguage(first.id, "en")
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            open(scenario, "go", "en")
            waitForCard(scenario, "go")
            assertNull(store.querySettings.languages["en"]?.primaryId)
            scenario.onActivity { card(scenario).dismiss() }
        }
        val other = dictionary("media.mdx", "media.mdd")
        store.querySettings = store.querySettings.withLanguage(other.id, "en")
        store.lastDictionaryId = null
        for (historical in listOf(false, true)) {
            store.querySettings = store.querySettings.withPrimary("en", if (historical) UUID.randomUUID().toString() else null)
            val expected = context.getString(if (historical) R.string.dictionary_previous_unavailable else R.string.dictionary_select_prompt)
            ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
                open(scenario, "go", "en")
                waitUntil { var prompted = false; scenario.onActivity { prompted = card(scenario).requireView().findViewById<TextView>(R.id.tv_dict).text.toString() == expected }; prompted }
                scenario.onActivity {
                    assertFalse(card(scenario).requireView().findViewById<WebView>(R.id.web_view).isShown)
                    card(scenario).dismiss()
                }
            }
        }
    }

    @Test fun longDefinitionsRemainScrollableAcrossThemesAndRotation() = runBlocking {
        val dictionary = dictionary("media.mdx", "media.mdd")
        store.lastDictionaryId = dictionary.id
        for (dark in listOf(false, true)) {
            AppConfig.isNightTheme = dark
            context.putPrefInt(PreferKey.fontScale, if (dark) 14 else 10)
            ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
                scenario.onActivity { assertEquals(if (dark) 1.4f else 1f, it.resources.configuration.fontScale, 0.01f); it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                waitUntil { var portrait = false; scenario.onActivity { portrait = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }; portrait }
                SystemClock.sleep(300)
                open(scenario, "long")
                waitForCard(scenario, "long")
                scenario.onActivity { card(scenario).requireView().findViewById<android.view.View>(R.id.btn_expand).performClick() }
                waitUntil {
                    var scrollable = false
                    scenario.onActivity {
                        val web = card(scenario).requireView().findViewById<WebView>(R.id.web_view)
                        scrollable = web.contentHeight * resourcesDensity(web) > web.height
                    }
                    scrollable
                }
                scenario.onActivity { card(scenario).requireView().findViewById<WebView>(R.id.web_view).pageDown(true) }
                waitUntil { var bottom = false; scenario.onActivity { bottom = card(scenario).requireView().findViewById<WebView>(R.id.web_view).scrollY > 0 }; bottom }
                screenshot(if (dark) "night-long" else "day-long")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                waitForCard(scenario, "long")
                scenario.onActivity {
                    val query = card(scenario).requireView().findViewById<TextView>(R.id.tv_query)
                    assertTrue(query.text.contains("long"))
                    assertEquals(context.getString(R.string.dictionary_collapse), card(scenario).requireView().findViewById<android.widget.Button>(R.id.btn_expand).text.toString())
                    card(scenario).requireView().findViewById<WebView>(R.id.web_view).pageDown(true)
                }
                waitUntil { var bottom = false; scenario.onActivity { bottom = card(scenario).requireView().findViewById<WebView>(R.id.web_view).scrollY > 0 }; bottom }
                screenshot(if (dark) "night-landscape" else "day-landscape")
                scenario.onActivity { card(scenario).dismiss(); it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                waitUntil { var portrait = false; scenario.onActivity { portrait = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }; portrait }
            }
        }
    }

    private fun waitForCard(scenario: ActivityScenario<LocalDictionaryActivity>, word: String) {
        waitUntil {
            var rendered = false
            scenario.onActivity {
                val view = it.supportFragmentManager.fragments.filterIsInstance<DictDialog>().lastOrNull()?.view
                    ?: return@onActivity
                val web = view.findViewById<WebView>(R.id.web_view)
                rendered = view.findViewById<TextView>(R.id.tv_query).text.toString() == word && web.isShown && web.contentHeight > 0
            }
            rendered
        }
        val painted = java.util.concurrent.CountDownLatch(1)
        scenario.onActivity {
            val web = card(scenario).requireView().findViewById<WebView>(R.id.web_view)
            if (android.os.Build.VERSION.SDK_INT >= 23) web.postVisualStateCallback(1,
                object : WebView.VisualStateCallback() { override fun onComplete(id: Long) { painted.countDown() } })
            else painted.countDown()
        }
        assertTrue("全文须完成绘制再交互", painted.await(5, java.util.concurrent.TimeUnit.SECONDS))
        SystemClock.sleep(150)
    }

    private fun resourcesDensity(web: WebView) = web.resources.displayMetrics.density
}
