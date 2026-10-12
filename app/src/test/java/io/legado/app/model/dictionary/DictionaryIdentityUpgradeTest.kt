package io.legado.app.model.dictionary

import io.legado.app.data.entities.LocalDictionary
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DictionaryIdentityUpgradeTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun validatedOldCopyUpgradesIdentityWithoutReplacingIdSortOrIndexInformation() {
        val root = temporary.newFolder()
        File(root, "first.mdx").writeText("own body")
        File(root, "second.mdx").writeText("unrelated sibling")
        val oldHash = DictionaryIdentity.legacy(root, "first.mdx") {}
        val dictionary = LocalDictionary("old-id", "title", "MDICT", "first.mdx", oldHash,
            inputLanguage = "English", sortNumber = 7, entryCount = 42)
        val upgrade = requireNotNull(DictionaryIdentity.upgrade(root, dictionary))
        assertEquals(dictionary.copy(fingerprint = upgrade.dictionary.fingerprint, identityVersion = 2), upgrade.dictionary)
        assertEquals(listOf(oldHash), upgrade.record.legacyFingerprints)
        assertEquals(listOf("first.mdx"), upgrade.record.group.files)
        assertEquals(DictionaryIdentity.calculate(root, upgrade.record.group), upgrade.dictionary.fingerprint)
        assertTrue(File(root, "second.mdx").isFile)
    }

    @Test fun cancellationCorruptHashAndUnclassifiedResourcesPreserveOldCopy() {
        val root = temporary.newFolder()
        File(root, "first.mdx").writeText("own body")
        File(root, "diagram.png").writeText("unclassified resource")
        val dictionary = LocalDictionary("old", "title", "MDICT", "first.mdx",
            DictionaryIdentity.legacy(root, "first.mdx") {})
        assertNull(DictionaryIdentity.upgrade(root, dictionary))
        assertThrows(DictionaryException::class.java) { DictionaryIdentity.upgrade(root, dictionary.copy(fingerprint = "0".repeat(64))) }
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            DictionaryIdentity.upgrade(root, dictionary) { throw kotlinx.coroutines.CancellationException("cancelled") }
        }
        assertEquals("own body", File(root, "first.mdx").readText())
        assertEquals("unclassified resource", File(root, "diagram.png").readText())
    }

    @Test fun oldFullBackupIsValidatedByOldAlgorithmBeforeGroupNormalization() {
        val root = temporary.newFolder()
        File(root, "first.mdx").writeText("own body")
        File(root, "second.mdx").writeText("unselected sibling")
        val old = LocalDictionary("old", "title", "MDICT", "first.mdx",
            DictionaryIdentity.legacy(root, "first.mdx") {}, sortNumber = 7)
        val restored = DictionaryIdentity.fromBackup(root, old)
        assertEquals(2, restored.dictionary.identityVersion)
        val record = requireNotNull(restored.record)
        assertEquals(listOf(old.fingerprint), record.legacyFingerprints)
        assertEquals(listOf("first.mdx"), record.group.files)
        assertEquals(DictionaryIdentity.calculate(root, record.group), restored.dictionary.fingerprint)
        assertEquals(old.id, restored.dictionary.id)
        assertEquals(old.sortNumber, restored.dictionary.sortNumber)
    }

    @Test fun versionTwoBackupRejectsFilesOutsideDeclaredGroup() {
        val root = temporary.newFolder()
        File(root, "first.mdx").writeText("own body")
        val group = DictionaryFileGroups.plan(root, "first.mdx")
        val dictionary = LocalDictionary("id", "title", "MDICT", "first.mdx",
            DictionaryIdentity.calculate(root, group), identityVersion = 2)
        File(root, "second.mdx").writeText("unselected payload")
        val error = assertThrows(DictionaryException::class.java) {
            DictionaryIdentity.fromBackup(root, dictionary, DictionaryIdentityRecord(group))
        }
        assertEquals(DictionaryProblem.CORRUPT, error.problem)
    }

    @Test fun explicitPendingRelinkPreservesOldReferencesButUsesVerifiedNewContent() {
        val old = LocalDictionary("old", "old name", "MDICT", "old.mdx", "old-hash", sortNumber = 7, state = "missing")
        val imported = LocalDictionary("new", "new name", "MDICT", "new.mdx", "new-hash", entryCount = 42, identityVersion = 2)
        assertEquals(imported.copy(id = old.id, name = old.name, sortNumber = old.sortNumber),
            DictionaryIdentity.relinkPending(old, imported))
    }
}
