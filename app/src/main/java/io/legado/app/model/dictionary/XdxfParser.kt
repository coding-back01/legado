package io.legado.app.model.dictionary

import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser
import org.xml.sax.Attributes
import org.xml.sax.ext.DefaultHandler2
import java.io.File
import javax.xml.parsers.SAXParserFactory

class XdxfParser : DictionaryParser {
    override fun parse(file: File, workDir: File, sink: DictionarySink): DictionaryMetadata {
        var title = file.name.removeSuffix(".dz").removeSuffix(".xdxf")
        var language = ""
        var target = ""
        var id = 0L
        var rootSeen = false
        var entry: StringBuilder? = null
        var fullName: StringBuilder? = null
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val reader = factory.newSAXParser().xmlReader
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ ->
            throw DictionaryException(DictionaryProblem.UNSUPPORTED, "XML entity")
        }
        val handler = object : DefaultHandler2() {
            override fun startDTD(name: String?, publicId: String?, systemId: String?) {
                throw DictionaryException(DictionaryProblem.UNSUPPORTED, "XML DTD")
            }
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                sink.checkpoint()
                if (qName == "xdxf") {
                    rootSeen = true
                    language = attributes.getValue("lang_from").orEmpty()
                    target = attributes.getValue("lang_to").orEmpty()
                }
                if (qName == "full_name") fullName = StringBuilder()
                if (qName == "ar") entry = StringBuilder()
                entry?.apply {
                    append('<').append(qName)
                    for (i in 0 until attributes.length) {
                        append(' ').append(attributes.getQName(i)).append("=\"")
                            .append(Entities.escape(attributes.getValue(i))).append('"')
                    }
                    append('>')
                }
            }

            override fun characters(chars: CharArray, start: Int, length: Int) {
                fullName?.append(chars, start, length)
                DictionaryLimits.check((fullName?.length ?: 0) <= DictionaryLimits.MAX_WORD_BYTES, DictionaryProblem.LIMIT)
                entry?.apply {
                    append(Entities.escape(String(chars, start, length)))
                    DictionaryLimits.check(this.length <= DictionaryLimits.MAX_ENTRY_BYTES, DictionaryProblem.LIMIT)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                entry?.apply { append("</").append(qName).append('>') }
                if (qName == "full_name") {
                    title = fullName.toString()
                    fullName = null
                }
                if (qName == "ar") {
                    val xml = entry.toString()
                    val words = Jsoup.parse(xml, "", Parser.xmlParser()).select("k").map { it.text() }
                    DictionaryLimits.check(words.isNotEmpty())
                    sink.entry(id++, words, DictionaryMarkup.xdxf(xml))
                    DictionaryLimits.check(id <= DictionaryLimits.MAX_ENTRIES, DictionaryProblem.LIMIT)
                    entry = null
                }
            }
        }
        reader.contentHandler = handler
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        dictionaryInput(file, sink, xml = true).use { reader.parse(org.xml.sax.InputSource(it)) }
        DictionaryLimits.check(rootSeen && id > 0)
        return DictionaryMetadata(title, DictionaryFormat.XDXF, language, target)
    }
}
