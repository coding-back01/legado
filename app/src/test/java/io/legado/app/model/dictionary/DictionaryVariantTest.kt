package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class DictionaryVariantTest {
    @get:Rule val temporary = TemporaryFolder()
    private class Sink : DictionarySink {
        val entries = arrayListOf<DictionaryEntry>()
        val words = arrayListOf<List<String>>()
        val resources = linkedMapOf<String, ByteArray>()
        override fun entry(id: Long, words: List<String>, html: String, target: String?) {
            entries.add(DictionaryEntry(id, words.first(), html)); this.words.add(words)
        }
        override fun alias(word: String, targetId: Long) = Unit
        override fun resource(path: String, bytes: ByteArray) { resources[path] = bytes }
        override fun checkpoint() = Unit
    }
    private fun fixture(name: String, folder: File = temporary.newFolder()): File = File(folder, name).also { file ->
        requireNotNull(javaClass.getResourceAsStream("/dictionaries/$name")).use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    @Test fun mdictVersionsEncodingsAndCompressionKeepSameChineseDefinition() {
        for (name in listOf("legacy.mdx", "utf16.mdx", "gbk.mdx", "lzo.mdx")) {
            val sink = Sink()
            MdictParser().parse(fixture(name), temporary.newFolder(), sink)
            assertEquals(name, listOf("go", "学习"), sink.entries.map { it.headword })
            assertTrue(name, sink.entries.last().html.contains("获得知识"))
        }
    }

    @Test fun mdictObfuscatedIndexUsesIndependentReferenceFixture() {
        val sink = Sink()
        MdictParser().parse(fixture("obfuscated.mdx"), temporary.newFolder(), sink)
        assertEquals(listOf("go", "went", "学习"), sink.entries.map { it.headword })
        assertTrue(sink.entries.last().html.contains("知识"))
    }

    @Test fun mdictNumericStylesAndMultipleResourceVolumesArePreserved() {
        val styled = Sink()
        MdictParser().parse(fixture("styled.mdx"), temporary.newFolder(), styled)
        assertTrue(styled.entries.single().html.contains("<b>meaning</b>"))
        assertTrue(styled.entries.single().html.contains("<i>example</i>"))
        val folder = temporary.newFolder()
        fixture("sample.mdd", folder); fixture("sample.1.mdd", folder)
        val sink = Sink()
        MdictParser().parse(fixture("sample.mdx", folder), temporary.newFolder(), sink)
        assertTrue(sink.resources.containsKey("diagram.png"))
        assertTrue(sink.resources.containsKey("tone.wav"))
    }

    @Test fun stardictWideOffsetsAndInvalidOffsetChecks() {
        val folder = temporary.newFolder()
        fixture("wide.dict", folder)
        val index = fixture("wide.idx", folder)
        val info = fixture("wide.ifo", folder)
        val sink = Sink()
        StarDictParser().parse(info, temporary.newFolder(), sink)
        assertEquals("学习", sink.entries.last().headword)
        RandomAccessFile(index, "rw").use { it.seek(3); it.writeLong(Long.MAX_VALUE) }
        assertThrows(DictionaryException::class.java) { StarDictParser().parse(info, temporary.newFolder(), Sink()) }
    }

    @Test fun stardictTextXmlAndEmbeddedAudioTypes() {
        for ((name, word) in listOf("text" to "plain", "xmlbody" to "xml", "binary" to "sound")) {
            val folder = temporary.newFolder()
            fixture("$name.idx", folder); fixture("$name.dict", folder)
            val sink = Sink()
            StarDictParser().parse(fixture("$name.ifo", folder), temporary.newFolder(), sink)
            assertEquals(word, sink.entries.single().headword)
            if (name == "binary") {
                assertTrue(sink.resources.values.single().size > 1000)
                assertTrue(sink.entries.single().html.contains("sound://"))
            } else assertTrue(sink.entries.single().html.contains("释义"))
        }
    }

    @Test fun bglDeclaredWindows1252EncodingPreservesAccents() {
        val sink = Sink()
        BglParser().parse(fixture("latin.bgl"), temporary.newFolder(), sink)
        assertEquals("café", sink.entries.single().headword)
        assertEquals("définition", sink.entries.single().html)
    }

    @Test fun dslCommentsOptionalHeadwordsAndEscapedParentheses() {
        val sink = Sink()
        DslParser().parse(fixture("optional.dsl"), temporary.newFolder(), sink)
        assertEquals(setOf("word", "words"), sink.words.first().toSet())
        assertEquals("学习", sink.entries.last().headword)
        assertFalse(sink.entries.any { it.headword.contains("注释") })
        assertEquals(listOf("literal(x)"), DslHeadwords.expand("literal\\(x\\)"))
        assertThrows(DictionaryException::class.java) { DslHeadwords.expand("broken(optional") }
    }

    @Test fun compressedXdxfAndMalformedXmlProducePredictableResults() {
        val sink = Sink()
        XdxfParser().parse(fixture("sample.xdxf.dz"), temporary.newFolder(), sink)
        assertEquals(listOf("go", "学习"), sink.entries.map { it.headword })
        val bad = File(temporary.newFolder(), "bad.xdxf").apply { writeText("<xdxf><ar><k>broken</k>") }
        assertThrows(Exception::class.java) { XdxfParser().parse(bad, temporary.newFolder(), Sink()) }
    }

    @Test fun bglZeroCrcVariantWorksButIncorrectLengthAndUnknownHeaderFail() {
        val file = fixture("sample.bgl")
        val bytes = file.readBytes()
        for (i in bytes.size - 8 until bytes.size - 4) bytes[i] = 0
        file.writeBytes(bytes)
        val sink = Sink()
        BglParser().parse(file, temporary.newFolder(), sink)
        assertTrue(sink.entries.last().html.contains("获得知识"))
        bytes[bytes.lastIndex] = 0x7f
        file.writeBytes(bytes)
        assertThrows(Exception::class.java) { BglParser().parse(file, temporary.newFolder(), Sink()) }
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
        assertThrows(DictionaryException::class.java) { BglParser().parse(file, temporary.newFolder(), Sink()) }
    }

    @Test fun dictdInvalidBase64AndOutOfRangeOffsetsFail() {
        val folder = temporary.newFolder()
        fixture("dictd.dict.dz", folder)
        val index = fixture("dictd.index", folder)
        index.writeText("bad\t!\tA\n")
        assertThrows(DictionaryException::class.java) { DictdParser().parse(index, temporary.newFolder(), Sink()) }
        index.writeText("bad\t//////\tB\n")
        assertThrows(DictionaryException::class.java) { DictdParser().parse(index, temporary.newFolder(), Sink()) }
    }

    @Test fun queryCacheEvictsOldEntriesAndNeverExceedsBudget() {
        val cache = DictionaryQueryCache(400)
        cache.put("a", "first", listOf(DictionaryEntry(1, "one", "x".repeat(70))))
        cache.put("a", "second", listOf(DictionaryEntry(2, "two", "y".repeat(70))))
        assertTrue(cache.bytes() <= 400)
        assertNull(cache.get("a", "first"))
        assertNotNull(cache.get("a", "second"))
        cache.remove("a")
        assertEquals(0L, cache.bytes())
        cache.put("b", "too-large", listOf(DictionaryEntry(3, "large", "z".repeat(1000))))
        assertNull(cache.get("b", "too-large"))
    }

    @Test fun dictionaryByteOffsetsRemainLongBeyondFourGiBWithoutReadingWholeFile() {
        val file = File(temporary.newFolder(), "sparse.bin")
        val offset = (1L shl 32) + 13
        val expected = "wide-offset-value".toByteArray()
        RandomAccessFile(file, "rw").use { body ->
            body.seek(offset)
            body.write(expected)
            assertArrayEquals(expected, body.readDictionaryBytes(offset, expected.size.toLong()))
        }
    }

    @Test fun declaredAllocationAndOversizedWordFailBeforeUnboundedAllocation() {
        val file = File(temporary.newFolder(), "oversized.mdx")
        java.io.DataOutputStream(file.outputStream()).use { it.writeInt(Int.MAX_VALUE) }
        val header = assertThrows(DictionaryException::class.java) { MdictParser().parse(file, temporary.newFolder(), Sink()) }
        assertEquals(DictionaryProblem.LIMIT, header.problem)
        val stream = java.io.DataInputStream(java.io.ByteArrayInputStream(ByteArray(DictionaryLimits.MAX_WORD_BYTES + 1) { 65 } + byteArrayOf(0)))
        val word = assertThrows(DictionaryException::class.java) { stream.readWord() }
        assertEquals(DictionaryProblem.LIMIT, word.problem)
    }

    @Test fun oversizedTextLineStopsBeforeReadingTheWholeInputAndCrLfIsPreserved() {
        val lines = arrayListOf<String>()
        java.io.StringReader("go\r\n学习\rlast\n").forDictionaryLines({}, lines::add)
        assertEquals(listOf("go", "学习", "last"), lines)
        val source = object : java.io.Reader() {
            var remaining = DictionaryLimits.MAX_ENTRY_BYTES.toLong() * 10
            override fun read(buffer: CharArray, offset: Int, size: Int): Int {
                if (remaining == 0L) return -1
                val count = minOf(size.toLong(), remaining).toInt()
                java.util.Arrays.fill(buffer, offset, offset + count, 'x')
                remaining -= count
                return count
            }
            override fun close() = Unit
        }
        val error = assertThrows(DictionaryException::class.java) { source.forDictionaryLines({}) {} }
        assertEquals(DictionaryProblem.LIMIT, error.problem)
        assertTrue(source.remaining > DictionaryLimits.MAX_ENTRY_BYTES)
    }
}
