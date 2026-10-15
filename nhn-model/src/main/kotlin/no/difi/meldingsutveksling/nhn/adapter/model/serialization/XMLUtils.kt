package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

object XMLUtils {
    private const val MIG_VERSION_ELEMENT = "MIGversion"

    /** Upper bound on prolog events (comments, processing instructions, ...) skipped before the root element. */
    private const val MAX_PROLOG_EVENTS = 100

    private val factory =
        XMLInputFactory.newInstance().apply { setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false) }

    fun validateRootElement(xml: String, expectedRoot: String) {
        if (getRootElement(xml) != expectedRoot) {
            throw ApplicationReceiptException(FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT)
        }
    }

    fun getVersion(xml: String): String? {
        val reader = createReader(xml)
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == MIG_VERSION_ELEMENT) {
                reader.next()
                return reader.text
            }
        }
        return null
    }

    fun getRootElement(xml: String): String? {
        val reader = createReader(xml)
        var skippedEvents = 0
        while (
            reader.hasNext() &&
                reader.next() != XMLStreamConstants.START_ELEMENT &&
                skippedEvents < MAX_PROLOG_EVENTS
        ) {
            skippedEvents++
        }
        return reader.localName
    }

    private fun createReader(xml: String): XMLStreamReader = factory.createXMLStreamReader(StringReader(xml))
}
