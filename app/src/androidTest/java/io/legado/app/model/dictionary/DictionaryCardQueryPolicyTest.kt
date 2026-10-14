package io.legado.app.model.dictionary

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.dict.DictDialog
import io.legado.app.ui.dict.local.LocalDictionaryActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryCardQueryPolicyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = LocalDictionaryStore(context)
    private val settings = store.querySettings
    private val selected = store.lastDictionaryId
    private val root = File(context.cacheDir, "card-policy-${UUID.randomUUID()}").apply { mkdirs() }
    private val ids = arrayListOf<String>()
    @After fun cleanup() = runBlocking { ids.forEach { store.delete(it) }; store.querySettings = settings; store.lastDictionaryId = selected; root.deleteRecursively(); Unit }
    private fun waitUntil(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < end) { instrumentation.waitForIdleSync(); if (check()) return; SystemClock.sleep(30) }
        assertTrue("词典查询策略未在界面生效", check())
    }

    @Test fun dialogLabelsFallbackAndOnlyExplicitSetPrimaryChangesPreference() = runBlocking {
        DictionaryTestFixtures.copy(root, "optional.dsl", "sample.mdx")
        val primary = store.importFiles(root, "optional.dsl").also { ids.add(it.id) }
        val fallback = store.importFiles(root, "sample.mdx").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(primary.id, "en").withLanguage(fallback.id, "en")
            .withPrimary("en", primary.id).withFallbacks("en", listOf(fallback.id))
        store.lastDictionaryId = null
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            scenario.onActivity { DictDialog("go", "en").show(it.supportFragmentManager, "policy") }
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    val dialog = activity.supportFragmentManager.fragments.filterIsInstance<DictDialog>().lastOrNull()
                    ready = dialog?.view?.findViewById<android.widget.Button>(R.id.btn_dictionary)?.text.toString() == fallback.name
                }; ready
            }
            assertEquals(primary.id, store.querySettings.languages["en"]?.primaryId)
            scenario.onActivity { activity ->
                val dialog = activity.supportFragmentManager.fragments.filterIsInstance<DictDialog>().last()
                dialog.requireView().findViewById<View>(R.id.btn_more).performClick()
            }
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.dictionary_set_primary))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup())
                .perform(androidx.test.espresso.action.ViewActions.click())
            waitUntil { store.querySettings.languages["en"]?.primaryId == fallback.id }
        }
    }

    @Test fun unknownLanguagePromptsRatherThanUsingGlobalLastSource() = runBlocking {
        DictionaryTestFixtures.copy(root, "sample.mdx")
        val dictionary = store.importFiles(root, "sample.mdx").also { ids.add(it.id) }
        store.lastDictionaryId = dictionary.id
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            scenario.onActivity { DictDialog("word").show(it.supportFragmentManager, "unknown") }
            waitUntil {
                var prompt = false
                scenario.onActivity { activity ->
                    val view = activity.supportFragmentManager.fragments.filterIsInstance<DictDialog>().lastOrNull()?.view
                    prompt = view?.findViewById<TextView>(R.id.tv_dict)?.text.toString() == context.getString(R.string.dictionary_language_unknown)
                }; prompt
            }
        }
    }

    @Test fun transformedMatchShowsActualHeadwordAndMatchExplanation() = runBlocking {
        DictionaryTestFixtures.copy(root, "dictd.index", "dictd.dict.dz")
        val dictionary = store.importFiles(root, "dictd.index").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "en").withPrimary("en", dictionary.id)
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            scenario.onActivity { DictDialog("gone", "en").show(it.supportFragmentManager, "match") }
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    val view = activity.supportFragmentManager.fragments.filterIsInstance<DictDialog>().lastOrNull()?.view
                    ready = view?.findViewById<TextView>(R.id.tv_dict)?.text?.contains("移动") == true
                }; ready
            }
            val label = context.resources.getIdentifier("dictionary_match_english_form", "string", context.packageName)
            assertNotEquals("词形匹配需要可见说明", 0, label)
            scenario.onActivity { activity ->
                val view = activity.supportFragmentManager.fragments.filterIsInstance<DictDialog>().last().requireView()
                val title = view.findViewById<TextView>(R.id.tv_query).text.toString() + view.findViewById<TextView>(R.id.tv_match).text.toString()
                assertTrue(title.contains("gone")); assertTrue(title.contains("go"))
                assertTrue(title.contains(context.getString(label)))
            }
        }
        Unit
    }

    @Test fun entryLimitIsVisibleAndDoesNotBecomeFallbackOrNotFound() = runBlocking {
        File(root, "limit.dsl").writeText("#NAME \"界面限额夹具\"\n" +
            (0..DictionaryLimits.MAX_CANDIDATES).joinToString("\n") { "same\n\tsense $it\n" })
        val dictionary = store.importFiles(root, "limit.dsl").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "en").withPrimary("en", dictionary.id)
            .withFallbacks("en", listOf("unavailable-fallback"))
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            scenario.onActivity { DictDialog("same", "en").show(it.supportFragmentManager, "limit") }
            waitUntil {
                var visible = false
                scenario.onActivity { activity ->
                    val view = activity.supportFragmentManager.fragments.filterIsInstance<DictDialog>().lastOrNull()?.view
                    visible = view?.findViewById<TextView>(R.id.tv_dict)?.text.toString() == context.getString(R.string.dictionary_limit)
                }; visible
            }
            assertEquals(dictionary.id, store.querySettings.languages["en"]?.primaryId)
        }
        Unit
    }
}
