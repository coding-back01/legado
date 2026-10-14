package io.legado.app.model.dictionary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.appDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryIdentityIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = LocalDictionaryStore(context)
    private val previousSettings = store.querySettings
    private val previousBackupOption = store.includeFilesInBackup
    private val folder = File(context.cacheDir, "identity-${UUID.randomUUID()}").apply { mkdirs() }
    private val ids = arrayListOf<String>()

    @After fun cleanup() = runBlocking {
        ids.forEach { store.delete(it) }
        store.querySettings = previousSettings
        store.includeFilesInBackup = previousBackupOption
        folder.deleteRecursively()
        Unit
    }

    private suspend fun legacyCopy(): io.legado.app.data.entities.LocalDictionary {
        DictionaryTestFixtures.copy(folder, "sample.mdx", "sample.mdd", "optional.dsl")
        val imported = store.importFiles(folder, "sample.mdx").also { ids.add(it.id) }
        val sources = File(store.directory(imported.id), "sources")
        File(folder, "optional.dsl").copyTo(File(sources, "optional.dsl"), overwrite = true)
        val legacy = imported.copy(sortNumber = 7, identityVersion = 1,
            fingerprint = DictionaryIdentity.legacy(sources, "sample.mdx") {})
        appDb.localDictionaryDao.insert(legacy)
        File(store.directory(imported.id), "identity.json").delete()
        return legacy
    }

    @Test fun validatedIdentityUpgradePreservesIdPreferencesIndexAndOldExtraFiles() = runBlocking {
        val old = legacyCopy()
        store.querySettings = store.querySettings.withLanguage(old.id, "en").withPrimary("en", old.id)
        val settings = store.querySettings
        val index = File(store.directory(old.id), "index.db")
        val modified = index.lastModified()
        assertTrue(store.upgradeIdentity(old.id))
        val current = requireNotNull(appDb.localDictionaryDao.get(old.id))
        assertEquals(2, current.identityVersion)
        assertEquals(old.copy(identityVersion = 2, fingerprint = current.fingerprint), current)
        assertEquals(settings, store.querySettings)
        assertEquals(modified, index.lastModified())
        assertTrue(File(store.directory(old.id), "sources/optional.dsl").isFile)
        assertTrue(File(store.directory(old.id), "identity.json").readText().contains(old.fingerprint))
        assertEquals("go", store.lookup(old.id, "went").single().headword)
    }

    @Test fun cancellationAfterMetadataPublicationRestoresOldIdentityAndKeepsQueryUsable() = runBlocking {
        val old = legacyCopy()
        val cancelling = LocalDictionaryStore(context) {
            if (it == DictionaryOperation.IDENTITY_METADATA_PUBLISHED) throw kotlinx.coroutines.CancellationException("cancelled identity")
        }
        try { cancelling.upgradeIdentity(old.id); fail("应在身份发布时取消") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(old, appDb.localDictionaryDao.get(old.id))
        assertFalse(File(store.directory(old.id), "identity.json").exists())
        assertTrue(File(store.directory(old.id), "sources/optional.dsl").isFile)
        assertEquals("go", store.lookup(old.id, "went").single().headword)
    }

    @Test fun writeFailureRestoresPreviousAuxiliaryMetadataAndSqlRecord() = runBlocking {
        val old = legacyCopy()
        val directory = store.directory(old.id)
        val record = requireNotNull(DictionaryIdentity.upgrade(File(directory, "sources"), old)).record
        val previous = io.legado.app.utils.GSON.toJson(record) + "\n"
        File(directory, "identity.json").writeText(previous)
        val failing = LocalDictionaryStore(context) {
            if (it == DictionaryOperation.IDENTITY_METADATA_PUBLISHED) throw java.io.IOException("injected identity write failure")
        }
        try { failing.upgradeIdentity(old.id); fail("应报告写入错误") } catch (_: java.io.IOException) { }
        assertEquals(old, appDb.localDictionaryDao.get(old.id))
        assertEquals(previous, File(directory, "identity.json").readText())
        assertEquals("go", store.lookup(old.id, "went").single().headword)
    }

    @Test fun unclassifiedResourcesOrInvalidDigestLeaveOldIndexAndRecordUsable() = runBlocking {
        val old = legacyCopy()
        val sources = File(store.directory(old.id), "sources")
        File(sources, "diagram.png").writeText("unclassified resource")
        val updated = old.copy(fingerprint = DictionaryIdentity.legacy(sources, old.sourceFile) {})
        appDb.localDictionaryDao.insert(updated)
        assertFalse(store.upgradeIdentity(old.id))
        assertEquals(updated, appDb.localDictionaryDao.get(old.id))
        File(sources, "sample.mdx").appendText("changed source")
        try { store.upgradeIdentity(old.id); fail("应拒绝摘要不符") } catch (error: DictionaryException) {
            assertEquals(DictionaryProblem.CORRUPT, error.problem)
        }
        assertEquals(updated, appDb.localDictionaryDao.get(old.id))
        assertEquals("go", store.lookup(old.id, "went").single().headword)
        assertFalse(File(store.directory(old.id), "identity.json").exists())
        assertTrue(File(sources, "optional.dsl").isFile)
    }

    @Test fun newGroupReimportReusesMigratedOldIdentityDespiteSiblingChanges() = runBlocking {
        val old = legacyCopy()
        assertTrue(store.upgradeIdentity(old.id))
        store.querySettings = store.querySettings.withLanguage(old.id, "en").withPrimary("en", old.id)
        val settings = store.querySettings
        File(folder, "optional.dsl").appendText("unrelated sibling change")
        val again = store.importFiles(folder, "sample.mdx").also { ids.add(it.id) }
        assertEquals(old.id, again.id)
        assertEquals(2, again.identityVersion)
        assertEquals(7, again.sortNumber)
        assertEquals(settings, store.querySettings)
    }

    @Test fun differentContentWithTheSamePrimaryNameStaysDistinct() = runBlocking {
        val firstFolder = File(folder, "first").apply { mkdirs() }
        DictionaryTestFixtures.copy(firstFolder, "sample.mdx")
        val first = store.importFiles(firstFolder, "sample.mdx").also { ids.add(it.id) }
        val secondFolder = File(folder, "second").apply { mkdirs() }
        DictionaryTestFixtures.copy(secondFolder, "legacy.mdx")
        File(secondFolder, "legacy.mdx").renameTo(File(secondFolder, "sample.mdx"))
        val second = store.importFiles(secondFolder, "sample.mdx").also { ids.add(it.id) }
        assertNotEquals(first.id, second.id)
        assertNotEquals(first.fingerprint, second.fingerprint)
        assertEquals(2, first.identityVersion)
        assertEquals(2, second.identityVersion)
        assertTrue(store.lookup(first.id, "go").isNotEmpty())
        assertTrue(store.lookup(second.id, "go").isNotEmpty())
    }

    @Test fun versionTwoRebuildUsesItsGroupWhilePreservingLanguageAndOldExtraFiles() = runBlocking {
        val old = legacyCopy()
        assertTrue(store.upgradeIdentity(old.id))
        store.querySettings = store.querySettings.withLanguage(old.id, "en").withPrimary("en", old.id)
        val settings = store.querySettings
        store.rebuild(old.id)
        assertEquals(settings, store.querySettings)
        assertEquals(2, appDb.localDictionaryDao.get(old.id)?.identityVersion)
        assertTrue(File(store.directory(old.id), "identity.json").isFile)
        assertTrue(File(store.directory(old.id), "sources/optional.dsl").isFile)
        assertEquals("go", store.lookup(old.id, "went").single().headword)
    }

    @Test fun ordinaryBackupSavesVersionTwoPreferencesAndIdentityWithoutFiles() = runBlocking {
        DictionaryTestFixtures.copy(folder, "sample.mdx")
        val dictionary = store.importFiles(folder, "sample.mdx").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(dictionary.id, "en")
            .withPrimary("en", dictionary.id).withFallbacks("en", listOf("second", "third"))
        store.includeFilesInBackup = false
        val backup = File(folder, "backup")
        store.backup(backup)
        assertFalse(File(backup, LocalDictionaryStore.FILES).exists())
        val manifest = requireNotNull(DictionaryCatalogCodec.decodeBackup(File(backup, LocalDictionaryStore.MANIFEST).readText()))
        assertEquals(2, manifest.version)
        assertEquals(store.querySettings, manifest.querySettings)
        assertEquals(2, manifest.dictionaries.first { it.id == dictionary.id }.identityVersion)
        assertEquals(listOf("sample.mdx"), manifest.identities[dictionary.id]?.group?.files)
    }

    @Test fun oldFullBackupChecksWholeOldCopyThenReusesItsPendingIdAndVerifiedAlias() = runBlocking {
        val old = legacyCopy()
        store.querySettings = store.querySettings.withLanguage(old.id, "en").withPrimary("en", old.id)
        val ordinary = File(folder, "old-ordinary").apply { mkdirs() }
        val full = File(folder, "old-full").apply { mkdirs() }
        val payload = File(File(full, LocalDictionaryStore.FILES), old.id)
        File(store.directory(old.id), "sources").copyRecursively(payload)
        fun manifest(target: File, included: Boolean) {
            val json = com.google.gson.JsonParser.parseString(io.legado.app.utils.GSON.toJson(
                LocalDictionaryStore.BackupManifest(1, old.id, included, listOf(old)))).asJsonObject
            json.remove("querySettings"); json.remove("identities")
            json.getAsJsonArray("dictionaries")[0].asJsonObject.remove("identityVersion")
            File(target, LocalDictionaryStore.MANIFEST).writeText(json.toString())
        }
        manifest(ordinary, false); manifest(full, true)
        store.delete(old.id)
        assertTrue(store.restore(ordinary).isEmpty())
        assertEquals("missing", appDb.localDictionaryDao.get(old.id)?.state)
        assertTrue(store.restore(full).isEmpty())
        val restoredId = requireNotNull(store.lastDictionaryId).also { ids.add(it) }
        assertEquals(old.id, restoredId)
        val restored = requireNotNull(appDb.localDictionaryDao.get(restoredId))
        assertEquals(2, restored.identityVersion)
        assertEquals(7, restored.sortNumber)
        assertTrue(File(store.directory(restoredId), "identity.json").readText().contains(old.fingerprint))
        assertFalse(File(store.directory(restoredId), "sources/optional.dsl").exists())
        assertEquals("go", store.lookup(restoredId, "went").single().headword)
        assertEquals(old.id, store.querySettings.languages["en"]?.primaryId)
    }

    @Test fun fullBackupIncludesOnlyVersionTwoGroupAndKeepsOldExtraFiles() = runBlocking {
        val old = legacyCopy()
        assertTrue(store.upgradeIdentity(old.id))
        store.includeFilesInBackup = true
        val backup = File(folder, "full-group")
        store.backup(backup)
        val payload = File(File(backup, LocalDictionaryStore.FILES), old.id)
        assertEquals(listOf("sample.mdd", "sample.mdx"), payload.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(payload).invariantSeparatorsPath }.sorted().toList())
        assertTrue(File(store.directory(old.id), "sources/optional.dsl").isFile)
    }

    @Test fun versionTwoRestoreRemapsLanguagePreferencesAfterIdConflict() = runBlocking {
        DictionaryTestFixtures.copy(folder, "sample.mdx", "legacy.mdx", "optional.dsl")
        val first = store.importFiles(folder, "sample.mdx").also { ids.add(it.id) }
        val second = store.importFiles(folder, "legacy.mdx").also { ids.add(it.id) }
        val chinese = store.importFiles(folder, "optional.dsl").also { ids.add(it.id) }
        val configured = store.querySettings.withLanguage(first.id, "en").withLanguage(second.id, "en")
            .withLanguage(chinese.id, "zh").withPrimary("en", first.id).withPrimary("zh", chinese.id)
            .withFallbacks("en", listOf(second.id, "unavailable"))
        store.querySettings = configured
        store.lastDictionaryId = first.id
        store.includeFilesInBackup = true
        val backup = File(folder, "mapped-backup")
        store.backup(backup)
        for (dictionary in listOf(first, second, chinese)) store.delete(dictionary.id)
        val collision = first.copy(name = "unrelated pending", fingerprint = "0".repeat(64), state = "missing", entryCount = 0)
        appDb.localDictionaryDao.insert(collision)
        store.querySettings = DictionaryQuerySettings()
        assertTrue(store.restore(backup).isEmpty())
        val restoredFirst = requireNotNull(store.lastDictionaryId).also { ids.add(it) }
        val restoredFingerprints = setOf(first.fingerprint, second.fingerprint, chinese.fingerprint)
        ids.addAll(store.all.filter { it.state == "ready" && it.fingerprint in restoredFingerprints }.map { it.id })
        assertNotEquals(first.id, restoredFirst)
        assertEquals(collision, appDb.localDictionaryDao.get(first.id))
        assertEquals(restoredFirst, store.querySettings.languages["en"]?.primaryId)
        assertEquals(listOf(second.id, "unavailable"), store.querySettings.languages["en"]?.fallbackIds)
        assertEquals(chinese.id, store.querySettings.languages["zh"]?.primaryId)
        assertEquals("en", store.querySettings.languageOverrides[restoredFirst])
        assertEquals("zh", store.querySettings.languageOverrides[chinese.id])
        assertEquals("go", store.lookup(restoredFirst, "go").single().headword)
    }

    @Test fun oldCatalogDoesNotGuessIdentityAndExplicitAssociationKeepsOriginalReferences() = runBlocking {
        val old = legacyCopy()
        val backup = File(folder, "old-catalog").apply { mkdirs() }
        File(backup, LocalDictionaryStore.MANIFEST).writeText(io.legado.app.utils.GSON.toJson(
            LocalDictionaryStore.BackupManifest(1, old.id, false, listOf(old))))
        store.querySettings = store.querySettings.withLanguage(old.id, "en").withPrimary("en", old.id)
        store.delete(old.id)
        assertTrue(store.restore(backup).isEmpty())
        val incoming = store.importFiles(folder, "sample.mdx").also { ids.add(it.id) }
        assertNotEquals(old.id, incoming.id)
        assertEquals("missing", appDb.localDictionaryDao.get(old.id)?.state)
        assertEquals(old.id, store.querySettings.languages["en"]?.primaryId)
        assertTrue(store.associatePending(old.id, incoming.id))
        assertEquals(old.id, store.querySettings.languages["en"]?.primaryId)
        assertEquals(7, appDb.localDictionaryDao.get(old.id)?.sortNumber)
        assertEquals(incoming.fingerprint, appDb.localDictionaryDao.get(old.id)?.fingerprint)
        assertNull(appDb.localDictionaryDao.get(incoming.id))
        assertFalse(store.directory(incoming.id).exists())
        assertEquals("go", store.lookup(old.id, "went").single().headword)
    }

    @Test fun repeatedRestorePreservesNewSettingsAndMissingOrUnknownCatalogPreservesData() = runBlocking {
        DictionaryTestFixtures.copy(folder, "sample.mdx", "legacy.mdx")
        val first = store.importFiles(folder, "sample.mdx").also { ids.add(it.id) }
        val other = store.importFiles(folder, "legacy.mdx").also { ids.add(it.id) }
        store.querySettings = store.querySettings.withLanguage(first.id, "en").withLanguage(other.id, "en").withPrimary("en", first.id)
        store.includeFilesInBackup = false
        val backup = File(folder, "repeated")
        store.backup(backup)
        assertTrue(store.restore(backup).isEmpty())
        store.querySettings = store.querySettings.withPrimary("en", other.id).withFallbacks("en", listOf(first.id))
        val changed = store.querySettings
        assertTrue(store.restore(backup).isEmpty())
        assertEquals(changed, store.querySettings)
        assertTrue(store.restore(File(folder, "without-catalog").apply { mkdirs() }).isEmpty())
        val manifest = File(backup, LocalDictionaryStore.MANIFEST)
        val json = com.google.gson.JsonParser.parseString(manifest.readText()).asJsonObject
        json.addProperty("version", 999)
        manifest.writeText(json.toString())
        assertEquals(listOf("version"), store.restore(backup))
        assertEquals(changed, store.querySettings)
        assertEquals("go", store.lookup(first.id, "go").single().headword)
    }

    @Test fun catalogOnlyRestoreRetainsGroupMetadataAcrossAnotherBackup() = runBlocking {
        DictionaryTestFixtures.copy(folder, "sample.mdx", "sample.mdd")
        val dictionary = store.importFiles(folder, "sample.mdx").also { ids.add(it.id) }
        store.includeFilesInBackup = false
        val first = File(folder, "first-catalog")
        store.backup(first)
        val original = requireNotNull(DictionaryCatalogCodec.decodeBackup(File(first, LocalDictionaryStore.MANIFEST).readText()))
        store.delete(dictionary.id)
        assertTrue(store.restore(first).isEmpty())
        val second = File(folder, "second-catalog")
        store.backup(second)
        val copied = requireNotNull(DictionaryCatalogCodec.decodeBackup(File(second, LocalDictionaryStore.MANIFEST).readText()))
        assertEquals(original.identities[dictionary.id], copied.identities[dictionary.id])
        assertEquals("missing", appDb.localDictionaryDao.get(dictionary.id)?.state)
        assertFalse(store.directory(dictionary.id).exists())
    }
}
