package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptDeserializer
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

private val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val UTF_16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
private val UTF_16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())

/** The XML declaration is ASCII and comes first, so its encoding can be read before the document is decoded. */
private const val DECLARATION_MAX_BYTES = 200
private val ENCODING_DECLARATION = Regex("""^<\?xml\s[^>]*?\bencoding\s*=\s*["']([A-Za-z0-9._:-]+)["']""")

/**
 * The text of an XML document from its bytes. The parsers read text, not bytes, so the encoding is found here as an
 * XML parser would: from a byte order mark, else from the encoding in the XML declaration, else UTF-8. The mark is
 * left out, and the declaration says UTF-8, which the text is when written out again.
 *
 * @throws ApplicationReceiptException if the document is not valid in its encoding, or the encoding is not supported
 */
internal fun decodeText(bytes: ByteArray): String {
    val (charset, offset) = encodingOf(bytes)
    val text =
        try {
            charset
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
        } catch (e: CharacterCodingException) {
            throw ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.UGYLDIG_XML,
                "The document is not valid ${charset.name()}",
                e,
            )
        }
    return if (charset == Charsets.UTF_8) text else declareUtf8(text)
}

private fun encodingOf(bytes: ByteArray): Pair<Charset, Int> {
    fun startsWith(bom: ByteArray) = bytes.size >= bom.size && bom.indices.all { bytes[it] == bom[it] }
    return when {
        startsWith(UTF_8_BOM) -> Charsets.UTF_8 to UTF_8_BOM.size
        startsWith(UTF_16BE_BOM) -> Charsets.UTF_16BE to UTF_16BE_BOM.size
        startsWith(UTF_16LE_BOM) -> Charsets.UTF_16LE to UTF_16LE_BOM.size
        else -> declaredEncoding(bytes) to 0
    }
}

private fun declaredEncoding(bytes: ByteArray): Charset {
    val start = String(bytes, 0, minOf(bytes.size, DECLARATION_MAX_BYTES), Charsets.ISO_8859_1)
    val name = ENCODING_DECLARATION.find(start)?.groupValues?.get(1) ?: return Charsets.UTF_8
    return runCatching { Charset.forName(name) }
        .getOrElse {
            throw ApplicationReceiptException(
                FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT,
                "The encoding of the document is not supported",
            )
        }
}

private fun declareUtf8(text: String): String {
    val declaration = Regex("""^<\?xml\s[^>]*?\?>""").find(text) ?: return text
    val utf8 = declaration.value.replace(Regex("""\bencoding\s*=\s*["'][^"']*["']"""), "encoding=\"UTF-8\"")
    return utf8 + text.substring(declaration.range.last + 1)
}

/** DTDs are skipped, not processed: only the name of the root element is read. */
private val rootElementFactory =
    XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }

/**
 * Whether the document is an AppRec, as far as its root element tells, also when the document is otherwise invalid,
 * e.g. has a DOCTYPE or is not valid in its encoding. False when the root element cannot be found.
 */
internal fun isAppRec(bytes: ByteArray): Boolean =
    runCatching {
            // The name of the root element is ASCII, so readable also in an encoding that is not
            // supported
            val (charset, offset) = runCatching { encodingOf(bytes) }.getOrElse { Charsets.ISO_8859_1 to 0 }
            val reader =
                rootElementFactory.createXMLStreamReader(
                    StringReader(String(bytes, offset, bytes.size - offset, charset))
                )
            while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT) {
                // Skips the prolog, a DOCTYPE included
            }
            reader.eventType == XMLStreamConstants.START_ELEMENT &&
                reader.localName == ApplicationReceiptDeserializer.APP_REC_ROOT
        }
        .getOrDefault(false)
