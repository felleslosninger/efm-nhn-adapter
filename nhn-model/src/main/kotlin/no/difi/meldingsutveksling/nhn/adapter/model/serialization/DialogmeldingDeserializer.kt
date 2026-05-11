package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import java.io.StringReader
import javax.xml.transform.stream.StreamSource
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.ks.fiks.nhn.edi.XmlContext

object DialogmeldingDeserializer {
    @JvmStatic
    fun deserializeDialogmelding(xml: String): Dialogmelding {
        val dialogmelding =
            XmlContext.createUnmarshaller()
                .unmarshal(StreamSource(StringReader(xml)), Dialogmelding::class.java)
                .value
        XmlContext.validateObject(dialogmelding)
        return dialogmelding
    }
}