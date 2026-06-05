package no.difi.meldingsutveksling.nhn.adapter.model.serialization

import jakarta.xml.bind.JAXBContext
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.XMLUtils.getVersion
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.XMLUtils.validateRootElement
import no.kith.xmlstds.CS
import no.kith.xmlstds.CV
import no.kith.xmlstds.URL
import no.kith.xmlstds.apprec._2004_11_21.AdditionalId as AdditionalId1_0
import no.kith.xmlstds.apprec._2004_11_21.Address as Address1_0
import no.kith.xmlstds.apprec._2004_11_21.AppRec as AppRec1_0
import no.kith.xmlstds.apprec._2004_11_21.AppRec.Receiver as Receiver1_0
import no.kith.xmlstds.apprec._2004_11_21.AppRec.Sender as Sender1_0
import no.kith.xmlstds.apprec._2004_11_21.CS as CS1_0
import no.kith.xmlstds.apprec._2004_11_21.CV as CV1_0
import no.kith.xmlstds.apprec._2004_11_21.Dept as Dept1_0
import no.kith.xmlstds.apprec._2004_11_21.HCP as HCP1_0
import no.kith.xmlstds.apprec._2004_11_21.HCPerson as HCPerson1_0
import no.kith.xmlstds.apprec._2004_11_21.HCProf as HCProf1_0
import no.kith.xmlstds.apprec._2004_11_21.Inst as Inst1_0
import no.kith.xmlstds.apprec._2004_11_21.OriginalMsgId as OriginalMsgId1_0
import no.kith.xmlstds.apprec._2004_11_21.URL as URL1_0
import no.kith.xmlstds.apprec._2012_02_15.AdditionalId
import no.kith.xmlstds.apprec._2012_02_15.Address
import no.kith.xmlstds.apprec._2012_02_15.AppRec
import no.kith.xmlstds.apprec._2012_02_15.AppRec.Receiver
import no.kith.xmlstds.apprec._2012_02_15.AppRec.Sender
import no.kith.xmlstds.apprec._2012_02_15.Dept
import no.kith.xmlstds.apprec._2012_02_15.HCP
import no.kith.xmlstds.apprec._2012_02_15.HCPerson
import no.kith.xmlstds.apprec._2012_02_15.HCProf
import no.kith.xmlstds.apprec._2012_02_15.Inst
import no.kith.xmlstds.apprec._2012_02_15.OriginalMsgId
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.edi.v1_0.AppRecDeserializer

object ApplicationReceiptDeserializer {
    const val APP_REC_ROOT = "AppRec"
    private const val APPREC_VERSION_1_0 = "1.0 2004-11-21"
    private const val APPREC_VERSION_1_1 = "v1.1 2012-02-15"

    private val context1_0 = JAXBContext.newInstance(AppRec1_0::class.java)
    private val context1_1 = JAXBContext.newInstance(AppRec::class.java)
    private val headSchema1_0 = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(
        arrayOf(
            StreamSource(AppRecDeserializer::class.java.classLoader.getResourceAsStream("xsd/kith.xsd")),
            StreamSource(AppRecDeserializer::class.java.classLoader.getResourceAsStream("xsd/apprec-v1.0.xsd")),
        )
    )
    private val headSchema1_1 = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(
        arrayOf(
            StreamSource(AppRecDeserializer::class.java.classLoader.getResourceAsStream("xsd/kith.xsd")),
            StreamSource(AppRecDeserializer::class.java.classLoader.getResourceAsStream("xsd/apprec-v1.1.xsd")),
        )
    )

    @JvmStatic
    fun deserializeAppRec(appRecXml: String): AppRec {
        validateRootElement(appRecXml, APP_REC_ROOT)
        return when (getAppRecVersion(appRecXml)) {
            AppRecVersion.V1_0 -> fromApplicationReceipt10(appRecXml)
            AppRecVersion.V1_1 -> fromApplicationReceipt11(appRecXml)
        }
    }

    private fun getAppRecVersion(appRecXml: String) = getVersion(appRecXml).let { version ->
        when (version) {
            APPREC_VERSION_1_0 -> AppRecVersion.V1_0
            APPREC_VERSION_1_1 -> AppRecVersion.V1_1
            else -> throw ApplicationReceiptException(FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT)
        }
    }

    private fun fromApplicationReceipt10(xml: String): AppRec {
        headSchema1_0.newValidator().validate(StreamSource(StringReader(xml)))
        val appRec =
            context1_0.createUnmarshaller().unmarshal(StreamSource(StringReader(xml)), AppRec1_0::class.java).value
        return toLatest(appRec)
    }

    private fun fromApplicationReceipt11(xml: String): AppRec {
        headSchema1_1.newValidator().validate(StreamSource(StringReader(xml)))
        return context1_1.createUnmarshaller().unmarshal(StreamSource(StringReader(xml)), AppRec::class.java).value
    }

    private fun toLatest(input: AppRec1_0): AppRec {
        return AppRec().apply {
            msgType = input.msgType?.toLatest()
            miGversion = input.miGversion
            softwareName = input.softwareName
            softwareVersion = input.softwareVersion
            genDate = input.genDate
            id = input.id
            sender = input.sender.toLatest()
            receiver = input.receiver.toLatest()
            status = input.status.toLatest()
            error = input.error.map { it.toLatest() }
            originalMsgId = input.originalMsgId?.toLatest()
        }
    }
}

private fun CS1_0.toLatest(): CS {
    val input = this
    return CS().apply {
        v = input.v
        dn = input.dn
    }
}

private fun CV1_0.toLatest(): CV {
    val input = this
    return CV().apply {
        v = input.v
        s = input.s
        dn = input.dn
        ot = input.ot
    }
}

private fun Sender1_0.toLatest(): Sender {
    val input = this
    return Sender().apply {
        role = input.role?.toLatest()
        hcp = input.hcp?.toLatest()
    }
}

private fun Receiver1_0.toLatest(): Receiver {
    val input = this
    return Receiver().apply {
        role = input.role?.toLatest()
        hcp = input.hcp.toLatest()
    }
}

private fun HCP1_0.toLatest(): HCP {
    val input = this
    return HCP().apply {
        inst = input.inst?.toLatest()
        hcProf = input.hcProf?.toLatest()
        medSpeciality = input.medSpeciality?.toLatest()
        address = input.address?.toLatest()
    }
}

private fun Address1_0.toLatest(): Address {
    val input = this
    return Address().apply {
        type = input.type?.toLatest()
        streetAdr = input.streetAdr
        postalCode = input.postalCode
        city = input.city
        county = input.county?.toLatest()
        country = input.country?.toLatest()
        cityDistr = input.cityDistr?.toLatest()
        teleAddress = input.teleAddress?.map { it.toLatest() }
    }
}

private fun URL1_0.toLatest(): URL {
    val input = this
    return URL().apply {
        v = input.v
    }
}

private fun HCProf1_0.toLatest(): HCProf {
    val input = this
    return HCProf().apply {
        type = input.type?.toLatest()
        name = input.name
        id = input.id
        typeId = input.typeId?.toLatest()
        additionalId = input.additionalId?.map { it.toLatest() }
    }
}

private fun AdditionalId1_0.toLatest(): AdditionalId {
    val input = this
    return AdditionalId().apply {
        id = input.id
        type = input.type?.toLatest()
    }
}

private fun Inst1_0.toLatest(): Inst {
    val input = this
    return Inst().apply {
        name = input.name
        id = input.id
        typeId = input.typeId?.toLatest()
        dept = input.dept?.map { it.toLatest() }
        additionalId = input.additionalId?.map { it.toLatest() }
        hcPerson = input.hcPerson?.map { it.toLatest() }
    }
}

private fun HCPerson1_0.toLatest(): HCPerson {
    val input = this
    return HCPerson().apply {
        name = input.name
        id = input.id
        typeId = input.typeId?.toLatest()
        additionalId = input.additionalId?.map { it.toLatest() }
    }
}

private fun Dept1_0.toLatest(): Dept {
    val input = this
    return Dept().apply {
        type = input.type?.toLatest()
        name = input.name
        id = input.id
        typeId = input.typeId?.toLatest()
        additionalId = input.additionalId?.map { it.toLatest() }
    }
}

private fun OriginalMsgId1_0.toLatest(): OriginalMsgId {
    val input = this
    return OriginalMsgId().apply {
        msgType = input.msgType.toLatest()
        issueDate = input.issueDate
        id = input.id
    }
}

private enum class AppRecVersion {
    V1_0, V1_1,
}
