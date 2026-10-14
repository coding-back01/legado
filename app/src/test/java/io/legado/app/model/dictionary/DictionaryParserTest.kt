package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DictionaryParserTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Sink : DictionarySink {
        data class Entry(val id: Long, val words: List<String>, val html: String, val target: String?)
        val entries = arrayListOf<Entry>()
        val aliases = linkedMapOf<String, Long>()
        val resources = linkedMapOf<String, ByteArray>()
        override fun entry(id: Long, words: List<String>, html: String, target: String?) {
            entries.add(Entry(id, words, html, target))
        }
        override fun alias(word: String, targetId: Long) { aliases[word] = targetId }
        override fun resource(path: String, bytes: ByteArray) { resources[path] = bytes }
        override fun checkpoint() = Unit
    }

    private fun fixture(name: String, directory: File): File {
        val output = File(directory, name)
        requireNotNull(javaClass.getResourceAsStream("/dictionaries/$name")).use { input ->
            output.outputStream().use { input.copyTo(it) }
        }
        return output
    }

    @Test fun mdictReadsCrossBlockDefinitionsAliasesAndMdd() {
        val folder = temporary.newFolder()
        fixture("sample.mdd", folder)
        val source = fixture("sample.mdx", folder)
        val sink = Sink()
        val metadata = MdictParser().parse(source, temporary.newFolder(), sink)
        assertEquals("自建词库", metadata.title)
        assertEquals(listOf("go", "went", "学习"), sink.entries.flatMap { it.words })
        assertEquals("go", sink.entries[1].target)
        assertTrue(sink.entries[2].html.contains("获得知识"))
        assertTrue(sink.resources.getValue("diagram.png").size > 20)
    }

    @Test fun mdictReadsUncompressedBlocks() {
        val sink = Sink()
        MdictParser().parse(fixture("uncompressed.mdx", temporary.newFolder()), temporary.newFolder(), sink)
        assertEquals(2, sink.entries.size)
        assertTrue(sink.entries[0].html.contains("移动"))
    }

    @Test fun stardictReadsCompressedIndexBodyAndSynonyms() {
        val folder = temporary.newFolder()
        listOf("sample.idx.gz", "sample.dict.dz", "sample.syn").forEach { fixture(it, folder) }
        val sink = Sink()
        StarDictParser().parse(fixture("sample.ifo", folder), temporary.newFolder(), sink)
        assertEquals(listOf("go", "学习"), sink.entries.flatMap { it.words })
        assertEquals(0L, sink.aliases["went"])
        assertTrue(sink.entries[1].html.contains("获得知识"))
    }

    @Test fun stardictMissingIndexFails() {
        val folder = temporary.newFolder()
        fixture("sample.dict.dz", folder)
        val error = assertThrows(DictionaryException::class.java) {
            StarDictParser().parse(fixture("sample.ifo", folder), temporary.newFolder(), Sink())
        }
        assertEquals(DictionaryProblem.MISSING_FILE, error.problem)
    }

    @Test fun dslPreservesAlternativeWordsFormattingAndLinks() {
        for (name in listOf("sample.dsl", "sample.dsl.dz")) {
            val sink = Sink()
            DslParser().parse(fixture(name, temporary.newFolder()), temporary.newFolder(), sink)
            assertEquals(listOf("go", "went"), sink.entries.first().words)
            assertTrue(sink.entries.last().html.contains("<img"))
            assertTrue(sink.entries.last().html.contains("entry://lookup/go"))
        }
    }

    @Test fun dictdChecksOffsetsAndDecodesChinese() {
        val folder = temporary.newFolder()
        fixture("dictd.dict.dz", folder)
        val sink = Sink()
        DictdParser().parse(fixture("dictd.index", folder), temporary.newFolder(), sink)
        assertEquals("学习", sink.entries.last().words.single())
        assertTrue(sink.entries.last().html.contains("获得知识"))
    }

    @Test fun xdxfPreservesWordsAndLocalResourceLinks() {
        val sink = Sink()
        XdxfParser().parse(fixture("sample.xdxf", temporary.newFolder()), temporary.newFolder(), sink)
        assertEquals(listOf("go", "went"), sink.entries.first().words)
        assertTrue(sink.entries.last().html.contains("diagram.png"))
    }

    @Test fun xdxfDoesNotReadExternalEntities() {
        assertThrows(Exception::class.java) {
            XdxfParser().parse(fixture("external.xdxf", temporary.newFolder()), temporary.newFolder(), Sink())
        }
    }

    @Test fun bglReadsUtf8WordsAliasesAndEmbeddedImage() {
        val sink = Sink()
        val info = BglParser().parse(fixture("sample.bgl", temporary.newFolder()), temporary.newFolder(), sink)
        assertEquals("自建 BGL", info.title)
        assertEquals(listOf("go", "went"), sink.entries.first().words)
        assertTrue(sink.entries.last().html.contains("获得知识"))
        assertTrue(sink.resources.containsKey("diagram.png"))
    }

    @Test fun resourcePathsCannotEscapeTheirDirectory() {
        val folder = temporary.newFolder()
        for (path in listOf("../outside", "..\\outside", "/outside", "C:/outside", "sub/../../outside")) {
            assertThrows(DictionaryException::class.java) { DictionaryPaths.file(folder, path) }
        }
        assertEquals(File(folder, "images/a.png").canonicalFile, DictionaryPaths.file(folder, "images/a.png"))
    }
}
