package io.legado.app.model.dictionary

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import org.hamcrest.Matchers.`is`
import io.legado.app.ui.dict.local.LocalDictionaryActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryManagementTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = LocalDictionaryStore(context)
    private val oldSettings = store.querySettings
    private val root = File(context.cacheDir, "management-${UUID.randomUUID()}").apply { mkdirs() }
    private val ids = arrayListOf<String>()
    @After fun cleanup() = runBlocking { ids.forEach { store.delete(it) }; store.querySettings = oldSettings; root.deleteRecursively(); Unit }
    private fun text(key: String): String {
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        assertNotEquals("缺少管理操作 $key", 0, id)
        return context.getString(id)
    }
    private fun waitUntil(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 8000
        while (SystemClock.elapsedRealtime() < end) { if (check()) return; SystemClock.sleep(40) }
        assertTrue(check())
    }
    @Test fun managementClassifiesLanguageAndSetsPrimaryAndOrderedFallback() = runBlocking {
        DictionaryTestFixtures.copy(root, "sample.mdx", "legacy.mdx")
        val first = store.importFiles(root, "sample.mdx").also { ids.add(it.id) }
        val second = store.importFiles(root, "legacy.mdx").also { ids.add(it.id) }
        store.querySettings = DictionaryQuerySettings()
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            lateinit var window: android.view.View
            scenario.onActivity { window = it.window.decorView }
            fun row(name: String) = onView(withText(name)).inRoot(withDecorView(`is`(window))).perform(click())
            row(first.name)
            onView(withText(text("dictionary_set_language"))).perform(click())
            onView(withText(text("dictionary_language_en"))).perform(click())
            waitUntil { store.querySettings.languageOverrides[first.id] == "en" }
            row(first.name)
            onView(withText(text("dictionary_set_primary"))).perform(click())
            waitUntil { store.querySettings.languages["en"]?.primaryId == first.id }
            row(second.name)
            onView(withText(text("dictionary_set_language"))).perform(click())
            onView(withText(text("dictionary_language_en"))).perform(click())
            row(second.name)
            onView(withText(text("dictionary_toggle_fallback"))).perform(click())
            waitUntil { store.querySettings.languages["en"]?.fallbackIds == listOf(second.id) }
            assertEquals(first.id, store.querySettings.languages["en"]?.primaryId)
            onView(withText(org.hamcrest.Matchers.containsString(text("dictionary_primary_role"))))
                .inRoot(withDecorView(`is`(window)))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            onView(withText(org.hamcrest.Matchers.containsString(text("dictionary_fallback_role"))))
                .inRoot(withDecorView(`is`(window)))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(context.filesDir, "dictionary-management.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test fun pendingAssociationRequiresConfirmationAndKeepsOriginalReferences() = runBlocking {
        DictionaryTestFixtures.copy(root, "sample.mdx")
        val source = store.importFiles(root, "sample.mdx").also { ids.add(it.id) }
        val pending = source.copy(id = UUID.randomUUID().toString(), name = "明确关联待导入夹具", sortNumber = 17,
            fingerprint = "a".repeat(64), identityVersion = 1, state = "missing")
        ids.add(pending.id)
        io.legado.app.data.appDb.localDictionaryDao.insert(pending)
        store.querySettings = store.querySettings.withLanguage(pending.id, "en").withPrimary("en", pending.id)
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            lateinit var window: android.view.View
            scenario.onActivity { window = it.window.decorView }
            onView(withText(pending.name)).inRoot(withDecorView(`is`(window))).perform(click())
            onView(withText(text("dictionary_associate_pending"))).perform(click())
            onView(withText(source.name)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            assertEquals("missing", io.legado.app.data.appDb.localDictionaryDao.get(pending.id)?.state)
            onView(withText(context.getString(io.legado.app.R.string.ok))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            waitUntil { io.legado.app.data.appDb.localDictionaryDao.get(pending.id)?.state == "ready" }
            assertNull(io.legado.app.data.appDb.localDictionaryDao.get(source.id))
            assertEquals(pending.name, io.legado.app.data.appDb.localDictionaryDao.get(pending.id)?.name)
            assertEquals(17, io.legado.app.data.appDb.localDictionaryDao.get(pending.id)?.sortNumber)
            assertEquals(pending.id, store.querySettings.languages["en"]?.primaryId)
            assertEquals("go", store.lookup(pending.id, "went").single().headword)
        }
        Unit
    }

    @Test fun oldResourcesUpgradeOnlyAfterExplicitResourceSelection() = runBlocking {
        DictionaryTestFixtures.copy(root, "sample.mdx", "sample.mdd")
        val imported = store.importFiles(root, "sample.mdx").also { ids.add(it.id) }
        val sources = File(store.directory(imported.id), "sources")
        File(sources, "shared.png").outputStream().use { out ->
            val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle()
        }
        val legacy = imported.copy(identityVersion = 1, fingerprint = DictionaryIdentity.legacy(sources, imported.sourceFile) {})
        io.legado.app.data.appDb.localDictionaryDao.insert(legacy)
        File(store.directory(imported.id), "identity.json").delete()
        ActivityScenario.launch(LocalDictionaryActivity::class.java).use { scenario ->
            lateinit var window: android.view.View
            scenario.onActivity { window = it.window.decorView }
            onView(withText(imported.name)).inRoot(withDecorView(`is`(window))).perform(click())
            onView(withText(text("dictionary_confirm_resources"))).perform(click())
            onView(withText("shared.png")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            assertEquals(1, io.legado.app.data.appDb.localDictionaryDao.get(imported.id)?.identityVersion)
            onView(withText(context.getString(io.legado.app.R.string.ok))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            waitUntil { io.legado.app.data.appDb.localDictionaryDao.get(imported.id)?.identityVersion == 2 }
            val record = io.legado.app.utils.GSON.fromJson(File(store.directory(imported.id), "identity.json").readText(), DictionaryIdentityRecord::class.java)
            assertTrue(record.group.files.contains("shared.png"))
            assertTrue(File(sources, "shared.png").isFile)
            assertEquals("go", store.lookup(imported.id, "went").single().headword)
        }
        Unit
    }
}
