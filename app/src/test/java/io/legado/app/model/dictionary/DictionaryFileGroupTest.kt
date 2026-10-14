package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 自建文件内容沿用仓库 GPL-3.0 许可；不包含用户词库。 */
class DictionaryFileGroupTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun files(vararg names: String): File = temporary.newFolder().also { root ->
        names.forEach { name -> File(root, name).apply { parentFile?.mkdirs(); writeText("content:$name") } }
    }

    @Test fun choosingOneDictionaryExcludesItsSiblingAndUnrelatedFiles() {
        val root = files("first.mdx", "second.mdx", "second.mdd", "notes.txt")
        assertEquals(listOf("first.mdx"), DictionaryFileGroups.plan(root, "first.mdx").files)
    }

    @Test fun mdictKeepsItsResourceVolumesAndStyleButNotAnotherDictionaryVolumes() {
        val root = files("first.mdx", "first.mdd", "first.1.mdd", "first.css", "second.mdx", "second.mdd", "second.css")
        assertEquals(listOf("first.1.mdd", "first.css", "first.mdd", "first.mdx"),
            DictionaryFileGroups.plan(root, "first.mdx").files)
    }

    @Test fun stardictKeepsCompressedIndexBodySynonymsAndNamedResourceArchive() {
        val root = files("first.ifo", "first.idx.gz", "first.dict.dz", "first.syn", "first.res.zip",
            "second.ifo", "second.idx", "second.dict", "second.res.zip")
        assertEquals(listOf("first.dict.dz", "first.idx.gz", "first.ifo", "first.res.zip", "first.syn"),
            DictionaryFileGroups.plan(root, "first.ifo").files)
    }

    @Test fun stardictRequiresBothIndexAndBodyBeforePublication() {
        val noBody = files("first.ifo", "first.idx")
        assertEquals(DictionaryProblem.MISSING_FILE,
            assertThrows(DictionaryException::class.java) { DictionaryFileGroups.plan(noBody, "first.ifo") }.problem)
        val noIndex = files("first.ifo", "first.dict")
        assertEquals(DictionaryProblem.MISSING_FILE,
            assertThrows(DictionaryException::class.java) { DictionaryFileGroups.plan(noIndex, "first.ifo") }.problem)
    }

    @Test fun compressedDslAssociatesBothSupportedArchiveNames() {
        val root = files("first.dsl.dz", "first.dsl.files.zip", "first.dsl.dz.files.zip",
            "second.dsl", "second.dsl.files.zip")
        assertEquals(listOf("first.dsl.dz", "first.dsl.dz.files.zip", "first.dsl.files.zip"),
            DictionaryFileGroups.plan(root, "first.dsl.dz").files)
    }

    @Test fun dictdIncludesItsCompressedBodyAndRejectsMissingBody() {
        val root = files("first.index", "first.dict.dz", "second.index", "second.dict")
        assertEquals(listOf("first.dict.dz", "first.index"), DictionaryFileGroups.plan(root, "first.index").files)
        val missing = files("first.index", "second.dict")
        assertEquals(DictionaryProblem.MISSING_FILE,
            assertThrows(DictionaryException::class.java) { DictionaryFileGroups.plan(missing, "first.index") }.problem)
    }

    @Test fun selfContainedFormatsAndOptionalMissingResourcesKeepReadablePrimary() {
        for (primary in listOf("first.mobi", "first.prc", "first.azw", "first.bgl", "first.xdxf", "first.xdxf.dz", "first.mdx", "first.dsl")) {
            val root = files(primary, "second.mdx", "notes.txt")
            assertEquals(primary, listOf(primary), DictionaryFileGroups.plan(root, primary).files)
        }
        val root = files("first.ifo", "first.idx", "first.dict")
        assertEquals(listOf("first.dict", "first.idx", "first.ifo"), DictionaryFileGroups.plan(root, "first.ifo").files)
    }

    @Test fun unassignedResourcesAreReportedWithoutCopyingTheParentFolder() {
        val root = files("first.mdx", "second.mdx", "shared/diagram.png", "tone.wav", "notes.txt")
        val plan = DictionaryFileGroups.plan(root, "first.mdx")
        assertEquals(listOf("first.mdx"), plan.files)
        assertEquals(listOf("shared/diagram.png", "tone.wav"), plan.unassignedResources)
    }

    @Test fun explicitlySharedResourcesBelongToEachIndependentFileGroup() {
        val root = files("first.mdx", "second.mdx", "shared/diagram.png", "tone.wav")
        for (primary in listOf("first.mdx", "second.mdx")) {
            val plan = DictionaryFileGroups.plan(root, primary, setOf("shared/diagram.png"))
            assertEquals(listOf(primary, "shared/diagram.png"), plan.files)
            assertEquals(listOf("tone.wav"), plan.unassignedResources)
        }
    }

    @Test fun associationCannotFollowSymlinksEvenWithinAuthorizedDirectory() {
        val root = files("first.mdx", "tone.wav")
        java.nio.file.Files.createSymbolicLink(File(root, "linked.wav").toPath(), File(root, "tone.wav").toPath())
        assertEquals(DictionaryProblem.INVALID_PATH, assertThrows(DictionaryException::class.java) {
            DictionaryFileGroups.plan(root, "first.mdx", setOf("linked.wav"))
        }.problem)
    }

    @Test fun associationRejectsTraversalUnauthorizedPathsAndAnotherPrimary() {
        val root = files("first.mdx", "second.mdx")
        assertEquals(DictionaryProblem.INVALID_PATH, assertThrows(DictionaryException::class.java) {
            DictionaryFileGroups.plan(root, "first.mdx", setOf("../tone.wav"))
        }.problem)
        assertEquals(DictionaryProblem.MISSING_FILE, assertThrows(DictionaryException::class.java) {
            DictionaryFileGroups.plan(root, "first.mdx", setOf("unauthorized.wav"))
        }.problem)
        assertEquals(DictionaryProblem.UNSUPPORTED, assertThrows(DictionaryException::class.java) {
            DictionaryFileGroups.plan(root, "first.mdx", setOf("second.mdx"))
        }.problem)
    }

    @Test fun identityDoesNotDependOnSiblingChangesOrParentLocation() {
        val root = files("first.mdx", "second.mdx", "shared/diagram.png")
        val group = DictionaryFileGroups.plan(root, "first.mdx", setOf("shared/diagram.png"))
        val before = DictionaryIdentity.calculate(root, group)
        File(root, "second.mdx").writeText("unrelated change")
        File(root, "notes.txt").writeText("unrelated addition")
        assertEquals(before, DictionaryIdentity.calculate(root, group))
        File(root, "second.mdx").delete()
        assertEquals(before, DictionaryIdentity.calculate(root, group))
        val moved = temporary.newFolder()
        root.copyRecursively(moved, overwrite = true)
        assertEquals(before, DictionaryIdentity.calculate(moved,
            DictionaryFileGroups.plan(moved, "first.mdx", setOf("shared/diagram.png"))))
    }

    @Test fun actualBodyAndAssociatedResourceChangesAlterIdentity() {
        val root = files("first.mdx", "shared/diagram.png")
        val group = DictionaryFileGroups.plan(root, "first.mdx", setOf("shared/diagram.png"))
        assertEquals(2, group.identityVersion)
        val original = DictionaryIdentity.calculate(root, group)
        assertTrue(Regex("[0-9a-f]{64}").matches(original))
        File(root, "first.mdx").appendText("new definition")
        val editedBody = DictionaryIdentity.calculate(root, group)
        assertNotEquals(original, editedBody)
        File(root, "shared/diagram.png").appendText("new image")
        assertNotEquals(editedBody, DictionaryIdentity.calculate(root, group))
    }

    @Test fun oldFingerprintRetainsWholeDirectoryForVersionOneValidation() {
        val root = files("first.mdx", "second.mdx", "shared/diagram.png")
        assertEquals("fbab41a45f52bacd198ed2d8fa0c27073ab685c10e57b94505a8ef02fa347e5c",
            DictionaryIdentity.legacy(root, "first.mdx") {})
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            DictionaryIdentity.calculate(root, DictionaryFileGroups.plan(root, "first.mdx")) {
                throw kotlinx.coroutines.CancellationException("cancelled")
            }
        }
    }

    @Test fun copyingAPlannedGroupPublishesOnlyItsOwnFilesAndExplicitResources() {
        val root = files("first.mdx", "first.mdd", "second.mdx", "shared/diagram.png", "notes.txt")
        val group = DictionaryFileGroups.plan(root, "first.mdx", setOf("shared/diagram.png"))
        val published = temporary.newFolder()
        DictionaryFileGroups.copy(root, group, published) {}
        assertEquals(group.files, published.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(published).invariantSeparatorsPath }.sorted().toList())
        assertEquals("content:shared/diagram.png", File(published, "shared/diagram.png").readText())
    }
}
