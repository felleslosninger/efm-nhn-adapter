package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import jakarta.xml.bind.JAXBContext
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.stream.StreamSource
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.XmlSchemas
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.newSecureValidator
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding

object DialogmeldingSerializer {
    private val context = JAXBContext.newInstance(Dialogmelding::class.java)
    private val schema = XmlSchemas.load("xsd/kith.xsd", "xsd/felleskomponent1.xsd", "xsd/dialogmelding-v1.1.xsd")

    /** Serializes [dialogmelding] to XML, validated against the Dialogmelding 1.1 schema. */
    fun serializeDialogmelding(dialogmelding: Dialogmelding): String {
        val xml = StringWriter().also { context.createMarshaller().marshal(dialogmelding, it) }.toString()
        schema.newSecureValidator().validate(StreamSource(StringReader(xml)))
        return xml
    }
}
