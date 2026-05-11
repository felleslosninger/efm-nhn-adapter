package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import jakarta.xml.bind.JAXBContext
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.ks.fiks.nhn.edi.v1_0.AppRecDeserializer

object DialogmeldingSerializer {
    private val context = JAXBContext.newInstance(Dialogmelding::class.java)
    private val schema =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(
                arrayOf(
                    StreamSource(AppRecDeserializer::class.java.classLoader.getResourceAsStream("xsd/kith.xsd")),
                    StreamSource(
                        AppRecDeserializer::class.java.classLoader.getResourceAsStream("xsd/felleskomponent1.xsd")
                    ),
                    StreamSource(
                        AppRecDeserializer::class.java.classLoader.getResourceAsStream("xsd/dialogmelding-v1.1.xsd")
                    ),
                )
            )

    fun serializeDialogmelding(dialogmelding: Dialogmelding): String {
        val xml = StringWriter().also { context.createMarshaller().marshal(dialogmelding, it) }.toString()

        schema.newValidator().validate(StreamSource(StringReader(xml)))
        return xml
    }
}
