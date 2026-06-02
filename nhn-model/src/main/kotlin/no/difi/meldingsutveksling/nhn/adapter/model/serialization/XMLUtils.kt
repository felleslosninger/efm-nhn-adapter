package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering

object XMLUtils {

    private val factory = XMLInputFactory.newInstance()

    fun validateRootElement(xml: String, expectedRoot: String) {
        getRootElement(xml).also {
            if (it != expectedRoot) {
                throw ApplicationReceiptException(FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT)
            }
        }
    }

    fun getVersion(xml: String): String? {
        val reader = factory.createXMLStreamReader(StringReader(xml))

        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                if (reader.localName == "MIGversion") {
                    reader.next()
                    return reader.text
                }
            }
        }
        return null
    }

    fun getRootElement(xml: String): String? {
        val reader = factory.createXMLStreamReader(StringReader(xml))

        var iterations = 0
        while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT && iterations < 100) {
            iterations++
        }
        return reader.localName
    }
}