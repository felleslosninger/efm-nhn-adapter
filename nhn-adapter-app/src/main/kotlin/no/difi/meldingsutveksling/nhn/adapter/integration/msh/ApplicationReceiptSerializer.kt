package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import jakarta.xml.bind.JAXBContext
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import no.kith.xmlstds.apprec._2012_02_15.AppRec
import no.ks.fiks.nhn.edi.v1_0.AppRecDeserializer

object ApplicationReceiptSerializer {
    private val context = JAXBContext.newInstance(AppRec::class.java)
    private val schema =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(
                arrayOf(
                    StreamSource(AppRecDeserializer::class.java.classLoader.getResourceAsStream("xsd/kith.xsd")),
                    StreamSource(AppRecDeserializer::class.java.classLoader.getResourceAsStream("xsd/apprec-v1.1.xsd")),
                )
            )

    fun serializeApplicationReceipt(receipt: AppRec): String {
        val xml = StringWriter().also { context.createMarshaller().marshal(receipt, it) }.toString()

        schema.newValidator().validate(StreamSource(StringReader(xml)))
        return xml
    }
}
