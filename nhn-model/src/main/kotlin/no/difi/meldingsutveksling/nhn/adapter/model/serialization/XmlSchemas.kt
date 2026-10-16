package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator
import no.ks.fiks.nhn.edi.v1_0.AppRecDeserializer

/** Loads the KITH XSDs bundled with the fiks-nhn EDI library. */
object XmlSchemas {
    private val classLoader: ClassLoader = AppRecDeserializer::class.java.classLoader

    fun load(vararg xsdResources: String): Schema =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(xsdResources.map { StreamSource(classLoader.getResourceAsStream(it)) }.toTypedArray())
}

/** A validator that never reads an external DTD or schema referenced by the document it validates. */
fun Schema.newSecureValidator(): Validator =
    newValidator().apply {
        setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }
