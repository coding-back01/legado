package io.legado.app.model.dictionary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryVariantIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "variant-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val store = LocalDictionaryStore(context)
    private val ids = arrayListOf<String>()
    @After fun cleanup() = runBlocking { ids.forEach { store.delete(it) }; root.deleteRecursively(); Unit }
    private suspend fun dictionary(primary: String, vararg names: String): String {
        val source = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        DictionaryTestFixtures.copy(source, primary, *names)
        return store.importFiles(source, primary).id.also { ids.add(it) }
    }

    @Test fun mdictVersionsEncodingsLzoAndObfuscatedIndexWorkOnAndroid() = runBlocking {
        for (name in listOf("legacy.mdx", "utf16.mdx", "gbk.mdx", "lzo.mdx", "obfuscated.mdx")) {
            val id = dictionary(name)
            assertTrue(name, store.lookup(id, "学习").first().html.contains("知识"))
        }
        val styled = dictionary("styled.mdx")
        assertTrue(store.lookup(styled, "style").first().html.contains("<b>meaning</b>"))
    }

    @Test fun resourceVolumesExternalStylesAndDslAndStardictResourcePackagesAreAssociated() = runBlocking {
        val mdict = dictionary("sample.mdx", "sample.mdd", "sample.1.mdd", "sample.css")
        assertTrue(store.resource(mdict, "diagram.png").isFile)
        assertTrue(store.resource(mdict, "tone.wav").length() > 1000)
        assertTrue(store.resource(mdict, "sample.css").isFile)
        val dsl = dictionary("sample.dsl", "sample.dsl.files.zip")
        assertTrue(store.resource(dsl, "diagram.png").isFile)
        assertTrue(store.resource(dsl, "tone.wav").isFile)
        val star = dictionary("sample.ifo", "sample.idx.gz", "sample.dict.dz", "sample.syn", "sample.res.zip")
        assertTrue(store.resource(star, "diagram.png").isFile)
        assertTrue(store.lookup(star, "went").isNotEmpty())
    }

    @Test fun wideOffsetBodyTypesCompressedXmlAndLatinBglKeepTheirContent() = runBlocking {
        val wide = dictionary("wide.ifo", "wide.idx", "wide.dict")
        assertTrue(store.lookup(wide, "学习").first().html.contains("知识"))
        val text = dictionary("text.ifo", "text.idx", "text.dict")
        assertTrue(store.lookup(text, "plain").first().html.contains("释义"))
        val xml = dictionary("xmlbody.ifo", "xmlbody.idx", "xmlbody.dict")
        assertTrue(store.lookup(xml, "xml").first().html.contains("释义"))
        val binary = dictionary("binary.ifo", "binary.idx", "binary.dict")
        assertTrue(store.lookup(binary, "sound").first().html.contains("sound://"))
        val xdxf = dictionary("sample.xdxf.dz")
        assertTrue(store.lookup(xdxf, "学习").first().html.contains("知识"))
        val bgl = dictionary("latin.bgl")
        assertEquals("définition", store.lookup(bgl, "café").first().html)
    }

    @Test fun stardict242KeepsNarrowOffsetsAndSynonyms() = runBlocking {
        val source = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        DictionaryTestFixtures.copy(source, "sample.ifo", "sample.idx.gz", "sample.dict.dz", "sample.syn")
        val info = File(source, "sample.ifo")
        info.writeText(info.readText().replace("version=3.0.0", "version=2.4.2"))
        val dictionary = store.importFiles(source, "sample.ifo").also { ids.add(it.id) }
        assertEquals("go", store.lookup(dictionary.id, "went").first().headword)
        assertTrue(store.lookup(dictionary.id, "学习").first().html.contains("知识"))
    }

    @Test fun mobiResourcesCrossRecordEntriesAndMissingLengthsRemainBounded() = runBlocking {
        val languages = dictionary("mobi-languages.mobi")
        val metadata = requireNotNull(io.legado.app.data.appDb.localDictionaryDao.get(languages))
        assertEquals("en", metadata.inputLanguage)
        assertEquals("zh", metadata.outputLanguage)
        assertEquals("być", store.lookup(languages, "jest").first().headword)
        val latin = dictionary("mobi-latin.mobi")
        val accented = store.lookup(latin, "byé").single()
        assertEquals("byé", accented.headword)
        assertTrue(accented.html.contains("café"))
        val resource = dictionary("mobi-resource.prc")
        val entry = store.lookup(resource, "jest").first()
        assertTrue(entry.html.contains("resource00000.png"))
        assertTrue(store.resource(resource, "resource00000.png").isFile)
        val cross = dictionary("mobi-cross-record.azw")
        assertEquals("być", store.lookup(cross, "jest").first().headword)
        assertEquals("mieć", store.lookup(cross, "mam").first().headword)
        val noLength = dictionary("mobi-no-length.mobi")
        val first = store.lookup(noLength, "jest").first()
        assertEquals("być", first.headword)
        assertFalse(first.html.contains("mieć"))
    }

    @Test fun circularAliasesFailWithoutHangingAndOriginalWordWins() = runBlocking {
        val id = dictionary("cycle.mdx")
        try { store.lookup(id, "alpha"); fail() } catch (e: DictionaryException) { assertEquals(DictionaryProblem.CORRUPT, e.problem) }
        val normal = dictionary("sample.mdx")
        assertEquals("go", store.lookup(normal, "went").first().headword)
        assertEquals("学习", store.lookup(normal, "学习").first().headword)
    }

    @Test fun missingOptionalResourcesAreReportedWhileTextRemainsQueryable() = runBlocking {
        val source = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        DictionaryTestFixtures.copy(source, "media.mdx")
        val dictionary = store.importFiles(source, "media.mdx").also { ids.add(it.id) }
        assertTrue(dictionary.warningCount > 0)
        assertTrue(store.lookup(dictionary.id, "media").single().html.contains("meaning"))
    }
}
